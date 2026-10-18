package io.github.rareities.protonbridge

import android.content.Context
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.util.BisyncPolicy

/** Captures the current explicit Resync settings, without running a dry-run. */
internal fun resyncFingerprint(context: Context, task: Task): String {
    val rclone = Rclone(context)
    val filters = task.filterId?.let { requireNotNull(DatabaseHandler(context).getFilter(it)).getFilters() }
        ?: arrayListOf()
    return BisyncPolicy.fingerprint(BisyncPolicy.effectiveTask(task, rclone.taskTransfers(task)),
        filters, rclone.configurationIdentity { false })
}
