package io.github.rareities.protonbridge

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.github.rareities.protonbridge.Activities.MainActivity
import io.github.rareities.protonbridge.Activities.TaskActivity
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.EngineProvenance
import io.github.rareities.protonbridge.util.NativeConfigEncryption
import io.github.rareities.protonbridge.util.NativeSessionGate
import io.github.rareities.protonbridge.util.ProcessRunner
import io.github.rareities.protonbridge.util.RunHistory
import io.github.rareities.protonbridge.workmanager.SyncManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Runs against the actual minified, signed, non-debuggable release APK. */
@RunWith(AndroidJUnit4::class)
class ReleasePackageDeviceTest {
    // Test-only Kotlin file extensions can be removed from a shrunk application.
    // Use platform I/O so this fixture does not require changing release keep rules.
    private fun write(file: File, text: String) {
        val output = java.io.FileOutputStream(file)
        try { output.write(text.toByteArray(java.nio.charset.StandardCharsets.UTF_8)) }
        finally { output.close() }
    }
    private fun read(file: File): String {
        val input = java.io.FileInputStream(file)
        val output = java.io.ByteArrayOutputStream()
        try {
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            return String(output.toByteArray(), java.nio.charset.StandardCharsets.UTF_8)
        } finally { input.close(); output.close() }
    }
    private fun removeFixture(file: File): Boolean {
        for (child in file.listFiles() ?: emptyArray()) if (!removeFixture(child)) return false
        return file.delete()
    }
    @Test(timeout = 150_000) fun signedReleaseLaunchesAndCompletesGuardedNativeBisync() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(InstrumentationRegistry.getArguments().getString("releaseAcceptance") == "true" &&
            (Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")))
        assertEquals("io.github.rareities.protonbridge", context.packageName)
        assertEquals(0, context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE)
        EngineProvenance.verify(context)
        val rclone = Rclone(context)
        val db = DatabaseHandler(context)
        val alias = "release_${UUID.randomUUID()}"
        val root = File(context.filesDir, alias).apply { check(mkdirs()) }
        val left = File(root, "left").apply { check(mkdirs()) }
        val right = File(root, "right").apply { check(mkdirs()) }
        var task: Task? = null
        var createdAlias = false
        fun await(label: String, timeout: Long = 30_000, condition: () -> Boolean) {
            val end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeout)
            while (System.nanoTime() < end) {
                if (condition()) return
                Thread.sleep(100)
            }
            assertTrue(label, condition())
        }
        fun command(child: Process?) {
            val start = System.nanoTime()
            val result = ProcessRunner().run(child,
                { TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) > 25 }, null, null)
            assertTrue("Release native command failed: ${result.exitCode}", result.success())
        }
        val completedWork = mutableSetOf<String>()
        fun finished(saved: Task) {
            var found: String? = null
            await("Release worker did not finish: ${RunHistory.read(context, saved)}") {
                val records = RunHistory.read(context, saved)
                for (i in 0 until records.length()) {
                    val record = records.getJSONObject(i)
                    val run = record.getString("runId")
                    if (run in completedWork) continue
                    val outcome = record.getString("status")
                    if (outcome != "IN_PROGRESS" && outcome != "SUCCESS")
                        fail("Release worker failed: $record")
                    if (outcome == "SUCCESS") found = run
                }
                found != null
            }
            await("Release process did not relinquish ownership") { !NativeSessionGate.GLOBAL.isBusy }
            // History is persisted before doWork returns. Queueing the next
            // operation before WorkManager commits SUCCEEDED is coalesced by
            // the production KEEP policy and does not create another request.
            // Use the same API as the production task editor. Unused query
            // methods are correctly removed from the minified release APK.
            val work = WorkManager.getInstance(context).getWorkInfosByTagLiveData(saved.id.toString())
            val committed = java.util.concurrent.atomic.AtomicBoolean()
            val observer = androidx.lifecycle.Observer<List<WorkInfo>> { items ->
                committed.set(items.isNotEmpty() && items.all { it.state.isFinished } &&
                    items.any { it.state == WorkInfo.State.SUCCEEDED })
            }
            instrumentation.runOnMainSync { work.observeForever(observer) }
            try {
                await("WorkManager did not commit the release operation's terminal state") { committed.get() }
            } finally { instrumentation.runOnMainSync { work.removeObserver(observer) } }
            completedWork.add(found!!)
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit().putBoolean("intro_protonbridge_v1_completed", true)
            .putBoolean(context.getString(R.string.pref_key_app_updates), false)
            .putBoolean(context.getString(R.string.pref_key_enable_vcp), false).commit()
        try {
            rclone.prepareProtectedConfig()
            assertTrue(NativeConfigEncryption.isEncrypted(File(context.filesDir, "rclone.conf")))
            createdAlias = true
            command(rclone.configCreateNoInteract(mutableListOf(alias, "alias", "remote", right.absolutePath)))
            write(File(left, "initial.txt"), "release initial bytes")
            val saved = db.createTask(Task(0).apply {
                title = "Signed release acceptance"; direction = 6
                localPath = left.absolutePath; remoteId = alias; remotePath = "//$alias"; wifionly = false
            }).also { task = it }
            val state = BisyncStateStore(BisyncPolicy.workdir(context, saved))
            ActivityScenario.launch<MainActivity>(Intent(context, MainActivity::class.java)).use {
                val manager = SyncManager(context)
                manager.initialiseBisync(saved.id, resyncFingerprint(context, saved))
                finished(saved)
                assertEquals("release initial bytes", read(File(right, "initial.txt")))
                write(File(left, "from-left.txt"), "L")
                write(File(right, "from-right.txt"), "R")
                manager.queue(saved.id)
                finished(saved)
                assertEquals("L", read(File(right, "from-left.txt")))
                assertEquals("R", read(File(left, "from-right.txt")))
                assertEquals(BisyncStateStore.READY, state.read().getString("status"))
            }
            ActivityScenario.launch<TaskActivity>(Intent(context, TaskActivity::class.java)
                .putExtra(TaskActivity.ID_EXTRA, saved.id)).use { editor ->
                editor.onActivity { activity ->
                    assertEquals(saved.title, activity.findViewById<android.widget.EditText>(R.id.task_title_textfield).text.toString())
                    assertNotNull(activity.findViewById<android.view.View>(R.id.bisync_help_button))
                    assertNotNull(activity.findViewById<android.view.View>(R.id.task_proton_connection_button))
                }
            }
        } finally {
            task?.let { SyncManager(context).cancel(it.id.toString()) }
            await("Release fixture cleanup must wait for native exit") { !NativeSessionGate.GLOBAL.isBusy }
            if (createdAlias) command(rclone.config("delete", mutableListOf(alias)))
            task?.let { db.deleteTask(it.id) }
            assertTrue(removeFixture(root))
        }
    }
}
