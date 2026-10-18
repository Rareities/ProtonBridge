package io.github.rareities.protonbridge.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Database.json.Exporter
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Items.Trigger
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class RetentionRecoveryTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()

    @Test fun stagingRecoveryKeepsActiveNativeFilesAndUnrelatedData() {
        val active = ConfigStaging.create(context, "config-import-", true)
        val activeBackup = File(active.path + ".old123").apply { writeText("active") }
        val abandoned = File(context.cacheDir, "config-protect-123.conf.old77").apply { writeText("abandoned") }
        val unrelated = File(context.cacheDir, "user-photo.conf").apply { writeText("user data") }
        try {
            ConfigStaging.recover(context)
            assertTrue(active.exists())
            assertTrue(activeBackup.exists())
            assertFalse(abandoned.exists())
            assertEquals("user data", unrelated.readText())
        } finally {
            ConfigStaging.discard(active)
            ConfigStaging.recover(context)
        }
        assertFalse(activeBackup.exists())
    }

    @Test fun terminalHistoryReplacesUnconfirmedRecordAndBoundsEscapedUnicode() {
        val task = Task(1).apply { bisyncUUID = UUID.randomUUID().toString() }
        RunHistory.record(context, task, "run", "run", "IN_PROGRESS", 1234L, null, "Starting")
        assertTrue(RunHistory.read(context, task).getJSONObject(0).isNull("finished"))
        RunHistory.record(context, task, "run", "run", "FAILED", 1234L, 8, "Failed")
        val completed = RunHistory.read(context, task)
        assertEquals(1, completed.length())
        assertEquals("FAILED", completed.getJSONObject(0).getString("status"))
        repeat(55) {
            RunHistory.record(context, task, "run-$it", "run", "FAILED", 1234L, 8, "\u0001".repeat(2048))
        }
        assertTrue(RunHistory.read(context, task).length() <= 40)
        assertEquals("run-54", RunHistory.read(context, task).getJSONObject(RunHistory.read(context, task).length() - 1).getString("runId"))
        assertTrue(File(context.filesDir, "run-history/task-${task.bisyncUUID}.json").length() <= 256 * 1024)
    }

    @Test fun boundedStateMessagesRemainReadableWithEscapedUnicode() {
        val dir = File(context.filesDir, "test-state").apply { mkdirs() }
        val state = BisyncStateStore(dir)
        val id = UUID.randomUUID().toString()
        state.begin("initialise", "fingerprint", "fingerprint", id)
        state.finish("initialise", "fingerprint", id, true, 0, "\u0001".repeat(40000))
        assertEquals(BisyncStateStore.READY, state.read().getString("status"))
        assertEquals(2048, state.read().getString("message").length)
        assertTrue(File(dir, "protonbridge.json").length() <= 65536)
    }

    @Test fun globalLogReadsOnlyRecentRecordsAndRedactsCredentials() {
        val log = File(context.filesDir, "sync.log")
        log.writeText("obsolete-legacy-record".repeat(60000) + "\n")
        SyncLog.info(context, "Transfer", "Authorization: Bearer test-canary-secret")
        assertTrue(log.length() <= 512 * 1024)
        assertFalse(log.readText().contains("test-canary-secret"))
        assertEquals("Transfer", SyncLog.getLog(context).first().getString("title"))
        repeat(210) { SyncLog.info(context, "Task $it", "Result $it") }
        assertEquals(200, SyncLog.getLog(context).size)
        assertEquals("Task 209", SyncLog.getLog(context).first().getString("title"))
    }

    @Test fun databaseExportKeepsTaskReferencesAndIdentityInOneSnapshot() {
        val db = DatabaseHandler(context)
        val task = db.createTask(Task(0).apply { title = "Backup task"; direction = 3 })
        val trigger = db.createTrigger(Trigger(0).apply { triggerTarget = task.id; title = "Daily"; time = 600 })
        val saved = JSONObject(Exporter.create(context))
        assertEquals(task.bisyncUUID, saved.getJSONArray("tasks").getJSONObject(0).getString("bisyncUUID"))
        assertEquals(trigger.triggerTarget, saved.getJSONArray("trigger").getJSONObject(0).getLong("triggerTarget"))
    }

    @Test fun remoteRootSentinelCannotContainItsOwnBackupDirectory() {
        val local = File(context.cacheDir, "local-sync").apply { mkdirs() }
        val task = Task(1).apply {
            direction = 6; localPath = local.absolutePath; remoteId = "proton"; remotePath = "//proton"
            bisyncUUID = UUID.randomUUID().toString(); bisyncBackupDir2 = "proton:backups"
        }
        assertThrows(IllegalArgumentException::class.java) { BisyncPolicy.validate(context, task) }
    }
}
