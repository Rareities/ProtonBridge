package io.github.rareities.protonbridge

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.preference.PreferenceManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.ForegroundUpdater
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.impl.utils.futures.SettableFuture
import androidx.work.testing.TestWorkerBuilder
import io.github.rareities.protonbridge.Activities.MainActivity
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.BisyncStateStore
import io.github.rareities.protonbridge.util.NativeSessionGate
import io.github.rareities.protonbridge.util.ProtectedRcloneConfig
import io.github.rareities.protonbridge.util.RunHistory
import io.github.rareities.protonbridge.workmanager.SyncManager
import io.github.rareities.protonbridge.workmanager.SyncWorker
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Runs real Android JobScheduler/WorkManager constraints and cancellation with packaged rclone.
 * Only the initial explicit Resync uses TestWorkerBuilder and a fake foreground updater.
 * The two queued RUN operations use the production WorkManager and SyncWorker unchanged.
 * One sequential test prevents these operations racing each other on native/global state.
 */
@RunWith(AndroidJUnit4::class)
class SystemJobIntegrationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context: Context get() = instrumentation.targetContext

    private val preparationForeground = object : ForegroundUpdater {
        override fun setForegroundAsync(context: Context, id: UUID, foregroundInfo: ForegroundInfo): ListenableFuture<Void> =
            SettableFuture.create<Void>().also { it.set(null) }
    }

    private fun requireDisposableApi35Emulator() {
        assumeTrue("This test requires an API 35 .debug ranchu/goldfish emulator",
            Build.VERSION.SDK_INT == 35 && context.packageName.endsWith(".debug") &&
                (Build.HARDWARE.contains("ranchu", ignoreCase = true) ||
                    Build.HARDWARE.contains("goldfish", ignoreCase = true)))
    }

    private fun awaitCondition(label: String, timeoutMs: Long, condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            if (condition()) return
            Thread.sleep(100)
        }
        assertTrue("Timed out after ${timeoutMs}ms: $label", condition())
    }

    private fun batteryShell(command: String) {
        // Reading to EOF guarantees dumpsys completed before querying battery/job state.
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command))
            .bufferedReader().use { it.readText() }
    }

    private fun isPluggedIn(): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return battery != null && battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
    }

    private fun isCharging(): Boolean {
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val state = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0 &&
            (state == BatteryManager.BATTERY_STATUS_CHARGING || state == BatteryManager.BATTERY_STATUS_FULL)
    }

    private fun mainHasCompletedConfigStartup(scenario: ActivityScenario<MainActivity>): Boolean {
        var ready = false
        scenario.onActivity { activity ->
            val field = MainActivity::class.java.getDeclaredField("protectedConfigReady")
            field.isAccessible = true
            ready = field.getBoolean(activity)
        }
        return ready
    }

    private fun checkNativeCommand(label: String, child: Process?) {
        assertNotNull("Could not start $label", child)
        val process = child!!
        if (!process.waitFor(20, TimeUnit.SECONDS)) {
            process.destroy()
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                assertTrue("Could not confirm native $label process exit", process.waitFor(3, TimeUnit.SECONDS))
            }
            fail("$label did not complete")
        }
        assertEquals("$label exited unsuccessfully", 0, process.exitValue())
        awaitCondition("$label native admission to release", 6_000) { !NativeSessionGate.GLOBAL.isBusy }
    }

    private fun initialise(task: Task) {
        val store = BisyncStateStore(BisyncPolicy.workdir(context, task))
        fun direct(operation: String, approval: String) {
            awaitCondition("preparation native ownership to settle before $operation", 8_000) {
                !NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()
            }
            val data = Data.Builder().putLong(SyncWorker.TASK_ID, task.id)
                .putString("TASK_UUID", task.bisyncUUID)
                .putString(SyncManager.TASK_REVISION, SyncManager.taskRevision(context, task))
                .putString(SyncWorker.BISYNC_OPERATION, operation)
                .putString(SyncWorker.BISYNC_APPROVAL, approval).build()
            val worker = TestWorkerBuilder.from(context, SyncWorker::class.java, Executor { it.run() })
                .setForegroundUpdater(preparationForeground).setInputData(data).build()
            val result = worker.doWork()
            assertTrue("$operation failed with $result (nativeBusy=${NativeSessionGate.GLOBAL.isBusy}): ${RunHistory.read(context, task)}",
                result is ListenableWorker.Result.Success)
        }
        direct(BisyncStateStore.INITIALISE, resyncFingerprint(context, task))
        assertEquals("Preparation did not create a usable baseline", BisyncStateStore.READY,
            store.read().getString("status"))
    }

    private fun workFor(manager: WorkManager, task: Task): List<WorkInfo> =
        manager.getWorkInfosForUniqueWork("task:${task.id}").get(4, TimeUnit.SECONDS)

    private fun runRecords(task: Task): List<org.json.JSONObject> {
        val history = RunHistory.read(context, task)
        return (0 until history.length()).map { history.getJSONObject(it) }
            .filter { it.optString("operation") == BisyncStateStore.RUN }
    }

    private fun snapshot(directory: File): Map<String, String> =
        directory.walkTopDown().filter { it.isFile }.associate { file ->
            val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            file.relativeTo(directory).invariantSeparatorsPath to
                digest.joinToString("") { "%02x".format(it) }
        }

    @Test(timeout = 240_000)
    fun chargingConstraintThenRealCancellationPreservesBisyncRecovery() {
        requireDisposableApi35Emulator()
        val db = DatabaseHandler(context)
        val manager = WorkManager.getInstance(context)
        val syncManager = SyncManager(context)
        val rclone = Rclone(context)
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val introKey = "intro_protonbridge_v1_completed"
        val hadIntro = prefs.contains(introKey)
        val originalIntro = prefs.getBoolean(introKey, false)
        val root = File(context.filesDir, "phase2-system-jobs-${UUID.randomUUID()}")
        val createdAliases = mutableListOf<String>()
        val installedAliases = mutableSetOf<String>()
        val taskIds = mutableListOf<Long>()
        var activity: ActivityScenario<MainActivity>? = null
        var primaryFailure: Throwable? = null
        try {
            assertTrue("Could not create isolated device test root", root.mkdirs())
            awaitCondition("previous native transfer to finish", 12_000) {
                !NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()
            }
            rclone.prepareProtectedConfig()

            val chargingLeft = File(root, "charging-left").apply { assertTrue(mkdirs()) }
            val chargingRight = File(root, "charging-right").apply { assertTrue(mkdirs()) }
            val slowLeft = File(root, "slow-left").apply { assertTrue(mkdirs()) }
            val slowRight = File(root, "slow-right").apply { assertTrue(mkdirs()) }
            // A matching seed ensures both bisync roots exist and have a known, stable
            // listing before the post-initialisation changes used by the device tests.
            File(chargingLeft, "baseline.txt").writeText("charging baseline")
            File(chargingRight, "baseline.txt").writeText("charging baseline")
            File(slowLeft, "baseline.txt").writeText("slow baseline")
            File(slowRight, "baseline.txt").writeText("slow baseline")
            val unique = UUID.randomUUID().toString().replace("-", "")
            val chargingAlias = "phase2_job_charge_$unique"
            val slowAlias = "phase2_job_slow_$unique"

            fun addAlias(name: String, directory: File) {
                createdAliases.add(name)
                checkNativeCommand("create $name", rclone.configCreateNoInteract(
                    mutableListOf(name, "alias", "remote", directory.absolutePath)))
                installedAliases.add(name)
            }
            addAlias(chargingAlias, chargingRight)
            addAlias(slowAlias, slowRight)

            fun persistTask(title: String, local: File, alias: String,
                            charging: Boolean, flags: String): Task {
                return db.createTask(Task(0).apply {
                    this.title = title
                    direction = 6
                    localPath = local.absolutePath
                    remoteId = alias
                    remotePath = "//$alias"
                    remoteType = 0
                    wifionly = false
                    chargingOnly = charging
                    batteryNotLow = false
                    bisyncAdvancedOptions = flags
                    bisyncMaxDelete = 10
                }).also { taskIds.add(it.id) }
            }

            val chargingTask = persistTask("phase2 charging $unique", chargingLeft, chargingAlias,
                charging = true, flags = "")
            val slowTask = persistTask("phase2 cancel $unique", slowLeft, slowAlias,
                charging = false, flags = "--bwlimit=128K")

            // Preparation uses precisely the task UUID/revision that real queueTask will persist.
            initialise(chargingTask)
            initialise(slowTask)
            assertEquals(chargingTask.bisyncUUID, db.getTask(chargingTask.id)!!.bisyncUUID)
            assertEquals(slowTask.bisyncUUID, db.getTask(slowTask.id)!!.bisyncUUID)

            // MainActivity and WorkManager are production components. Do not replace their
            // application, Scheduler, WorkerFactory, constraints or foreground updater.
            assertTrue("Could not set intro preference", prefs.edit().putBoolean(introKey, true).commit())
            activity = ActivityScenario.launch(Intent(context, MainActivity::class.java))
            activity!!.onActivity { main ->
                assertFalse("MainActivity redirected to onboarding (storage permission missing)", main.isFinishing)
            }
            awaitCondition("MainActivity to finish real protected-config startup", 12_000) {
                mainHasCompletedConfigStartup(activity!!)
            }
            awaitCondition("MainActivity native operations to settle", 8_000) {
                !NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()
            }

            File(chargingLeft, "charge-only.txt").writeText("charging constraint payload")
            val peerBeforeUnpluggedRun = snapshot(chargingRight)
            batteryShell("cmd battery unplug -f")
            batteryShell("cmd battery set -f status 3")
            awaitCondition("emulator discharging and unplugged state", 5_000) {
                !isPluggedIn() && context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) == BatteryManager.BATTERY_STATUS_DISCHARGING
            }
            // The debug unplug command leaves the previous charging status.
            // Model both physical properties before WorkManager reads them.
            Thread.sleep(300)
            syncManager.queue(chargingTask.id)
            awaitCondition("charging work to enter the real WorkManager queue", 8_000) {
                workFor(manager, chargingTask).any { it.state == WorkInfo.State.ENQUEUED }
            }
            Thread.sleep(1_300)
            val unplugged = workFor(manager, chargingTask)
            assertEquals("Charging-only work ran while unplugged", WorkInfo.State.ENQUEUED,
                unplugged.single().state)
            assertFalse("Charging-only work changed the peer while unplugged",
                File(chargingRight, "charge-only.txt").exists())
            assertEquals("The unplugged charging-only task mutated the peer",
                peerBeforeUnpluggedRun, snapshot(chargingRight))

            batteryShell("cmd battery reset -f")
            awaitCondition("battery reset to restore a real charging state", 8_000) { isCharging() }
            // Platform constraint-change cancellation can legitimately impose
            // the production one-minute WorkManager backoff. Do not force-run
            // a JobScheduler job or assume reset bypasses its eligibility time.
            try { awaitCondition("charging-only production worker to finish successfully", 120_000) {
                val state = workFor(manager, chargingTask).singleOrNull()?.state
                if (state == WorkInfo.State.FAILED || state == WorkInfo.State.CANCELLED)
                    fail("Charging worker failed: ${RunHistory.read(context, chargingTask)}")
                state == WorkInfo.State.SUCCEEDED
            } } catch (timeout: AssertionError) {
                val current = workFor(manager, chargingTask).map { "${it.state}/attempt=${it.runAttemptCount}/stop=${it.stopReason}/next=${it.nextScheduleTimeMillis}/now=${System.currentTimeMillis()}" }
                val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                throw AssertionError("$timeout; work=$current; plugged=${isPluggedIn()}; charging=${isCharging()}; batteryManagerCharging=${battery.isCharging}; nativeBusy=${NativeSessionGate.GLOBAL.isBusy}; history=${RunHistory.read(context, chargingTask)}", timeout)
            }
            assertEquals("charging constraint task did not copy the new file",
                "charging constraint payload", File(chargingRight, "charge-only.txt").readText())
            assertEquals("SUCCESS", runRecords(chargingTask).last().getString("status"))

            // The 16 MiB file appears AFTER a fully approved baseline. At 128 KiB/s a
            // completed transfer would take ~128 seconds; cancel only after native RUNNING.
            val slowFile = File(slowLeft, "large.bin")
            val block = ByteArray(256 * 1024) { (it % 239).toByte() }
            slowFile.outputStream().buffered().use { out -> repeat(64) { out.write(block) } }
            assertEquals(16L * 1024 * 1024, slowFile.length())
            val slowState = BisyncStateStore(BisyncPolicy.workdir(context, slowTask))
            assertEquals(BisyncStateStore.READY, slowState.read().getString("status"))
            syncManager.queue(slowTask.id)
            awaitCondition("real worker RUNNING, active bisync baseline and native admission", 30_000) {
                workFor(manager, slowTask).any { it.state == WorkInfo.State.RUNNING } &&
                    slowState.read().optString("status") == "RUNNING" &&
                    NativeSessionGate.GLOBAL.isBusy
            }
            assertFalse("Rate-limited 16 MiB file completed before cancellation",
                File(slowRight, "large.bin").length() == slowFile.length())

            manager.cancelUniqueWork("task:${slowTask.id}").result.get(10, TimeUnit.SECONDS)
            awaitCondition("cancelled WorkManager request to reach terminal CANCELLED", 15_000) {
                workFor(manager, slowTask).any { it.state == WorkInfo.State.CANCELLED }
            }
            awaitCondition("actual native exit and recovery state after cancellation", 25_000) {
                slowState.read().optString("status") == BisyncStateStore.NEEDS_RECOVERY &&
                    !NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()
            }
            awaitCondition("cancelled run to write a terminal, non-success history", 8_000) {
                runRecords(slowTask).any { it.optString("status") in setOf("CANCELLED", "FAILED") }
            }
            val runs = runRecords(slowTask)
            assertEquals("Cancellation should have one run, without an automatic resync", 1, runs.size)
            assertNotEquals("SUCCESS", runs.single().getString("status"))
            assertEquals("Recovery must be explicit after cancellation",
                BisyncStateStore.NEEDS_RECOVERY, slowState.read().getString("status"))
            assertEquals("A cancelled run must not queue an automatic restart",
                WorkInfo.State.CANCELLED, workFor(manager, slowTask).single().state)
        } catch (failure: Throwable) {
            primaryFailure = failure
            throw failure
        } finally {
            // Never leave the emulator unplugged, even when any assertion or native command fails.
            val cleanupErrors = mutableListOf<Throwable>()
            fun cleanup(action: () -> Unit) {
                try { action() } catch (error: Throwable) { cleanupErrors.add(error) }
            }
            cleanup { batteryShell("dumpsys battery reset") }
            for (id in taskIds) cleanup {
                manager.cancelUniqueWork("task:$id").result.get(10, TimeUnit.SECONDS)
            }
            for (id in taskIds) cleanup {
                awaitCondition("fixture WorkManager task:$id to finish cancellation", 15_000) {
                    manager.getWorkInfosForUniqueWork("task:$id").get(4, TimeUnit.SECONDS)
                        .all { it.state.isFinished }
                }
            }
            cleanup {
                awaitCondition("native process leases to release before config cleanup", 20_000) {
                    !NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()
                }
            }
            cleanup { activity?.close() }

            // Do not alter config while the native gate is busy; retain fixtures for
            // diagnosis rather than changing any unrelated or still-active process.
            if (!NativeSessionGate.GLOBAL.isBusy && !ProtectedRcloneConfig.hasRunningProcess()) {
                for (alias in createdAliases.asReversed()) cleanup {
                    val removal = rclone.config("delete", mutableListOf(alias))
                    if (alias in installedAliases) checkNativeCommand("remove $alias", removal)
                    else removal?.let { child ->
                        if (!child.waitFor(10, TimeUnit.SECONDS)) {
                            child.destroy()
                            if (!child.waitFor(3, TimeUnit.SECONDS)) {
                                child.destroyForcibly()
                                assertTrue("Could not confirm fixture alias CLI exit",
                                    child.waitFor(3, TimeUnit.SECONDS))
                            }
                        }
                        Unit
                    }
                }
                for (id in taskIds) cleanup { db.deleteTask(id) }
                cleanup { assertTrue("Could not remove only this test's temporary files", root.deleteRecursively()) }
            }
            cleanup {
                val editor = prefs.edit()
                if (hadIntro) editor.putBoolean(introKey, originalIntro) else editor.remove(introKey)
                assertTrue("Could not restore the original intro preference", editor.commit())
            }
            if (cleanupErrors.isNotEmpty()) {
                val combined = AssertionError("SystemJobIntegrationTest cleanup was incomplete")
                cleanupErrors.forEach { combined.addSuppressed(it) }
                if (primaryFailure == null) throw combined else primaryFailure?.addSuppressed(combined)
            }
            Unit
        }
    }
}
