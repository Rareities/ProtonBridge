package io.github.rareities.protonbridge.util

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Filter
import io.github.rareities.protonbridge.Items.FilterEntry
import io.github.rareities.protonbridge.Items.RemoteItem
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Rclone
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class FilterArgumentsTest {
    @Test fun conventionalRulesAndCrLfRoundTripWithoutPatternWhitespace() {
        val filter = Filter(1)
        filter.setFiltersRaw("- /.nomedia\r\n+ *.anki2\r\n")
        val entries = filter.getFilters()
        assertEquals(listOf("/.nomedia", "*.anki2"), entries.map { it.filter })
        assertEquals(listOf(FilterEntry.FILTER_EXCLUDE, FilterEntry.FILTER_INCLUDE), entries.map { it.filterType })
    }

    @Test fun flagsAndFiltersSurviveSavingAndEveryBisyncOperation() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = DatabaseHandler(context)
        val saved = db.createFilter(Filter(0).apply {
            title = "Anki fixture"
            setFilters(listOf(FilterEntry(FilterEntry.FILTER_INCLUDE, "**"), FilterEntry(FilterEntry.FILTER_EXCLUDE, "/cache/**")))
        })
        val task = db.createTask(Task(0).apply {
            title = "Anki fixture"; direction = 6; remoteId = "fixture"; remotePath = "right"; localPath = "/tmp/left"
            transfers = 1; filterId = saved.id; bisyncResyncMode = "path2"
            bisyncAdvancedOptions = "--exclude \"/.nomedia\"\n--include=**\n--tpslimit=6"
        })
        val loaded = db.getTask(task.id)!!
        val filters = db.getFilter(loaded.filterId!!)!!.getFilters()
        val rclone = object : Rclone(context) {
            override fun getRemoteItemFromName(name: String): RemoteItem = RemoteItem(name, "webdav")
        }
        for (operation in listOf("initialise", "run")) {
            val args = rclone.bisyncArguments(BisyncPolicy.effectiveTask(loaded, 1), File(context.filesDir, "fixture-baseline"), operation, filters, false).toList()
            assertTrue("$operation lost a non-filter flag", args.contains("--tpslimit=6"))
            assertTrue("$operation lost the exclusion", args.contains("--filter=- /.nomedia"))
            assertTrue(args.indexOf("--filter=- /.nomedia") < args.indexOf("--filter=+ **"))
            assertTrue(args.contains("--filter=- /cache/**"))
            assertTrue(args.contains("--filter=- /**"))
            assertFalse(args.any { it.startsWith("--include=") || it.startsWith("--exclude=") })
            assertEquals(operation != "run", args.contains("--resync-mode=path2"))
            assertFalse(args.contains("--dry-run"))
        }
    }

    @Test fun invalidPatternsAndResetCannotSilentlyDisableRestrictions() {
        for (pattern in listOf("", "  ", "--exclude=/.nomedia", "bad\nname", "\"unfinished")) {
            assertThrows(IllegalArgumentException::class.java) { FilterRules.pattern(pattern) }
        }
        assertThrows(IllegalArgumentException::class.java) { FilterRules.savedPattern(FilterEntry(99, "*")) }
        assertThrows(IllegalArgumentException::class.java) { FilterRules.savedPattern(FilterEntry(FilterEntry.FILTER_EXCLUDE, "+ *")) }
        val saved = listOf(FilterEntry(FilterEntry.FILTER_EXCLUDE, "/.nomedia"))
        for (reset in listOf("--filter=!", "--filter=\"!\"")) {
            assertThrows(IllegalArgumentException::class.java) { FilterRules.arguments(saved, listOf(reset)) }
            assertThrows(IllegalArgumentException::class.java) { FilterRules.arguments(emptyList(), listOf("--include=*", reset)) }
        }
        val expert = listOf("--filter=+ name with spaces ", "--filter=!", "--filter=- /**")
        assertEquals(expert, FilterRules.arguments(emptyList(), expert))
    }

    @Test fun restrictiveIncludesRequireReviewForLegacyDestructiveTasks() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        val db = DatabaseHandler(context)
        val task = db.createTask(Task(0).apply { direction = 3; deleteExcluded = true })
        val filters = listOf(FilterEntry(FilterEntry.FILTER_INCLUDE, "*.anki2"))
        assertThrows(IllegalArgumentException::class.java) { FilterRules.requireReviewedDeletion(db.getTask(task.id)!!, filters) }
        task.filterPolicyVersion = 1
        db.updateTask(task)
        assertEquals(1, db.getTask(task.id)!!.filterPolicyVersion)
        FilterRules.requireReviewedDeletion(db.getTask(task.id)!!, filters)
        FilterRules.requireReviewedDeletion(Task(0), filters)
    }
}
