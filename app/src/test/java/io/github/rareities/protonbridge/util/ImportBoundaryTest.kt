package io.github.rareities.protonbridge.util

import android.app.Application
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Database.json.ImportPlan
import io.github.rareities.protonbridge.Database.json.Importer
import io.github.rareities.protonbridge.Database.json.SharedPreferencesBackup
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.R
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImportBoundaryTest {
    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private fun archive(vararg tasks: Task): String = JSONObject()
        .put("tasks", JSONArray().apply { tasks.forEach { put(it.asJSON()) } })
        .put("filters", JSONArray()).put("trigger", JSONArray()).toString()
    private fun task(id: Long = 1) = Task(id).apply {
        title = "Imported fixture"; direction = 3; remoteId = "remote"; remotePath = "folder"
        localPath = "/storage/emulated/0/Download/sync"
    }

    @Test fun privateRootsAndTheirAncestorsAreRejectedBeforeExistingTasksChange() {
        val db = DatabaseHandler(context)
        val saved = db.createTask(task(0).apply { title = "Keep existing" })
        val data = context.applicationInfo.dataDir
        val paths = listOf(data, context.filesDir.path, File(context.filesDir, "../shared_prefs").path,
            File(data).parent!!, "/", context.createDeviceProtectedStorageContext().applicationInfo.dataDir)
        for (path in paths) {
            assertThrows("Accepted private sync path $path", JSONException::class.java) {
                Importer.importJson(archive(task().apply { localPath = path }), context)
            }
            assertEquals("Keep existing", db.getTask(saved.id)!!.title)
        }
    }

    @Test fun bothCloudEndpointNamesRejectLeadingOptionPrefix() {
        for (second in listOf(false, true)) {
            val bad = task().apply {
                if (second) { direction = 7; remoteId2 = "--config" } else remoteId = "--config"
            }
            assertThrows(JSONException::class.java) { ImportPlan.parse(archive(bad), context) }
        }
    }

    @Test fun ordinaryStorageAndSimilarPrivatePrefixAreNotRejected() {
        assertEquals(1, ImportPlan.parse(archive(task()), context).tasks.size)
        val adjacent = task().apply { localPath = context.applicationInfo.dataDir + "-other/folder" }
        assertEquals(adjacent.localPath, ImportPlan.parse(archive(adjacent), context).tasks.single().localPath)
    }

    @Test fun invalidProxyCannotPartiallyChangePreferences() {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val key = context.getString(R.string.pref_key_use_proxy)
        prefs.edit().putBoolean(key, true).commit()
        val original = SharedPreferencesBackup.export(context)
        for (change in listOf("proxyProtocol" to "file", "proxyPort" to 0, "proxyHost" to "https://attacker")) {
            val changed = JSONObject(original).put(change.first, change.second).put("useProxy", false)
            assertThrows(JSONException::class.java) { SharedPreferencesBackup.importJson(changed.toString(), context) }
            assertTrue(prefs.getBoolean(key, false))
        }
        // The same importer is used for trusted rollback; its previous policy must survive.
        SharedPreferencesBackup.importJson(original, context)
        assertTrue(prefs.getBoolean(key, false))
    }
}
