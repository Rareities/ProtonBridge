package io.github.rareities.protonbridge

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isEnabled
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.matcher.ViewMatchers.withId
import org.hamcrest.Matchers.containsString
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.rareities.protonbridge.Activities.TaskActivity
import io.github.rareities.protonbridge.Activities.TriggerActivity
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.SyncDirectionObject
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Services.TriggerService
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.NativeSessionGate
import io.github.rareities.protonbridge.util.ProtectedRcloneConfig
import io.github.rareities.protonbridge.util.RunHistory
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Drives the real editor, explicit Resync confirmation, WorkManager and packaged native engine together.
 * No remote-discovery override, WorkerFactory, foreground updater or scheduler is substituted.
 */
@RunWith(AndroidJUnit4::class)
class TaskEditorDeviceIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext
    private val db get() = DatabaseHandler(context)
    private val work get() = WorkManager.getInstance(context)

    private fun await(label: String, timeout: Long = 20_000, ready: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout)
        while (System.nanoTime() < deadline) {
            if (ready()) return
            Thread.sleep(100)
        }
        assertTrue("Timed out: $label", ready())
    }

    private fun awaitNativeIdle() = await("native session release") {
        !NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()
    }

    private fun command(label: String, process: Process?) {
        val child = requireNotNull(process) { "$label did not start" }
        if (!child.waitFor(20, TimeUnit.SECONDS)) {
            child.destroy()
            if (!child.waitFor(5, TimeUnit.SECONDS)) {
                child.destroyForcibly()
                assertTrue(child.waitFor(5, TimeUnit.SECONDS))
            }
            fail("$label timed out")
        }
        assertEquals(label, 0, child.exitValue())
        awaitNativeIdle()
    }

    private fun jobs(task: Task) = work.getWorkInfosForUniqueWork("task:${task.id}").get(5, TimeUnit.SECONDS)

    private fun awaitNewSuccess(task: Task, previous: Set<UUID>) {
        await("real GUI-enqueued task ${task.id}", 100_000) {
            val newJobs = jobs(task).filter { it.id !in previous }
            if (newJobs.any { it.state == WorkInfo.State.FAILED || it.state == WorkInfo.State.CANCELLED })
                fail("Editor operation failed: ${RunHistory.read(context, task)}")
            newJobs.isNotEmpty() && newJobs.all { it.state == WorkInfo.State.SUCCEEDED }
        }
        awaitNativeIdle()
    }

    private fun awaitEnabled(editor: ActivityScenario<TaskActivity>, id: Int) {
        await("editor action $id to become available") {
            var enabled = false
            editor.onActivity { enabled = it.findViewById<Button>(id).isEnabled }
            enabled
        }
    }

    private fun showAdvancedIfNeeded(editor: ActivityScenario<TaskActivity>, id: Int) {
        if (id in setOf(R.id.bisync_help_button, R.id.bisync_advanced_flags))
            editor.onActivity {
                if (it.findViewById<android.view.View>(R.id.bisync_advanced_panel).visibility != android.view.View.VISIBLE)
                    it.findViewById<Button>(R.id.bisync_advanced_toggle).performClick()
            }
    }

    private fun press(editor: ActivityScenario<TaskActivity>, id: Int) {
        showAdvancedIfNeeded(editor, id)
        editor.onActivity {
            val button = it.findViewById<Button>(id)
            assertTrue("Editor action must be enabled", button.isEnabled)
        }
        onView(withId(id)).perform(scrollTo())
        instrumentation.waitForIdleSync()
        onView(withId(id)).check(matches(isDisplayed())).check(matches(isEnabled())).perform(click())
        instrumentation.waitForIdleSync()
    }

    private fun capture(name: String, control: Int) {
        if (control == R.id.bisync_advanced_flags) {
            var expanded = false
            onView(withId(R.id.bisync_advanced_panel)).check { view, _ -> expanded = view.visibility == android.view.View.VISIBLE }
            if (!expanded) onView(withId(R.id.bisync_advanced_toggle)).perform(scrollTo(), click())
        }
        onView(withId(control)).perform(scrollTo())
        instrumentation.waitForIdleSync()
        // Queue idleness alone can precede the rendered transition frame.
        instrumentation.uiAutomation.waitForIdle(500, 8000)
        if (name == "schedule-and-actions") Thread.sleep(3500) // Let the deliberately rejected flag's toast expire.
        val image = requireNotNull(instrumentation.uiAutomation.takeScreenshot()) { "Could not capture visible editor" }
        File(context.cacheDir, "final-qa-$name.png").outputStream().use {
            assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it))
        }
        image.recycle()
    }

    @Test(timeout = 240_000)
    fun createResyncRotateSyncEditAndScheduleThroughGui() {
        assumeTrue("Disposable API 35 debug emulator required",
            Build.VERSION.SDK_INT == 35 && context.packageName.endsWith(".debug") &&
                (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")))
        assertNull("Use real native remote discovery", TaskActivity.editorRemotesForTests)
        val root = File(context.filesDir, "gui-acceptance-${UUID.randomUUID()}").apply { check(mkdirs()) }
        val left = File(root, "left").apply { check(mkdirs()) }
        val right = File(root, "right").apply { check(mkdirs()) }
        File(left, "local.txt").writeText("local before initialisation")
        File(right, "remote.txt").writeText("remote before initialisation")
        val name = "gui_" + UUID.randomUUID().toString().replace("-", "")
        val title = "GUI acceptance $name"
        val rclone = Rclone(context)
        var aliasCreated = false
        var saved: Task? = null
        var editor: ActivityScenario<TaskActivity>? = null
        try {
            awaitNativeIdle()
            rclone.prepareProtectedConfig()
            command("create isolated GUI alias", rclone.configCreateNoInteract(
                mutableListOf(name, "alias", "remote", right.absolutePath)))
            aliasCreated = true
            editor = ActivityScenario.launch(Intent(context, TaskActivity::class.java))
            val screen = editor!!
            screen.onActivity { activity ->
                val remotes = activity.findViewById<Spinner>(R.id.task_remote_spinner)
                val position = (0 until remotes.count).first { remotes.getItemAtPosition(it).toString() == name }
                remotes.setSelection(position)
                activity.findViewById<Spinner>(R.id.task_direction_spinner).setSelection(
                    SyncDirectionObject.spinnerPositionForDirection(SyncDirectionObject.SYNC_BIDIRECTIONAL))
            }
            instrumentation.waitForIdleSync()
            screen.onActivity { activity ->
                activity.findViewById<EditText>(R.id.task_title_textfield).setText(title)
                activity.findViewById<EditText>(R.id.task_local_path_textfield).setText(left.absolutePath)
                activity.findViewById<EditText>(R.id.task_remote_path_textfield).setText("//$name")
                activity.findViewById<EditText>(R.id.bisync_advanced_flags).setText("--bwlimit=8M")
            }

            // Resync saves the task, but cancellation of confirmation must transfer nothing.
            press(screen, R.id.bisync_initialize_button)
            await("GUI-created SQLite task") { db.allTasks.any { it.title == title } }
            val task = db.allTasks.single { it.title == title }
            saved = task
            await("explicit Resync confirmation") {
                try {
                    onView(withText(R.string.bisync_approve)).inRoot(isDialog()).check(matches(isDisplayed()))
                    true
                } catch (_: androidx.test.espresso.NoMatchingViewException) { false }
                catch (_: androidx.test.espresso.NoMatchingRootException) { false }
            }
            onView(withText(android.R.string.cancel)).inRoot(isDialog()).perform(click())
            assertTrue("Cancel must not enqueue native work", jobs(task).isEmpty())
            val state = BisyncStateStore(BisyncPolicy.workdir(context, task))
            assertEquals(BisyncStateStore.NEEDS_INITIALISATION, state.read().getString("status"))
            assertFalse(File(right, "local.txt").exists())
            assertFalse(File(left, "remote.txt").exists())
            screen.recreate()
            screen.onActivity { activity ->
                assertEquals(title, activity.findViewById<EditText>(R.id.task_title_textfield).text.toString())
                assertEquals("--bwlimit=8M", activity.findViewById<EditText>(R.id.bisync_advanced_flags).text.toString())
            }
            awaitEnabled(screen, R.id.bisync_initialize_button)
            val beforeInitialise = jobs(task).map { it.id }.toSet()
            press(screen, R.id.bisync_initialize_button)
            await("explicit Resync confirmation") {
                try {
                    onView(withText(R.string.bisync_approve)).inRoot(isDialog()).check(matches(isDisplayed()))
                    true
                } catch (_: androidx.test.espresso.NoMatchingViewException) { false }
                catch (_: androidx.test.espresso.NoMatchingRootException) { false }
            }
            onView(withText(R.string.bisync_approve)).inRoot(isDialog()).perform(click())
            awaitNewSuccess(task, beforeInitialise)
            assertEquals(1, db.allTasks.count { it.title == title })
            assertEquals(task.bisyncUUID, db.getTask(task.id)!!.bisyncUUID)
            assertEquals(BisyncStateStore.READY, state.read().getString("status"))
            assertEquals("local before initialisation", File(right, "local.txt").readText())
            assertEquals("remote before initialisation", File(left, "remote.txt").readText())
            capture("bisync-ready", R.id.bisync_status)
            capture("advanced-options", R.id.bisync_advanced_flags)

            File(left, "left-change.txt").writeText("L")
            File(right, "right-change.txt").writeText("R")
            awaitEnabled(screen, R.id.task_run_button)
            val beforeRun = jobs(task).map { it.id }.toSet()
            val baselineBeforeRun = state.read().getString("baselineId")
            press(screen, R.id.task_run_button)
            awaitNewSuccess(task, beforeRun)
            assertEquals(baselineBeforeRun, state.read().getString("baselineId"))
            assertEquals("L", File(right, "left-change.txt").readText())
            assertEquals("R", File(left, "right-change.txt").readText())

            showAdvancedIfNeeded(screen, R.id.bisync_advanced_flags)
            screen.onActivity { activity ->
                val custom = activity.findViewById<EditText>(R.id.bisync_advanced_flags)
                custom.setText("--conflict-resolve newer\n--bwlimit 8M")
                assertFalse(activity.findViewById<Button>(R.id.task_run_button).isEnabled)
                custom.setText("--bwlimit=8M")
            }

            // Unsaved destructive options revoke the GUI's run permission immediately.
            awaitEnabled(screen, R.id.task_run_button)
            screen.onActivity { activity ->
                activity.findViewById<EditText>(R.id.bisync_advanced_flags).setText("--force")
                assertFalse(activity.findViewById<Button>(R.id.task_run_button).isEnabled)
                activity.findViewById<android.view.View>(R.id.saveButton).performClick()
                assertFalse(activity.isFinishing)
            }
            assertEquals("--bwlimit=8M", db.getTask(task.id)!!.bisyncAdvancedOptions)
            screen.onActivity { it.findViewById<EditText>(R.id.bisync_advanced_flags).setText("--bwlimit=8M") }
            awaitEnabled(screen, R.id.task_run_button)

            press(screen, R.id.task_schedule_button)
            onView(withText(R.string.schedule_add)).inRoot(isDialog()).perform(click())
            await("real schedule editor") {
                var found = false
                instrumentation.runOnMainSync {
                    found = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                        .any { it is TriggerActivity }
                }
                found
            }
            instrumentation.runOnMainSync {
                val schedule = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<TriggerActivity>().single()
                schedule.findViewById<EditText>(R.id.trigger_name_edit).setText("GUI daily $name")
                schedule.findViewById<android.view.View>(R.id.saveButton).performClick()
            }
            await("saved GUI schedule and next-run summary") {
                var visible = false
                screen.onActivity {
                    visible = it.findViewById<TextView>(R.id.task_schedule_summary).text.contains("GUI daily $name")
                }
                db.allTrigger.any { it.triggerTarget == task.id && it.nextDue > System.currentTimeMillis() } && visible
            }
            assertEquals(1, db.allTasks.count { it.title == title })
            capture("schedule-and-actions", R.id.task_schedule_summary)
        } finally {
            editor?.close()
            saved?.let { task ->
                for (trigger in db.allTrigger.filter { it.triggerTarget == task.id }) {
                    TriggerService(context).cancelTrigger(trigger.id)
                    db.deleteTrigger(trigger.id)
                }
                work.cancelUniqueWork("task:${task.id}").result.get(10, TimeUnit.SECONDS)
            }
            awaitNativeIdle()
            if (aliasCreated) command("remove only the GUI alias", rclone.config("delete", mutableListOf(name)))
            saved?.let { db.deleteTask(it.id) }
            assertTrue("Remove only this test's local trees", root.deleteRecursively())
        }
    }
}
