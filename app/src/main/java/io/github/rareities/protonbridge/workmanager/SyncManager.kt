package io.github.rareities.protonbridge.workmanager

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Items.Trigger
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.ScheduleCalculator
import java.security.MessageDigest
import java.util.Random
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class SyncManager(private val mContext: Context) {
    companion object {
        const val SYNC_WORK_TAG = "sync_work"
        const val CHAIN_TASKS = "CHAIN_TASKS"
        const val AUTOMATIC = "AUTOMATIC"
        const val MAX_CHAIN_LENGTH = 16
        const val TASK_REVISION = "TASK_REVISION"

        /** Captures what a queued action was authorised to execute. */
        @JvmStatic fun taskRevision(context: Context, task: Task): String {
            val filter = task.filterId?.takeIf { it >= 0 }?.let { id ->
                requireNotNull(DatabaseHandler(context).getFilter(id)) { "The selected filter no longer exists" }
                    .getFiltersRaw()
            } ?: ""
            val digest = MessageDigest.getInstance("SHA-256")
            for (part in listOf(task.asJSON().toString(), filter)) {
                val bytes = part.toByteArray(Charsets.UTF_8)
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.size).array())
                digest.update(bytes)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }
    }

    fun queue(trigger: Trigger) { if (trigger.isEnabled) queueTask(trigger.triggerTarget, automatic = true) }
    fun queue(task: Task) { queue(task.id) }
    fun queue(taskID: Long) { queueTask(taskID) }
    fun initialiseBisync(taskID: Long, approval: String) {
        require(approval.matches(Regex("[0-9a-f]{64}"))) { "Confirm the current Resync settings first" }
        queueTask(taskID, operation = BisyncStateStore.INITIALISE, approval = approval)
    }

    fun queueFollowup(taskID: Long, previous: LongArray): Boolean {
        if (taskID in previous || previous.size >= MAX_CHAIN_LENGTH) return false
        queueTask(taskID, automatic = true, chain = previous)
        return true
    }

    private fun queueTask(taskID: Long, operation: String = BisyncStateStore.RUN,
                          approval: String? = null, automatic: Boolean = false,
                          chain: LongArray = longArrayOf()) {
        val task = DatabaseHandler(mContext).getTask(taskID) ?: return
        val data = Data.Builder().putLong(SyncWorker.TASK_ID, taskID)
            .putString("TASK_UUID", task.bisyncUUID)
            .putString(TASK_REVISION, taskRevision(mContext, task))
            .putString(SyncWorker.BISYNC_OPERATION, operation)
            .putString(SyncWorker.BISYNC_APPROVAL, approval)
            .putBoolean(AUTOMATIC, automatic)
            .putLongArray(CHAIN_TASKS, chain + taskID)
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setInputData(data.build())
            .setConstraints(constraints(task))
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(taskID.toString()).addTag(SYNC_WORK_TAG)
        if (automatic) {
            val now = System.currentTimeMillis()
            val due = ScheduleCalculator.afterQuietHours(now, task.quietHoursStart, task.quietHoursEnd, TimeZone.getDefault())
            if (due > now) request.setInitialDelay(due - now, TimeUnit.MILLISECONDS)
        }
        WorkManager.getInstance(mContext)
            .enqueueUniqueWork("task:$taskID", ExistingWorkPolicy.KEEP, request.build())
    }

    private fun constraints(task: Task): Constraints = Constraints.Builder()
        .setRequiredNetworkType(if (task.wifionly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(task.chargingOnly)
        .setRequiresBatteryNotLow(task.batteryNotLow)
        .setRequiresStorageNotLow(true).build()

    fun queueEphemeral(task: Task) {
        task.id = Random().nextLong()
        val data = Data.Builder().putString(SyncWorker.TASK_EPHEMERAL, task.asJSON().toString()).build()
        val request = OneTimeWorkRequestBuilder<SyncWorker>().setInputData(data)
            .setConstraints(constraints(task)).addTag(task.id.toString()).addTag(SYNC_WORK_TAG).build()
        WorkManager.getInstance(mContext).enqueue(request)
    }
    fun cancel() { WorkManager.getInstance(mContext).cancelAllWorkByTag(SYNC_WORK_TAG) }
    fun cancel(tag: String) { WorkManager.getInstance(mContext).cancelAllWorkByTag(tag) }
}
