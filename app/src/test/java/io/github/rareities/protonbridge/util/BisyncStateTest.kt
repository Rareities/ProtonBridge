package io.github.rareities.protonbridge.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.rareities.protonbridge.Items.Task
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BisyncStateTest {
    private fun store() = BisyncStateStore(File(ApplicationProvider.getApplicationContext<Application>().filesDir, UUID.randomUUID().toString()))

    private val baseline = "00000000-0000-0000-0000-000000000001"

    private fun ready(store: BisyncStateStore, fingerprint: String = "one") {
        store.begin(BisyncStateStore.INITIALISE, fingerprint, fingerprint, baseline)
        store.finish(BisyncStateStore.INITIALISE, fingerprint, baseline, true, 0, "success")
    }

    @Test fun resyncRequiresMatchingExplicitConfirmationWithoutPrerequisite() {
        val store = store()
        assertThrows(IllegalArgumentException::class.java) { store.begin("run", "one", null, "run") }
        assertThrows(IllegalArgumentException::class.java) { store.begin("initialise", "one", null, baseline) }
        assertThrows(IllegalArgumentException::class.java) { store.begin("initialise", "changed", "one", baseline) }
        ready(store)
        assertTrue(BisyncStateStore.isReady(store.read(), "one"))
        assertEquals(baseline, BisyncStateStore.baselineForRun(store.read(), "one"))
    }

    @Test fun interruptedRunRequiresExplicitRecoveryAndMatchingBaseline() {
        val store = store()
        ready(store)
        store.begin("run", "one", null, "interrupted")
        assertThrows(IllegalArgumentException::class.java) { store.begin("run", "one", null, "retry") }
        assertFalse(BisyncStateStore.isReady(store.read(), "one"))
        assertTrue(BisyncStateStore.isRunnable(store.read(), "one", true))
        store.finish("run", "one", "interrupted", false, 7, "cancelled")
        assertEquals(BisyncStateStore.NEEDS_RECOVERY, store.read().getString("status"))
        assertThrows(IllegalArgumentException::class.java) { store.begin("run", "one", null, "retry") }
        assertThrows(IllegalArgumentException::class.java) { store.begin("run", "changed", null, "retry", true) }
        store.begin("run", "one", null, "recovery", true)
        store.finish("run", "one", "recovery", true, 0, "recovered")
        assertTrue(BisyncStateStore.isReady(store.read(), "one"))
        assertEquals(baseline, store.read().getString("baselineId"))
    }

    @Test fun failedFirstResyncNeverCreatesRecoverableBaseline() {
        val store = store()
        store.begin("initialise", "one", "one", baseline)
        store.finish("initialise", "one", baseline, false, 7, "failed")
        assertFalse(BisyncStateStore.isRunnable(store.read(), "one", true))
        assertThrows(IllegalArgumentException::class.java) { store.begin("run", "one", null, "retry", true) }
    }

    @Test fun staleCompletionCannotPromoteAnotherRunsState() {
        val store = store()
        store.begin("initialise", "one", "one", baseline)
        store.finish("initialise", "one", "stale", true, 0, "stale success")
        assertEquals("INITIALISING", store.read().getString("status"))
    }

    @Test fun editedSettingsRequireNewConfirmationButDoNotDestroyHealthyBaseline() {
        val store = store()
        ready(store)
        assertThrows(IllegalArgumentException::class.java) { store.begin("initialise", "edited", "one", baseline) }
        assertThrows(IllegalArgumentException::class.java) { store.begin("run", "edited", null, "run", true) }
        assertTrue(BisyncStateStore.isReady(store.read(), "one"))
    }

    @Test fun missingOrInvalidBaselineCannotEnableRecovery() {
        for (id in listOf("", "broken", "1-1-1-1-1", "../escape")) {
            val state = org.json.JSONObject().put("status", BisyncStateStore.NEEDS_RECOVERY)
                .put("fingerprint", "one").put("baselineId", id)
            assertFalse(BisyncStateStore.isRunnable(state, "one", true))
            assertThrows(IllegalArgumentException::class.java) { BisyncStateStore.baselineForRun(state, "one", true) }
        }
    }

    @Test fun existingHealthyBaselineRemainsReadyAndRetiredDiagnosticsAreDiscarded() {
        val directory = File(ApplicationProvider.getApplicationContext<Application>().filesDir, UUID.randomUUID().toString()).apply { mkdirs() }
        File(directory, "protonbridge.json").writeText(org.json.JSONObject().put("version", 1)
            .put("status", BisyncStateStore.READY).put("fingerprint", "existing")
            .put("baselineId", baseline).put("preview", "retired diagnostic")
            .put("previewAt", 1234).toString())
        val state = BisyncStateStore(directory).read()
        assertTrue(BisyncStateStore.isReady(state, "existing"))
        assertEquals(baseline, BisyncStateStore.baselineForRun(state, "existing"))
        assertFalse(state.has("preview"))
        assertFalse(state.has("previewAt"))
    }

    @Test fun interruptedRetiredAdvisoryRestoresOnlyAnIdentifiedHealthyBaseline() {
        val directory = File(ApplicationProvider.getApplicationContext<Application>().filesDir, UUID.randomUUID().toString()).apply { mkdirs() }
        val file = File(directory, "protonbridge.json")
        val healthy = org.json.JSONObject().put("version", 1).put("status", "PREVIEWING")
            .put("previewWasReady", true).put("fingerprint", "a".repeat(64)).put("baselineId", baseline)
        file.writeText(healthy.toString())
        assertTrue(BisyncStateStore.isReady(BisyncStateStore(directory).read(), "a".repeat(64)))
        for (invalid in listOf(
            org.json.JSONObject(healthy.toString()).put("previewWasReady", false),
            org.json.JSONObject(healthy.toString()).put("fingerprint", ""),
            org.json.JSONObject(healthy.toString()).put("baselineId", "../escape"),
            org.json.JSONObject(healthy.toString()).put("status", "INITIALISING")
        )) {
            file.writeText(invalid.toString())
            assertFalse(BisyncStateStore.isReady(BisyncStateStore(directory).read(), "a".repeat(64)))
        }
    }

    @Test fun unchangedLegacyResilientBaselineMigratesWithoutAcceptingChangedSettings() {
        val task = Task(4).apply {
            direction = 6; bisyncUUID = java.util.UUID.randomUUID().toString()
            localPath = "/local"; remoteId = "demo"; remotePath = "folder"
            bisyncAdvancedOptions = "--resilient"
        }
        val legacy = BisyncPolicy.legacyFingerprint(task, emptyList(), "config")
        val current = BisyncPolicy.fingerprint(task, emptyList(), "config")
        assertNotEquals(legacy, current)
        val store = store()
        ready(store, legacy)
        task.remotePath = "changed"
        store.migrateLegacyFingerprint(BisyncPolicy.fingerprint(task, emptyList(), "config"),
            BisyncPolicy.legacyFingerprint(task, emptyList(), "config"))
        assertTrue(BisyncStateStore.isReady(store.read(), legacy))
        task.remotePath = "folder"
        store.migrateLegacyFingerprint(current, BisyncPolicy.legacyFingerprint(task, emptyList(), "different-config"))
        assertTrue(BisyncStateStore.isReady(store.read(), legacy))
        store.migrateLegacyFingerprint(current, legacy)
        assertTrue(BisyncStateStore.isReady(store.read(), current))
        assertEquals(baseline, store.read().getString("baselineId"))
        assertFalse(BisyncStateStore.isReady(store.read(), legacy))
    }

    @Test fun legacyFingerprintMigrationNeverPromotesFailedState() {
        val store = store()
        ready(store, "legacy")
        store.begin("run", "legacy", null, "interrupted")
        store.migrateLegacyFingerprint("current", "legacy")
        assertEquals("legacy", store.read().getString("fingerprint"))
        assertEquals("RUNNING", store.read().getString("status"))
    }

    @Test fun recoveryFlagsDoNotInvalidateListingPolicyButOtherChangesDo() {
        val task = Task(4).apply {
            direction = 6; bisyncUUID = java.util.UUID.randomUUID().toString()
            localPath = "/local"; remoteId = "demo"; remotePath = "folder"
        }
        val original = BisyncPolicy.fingerprint(task, emptyList(), "config")
        for (flags in listOf("--recover", "--resilient=true", "--recover=false\n--resilient")) {
            task.bisyncAdvancedOptions = flags
            assertEquals(original, BisyncPolicy.fingerprint(task, emptyList(), "config"))
        }
        task.bisyncAdvancedOptions = "--recover\n--bwlimit=4M"
        assertNotEquals(original, BisyncPolicy.fingerprint(task, emptyList(), "config"))
    }

    @Test fun firstRunOffersResyncRatherThanParsingAnEmptyIdentity() {
        val failure = assertThrows(IllegalArgumentException::class.java) {
            BisyncStateStore.baselineForRun(store().read(), "one", true)
        }
        assertTrue(failure.message!!.contains("Resync"))
        assertFalse(failure.message!!.contains("Invalid UUID"))
    }

    @Test fun identityIncludesEngineConfigurationOptionsAndOrderedPaths() {
        val task = Task(4).apply {
            bisyncUUID = UUID.randomUUID().toString()
            localPath = File(ApplicationProvider.getApplicationContext<Application>().filesDir, "local").absolutePath
            remoteId = "proton"
            remotePath = "folder"
        }
        val initial = BisyncPolicy.fingerprint(task, emptyList(), "account-generation-a")
        assertNotEquals(initial, BisyncPolicy.fingerprint(task, emptyList(), "account-generation-b"))
        task.bisyncAdvancedOptions = "--bwlimit=4M"
        assertNotEquals(initial, BisyncPolicy.fingerprint(task, emptyList(), "account-generation-a"))
        assertFalse(BisyncPolicy.overlaps("/a/b", "/a/b2"))
        assertTrue(BisyncPolicy.overlaps("/a/b", "/a/b/c"))
    }


    @Test fun changingTransferModeRotatesIdentityButLegacyBisyncModesShareOneBaseline() {
        val original = Task(17).apply {
            direction = 5
            bisyncUUID = UUID.randomUUID().toString()
            localPath = File(ApplicationProvider.getApplicationContext<Application>().filesDir, "local").absolutePath
            remoteId = "proton"
            remotePath = "data"
        }
        assertEquals(original.bisyncUUID, BisyncPolicy.identityForEdit(original, 6))
        assertNotEquals(original.bisyncUUID, BisyncPolicy.identityForEdit(original, 1))
        val oneWay = Task(original.id).apply { direction = 1; bisyncUUID = original.bisyncUUID }
        assertEquals(original.bisyncUUID, BisyncPolicy.identityForEdit(oneWay, 2))
        assertNotEquals(original.bisyncUUID, BisyncPolicy.identityForEdit(oneWay, 6))
        val bisync = BisyncPolicy.fingerprint(original, emptyList(), "same-config")
        original.direction = 6
        assertEquals(bisync, BisyncPolicy.fingerprint(original, emptyList(), "same-config"))
        original.direction = 1
        assertNotEquals(bisync, BisyncPolicy.fingerprint(original, emptyList(), "same-config"))
    }


    @Test fun savingRepairsMalformedTaskIdentityAndInvalidWorkdirHasAnActionableError() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val task = Task(7).apply { direction = 6; bisyncUUID = "bad uuid" }
        val error = assertThrows(IllegalArgumentException::class.java) { BisyncPolicy.workdir(context, task) }
        assertTrue(error.message!!.contains("save"))
        assertFalse(error.message!!.contains("Invalid UUID"))
        val repaired = BisyncPolicy.identityForEdit(task, 6)
        assertTrue(BisyncPolicy.isValidIdentity(repaired))
        task.bisyncUUID = repaired
        assertEquals(repaired, BisyncPolicy.identityForEdit(task, 6))
    }
}
