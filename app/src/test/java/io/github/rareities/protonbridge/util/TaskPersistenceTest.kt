package io.github.rareities.protonbridge.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Database.json.ImportPlan
import io.github.rareities.protonbridge.Database.json.Importer
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Items.Trigger
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class TaskPersistenceTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun newScheduleIdentityIsAllocatedUnlessExplicitlyRestoring() {
        val db = DatabaseHandler(context)
        val first = db.createTrigger(Trigger(0).apply { title = "First" })
        val second = db.createTrigger(Trigger(0).apply { title = "Second" })
        assertNotEquals(first.id, second.id)
        val restored = db.createTrigger(Trigger(501).apply { title = "Restored" }, withId = true)
        assertEquals(501L, restored.id)
        restored.title = "Edited"
        db.updateTrigger(restored)
        assertEquals("Edited", db.getTrigger(501)!!.title)
    }

    @Test fun policyAndNullableReferencesSurviveDatabaseRoundTrip() {
        val db = DatabaseHandler(context)
        val saved = db.createTask(Task(0).apply {
            title = "Bisync"
            direction = 6
            bisyncAdvancedOptions = "--bwlimit=8M"
            bisyncResyncMode = "path2"
            chargingOnly = true
            quietHoursStart = 22 * 60
            quietHoursEnd = 7 * 60
        })
        val read = db.getTask(saved.id)!!
        assertEquals(saved.bisyncUUID, read.bisyncUUID)
        assertEquals("path2", read.bisyncResyncMode)
        assertTrue(read.chargingOnly)
        assertEquals(saved.quietHoursEnd, read.quietHoursEnd)
        assertNull(read.filterId)
        assertNull(read.onFailFollowup)
    }

    @Test fun malformedImportCannotEraseExistingTasks() {
        val db = DatabaseHandler(context)
        val saved = db.createTask(Task(0).apply { title = "Keep this task"; direction = 3 })
        assertThrows(JSONException::class.java) { Importer.importJson("{broken", context) }
        assertEquals("Keep this task", db.getTask(saved.id)!!.title)
    }

    @Test fun failedRestoreTransactionRollsBackDeletes() {
        val db = DatabaseHandler(context)
        val saved = db.createTask(Task(0).apply { title = "Original"; direction = 3 })
        val duplicate = Task(10).apply { title = "Duplicate"; direction = 3 }
        assertThrows(Exception::class.java) { db.replaceAll(listOf(duplicate, duplicate), emptyList(), emptyList()) }
        assertEquals("Original", db.getTask(saved.id)!!.title)
    }

    @Test fun importedBisyncNeedsFreshBaselineAndDisabledSchedule() {
        val task = Task(7).apply { title = "Imported"; direction = 6; bisyncUUID = "existing-identity" }
        val trigger = Trigger(8).apply { title = "Daily"; triggerTarget = 7; time = 600; isEnabled = true; setWeekdays(127) }
        val root = JSONObject().put("tasks", JSONArray().put(task.asJSON()))
            .put("filters", JSONArray()).put("trigger", JSONArray().put(trigger.asJSON()))
        val imported = ImportPlan.parse(root.toString(), context)
        assertNotEquals("existing-identity", imported.tasks.first().bisyncUUID)
        assertFalse(imported.triggers.first().isEnabled)
    }

    @Test fun followupCyclesAreRejected() {
        val a = Task(1).apply { onSuccessFollowup = 2 }
        val b = Task(2).apply { onFailFollowup = 1 }
        assertThrows(IllegalArgumentException::class.java) { ImportPlan.validateFollowups(listOf(a, b)) }
    }

    @Test fun shortcutCapabilityIsBoundToTaskIdentity() {
        val first = DatabaseHandler(context).createTask(Task(0).apply { title = "First"; direction = 3 })
        val capability = ShortcutAuthorisation.issue(context, first)
        assertTrue(ShortcutAuthorisation.verify(context, first, capability))
        assertFalse(ShortcutAuthorisation.verify(context, first, "guess"))
        val replacement = Task(first.id).apply { bisyncUUID = "a-new-job-identity" }
        assertFalse(ShortcutAuthorisation.verify(context, replacement, capability))
    }
}
