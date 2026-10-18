package io.github.rareities.protonbridge.workmanager

import android.app.Notification
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
import androidx.annotation.StringRes
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.FilterEntry
import io.github.rareities.protonbridge.Items.RemoteItem
import io.github.rareities.protonbridge.Items.SyncDirectionObject
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.R
import io.github.rareities.protonbridge.Rclone
import io.github.rareities.protonbridge.notifications.GenericSyncNotification
import io.github.rareities.protonbridge.notifications.ReportNotifications
import io.github.rareities.protonbridge.notifications.SyncServiceNotifications
import io.github.rareities.protonbridge.notifications.SyncServiceNotifications.Companion.GROUP_ID
import io.github.rareities.protonbridge.notifications.support.StatusObject
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.DiagnosticRedactor
import io.github.rareities.protonbridge.util.FLog
import io.github.rareities.protonbridge.util.ForegroundStartPolicy
import io.github.rareities.protonbridge.util.ProcessRunner
import io.github.rareities.protonbridge.util.RunHistory
import io.github.rareities.protonbridge.util.ScheduleCalculator
import io.github.rareities.protonbridge.util.SyncLog
import io.github.rareities.protonbridge.util.WifiConnectivitiyUtil
import io.github.rareities.protonbridge.proton.ProtonAccountPolicy
import io.github.rareities.protonbridge.proton.ProtonAccountState
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.File
import java.util.Random
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

class SyncWorker(private val mContext: Context, params: WorkerParameters) : Worker(mContext, params) {
    companion object {
        const val PROGRESS_MESSAGE = "TASK_PROGRESS_MESSAGE"
        const val TASK_ID = "TASK_ID"
        const val TASK_EPHEMERAL = "TASK_EPHEMERAL"
        const val BISYNC_OPERATION = "BISYNC_OPERATION"
        const val BISYNC_APPROVAL = "BISYNC_APPROVAL"
        const val TASK_SYNC_ACTION = "START_TASK"
        const val TASK_CANCEL_ACTION = "CANCEL_TASK"
        const val EXTRA_TASK_ID = "task"
        const val EXTRA_TASK_SILENT = "notification"
        private const val TAG = "SyncWorker"
    }

    private val mRclone = Rclone(mContext)
    private val mDatabase = DatabaseHandler(mContext)
    private val mNotificationManager = SyncServiceNotifications(mContext)
    private val statusObject = StatusObject(mContext)
    private val terminal = AtomicBoolean()
    private val ongoingNotificationID = Random().nextInt()
    private var mTitle = "Transfer"
    @Volatile private var currentRunner = ProcessRunner()
    @Volatile private var currentProcess: Process? = null
    @Volatile private var cancelled = false
    private val diagnostics = StringBuilder()
    private var lastNotify = 0L
    private var lastHeartbeat = 0L
    private var phase = "Preparing task"
    @Volatile private var lastEngineOutput = 0L
    private var exitCode: Int? = null
    private var completed = false
    private var allowFollowup = false
    private var detail = ""
    private var startedAt = 0L
    private var accountIdentity: String? = null
    private var accountFailure: ProtonAccountPolicy.Action? = null
    private var needsUserAction = false
    private var notifyResult = true
    private var foregroundEstablished = false

