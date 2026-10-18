package io.github.rareities.protonbridge

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.testing.TestWorkerBuilder
import io.github.rareities.protonbridge.Activities.MainActivity
import io.github.rareities.protonbridge.Activities.TaskActivity
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Database.json.BackupRestore
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Items.Trigger
import io.github.rareities.protonbridge.Services.TriggerService
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.NativeSessionGate
import io.github.rareities.protonbridge.util.RunHistory
import io.github.rareities.protonbridge.workmanager.SyncManager
import io.github.rareities.protonbridge.workmanager.SyncWorker
import com.google.common.util.concurrent.ListenableFuture
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Run stageForRestart, force-stop this disposable debug installation, then
 * verifyRestartRunsOneRealScheduledBisync. The second phase uses real production
 * WorkManager and foreground-service promotion, not a substituted worker.
 */
@RunWith(AndroidJUnit4::class)
class ScheduledRestartIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val marker get() = File(context.filesDir, "phase2-scheduled-restart.json")
    private fun environment() {
        assumeTrue(context.packageName.endsWith(".debug") &&
            (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")))
    }

    @Test fun stageForRestart() {
        environment()
        val db = DatabaseHandler(context)
        if (marker.isFile) {
            val old = JSONObject(marker.readText())
            db.getTrigger(old.getLong("triggerId"))?.let {
                it.isEnabled = false; db.updateTrigger(it); TriggerService(context).cancelTrigger(it.id)
            }
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("intro_protonbridge_v1_completed", true)
            .putBoolean(context.getString(R.string.pref_key_enable_vcp), false)
            .putBoolean(context.getString(R.string.pref_key_app_updates), false).commit()
        val root = File(context.filesDir, "phase2-scheduled-${UUID.randomUUID()}").apply { mkdirs() }
        val left = File(root, "left").apply { mkdirs() }
        val right = File(root, "right").apply { mkdirs() }
        File(left, "original.txt").writeText("before restart")
        val rclone = Rclone(context)
        val config = File(context.cacheDir, "phase2-schedule.conf")
        config.writeText("[phase2_schedule]\ntype = alias\nremote = ${right.absolutePath}\n")
        try { BackupRestore.installConfig(context, rclone, config, rclone.protectImportedConfig(config, null)) }
        finally { config.delete() }
        val settleDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (WorkManager.getInstance(context).getWorkInfosForUniqueWork("schedule-restore-reconcile")
                .get(5, TimeUnit.SECONDS).any { !it.state.isFinished }) {
            check(System.nanoTime() < settleDeadline) { "Restore schedule reconciliation did not settle" }
            Thread.sleep(50)
        }
        val task = db.createTask(Task(0).apply {
            title = "Scheduled bisync acceptance"; direction = 6
            localPath = left.absolutePath; remoteId = "phase2_schedule"; remotePath = "//phase2_schedule"
            remoteType = 0; wifionly = false
        })
        val state = BisyncStateStore(BisyncPolicy.workdir(context, task))
        val foreground = object : ForegroundUpdater {
            override fun setForegroundAsync(context: Context, id: UUID, info: ForegroundInfo): ListenableFuture<Void> =
                SettableFuture.create<Void>().also { it.set(null) }
        }
        fun initialise(operation: String, approval: String) {
            val data = Data.Builder().putLong(SyncWorker.TASK_ID, task.id).putString("TASK_UUID", task.bisyncUUID)
                .putString(SyncManager.TASK_REVISION, SyncManager.taskRevision(context, task))
                .putString(SyncWorker.BISYNC_OPERATION, operation).putString(SyncWorker.BISYNC_APPROVAL, approval).build()
            val worker = TestWorkerBuilder.from(context, SyncWorker::class.java, Executor { it.run() })
                .setForegroundUpdater(foreground).setInputData(data).build()
            assertTrue("Initialisation failed: ${RunHistory.read(context, task)}", worker.doWork() is ListenableWorker.Result.Success)
        }
        initialise(BisyncStateStore.INITIALISE, resyncFingerprint(context, task))
        File(left, "from-left.txt").writeText("local change during downtime")
        File(right, "from-right.txt").writeText("remote change during downtime")
        val trigger = Trigger(0).apply {
            title = "Missed interval acceptance"; triggerTarget = task.id
            type = Trigger.TRIGGER_TYPE_INTERVAL; time = 15; setWeekdays(127)
            isEnabled = true; zoneId = "Pacific/Auckland"
        }
        db.createTrigger(trigger)
        // Arm the real schedule first, then model a missed persisted occurrence.
        TriggerService(context).queueSingleTrigger(trigger)
        trigger.nextDue = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        db.updateTrigger(trigger)
        marker.writeText(JSONObject().put("taskId", task.id).put("uuid", task.bisyncUUID)
            .put("triggerId", trigger.id).put("root", root.absolutePath)
            .put("task", task.asJSON()).put("configIdentity", rclone.configurationIdentity { false })
            .put("fingerprint", state.read().getString("fingerprint"))
            .put("baseline", state.read().getString("baselineId")).toString())
    }

    @Test fun verifyRestartRunsOneRealScheduledBisync() {
        environment()
        assertTrue("Run the staging phase before force-stop/restart verification", marker.isFile)
        val fixture = JSONObject(marker.readText())
        val db = DatabaseHandler(context)
        val task = db.getTask(fixture.getLong("taskId"))!!
        assertEquals(fixture.getString("uuid"), task.bisyncUUID)
        val root = File(fixture.getString("root"))
        val left = File(root, "left"); val right = File(root, "right")
        val configIdentityBefore = Rclone(context).configurationIdentity { false }
        assertEquals("A clean restart should retain its healthy baseline",
            BisyncStateStore.READY, BisyncStateStore(BisyncPolicy.workdir(context, task)).read().getString("status"))
        assertEquals("Config identity changed before UI", fixture.getString("configIdentity"), configIdentityBefore)
        assertEquals("Task baseline fingerprint changed before UI; saved=${fixture.getJSONObject("task")}, loaded=${task.asJSON()}",
            fixture.getString("fingerprint"), BisyncPolicy.fingerprint(Task.fromString(task.asJSON().toString()).apply {
                transfers = Rclone(context).taskTransfers(task)
            }, emptyList(), configIdentityBefore))
        ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(75)
            var succeeded = false
            while (System.nanoTime() < deadline) {
                val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("task:${task.id}")
                    .get(5, TimeUnit.SECONDS)
                if (work.any { it.state == androidx.work.WorkInfo.State.FAILED })
                    fail("Production scheduled worker failed: ${RunHistory.read(context, task)}; configAfter=${Rclone(context).configurationIdentity { false }}; configBefore=$configIdentityBefore; remotes=${Rclone(context).remotes.map { it.name }}")
                if (work.any { it.state == androidx.work.WorkInfo.State.SUCCEEDED }) { succeeded = true; break }
                Thread.sleep(100)
            }
            assertTrue("Scheduled work did not finish: ${RunHistory.read(context, task)}", succeeded)
            assertEquals("local change during downtime", File(right, "from-left.txt").readText())
            assertEquals("remote change during downtime", File(left, "from-right.txt").readText())
            TriggerService(context).queueTrigger()
            TriggerService(context).queueTrigger()
            val history = RunHistory.read(context, task)
            val runs = (0 until history.length()).map { history.getJSONObject(it) }
                .filter { it.getString("operation") == BisyncStateStore.RUN }
            assertEquals("Missed intervals must coalesce to one run", 1, runs.size)
            assertEquals("SUCCESS", runs.single().getString("status"))
            assertTrue(db.getTrigger(fixture.getLong("triggerId"))!!.nextDue > System.currentTimeMillis())
            assertFalse(NativeSessionGate.GLOBAL.isBusy)
        }
        // Real task editor launch/recreation, with the selected native backend.
        ActivityScenario.launch<TaskActivity>(Intent(context, TaskActivity::class.java)
            .putExtra(TaskActivity.ID_EXTRA, task.id)).use { editor ->
            editor.onActivity { activity ->
                assertEquals(task.title, activity.findViewById<android.widget.EditText>(R.id.task_title_textfield).text.toString())
                assertNotNull(activity.findViewById<android.view.View>(R.id.bisync_advanced_flags))
            }
            editor.recreate()
            editor.onActivity { activity ->
                assertEquals(task.title, activity.findViewById<android.widget.EditText>(R.id.task_title_textfield).text.toString())
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            if (screenshot != null) File(context.cacheDir, "phase2-task-editor.png").outputStream().use { out ->
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
            }
        }
        marker.writeText(fixture.put("verified", true).toString())
        db.getTrigger(fixture.getLong("triggerId"))?.let {
            it.isEnabled = false
            db.updateTrigger(it)
            TriggerService(context).cancelTrigger(it.id)
        }
    }
}
