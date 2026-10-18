package io.github.rareities.protonbridge.Activities

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.EditText
import android.widget.Spinner
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.impl.WorkManagerImpl
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Filter
import io.github.rareities.protonbridge.Items.RemoteItem
import io.github.rareities.protonbridge.Items.SyncDirectionObject
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.BuildConfig
import io.github.rareities.protonbridge.R
import io.github.rareities.protonbridge.Services.TriggerService
import io.github.rareities.protonbridge.workmanager.SyncManager
import io.github.rareities.protonbridge.workmanager.SyncWorker
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.TimeUnit

/** Uses real editor widgets, SQLite and WorkManager; replaces only remote discovery/native execution. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskWorkflowIntegrationTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val db: DatabaseHandler get() = DatabaseHandler(app)

    @Before fun setUp() {
        TaskActivity.editorRemotesForTests = listOf(RemoteItem("demo", "local"))
        val configuration = Configuration.Builder().setWorkerFactory(object : WorkerFactory() {
            override fun createWorker(context: Context, workerClassName: String, params: WorkerParameters): ListenableWorker? {
                if (workerClassName != SyncWorker::class.java.name) return null
                return object : Worker(context, params) { override fun doWork() = Result.success() }
            }
        }).build()
        // Each Robolectric method has its own app/database. Avoid the static
        // WorkManager.initialize singleton from the preceding test method.
        androidx.work.testing.WorkManagerTestInitHelper.initializeTestWorkManager(app, configuration)
    }

    @After fun tearDown() {
        TaskActivity.editorRemotesForTests = null
        WorkManagerImpl.setDelegate(null)
    }

    private fun createSavedBisync(): Long {
        Robolectric.buildActivity(TaskActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<EditText>(R.id.task_title_textfield).setText("Documents")
            val local = File(app.filesDir, "sync-root").apply { mkdirs() }
            activity.findViewById<EditText>(R.id.task_local_path_textfield).setText(local.absolutePath)
            activity.findViewById<EditText>(R.id.task_remote_path_textfield).setText("documents")
            activity.findViewById<Spinner>(R.id.task_direction_spinner)
                .setSelection(SyncDirectionObject.spinnerPositionForDirection(SyncDirectionObject.SYNC_BIDIRECTIONAL))
            activity.findViewById<EditText>(R.id.bisync_advanced_flags).setText("--bwlimit=8M")
            activity.findViewById<android.view.View>(R.id.saveButton).performClick()
        }
        return db.allTasks.single().id
    }

    @Test fun editorSaveRecreationAndQueuedWorkUsePersistedSettings() {
        val id = createSavedBisync()
        val before = db.getTask(id)!!
        assertEquals("--bwlimit=8M", before.bisyncAdvancedOptions)
        assertEquals("documents", before.remotePath)
        assertEquals(SyncDirectionObject.SYNC_BIDIRECTIONAL, before.direction)
        assertTrue(before.bisyncUUID.isNotBlank())

        val editIntent = Intent(app, TaskActivity::class.java).putExtra(TaskActivity.ID_EXTRA, id)
        Robolectric.buildActivity(TaskActivity::class.java, editIntent).setup().use { controller ->
            val activity = controller.get()
            assertEquals("--bwlimit=8M", activity.findViewById<EditText>(R.id.bisync_advanced_flags).text.toString())
            activity.findViewById<EditText>(R.id.bisync_advanced_flags).setText("--bwlimit=3M")
            activity.findViewById<android.view.View>(R.id.saveButton).performClick()
        }
        val updated = db.getTask(id)!!
        assertEquals("--bwlimit=3M", updated.bisyncAdvancedOptions)
        assertEquals(before.bisyncUUID, updated.bisyncUUID)
        assertNotEquals(SyncManager.taskRevision(app, before), SyncManager.taskRevision(app, updated))

        SyncManager(app).queue(id)
        assertTrue(WorkManager.getInstance(app).getWorkInfosByTag(id.toString()).get(10, TimeUnit.SECONDS).isNotEmpty())
    }

    @Test fun invalidAdvancedFlagIsRejectedBeforeSqliteChanges() {
        val id = createSavedBisync()
        val original = db.getTask(id)!!
        val intent = Intent(app, TaskActivity::class.java).putExtra(TaskActivity.ID_EXTRA, id)
        Robolectric.buildActivity(TaskActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<EditText>(R.id.bisync_advanced_flags).setText("--resync-mode=path2")
            activity.findViewById<android.view.View>(R.id.saveButton).performClick()
            assertFalse(activity.isFinishing)
        }
        assertEquals(original.bisyncAdvancedOptions, db.getTask(id)!!.bisyncAdvancedOptions)
    }

    @Test fun editedFilterInvalidatesQueuedTaskRevision() {
        val id = createSavedBisync()
        val filter = db.createFilter(Filter(0).apply {
            title = "Selective"
            setFiltersRaw("+documents/**")
        })
        val intent = Intent(app, TaskActivity::class.java).putExtra(TaskActivity.ID_EXTRA, id)
        Robolectric.buildActivity(TaskActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<Spinner>(R.id.task_filter_spinner).setSelection(1)
            activity.findViewById<android.view.View>(R.id.saveButton).performClick()
        }
        val task = db.getTask(id)!!
        assertEquals(filter.id, task.filterId)
        val queuedRevision = SyncManager.taskRevision(app, task)
        SyncManager(app).queue(id)
        assertTrue(WorkManager.getInstance(app).getWorkInfosByTag(id.toString()).get(10, TimeUnit.SECONDS).isNotEmpty())
        filter.setFiltersRaw("-documents/private/**")
        db.updateFilter(filter)
        assertNotEquals(queuedRevision, SyncManager.taskRevision(app, db.getTask(id)!!))
    }

    @Test fun scheduleEditorPersistsThroughAlarmAdapterAndQueuesTask() {
        val id = createSavedBisync()
        val intent = Intent(app, TriggerActivity::class.java).putExtra("TASK_TARGET_ID", id)
        Robolectric.buildActivity(TriggerActivity::class.java, intent).setup().use { controller ->
            val activity = controller.get()
            activity.findViewById<EditText>(R.id.trigger_name_edit).setText("Morning")
            activity.findViewById<android.view.View>(R.id.saveButton).performClick()
        }
        val trigger = db.allTrigger.single()
        assertEquals(id, trigger.triggerTarget)
        assertTrue(trigger.nextDue > System.currentTimeMillis())
        trigger.nextDue = System.currentTimeMillis() - 1000
        db.updateTrigger(trigger)
        TriggerService(app).receiveTrigger(trigger.id)
        assertTrue(db.getTrigger(trigger.id)!!.nextDue > System.currentTimeMillis())
        assertTrue(WorkManager.getInstance(app).getWorkInfosByTag(id.toString()).get(10, TimeUnit.SECONDS).isNotEmpty())
    }

    @Test fun taskCreatedByEditorControlsSurvivesRecreationWithoutDuplication() {
        val savedState = Bundle()
        val launch = Intent(app, TaskActivity::class.java)
        val controller = Robolectric.buildActivity(TaskActivity::class.java, launch).setup()
        val activity = controller.get()
        activity.findViewById<EditText>(R.id.task_title_textfield).setText("Persistent editor")
        activity.findViewById<EditText>(R.id.task_local_path_textfield).setText(app.filesDir.absolutePath)
        activity.findViewById<EditText>(R.id.task_remote_path_textfield).setText("documents")
        activity.findViewById<android.view.View>(R.id.task_schedule_button).performClick()
        org.robolectric.shadows.ShadowDialog.getLatestDialog()?.dismiss()
        val saved = db.allTasks.single()
        controller.pause().saveInstanceState(savedState).stop().destroy()

        Robolectric.buildActivity(TaskActivity::class.java, launch).setup(savedState).use { recreated ->
            recreated.get().findViewById<android.view.View>(R.id.task_schedule_button).performClick()
            org.robolectric.shadows.ShadowDialog.getLatestDialog()?.dismiss()
            assertEquals("Recreation must edit the task saved by the control, not create a second task", 1, db.allTasks.size)
            assertEquals(saved.id, db.allTasks.single().id)
            assertEquals(saved.bisyncUUID, db.allTasks.single().bisyncUUID)
        }
    }

    @Test fun unavailableRemoteIsNotReplacedByTheFirstAvailableRemote() {
        val task = db.createTask(Task(0).apply {
            title = "Original target"; direction = SyncDirectionObject.COPY_LOCAL_TO_REMOTE
            remoteId = "removed-remote"; remotePath = "documents"; localPath = app.filesDir.absolutePath
        })
        val launch = Intent(app, TaskActivity::class.java).putExtra(TaskActivity.ID_EXTRA, task.id)
        Robolectric.buildActivity(TaskActivity::class.java, launch).setup().use { controller ->
            controller.get().findViewById<android.view.View>(R.id.saveButton).performClick()
            assertEquals("A missing remote must not silently become demo", "removed-remote", db.getTask(task.id)!!.remoteId)
            assertFalse("The editor must remain open for an explicit replacement", controller.get().isFinishing)
        }
    }

    @Test fun folderPickerUsesCurrentAuthorityAndPreservesEncodedNames() {
        Robolectric.buildActivity(TaskActivity::class.java).setup().use { controller ->
            val activity = controller.get()
            val path = "/a+b:unicode-\u00e9"
            val deliver = TaskActivity::class.java.getDeclaredMethod("onActivityResult",
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Intent::class.java).apply { isAccessible = true }
            val selected = DocumentsContract.buildTreeDocumentUri(BuildConfig.VCP_AUTHORITY, "rclone/remotes/demo:$path")
            deliver.invoke(activity, TaskActivity.REQUEST_CODE_FP_REMOTE, android.app.Activity.RESULT_OK, Intent().setData(selected))
            assertEquals(path, activity.findViewById<EditText>(R.id.task_remote_path_textfield).text.toString())
            val foreign = DocumentsContract.buildTreeDocumentUri("untrusted.provider", "rclone/remotes/demo:/elsewhere")
            deliver.invoke(activity, TaskActivity.REQUEST_CODE_FP_REMOTE, android.app.Activity.RESULT_OK, Intent().setData(foreign))
            assertEquals(path, activity.findViewById<EditText>(R.id.task_remote_path_textfield).text.toString())
        }
    }

    @Test fun advancedToolsStartCollapsedAndCanBeOpenedWithoutChangingSavedOptions() {
        val id = createSavedBisync()
        val before = db.getTask(id)!!.asJSON().toString()
        val launch = Intent(app, TaskActivity::class.java).putExtra(TaskActivity.ID_EXTRA, id)
        Robolectric.buildActivity(TaskActivity::class.java, launch).setup().use { controller ->
            val activity = controller.get()
            val panel = activity.findViewById<android.view.View>(R.id.bisync_advanced_panel)
            val toggle = activity.findViewById<android.widget.Button>(R.id.bisync_advanced_toggle)
            assertEquals(android.view.View.GONE, panel.visibility)
            toggle.performClick()
            assertEquals(android.view.View.VISIBLE, panel.visibility)
            toggle.performClick()
            assertEquals(android.view.View.GONE, panel.visibility)
            assertEquals(before, db.getTask(id)!!.asJSON().toString())
        }
    }

    @Test fun resyncIsAvailableWithoutPriorNativeOperation() {
        val task = db.getTask(createSavedBisync())!!
        val launch = Intent(app, TaskActivity::class.java).putExtra(TaskActivity.ID_EXTRA, task.id)
        Robolectric.buildActivity(TaskActivity::class.java, launch).setup().use { controller ->
            val button = controller.get().findViewById<android.widget.Button>(R.id.bisync_initialize_button)
            assertTrue("Resync opens explicit settings confirmation", button.isEnabled)
        }
    }

}