    override fun doWork(): Result {
        val initialTask = try {
            if (inputData.keyValueMap.containsKey(TASK_EPHEMERAL)) {
                Json.decodeFromString<Task>(inputData.getString(TASK_EPHEMERAL) ?: "")
            } else mDatabase.getTask(inputData.getLong(TASK_ID, -1L))
        } catch (_: Exception) { null }
        if (initialTask == null) return Result.failure(Data.Builder().putString("error", "Task is missing or invalid").build())
        var task: Task = initialTask
        val operation = inputData.getString(BISYNC_OPERATION) ?: BisyncStateStore.RUN
        mTitle = task.title.ifBlank { "Task ${task.id}" }
        startedAt = System.currentTimeMillis()
        if (!requestMatches(task)) return failBeforeStart(task, operation, "This queued task was edited or replaced. Run the saved version explicitly.")
        allowFollowup = true
        if (isStopped) return failBeforeStart(task, operation, "Cancelled before the task started", true)
        val automatic = inputData.getBoolean(SyncManager.AUTOMATIC, false)
        if (automatic && ScheduleCalculator.afterQuietHours(System.currentTimeMillis(), task.quietHoursStart,
                task.quietHoursEnd, TimeZone.getDefault()) > System.currentTimeMillis()) return retryWaiting(task, operation, "Waiting until quiet hours end")
        if (!networkAvailable(task)) return if (runAttemptCount < 6) retryWaiting(task, operation, "Waiting for the required network")
            else failBeforeStart(task, operation, "Required network remained unavailable. Review the task constraints before retrying.")
        if (task.chargingOnly && !chargingAvailable()) return if (runAttemptCount < 6) retryWaiting(task, operation, "Waiting for charging")
            else failBeforeStart(task, operation, "Charging remained unavailable. This task did not start.")
        if (io.github.rareities.protonbridge.util.NativeSessionGate.GLOBAL.isBusy()) return if (runAttemptCount < 8) retryWaiting(task, operation, "Waiting for another native operation to finish")
            else failBeforeStart(task, operation, "Another native operation remained active. This task did not start.")
        val admission = try { BisyncPolicy.Admission.acquire(mContext) } catch (_: Exception) { null }
        if (admission == null) return if (runAttemptCount < 8) retryWaiting(task, operation, "Waiting for another transfer to finish")
            else failBeforeStart(task, operation, "Another transfer remained active. This task did not start.")
        try {
            if (!inputData.keyValueMap.containsKey(TASK_EPHEMERAL)) {
                allowFollowup = false
                task = requireNotNull(mDatabase.getTask(task.id)) { "The task was deleted while waiting" }
                val expected = inputData.getString("TASK_UUID")
                require(expected == null || expected == task.bisyncUUID) { "This queued request belongs to an older task configuration" }
                val revision = inputData.getString(SyncManager.TASK_REVISION)
                require(revision == null || revision == SyncManager.taskRevision(mContext, task)) {
                    "This queued task was edited. Run the saved version explicitly instead."
                }
                allowFollowup = true
            }
            require(task.validationError == null) { task.validationError ?: "Invalid task" }
            require(task.direction in 1..8) { "Unsupported task mode; edit this task before running it" }
            require(BisyncPolicy.isBisync(task) || operation == BisyncStateStore.RUN) { "This task does not support bisync initialisation" }
            RunHistory.record(mContext, task, id.toString(), operation, "IN_PROGRESS", startedAt, null,
                "Task started; a terminal result has not yet been confirmed.")
            prepareNotifications()
            mNotificationManager.setCancelId(id)
            mNotificationManager.updateSyncNotification(mTitle, "Preparing task", ArrayList(), 0, ongoingNotificationID)?.let {
                try {
                    setForegroundAsync(ForegroundInfo(ongoingNotificationID, it, FOREGROUND_SERVICE_TYPE_DATA_SYNC)).get(30, TimeUnit.SECONDS)
                } catch (e: java.util.concurrent.TimeoutException) {
                    throw java.io.IOException("Android did not start the foreground service within 30 seconds. Check background restrictions and retry.", e)
                }
            }
            foregroundEstablished = true
            if (isStopped) throw InterruptedException("Task was cancelled")
            publishPhase("Checking task and remote settings")
            // Foreground WorkManager owns the CPU wake lock for this worker.
            val filters = task.filterId?.takeIf { it >= 0 }?.let { filterId ->
                requireNotNull(mDatabase.getFilter(filterId)) { "The selected filter no longer exists" }.getFilters()
            } ?: arrayListOf()
            io.github.rareities.protonbridge.util.FilterRules.requireReviewedDeletion(task, filters)
            if (BisyncPolicy.isBisync(task)) handleBisync(task, operation, filters) else handleOneWay(task, filters)
        } catch (e: ProtonAccountState.NeedsUserAction) {
            needsUserAction = true
            allowFollowup = false
            notifyResult = ProtonAccountState.consumeNotification(mContext, e.identity)
            detail = e.message ?: "The Proton account needs user action"
            completed = false
        } catch (e: InterruptedException) {
            cancelled = true
            currentRunner.requestStop()
            detail = "Cancelled. Any interrupted bisync baseline requires review."
            Thread.currentThread().interrupt()
        } catch (e: Exception) {
            completed = false
            if (!foregroundEstablished && ForegroundStartPolicy.isDenied(e)) {
                allowFollowup = false
                needsUserAction = true
                detail = ForegroundStartPolicy.ACTION_REQUIRED
            } else detail = DiagnosticRedactor.redact(e.message ?: e.javaClass.simpleName)
        } finally {
            cancelled = cancelled || isStopped
            if (cancelled) currentRunner.requestStop()
            admission.closeAfterExit(currentProcess)
            try { deliverResult(task, operation) } catch (e: Exception) {
                FLog.e(TAG, "Could not deliver task result: ${e.javaClass.simpleName}")
            }
            mNotificationManager.cancelSyncNotification(ongoingNotificationID)
        }
        return if (completed && !cancelled) Result.success() else
            Result.failure(Data.Builder().putString("error", detail.take(1800)).build())
    }

