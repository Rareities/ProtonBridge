package io.github.rareities.protonbridge.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class BisyncStateDurabilityTest {
    private lateinit var originalSync: DurableFiles.DirectorySync
    @Before fun setup() {
        originalSync = DurableFiles.directorySync
        DurableFiles.directorySync = DurableFiles.DirectorySync { }
    }
    @After fun restore() { DurableFiles.directorySync = originalSync }

    @Test fun suppressedRunningReplacementCannotStartNativeWorkAsIfPersisted() {
        val directory = File(ApplicationProvider.getApplicationContext<Application>().filesDir, UUID.randomUUID().toString())
        val state = BisyncStateStore(directory)
        val baseline = UUID.randomUUID().toString()
        state.begin("initialise", "policy", "policy", baseline)
        state.finish("initialise", "policy", baseline, true, 0, "success")
        val file = File(directory, "protonbridge.json")
        val original = file.readBytes()
        DurableFiles.directorySync = DurableFiles.DirectorySync { file.writeBytes(original) }
        assertThrows(IOException::class.java) { state.begin("run", "policy", null, "next") }
        assertArrayEquals(original, file.readBytes())
        assertTrue(BisyncStateStore.isReady(state.read(), "policy"))
    }

    @Test fun failedStateDirectoryBarrierCannotReportCompletedResync() {
        val directory = File(ApplicationProvider.getApplicationContext<Application>().filesDir, UUID.randomUUID().toString())
        val state = BisyncStateStore(directory)
        val baseline = UUID.randomUUID().toString()
        state.begin("initialise", "policy", "policy", baseline)
        DurableFiles.directorySync = DurableFiles.DirectorySync { throw IOException("state barrier failure") }
        assertThrows(IOException::class.java) { state.finish("initialise", "policy", baseline, true, 0, "success") }
    }
}
