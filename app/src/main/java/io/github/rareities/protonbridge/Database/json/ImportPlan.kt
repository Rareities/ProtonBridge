package io.github.rareities.protonbridge.Database.json

import android.content.Context
import android.os.Build
import java.io.File
import io.github.rareities.protonbridge.Items.Filter
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Items.Trigger
import io.github.rareities.protonbridge.util.AdvancedOptions
import io.github.rareities.protonbridge.util.ScheduleCalculator
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/** Parse and validate the complete replacement before any database mutation. */
class ImportPlan(val tasks: List<Task>, val filters: List<Filter>, val triggers: List<Trigger>) {
    companion object {
        @JvmStatic @Throws(JSONException::class)
        fun parse(json: String, context: Context): ImportPlan {
            try {
                require(json.length <= 4 * 1024 * 1024) { "Task backup exceeds 4 MiB" }
                val root = JSONObject(json)
                val taskJson = root.getJSONArray("tasks")
                val filterJson = root.getJSONArray("filters")
                val triggerJson = root.getJSONArray("trigger")
                require(taskJson.length() <= 2000 && filterJson.length() <= 2000 && triggerJson.length() <= 5000) {
                    "Task backup contains too many records"
                }
                val tasks = (0 until taskJson.length()).map { Task.fromString(taskJson.getJSONObject(it).toString()) }
                val filters = (0 until filterJson.length()).map { Filter.fromString(filterJson.getJSONObject(it).toString()) }
                val triggers = (0 until triggerJson.length()).map { Trigger.fromString(triggerJson.getJSONObject(it).toString()) }
                fun unique(ids: List<Long>, name: String): Set<Long> {
                    require(ids.all { it >= 0 } && ids.toSet().size == ids.size) { "Invalid or duplicate $name IDs" }
                    return ids.toSet()
                }
                val taskIds = unique(tasks.map { it.id }, "task")
                val filterIds = unique(filters.map { it.id }, "filter")
                unique(triggers.map { it.id }, "schedule")
                val privateRoots = mutableListOf(File(context.applicationInfo.dataDir).canonicalFile)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    privateRoots.add(File(context.createDeviceProtectedStorageContext().applicationInfo.dataDir).canonicalFile)
                }
                for (task in tasks) {
                    require(listOf(task.remoteId, task.remoteId2).none { it.startsWith("-") }) {
                        "Imported task has an invalid remote name"
                    }
                    if (task.localPath.isNotBlank()) {
                        val local = File(task.localPath).canonicalPath
                        require(privateRoots.none { root ->
                            val privatePath = root.path
                            local == privatePath || local.startsWith(privatePath + File.separator) ||
                                privatePath.startsWith(local.trimEnd(File.separatorChar) + File.separator)
                        }) { "Imported task cannot synchronise app-private data" }
                    }
                    require(task.title.isNotBlank()) { "Imported task has no name" }
                    require(task.filterId == null || task.filterId == -1L || task.filterId in filterIds) { "Imported task refers to a missing filter" }
                    for (next in listOf(task.onFailFollowup, task.onSuccessFollowup))
                        require(next == null || next == -1L || next in taskIds) { "Imported task refers to a missing follow-up" }
                    require(task.bisyncMaxDelete in 0..100) { "Invalid bisync deletion percentage" }
                    require(task.bisyncResyncMode in setOf("path1", "path2")) { "Invalid initialisation preference" }
                    ScheduleCalculator.afterQuietHours(0L, task.quietHoursStart, task.quietHoursEnd, java.util.TimeZone.getTimeZone("UTC"))
                    AdvancedOptions.parse(task.bisyncAdvancedOptions)
                    // An imported task never inherits an on-device baseline or shortcut capability.
                    task.bisyncUUID = UUID.randomUUID().toString()
                }
                validateFollowups(tasks)
                for (trigger in triggers) {
                    require(trigger.triggerTarget in taskIds) { "Imported schedule refers to a missing task" }
                    require(trigger.type in 0..1 && trigger.getWeekdays() in 1..127) { "Invalid imported schedule" }
                    ScheduleCalculator.resolveZone(trigger.zoneId)
                    require(if (trigger.type == 0) trigger.time in 0..1439 else trigger.time in 15..525600) { "Invalid imported schedule time or interval" }
                    trigger.nextDue = 0
                    if (tasks.first { it.id == trigger.triggerTarget }.direction in 5..6) trigger.isEnabled = false
                }
                return ImportPlan(tasks, filters, triggers)
            } catch (e: JSONException) { throw e }
            catch (e: Exception) { throw JSONException(e.message ?: "Invalid task backup") }
        }

        @JvmStatic fun validateFollowups(tasks: List<Task>) {
            val edges = tasks.associate { task -> task.id to listOfNotNull(task.onSuccessFollowup, task.onFailFollowup).filter { it >= 0 } }
            val finished = mutableSetOf<Long>()
            for (root in edges.keys) {
                val stack = java.util.ArrayDeque<Pair<Long, Boolean>>()
                val active = mutableSetOf<Long>()
                stack.push(root to false)
                while (stack.isNotEmpty()) {
                    val (id, leaving) = stack.pop()
                    if (leaving) { active.remove(id); finished.add(id); continue }
                    require(id !in active) { "Follow-up tasks contain a cycle" }
                    if (id in finished) continue
                    active.add(id)
                    stack.push(id to true)
                    for (next in edges[id].orEmpty()) stack.push(next to false)
                }
            }
        }
    }
}