    private fun retryWaiting(task: Task, operation: String, message: String): Result {
        // WorkManager removes progress on retry; keep the wait reason by request ID.
        try { RunHistory.record(mContext, task, id.toString(), operation, "WAITING", startedAt, null, message) }
        catch (e: Exception) { FLog.w(TAG, "Task waiting reason could not be recorded") }
        publishProgress(message)
        return Result.retry()
    }

    private fun publishProgress(message: String) {
        try { setProgressAsync(Data.Builder().putString(PROGRESS_MESSAGE, message.take(800)).build()) }
        catch (e: RuntimeException) { FLog.w(TAG, "Task progress could not be published") }
    }

    private fun publishPhaseNotification(message: String) {
        try {
            updateForegroundNotification(mNotificationManager.updateSyncNotification(
                mTitle, message, ArrayList(), 0, ongoingNotificationID))
        } catch (e: RuntimeException) { FLog.w(TAG, "Task notification could not be updated") }
    }

    private fun publishPhase(message: String) {
        phase = message
        lastHeartbeat = android.os.SystemClock.elapsedRealtime()
        publishProgress(message)
        publishPhaseNotification(message)
    }

    private fun heartbeat() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastHeartbeat < 15_000L) return
        lastHeartbeat = now
        val quietSeconds = (now - lastEngineOutput).coerceAtLeast(0) / 1000
        val message = "$phase · ${(System.currentTimeMillis() - startedAt).coerceAtLeast(0) / 1000}s elapsed" +
            if (quietSeconds >= 30) "; no engine output for ${quietSeconds}s. You can cancel below." else ""
        publishProgress(message)
        // Transfer statistics remain in the notification while the editor shows elapsed time.
        if (quietSeconds >= 30 || statusObject.notificationContent.isEmpty())
            publishPhaseNotification(message)
    }

    private fun failBeforeStart(task: Task, operation: String, message: String, stopped: Boolean = false): Result {
        detail = message
        cancelled = stopped || isStopped
        completed = false
        try {
            prepareNotifications()
            deliverResult(task, operation)
        } catch (e: Exception) {
            FLog.e(TAG, "Could not deliver task start failure: ${e.javaClass.simpleName}")
        }
        return Result.failure(Data.Builder().putString("error", message).build())
    }

    private fun handleOneWay(task: Task, filters: ArrayList<FilterEntry>) {
        val preparation = io.github.rareities.protonbridge.util.ConfigurationExclusion.prepare()
        try {
            mRclone.invalidateRemotesCache()
            val primary = mRclone.protonAccount(task.remoteId)
            primary?.let { ProtonAccountState.requireReady(mContext, it.identity) }
            if (task.direction == 7 || task.direction == 8) {
                mRclone.protonAccount(task.remoteId2)?.let { ProtonAccountState.requireReady(mContext, it.identity) }
                accountIdentity = null
            } else accountIdentity = primary?.identity
            val remote = requireNotNull(mRclone.getRemoteItemFromName(task.remoteId)) {
                "The configured task remote is unavailable; reconnect or edit the task"
            }
            val cloudToCloud = task.direction == 7 || task.direction == 8
            val child = if (cloudToCloud) mRclone.sync(remote, task.remotePath,
                requireNotNull(mRclone.getRemoteItemFromName(task.remoteId2)) {
                    "The configured destination remote is unavailable; reconnect or edit the task"
                }, task.remotePath2, task.direction,
                task.md5sum, filters, task.deleteExcluded, task.transfers?.toString())
            else mRclone.sync(remote, task.localPath, task.remotePath, task.direction,
                task.md5sum, filters, task.deleteExcluded, task.transfers?.toString())
            currentProcess = child
            preparation.close()
            publishPhase("Connecting and scanning files")
            val outcome = execute(child, true)
            completed = outcome.success()
            if (completed) sendUploadFinishedBroadcast(if (cloudToCloud) task.remoteId2 else task.remoteId,
                if (cloudToCloud) task.remotePath2 else task.remotePath)
        } finally { preparation.close() }
    }

    private fun handleBisync(savedTask: Task, operation: String, filters: ArrayList<FilterEntry>) {
        val preparation = io.github.rareities.protonbridge.util.ConfigurationExclusion.prepare()
        try {
            // Task.copy() only copies its constructor ID; serialize every option instead.
            val task = BisyncPolicy.effectiveTask(savedTask, mRclone.taskTransfers(savedTask))
            mRclone.invalidateRemotesCache()
            val account = mRclone.protonAccount(task.remoteId)
            accountIdentity = account?.identity
            account?.let { ProtonAccountState.requireReady(mContext, it.identity) }
            BisyncPolicy.validate(mContext, task)
            val directory = BisyncPolicy.workdir(mContext, task)
            val state = BisyncStateStore(directory)
            val configIdentity = mRclone.configurationIdentity { isStopped || cancelled }
            val fingerprint = BisyncPolicy.fingerprint(task, filters, configIdentity)
            if (operation == BisyncStateStore.RUN) state.migrateLegacyFingerprint(fingerprint,
                BisyncPolicy.legacyFingerprint(task, filters, configIdentity))
            val recoveryEnabled = io.github.rareities.protonbridge.util.AdvancedOptions.recoveryEnabled(task.bisyncAdvancedOptions)
            val childDirectory = when (operation) {
                BisyncStateStore.INITIALISE -> File(directory, "baselines/$id")
                BisyncStateStore.RUN -> {
                    val baseline = BisyncStateStore.baselineForRun(state.read(), fingerprint, recoveryEnabled)
                    File(directory, "baselines/$baseline").also {
                        require(it.isDirectory) { "The saved bisync baseline is missing. Review recovery before continuing." }
                    }
                }
                else -> throw IllegalArgumentException("Unsupported bisync operation")
            }
            require(childDirectory.isDirectory || childDirectory.mkdirs()) { "Cannot create bisync working folder" }
            // Parse every custom flag against the exact bundled engine before authentication or mutation.
            publishPhase("Checking rclone flags")
            val check = execute(mRclone.bisync(task, childDirectory, operation, filters, true), false)
            require(check.success()) { detail.ifBlank { "The selected engine rejected a task option" } }
            diagnostics.setLength(0)
            exitCode = null
            state.begin(operation, fingerprint, inputData.getString(BISYNC_APPROVAL), id.toString(), recoveryEnabled)
            var success = false
            try {
                if (isStopped || cancelled) throw InterruptedException("Cancelled before bisync started")
                val child = mRclone.bisync(task, childDirectory, operation, filters, false)
                currentProcess = child
                // launch registered its native lease under the same lock, so configuration
                // writers stay excluded after preparation releases its thread-bound lock.
                preparation.close()
                publishPhase(if (operation == BisyncStateStore.INITIALISE) "Resync: connecting and scanning files"
                    else "Bisync: connecting and scanning files")
                val outcome = execute(child, true)
                success = outcome.success() && !isStopped && !cancelled
                completed = success
                if (success) sendUploadFinishedBroadcast(task.remoteId, task.remotePath)
            } finally {
                state.finish(operation, fingerprint, id.toString(), success && !isStopped && !cancelled,
                    exitCode, detail)
            }
        } finally {
            preparation.close()
        }
    }

    private fun execute(child: Process?, progress: Boolean): ProcessRunner.Outcome {
        currentProcess = child
        currentRunner = ProcessRunner()
        lastEngineOutput = android.os.SystemClock.elapsedRealtime()
        val outcome = currentRunner.run(child, {
            heartbeat()
            isStopped || cancelled
        },
            { line -> lastEngineOutput = android.os.SystemClock.elapsedRealtime(); appendDiagnostic(line) },
            { line ->
                lastEngineOutput = android.os.SystemClock.elapsedRealtime()
                val safe = DiagnosticRedactor.redact(line)
                val json = try { JSONObject(safe) } catch (_: Exception) { null }
                val message = json?.optString("msg", safe) ?: safe
                val objectName = json?.optString("object").orEmpty()
                if (accountIdentity != null && objectName.isBlank() &&
                    (json == null || json.optString("level").equals("error", ignoreCase = true))) {
                    ProtonAccountPolicy.classify(message)?.let { accountFailure = it }
                }
                appendDiagnostic(if (objectName.isBlank()) message else "$objectName: $message")
                if (json != null && progress) {
                    try {
                        statusObject.parseLoglineToStatusObject(json)
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (now - lastNotify >= 5_000L && statusObject.notificationContent.isNotEmpty()) {
                            lastNotify = now
                            updateForegroundNotification(mNotificationManager.updateSyncNotification(
                                mTitle, statusObject.notificationContent, statusObject.notificationBigText,
                                statusObject.notificationPercent, ongoingNotificationID))
                            publishProgress("$phase: " + statusObject.notificationContent.take(400))
                        }
                    } catch (_: Exception) { /* Progress is advisory; the exit result remains authoritative. */ }
                }
            }, if (progress) 0L else 30_000L)
        exitCode = outcome.exitCode
        cancelled = cancelled || (outcome.cancelled && !outcome.timedOut)
        detail = when {
            outcome.timedOut -> "The local rclone flag check timed out. Cancel any remaining operation and retry; no bisync operation was started."
            cancelled -> "Cancelled. Bisync state may need recovery."
            !outcome.exited -> "The process did not confirm exit; another transfer remains blocked until it stops."
            outcome.streamFailure -> "Could not completely read the process output. Review the task before retrying."
            outcome.exitCode == null -> "The rclone process could not be started."
            outcome.exitCode != 0 -> io.github.rareities.protonbridge.util.BisyncFailureReport.exited(outcome.exitCode, diagnosticText())
            else -> "Completed successfully"
        }
        val account = accountIdentity
        val action = accountFailure
        if (!outcome.success() && !outcome.timedOut && !cancelled && outcome.exited && account != null && action != null) {
            ProtonAccountState.pause(mContext, account, action)
            needsUserAction = true
            allowFollowup = false
            notifyResult = ProtonAccountState.consumeNotification(mContext, account)
            detail = ProtonAccountPolicy.message(action)
        }
        return outcome
    }

    @Synchronized private fun appendDiagnostic(line: String) {
        diagnostics.append(DiagnosticRedactor.redact(line)).append('\n')
        if (diagnostics.length > 20000) diagnostics.delete(0, diagnostics.length - 20000)
    }

    @Synchronized private fun diagnosticText(): String = diagnostics.toString()

    private fun chargingAvailable(): Boolean {
        val battery = mContext.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            ?: return false
        val status = battery.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)
        return battery.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0) != 0 &&
            (status == android.os.BatteryManager.BATTERY_STATUS_CHARGING || status == android.os.BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun requestMatches(task: Task): Boolean = try {
        val expected = inputData.getString("TASK_UUID")
        val revision = inputData.getString(SyncManager.TASK_REVISION)
        (expected == null || expected == task.bisyncUUID) &&
            (revision == null || revision == SyncManager.taskRevision(mContext, task))
    } catch (_: Exception) { false }

    override fun onStopped() {
        cancelled = true
        currentRunner.requestStop()
        super.onStopped()
    }

    private fun deliverResult(task: Task, operation: String) {
        if (!terminal.compareAndSet(false, true)) return
        val status = if (cancelled) "CANCELLED" else if (completed) "SUCCESS" else if (needsUserAction) "NEEDS_USER_ACTION" else "FAILED"
        RunHistory.record(mContext, task, id.toString(), operation, status, startedAt, exitCode, detail)
        val notificationId = System.currentTimeMillis().toInt()
        if (notifyResult) when {
            cancelled -> mNotificationManager.showCancelledNotificationOrReport(mTitle, notificationId, task.id)
            completed -> mNotificationManager.showSuccessNotificationOrReport(mTitle,
                "Transfer completed successfully", notificationId)
            else -> mNotificationManager.showFailedNotificationOrReport(mTitle, detail, notificationId, task.id)
        }
        SyncLog.info(mContext, mTitle, "$status: $detail")
        if (allowFollowup && operation == BisyncStateStore.RUN && !cancelled) {
            val followup = if (completed) task.onSuccessFollowup else task.onFailFollowup
            if (followup != null && followup >= 0) {
                val chain = inputData.getLongArray(SyncManager.CHAIN_TASKS) ?: longArrayOf(task.id)
                if (!SyncManager(mContext).queueFollowup(followup, chain))
                    SyncLog.error(mContext, mTitle, "Follow-up stopped: repeated task or chain limit reached")
            }
        }
    }

    private fun networkAvailable(task: Task): Boolean {
        val connection = WifiConnectivitiyUtil.dataConnection(mContext)
        return connection != WifiConnectivitiyUtil.Connection.DISCONNECTED &&
            connection != WifiConnectivitiyUtil.Connection.NOT_AVAILABLE &&
            (!task.wifionly || connection != WifiConnectivitiyUtil.Connection.METERED)
    }

    private fun prepareNotifications() {

        GenericSyncNotification(mContext).setNotificationChannel(
                SyncServiceNotifications.CHANNEL_ID,
                getString(R.string.sync_service_notification_channel_title),
                getString(R.string.sync_service_notification_channel_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            SyncServiceNotifications.CHANNEL_SUCCESS_ID,
            getString(R.string.sync_service_notification_channel_success_title),
            getString(R.string.sync_service_notification_channel_success_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            SyncServiceNotifications.CHANNEL_FAIL_ID,
            getString(R.string.sync_service_notification_channel_fail_title),
            getString(R.string.sync_service_notification_channel_fail_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )
        GenericSyncNotification(mContext).setNotificationChannel(
            ReportNotifications.CHANNEL_REPORT_ID,
            getString(R.string.sync_service_notification_channel_report_title),
            getString(R.string.sync_service_notification_channel_report_description),
                GROUP_ID,
                getString(R.string.sync_service_notification_group)
        )

    }

    private fun sendUploadFinishedBroadcast(remote: String, path: String?) {
        val intent = Intent()
        intent.action = getString(R.string.background_service_broadcast)
        intent.putExtra(getString(R.string.background_service_broadcast_data_remote), remote)
        intent.putExtra(getString(R.string.background_service_broadcast_data_path), path)
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent)
    }

    // Creates an instance of ForegroundInfo which can be used to update the
    // ongoing notification.
    private fun updateForegroundNotification(notification: Notification?) {
        notification?.let {
            setForegroundAsync(ForegroundInfo(ongoingNotificationID, it, FOREGROUND_SERVICE_TYPE_DATA_SYNC))
        }
    }


    private fun getString(@StringRes id: Int): String = mContext.getString(id)
}
