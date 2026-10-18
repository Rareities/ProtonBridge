package io.github.rareities.protonbridge.Activities

import android.content.Intent
import android.os.SystemClock
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.R
import io.github.rareities.protonbridge.Rclone
import io.github.rareities.protonbridge.util.AdvancedOptions
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.ProcessRunner
import io.github.rareities.protonbridge.util.RunHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.rareities.protonbridge.workmanager.SyncManager
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONObject

/** Extends the existing task screen without replacing its remote and filter pickers. */
class TaskEditorControls(
    private val activity: AppCompatActivity,
    private val rclone: Rclone,
    private val taskProvider: () -> Task?,
    private val persist: () -> Task?,
    private val draftMatchesSaved: (Task) -> Boolean
) {
    private val panel = activity.findViewById<View>(R.id.bisync_options_panel)
    private val status = activity.findViewById<TextView>(R.id.bisync_status)
    private val preference = activity.findViewById<Spinner>(R.id.bisync_precedence)
    private val maxDelete = activity.findViewById<EditText>(R.id.bisync_max_delete)
    private val flags = activity.findViewById<EditText>(R.id.bisync_advanced_flags)
    private val checkAccess = activity.findViewById<Switch>(R.id.bisync_check_access)
    private val backupLocal = activity.findViewById<EditText>(R.id.bisync_backup_local)
    private val backupRemote = activity.findViewById<EditText>(R.id.bisync_backup_remote)
    private val approve = activity.findViewById<Button>(R.id.bisync_initialize_button)
    private val charging = activity.findViewById<Switch>(R.id.task_charging_only)
    private val battery = activity.findViewById<Switch>(R.id.task_battery_not_low)
    private val quietStart = activity.findViewById<EditText>(R.id.task_quiet_start)
    private val quietEnd = activity.findViewById<EditText>(R.id.task_quiet_end)
    private val run = activity.findViewById<Button>(R.id.task_run_button)
    private var observed: LiveData<List<WorkInfo>>? = null
    private var observedId: Long? = null
    private var active = false
    private var activeWorkState: WorkInfo.State? = null
    private var activeWorkMessage: String? = null
    private var verificationKey: String? = null
    private var verifiedFingerprint: String? = null
    private var verifiedAt = 0L
    private var checkingKey: String? = null
    private var verificationRevision = 0
    private var verificationError: String? = null

    init {
        val advanced = activity.findViewById<View>(R.id.bisync_advanced_panel)
        activity.findViewById<Button>(R.id.bisync_advanced_toggle).setOnClickListener { button ->
            val expanded = advanced.visibility != View.VISIBLE
            advanced.visibility = if (expanded) View.VISIBLE else View.GONE
            (button as Button).setText(if (expanded) R.string.bisync_advanced_hide else R.string.bisync_advanced_show)
        }
        approve.setOnClickListener { review() }
        run.setOnClickListener {
            val task = persist() ?: return@setOnClickListener
            observe(task)
            SyncManager(activity).queue(task.id)
        }
        activity.findViewById<Button>(R.id.task_cancel_button).setOnClickListener {
            taskProvider()?.let { SyncManager(activity).cancel(it.id.toString()) }
        }
        activity.findViewById<Button>(R.id.task_schedule_button).setOnClickListener { schedules() }
        activity.findViewById<Button>(R.id.task_history_button).setOnClickListener { history() }
        activity.findViewById<Button>(R.id.bisync_help_button).setOnClickListener { help() }
        activity.findViewById<Button>(R.id.task_proton_connection_button).setOnClickListener { protonConnection() }
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { onDraftChanged() }
            override fun afterTextChanged(s: Editable?) {}
        }
        listOf(maxDelete, flags, backupLocal, backupRemote).forEach { it.addTextChangedListener(watcher) }
        checkAccess.setOnCheckedChangeListener { _, _ -> onDraftChanged() }
        preference.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) { onDraftChanged() }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    fun populate(task: Task?) {
        maxDelete.setText((task?.bisyncMaxDelete ?: 10).toString())
        preference.setSelection(if (task?.bisyncResyncMode == "path2") 1 else 0)
        flags.setText(task?.bisyncAdvancedOptions ?: "")
        checkAccess.isChecked = task?.bisyncCheckAccess ?: false
        backupLocal.setText(task?.bisyncBackupDir1 ?: "")
        backupRemote.setText(task?.bisyncBackupDir2 ?: "")
        charging.isChecked = task?.chargingOnly ?: false
        battery.isChecked = task?.batteryNotLow ?: false
        quietStart.setText(formatTime(task?.quietHoursStart ?: -1))
        quietEnd.setText(formatTime(task?.quietHoursEnd ?: -1))
        task?.let { observe(it) }
    }

    fun applyTo(task: Task) {
        task.chargingOnly = charging.isChecked
        task.batteryNotLow = battery.isChecked
        task.quietHoursStart = parseTime(quietStart.text.toString())
        task.quietHoursEnd = parseTime(quietEnd.text.toString())
        require((task.quietHoursStart == -1 && task.quietHoursEnd == -1) ||
            (task.quietHoursStart >= 0 && task.quietHoursEnd >= 0 && task.quietHoursStart != task.quietHoursEnd)) {
            "Enter two different quiet-hour times, or leave both blank"
        }
        if (!BisyncPolicy.isBisync(task)) return
        task.bisyncMaxDelete = maxDelete.text.toString().toIntOrNull()
            ?.takeIf { it in 0..100 } ?: throw IllegalArgumentException("Deletion limit must be 0 to 100 percent")
        task.bisyncResyncMode = if (preference.selectedItemPosition == 1) "path2" else "path1"
        task.bisyncCheckAccess = checkAccess.isChecked
        task.bisyncBackupDir1 = backupLocal.text.toString().trim()
        task.bisyncBackupDir2 = backupRemote.text.toString().trim()
        task.bisyncAdvancedOptions = flags.text.toString()
        AdvancedOptions.parse(task.bisyncAdvancedOptions)
        AdvancedOptions.validateConnectionBudget(task.bisyncAdvancedOptions, rclone.taskTransfers(task))
    }

    fun mode(direction: Int) {
        panel.visibility = if (direction == 5 || direction == 6) View.VISIBLE else View.GONE
        refresh()
    }

    fun matchesSavedOptions(task: Task): Boolean =
        maxDelete.text.toString().toIntOrNull() == task.bisyncMaxDelete &&
            (if (preference.selectedItemPosition == 1) "path2" else "path1") == task.bisyncResyncMode &&
            flags.text.toString() == task.bisyncAdvancedOptions &&
            checkAccess.isChecked == task.bisyncCheckAccess &&
            backupLocal.text.toString().trim() == task.bisyncBackupDir1 &&
            backupRemote.text.toString().trim() == task.bisyncBackupDir2

    /** Editing immediately withdraws authorisation, even before the next worker starts. */
    fun onDraftChanged() {
        verificationRevision++
        verificationKey = null
        checkingKey = null
        verifiedFingerprint = null
        verificationError = null
        run.isEnabled = false
        approve.isEnabled = false
        if (panel.visibility == View.VISIBLE) refresh()
    }

    fun refreshAfterResume() {
        verificationRevision++
        verificationKey = null
        checkingKey = null
        verifiedFingerprint = null
        verificationError = null
        refresh()
    }

    private fun currentFingerprint(task: Task, savedState: JSONObject? = null, stopped: () -> Boolean): String {
        val filters = task.filterId?.takeIf { it >= 0 }?.let { id ->
            requireNotNull(DatabaseHandler(activity).getFilter(id)) { "The selected filter no longer exists" }.getFilters()
        } ?: arrayListOf()
        val effective = BisyncPolicy.effectiveTask(task, rclone.taskTransfers(task))
        val configIdentity = rclone.configurationIdentity { stopped() }
        val fingerprint = BisyncPolicy.fingerprint(effective, filters, configIdentity)
        if (savedState != null) {
            val legacy = BisyncPolicy.legacyFingerprint(effective, filters, configIdentity)
            if (BisyncStateStore.isReady(savedState, legacy)) return legacy
        }
        return fingerprint
    }

    private fun verifyInBackground(task: Task, state: JSONObject, key: String) {
        if (checkingKey == key) return
        checkingKey = key
        val revision = ++verificationRevision
        activity.lifecycleScope.launch {
            val job = currentCoroutineContext()
            val checked = withContext(Dispatchers.IO) {
                runCatching { currentFingerprint(task, state) { !job.isActive } }
            }
            if (checkingKey != key || verificationRevision != revision || activity.isFinishing) return@launch
            // A file can change while the engine identity is being checked.
            val latest = runCatching { BisyncStateStore(BisyncPolicy.workdir(activity, task)).read() }.getOrNull()
            checkingKey = null
            if (latest?.toString() != state.toString()) {
                refresh()
                return@launch
            }
            verificationKey = key
            verifiedFingerprint = checked.getOrNull()
            verificationError = checked.exceptionOrNull()?.let {
                io.github.rareities.protonbridge.util.DiagnosticRedactor.redact(it.message ?: "Could not check saved settings").take(500)
            }
            verifiedAt = SystemClock.elapsedRealtime()
            refresh()
        }
    }

    private fun observe(task: Task) {
        if (observedId == task.id) { refresh(); return }
        observed?.removeObservers(activity)
        observedId = task.id
        observed = WorkManager.getInstance(activity).getWorkInfosByTagLiveData(task.id.toString())
        observed?.observe(activity) { work ->
            val pending = work.filter { !it.state.isFinished }
            active = pending.isNotEmpty()
            val selected = pending.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: pending.firstOrNull()
            activeWorkState = selected?.state
            activeWorkMessage = selected?.progress?.getString(io.github.rareities.protonbridge.workmanager.SyncWorker.PROGRESS_MESSAGE)
            if (activeWorkMessage.isNullOrBlank() && selected != null && selected.state != WorkInfo.State.RUNNING) {
                val history = RunHistory.read(activity, task)
                for (i in history.length() - 1 downTo 0) {
                    val entry = history.optJSONObject(i) ?: continue
                    if (entry.optString("runId") == selected.id.toString() && entry.optString("status") == "WAITING") {
                        activeWorkMessage = entry.optString("message")
                        break
                    }
                }
            }
            refresh()
        }
    }

    fun refresh() {
        val task = taskProvider()
        var runEnabled = !active && panel.visibility != View.VISIBLE
        var approvalEnabled = false
        if (task != null && BisyncPolicy.isBisync(task) && task.bisyncUUID.isNotEmpty()) {
            try {
                val state = BisyncStateStore(BisyncPolicy.workdir(activity, task)).read()
                val code = state.optString("status", BisyncStateStore.NEEDS_INITIALISATION)
                val current = draftMatchesSaved(task)
                val checkable = current && !active
                val key = "${task.asJSON()}|${state}"
                val verified = checkable && verificationKey == key &&
                    SystemClock.elapsedRealtime() - verifiedAt in 0..15_000L
                val fingerprint = if (verified) verifiedFingerprint else null
                val canRun = fingerprint != null && BisyncStateStore.isRunnable(state, fingerprint,
                    AdvancedOptions.recoveryEnabled(task.bisyncAdvancedOptions))
                val label = if (active) {
                    if (!activeWorkMessage.isNullOrBlank()) "${activeWorkMessage}. Cancel below to stop it."
                    else if (activeWorkState == WorkInfo.State.RUNNING)
                        "Task running. Wait for it to finish, or cancel it below."
                    else "Task queued: waiting for network, charging or Android to start it. Cancel the queued task below to start over."
                } else when {
                    !current -> "Unsaved settings. Resync saves and confirms a new baseline."
                    !verified -> "Checking saved task and remote settings"
                    verificationError != null -> "Could not check saved settings: $verificationError. Close active operations and reopen this task to retry."
                    canRun && code == BisyncStateStore.READY -> "Ready"
                    canRun -> "Recovery enabled for the matching saved baseline. Run lets rclone validate and recover its listings."
                    code == BisyncStateStore.READY -> "Settings or engine changed. Confirm Resync to create a new baseline."
                    code in setOf(BisyncStateStore.NEEDS_RECOVERY, "RUNNING", "INITIALISING") ->
                        "Previous run incomplete. Enable recover or resilient for a matching baseline, or confirm Resync."
                    else -> "Select Resync to review settings and create the first baseline."
                }
                val lastSuccess = state.optLong("lastSuccessfulRun")
                val last = if (lastSuccess > 0) activity.getString(R.string.bisync_last_success, date(lastSuccess))
                    else activity.getString(R.string.bisync_never_run)
                status.text = activity.getString(R.string.bisync_status_format, label, last) +
                    state.optString("message").takeIf { it.isNotBlank() }?.let { "\n$it" }.orEmpty()
                if (!active && state.optString("message").isBlank()) {
                    val history = RunHistory.read(activity, task)
                    val lastRecord = if (history.length() > 0) history.optJSONObject(history.length() - 1) else null
                    if (lastRecord?.optString("status") in setOf("FAILED", "CANCELLED", "NEEDS_USER_ACTION"))
                        status.append("\nLast attempt: " + lastRecord?.optString("message").orEmpty().take(600))
                }
                approvalEnabled = !active
                runEnabled = !active && current && canRun
                if (checkable && !verified) verifyInBackground(task, state, key)
            } catch (e: Exception) { status.text = "Review and save this task before Resync"; runEnabled = false; approvalEnabled = !active }
        } else {
            status.text = if (active) "Task queued or running. Cancel it below before starting Resync."
                else "Select Resync to save this task and confirm its settings."
            if (panel.visibility == View.VISIBLE) { runEnabled = false; approvalEnabled = !active }
        }
        // Temporarily disabling an unchanged action cancels Android's queued
        // click callback. Apply only the final availability once per refresh.
        run.isEnabled = runEnabled
        approve.isEnabled = approvalEnabled
        val schedules = task?.let { saved -> DatabaseHandler(activity).allTrigger.filter { it.triggerTarget == saved.id } }.orEmpty()
        activity.findViewById<TextView>(R.id.task_schedule_summary).text = if (schedules.isEmpty())
            activity.getString(R.string.schedule_none) else schedules.joinToString("\n") {
                "${it.title}: ${if (!it.isEnabled) "disabled" else if (it.nextDue > 0) date(it.nextDue) else "scheduled"}"
            }
    }

    private fun review() {
        val task = persist() ?: return
        approve.isEnabled = false
        activity.lifecycleScope.launch {
            val job = currentCoroutineContext()
            val checked = withContext(Dispatchers.IO) {
                runCatching {
                    BisyncPolicy.validate(activity, task)
                    currentFingerprint(task) { !job.isActive }
                }
            }
            if (!job.isActive || activity.isFinishing) return@launch
            refresh()
            val approval = checked.getOrElse { error(it.message); return@launch }
            val filters = task.filterId?.takeIf { it >= 0 }?.let { id ->
                DatabaseHandler(activity).getFilter(id)?.getFiltersRaw()
            }.orEmpty()
            val summary = io.github.rareities.protonbridge.util.DiagnosticRedactor.redact(
                "Local: ${task.localPath}\nRemote: ${task.remoteId}:${task.remotePath}\n" +
                "Preference: ${if (task.bisyncResyncMode == "path1") "local" else "remote"}\n" +
                "Maximum deletions during normal runs: ${task.bisyncMaxDelete}%\n" +
                "Require access markers: ${task.bisyncCheckAccess}\n" +
                "Local backups: ${task.bisyncBackupDir1.ifBlank { "none" }}\n" +
                "Remote backups: ${task.bisyncBackupDir2.ifBlank { "none" }}\n" +
                "Transfers: ${rclone.taskTransfers(task)}\n" +
                "Filters: ${filters.ifBlank { "none" }}\n" +
                "Advanced options:\n${task.bisyncAdvancedOptions.ifBlank { "defaults" }}")
            val content = selectableText(activity.getString(R.string.bisync_review_warning, summary))
            AlertDialog.Builder(activity).setTitle(R.string.bisync_review_title).setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.bisync_approve) { _, _ ->
                    SyncManager(activity).initialiseBisync(task.id, approval)
                    observe(task)
                }.show()
        }
    }

    private fun schedules() {
        val task = persist() ?: return
        val items = DatabaseHandler(activity).allTrigger.filter { it.triggerTarget == task.id }
        val labels = items.map { it.title }.toMutableList().apply { add(activity.getString(R.string.schedule_add)) }
        AlertDialog.Builder(activity).setTitle(R.string.bisync_schedule).setItems(labels.toTypedArray()) { _, index ->
            val intent = Intent(activity, TriggerActivity::class.java)
            if (index < items.size) intent.putExtra(TriggerActivity.ID_EXTRA, items[index].id)
            else intent.putExtra("TASK_TARGET_ID", task.id)
            activity.startActivity(intent)
        }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun history() {
        val task = taskProvider() ?: run { error("Save the task first"); return }
        val records = RunHistory.read(activity, task)
        val text = (records.length() - 1 downTo 0).joinToString("\n\n") { i ->
            val record = records.getJSONObject(i)
            val finished = record.optLong("finished")
            val time = if (finished > 0) finished else record.optLong("started")
            val outcome = if (record.optString("status") == "WAITING") "Waiting; no terminal result confirmed"
                else if (record.optString("status") == "IN_PROGRESS")
                "Completion unconfirmed" else record.optString("status")
            "${date(time)}: ${record.optString("operation")} $outcome\n${record.optString("message")}"
        }.ifBlank { activity.getString(R.string.bisync_no_history) }
        AlertDialog.Builder(activity).setTitle(R.string.bisync_history).setView(selectableText(text))
            .setPositiveButton(android.R.string.ok, null).show()
    }

    private var referenceText: String? = null
    private fun help() {
        activity.lifecycleScope.launch {
            val jobContext = currentCoroutineContext()
            val output = referenceText ?: withContext(Dispatchers.IO) {
                val text = StringBuilder(activity.getString(R.string.bisync_reference_help) + "\n\n")
                runCatching {
                    for ((title, start) in listOf(
                        "Bisync flags" to { rclone.bisyncHelp() },
                        "Global and backend flags" to { rclone.globalFlagsHelp() }
                    )) {
                        text.append(title).append("\n\n")
                        var overflow = false
                        val result = ProcessRunner().run(start(), { !jobContext.isActive }, { line ->
                            if (text.length + line.length > 1_048_576) overflow = true
                            else text.append(line).append('\n')
                        }, null, 20_000L)
                        require(result.success() && !overflow) { "Could not load the complete bundled flag reference" }
                        text.append("\n")
                    }
                    text.toString()
                }.getOrElse { "Unable to load flag reference: ${it.message ?: it.javaClass.simpleName}" }
            }
            if (!isActive || activity.isFinishing) return@launch
            if (output.startsWith(activity.getString(R.string.bisync_reference_help))) referenceText = output
            val box = android.widget.LinearLayout(activity).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(24, 8, 24, 8)
            }
            val search = EditText(activity).apply {
                setHint(R.string.bisync_reference_search)
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                isSingleLine = true
            }
            val body = TextView(activity).apply { text = output; setTextIsSelectable(true); textSize = 13f }
            box.addView(search)
            box.addView(ScrollView(activity).apply { addView(body) }, android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            val entries = output.split(Regex("(?m)(?=^\\s*(?:-[^\\s,],\\s+)?--[a-z])"))
            search.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun afterTextChanged(s: Editable?) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val query = s.toString().trim()
                    body.text = if (query.isBlank()) output else entries.filter { it.contains(query, ignoreCase = true) }
                        .joinToString("\n").ifBlank { activity.getString(R.string.bisync_reference_no_matches) }
                }
            })
            AlertDialog.Builder(activity).setTitle(R.string.bisync_engine_help).setView(box)
                .setPositiveButton(android.R.string.ok, null).show().also { dialog ->
                    dialog.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        (activity.resources.displayMetrics.heightPixels * 0.85).toInt())
                }
        }
    }

    private fun protonConnection() {
        val task = persist() ?: return
        val code = EditText(activity).apply {
            setHint(R.string.proton_one_time_code)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            isSaveEnabled = false
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            if (android.os.Build.VERSION.SDK_INT >= 26) importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        AlertDialog.Builder(activity).setTitle(R.string.proton_connection_title)
            .setMessage(R.string.proton_connection_explanation).setView(code)
            .setNegativeButton(android.R.string.cancel, null)
            .setNeutralButton(R.string.edit) { _, _ ->
                code.text.clear()
                activity.startActivity(Intent(activity, io.github.rareities.protonbridge.RemoteConfig.RemoteConfig::class.java)
                    .putExtra(io.github.rareities.protonbridge.RemoteConfig.RemoteConfig.CONFIG_EDIT_TARGET, task.remoteId))
            }
            .setPositiveButton(R.string.proton_verify_connection) { _, _ ->
                val otp = code.text.toString()
                code.text.clear()
                val cancel = java.util.concurrent.atomic.AtomicBoolean()
                val progress = AlertDialog.Builder(activity).setMessage(R.string.proton_verifying)
                    .setNegativeButton(android.R.string.cancel) { _, _ -> cancel.set(true) }
                    .setOnCancelListener { cancel.set(true) }.create()
                progress.show()
                activity.lifecycleScope.launch {
                    val coroutine = currentCoroutineContext()
                    try {
                        val result = withContext(Dispatchers.IO) {
                            runCatching { rclone.reconnectProtonAccount(task.remoteId, otp) { cancel.get() || !coroutine.isActive } }
                        }
                        if (!activity.isFinishing && !activity.isDestroyed) {
                            result.fold({ Toast.makeText(activity, R.string.proton_verified, Toast.LENGTH_LONG).show() },
                                { error(it.message) })
                            refreshAfterResume()
                        }
                    } finally {
                        cancel.set(true)
                        progress.dismiss()
                    }
                }
            }.setOnDismissListener { code.text.clear() }.show()
    }

    private fun selectableText(text: String): ScrollView = ScrollView(activity).apply {
        addView(TextView(activity).apply { this.text = text; setPadding(32, 16, 32, 16); setTextIsSelectable(true) })
    }
    private fun error(message: String?) { Toast.makeText(activity, message ?: "Invalid task settings", Toast.LENGTH_LONG).show() }
    private fun date(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
    private fun formatTime(minutes: Int) = if (minutes < 0) "" else String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60)
    private fun parseTime(value: String): Int {
        if (value.isBlank()) return -1
        val parts = value.trim().split(':')
        require(parts.size == 2) { "Use HH:mm for quiet hours" }
        val hour = parts[0].toIntOrNull()
        val minute = parts[1].toIntOrNull()
        require(hour != null && minute != null && hour in 0..23 && minute in 0..59) { "Use a valid time from 00:00 to 23:59" }
        return hour * 60 + minute
    }
}
