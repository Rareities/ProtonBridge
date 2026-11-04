package io.github.rareities.protonbridge.Database.json

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import androidx.work.WorkManager
import io.github.rareities.protonbridge.Database.DatabaseHandler
import io.github.rareities.protonbridge.Items.Filter
import io.github.rareities.protonbridge.Items.Task
import io.github.rareities.protonbridge.Items.Trigger
import io.github.rareities.protonbridge.Rclone
import io.github.rareities.protonbridge.RcloneRcd
import io.github.rareities.protonbridge.util.BisyncPolicy
import io.github.rareities.protonbridge.util.ConfigurationExclusion
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Private rollback journal makes the three-component archive restore recoverable after a crash. */
object BackupRestore {
    private fun root(context: Context) = File(context.filesDir, "restore-journal")
    private fun passwordFile(context: Context) = File(context.filesDir, "config-password.json")

    @JvmStatic @JvmOverloads @Throws(Exception::class)
    fun restore(context: Context, rclone: Rclone, uri: Uri, password: String? = null): Unit = run {
        val archive = BackupArchive.read(context, uri, password)
        try {
            val plan = ImportPlan.parse(archive.tasks, context)
            val preferences = JSONObject(archive.preferences)
            val proxyPassword = if (archive.encrypted) preferences.optString("proxyPassword", "") else ""
            require(proxyPassword.toByteArray(Charsets.UTF_8).size <= 8192) { "Proxy password exceeds size limit" }
            preferences.remove("proxyPassword")
            // Old backups did not contain the proxy credential. Never send an old device
            // password to a newly restored host, or silently bypass its required proxy.
            if (!archive.encrypted && preferences.optBoolean("useProxy"))
                throw IOException("This legacy backup has no protected proxy credential. Import its configuration separately, then configure the proxy explicitly.")
            SharedPreferencesBackup.validateJson(preferences.toString(), context)
            val staging = io.github.rareities.protonbridge.util.ConfigStaging.create(context, "restore-config-", true)
            val prepared = try {
                staging.writeBytes(archive.config)
                val preparedConfig = rclone.protectImportedConfig(staging, password)
                Pair(staging.readBytes(), preparedConfig)
            } finally { io.github.rareities.protonbridge.util.ConfigStaging.discard(staging) }
            commit(context, rclone, prepared.first, prepared.second, plan, preferences.toString(), proxyPassword)
        } finally { archive.config.fill(0) }
    }

    /** Raw .conf import uses the same crash journal as the three-part ZIP restore. */
    @JvmStatic @Throws(IOException::class)
    fun installConfig(context: Context, rclone: Rclone, staged: File,
                      prepared: io.github.rareities.protonbridge.util.ProtectedRcloneConfig.PreparedImport) = run {
        try {
            commit(context, rclone, staged.readBytes(), prepared, null, null)
        } catch (error: IOException) { throw error }
        catch (error: Exception) { throw IOException("Could not install the configuration safely", error) }
    }

    private fun commit(context: Context, rclone: Rclone, protectedConfig: ByteArray,
                       prepared: io.github.rareities.protonbridge.util.ProtectedRcloneConfig.PreparedImport,
                       plan: ImportPlan?, restoredPreferences: String?, proxyPassword: String? = null) {
        val lock = ConfigurationExclusion.LOCK.writeLock()
        lock.lock()
        var schedulesNeedReconcile = false
        try {
            if (RcloneRcd.hasRunningProcess() || io.github.rareities.protonbridge.util.ProtectedRcloneConfig.hasRunningProcess())
                throw IOException("Stop active transfers and the document-provider service before restoring a backup")
            val admission = BisyncPolicy.Admission.acquire(context)
                ?: throw IOException("Finish or cancel active transfers before restoring a backup")
            admission.use {
                prepared.assertCurrent(context)
                recoverLocked(context)
                val db = DatabaseHandler(context)
                val journal = root(context)
                require(journal.isDirectory || journal.mkdirs()) { "Cannot create restore journal" }
                io.github.rareities.protonbridge.util.DurableFiles.syncDirectory(context.filesDir)
                val config = File(context.filesDir, "rclone.conf")
                val wrapped = passwordFile(context)
                val hadConfig = config.exists() || File(config.path + ".bak").exists()
                val hadPassword = wrapped.exists() || File(wrapped.path + ".bak").exists()
                val backup = JSONObject().put("version", 1).put("hadConfig", hadConfig)
                    .put("hadPassword", hadPassword)
                    .put("database", JSONObject(Exporter.create(context)))
                    .put("preferences", JSONObject(SharedPreferencesBackup.export(context)))
                    .put("secretSettings", io.github.rareities.protonbridge.util.SecretSettings.snapshot(context))
                if (hadConfig) {
                    val oldConfig = AtomicFile(config).openRead().use { it.readBytes() }
                    require(oldConfig.size <= 4 * 1024 * 1024) { "Existing configuration is too large to back up safely" }
                    write(File(journal, "previous.conf"), oldConfig)
                }
                if (hadPassword) {
                    val oldRecord = AtomicFile(wrapped).openRead().use { it.readBytes() }
                    require(oldRecord.size <= 32768) { "Wrapped password is too large to back up safely" }
                    write(File(journal, "previous.password"), oldRecord)
                }
                write(File(journal, "previous.json"), backup.toString().toByteArray(Charsets.UTF_8))
                write(File(journal, "pending"), byteArrayOf(1))
                // Snapshots and pending must be durable before any live component changes.
                try {
                    io.github.rareities.protonbridge.util.ConfigurationGeneration.advance(context)
                    // Restored task IDs can refer to different jobs. Old queued requests must not carry across.
                    val work = WorkManager.getInstance(context)
                    work.cancelAllWorkByTag(io.github.rareities.protonbridge.workmanager.SyncManager.SYNC_WORK_TAG)
                        .result.get(15, TimeUnit.SECONDS)
                    work.cancelAllWorkByTag(io.github.rareities.protonbridge.workmanager.EphemeralTaskManager.EPHEMERAL_WORK_TAG)
                        .result.get(15, TimeUnit.SECONDS)
                    schedulesNeedReconcile = true
                    if (plan != null) {
                        val scheduler = io.github.rareities.protonbridge.Services.TriggerService(context)
                        for (old in db.allTrigger) scheduler.cancelTrigger(old.id)
                    }
                    write(config, protectedConfig)
                    if (prepared.passwordRecord != null) write(wrapped, prepared.passwordRecord)
                    if (plan != null) db.replaceAll(plan.tasks, plan.filters, plan.triggers)
                    if (restoredPreferences != null) SharedPreferencesBackup.importJson(restoredPreferences, context)
                    if (proxyPassword != null) io.github.rareities.protonbridge.util.SecretSettings.store(context,
                        context.getString(io.github.rareities.protonbridge.R.string.pref_key_proxy_password), proxyPassword)
                    syncInstalled(context)
                    retirePending(journal)
                    cleanupCommitted(journal)
                    rclone.invalidateRemotesCache()
                } catch (failure: Exception) {
                    try { recoverLocked(context) } catch (rollback: Exception) { failure.addSuppressed(rollback) }
                    throw failure
                }
            }
        } finally {
            lock.unlock()
            prepared.passwordRecord?.fill(0)
            if (schedulesNeedReconcile) {
                io.github.rareities.protonbridge.workmanager.ScheduleDispatchWorker.reconcileAfterRestore(context)
            }
        }
    }

    @JvmStatic @Throws(IOException::class)
    fun recoverIfNeeded(context: Context) = run {
        if (!ConfigurationExclusion.isRestoreRecoveryRequired() && !File(root(context), "pending").exists() && !File(root(context), "pending.bak").exists()) return@run
        val lock = ConfigurationExclusion.LOCK.writeLock()
        lock.lock()
        try {
            if (RcloneRcd.hasRunningProcess() || io.github.rareities.protonbridge.util.ProtectedRcloneConfig.hasRunningProcess())
                throw IOException("Pending configuration restore requires active processes to stop")
            val admission = BisyncPolicy.Admission.acquire(context) ?: throw IOException("Pending restore cannot recover while a transfer is active")
            admission.use { recoverLocked(context) }
        } catch (error: IOException) { throw error }
        catch (error: Exception) { throw IOException("Cannot recover the previous configuration generation", error) }
        finally { lock.unlock() }
    }

    private fun recoverLocked(context: Context) {
        val journal = root(context)
        val marker = File(journal, "pending")
        if (!ConfigurationExclusion.isRestoreRecoveryRequired() && !marker.exists() && !File(journal, "pending.bak").exists()) return
        val snapshotFile = File(journal, "previous.json")
        require(snapshotFile.length() <= 9 * 1024 * 1024) { "Restore journal is invalid" }
        val backup = JSONObject(AtomicFile(snapshotFile).openRead().bufferedReader().use { it.readText() })
        require(backup.getInt("version") == 1) { "Unsupported restore journal" }
        val data = backup.getJSONObject("database")
        val tasks = data.getJSONArray("tasks").let { array -> (0 until array.length()).map { Task.fromString(array.getJSONObject(it).toString()) } }
        val filters = data.getJSONArray("filters").let { array -> (0 until array.length()).map { Filter.fromString(array.getJSONObject(it).toString()) } }
        val triggers = data.getJSONArray("trigger").let { array -> (0 until array.length()).map { Trigger.fromString(array.getJSONObject(it).toString()) } }
        val config = File(context.filesDir, "rclone.conf")
        if (backup.getBoolean("hadConfig")) {
            val previous = File(journal, "previous.conf")
            require(previous.length() <= 4 * 1024 * 1024) { "Previous configuration is too large" }
            write(config, AtomicFile(previous).openRead().use { it.readBytes() })
        } else AtomicFile(config).delete()
        if (backup.has("hadPassword")) {
            val wrapped = passwordFile(context)
            if (backup.getBoolean("hadPassword")) {
                val previous = File(journal, "previous.password")
                require(previous.length() <= 32768) { "Previous wrapped password is too large" }
                write(wrapped, AtomicFile(previous).openRead().use { it.readBytes() })
            } else AtomicFile(wrapped).delete()
        }
        DatabaseHandler(context).replaceAll(tasks, filters, triggers)
        SharedPreferencesBackup.importJson(backup.getJSONObject("preferences").toString(), context)
        backup.optJSONObject("secretSettings")?.let {
            io.github.rareities.protonbridge.util.SecretSettings.restoreSnapshot(context, it)
        }
        syncInstalled(context)
        retirePending(journal)
        cleanupCommitted(journal)
    }

    private fun syncInstalled(context: Context) {
        // SQLite FULL commits sync the journal/WAL. Also commit parent entries and
        // the synchronously committed preference files before retiring rollback state.
        val directories = listOf(context.filesDir, context.getDatabasePath(
            io.github.rareities.protonbridge.Database.DatabaseInfo.DATABASE_NAME).parentFile!!,
            File(context.applicationInfo.dataDir, "shared_prefs"))
        for (directory in directories) if (directory.isDirectory) {
            if (directory.name == "shared_prefs") directory.listFiles()?.filter { it.isFile }?.forEach {
                io.github.rareities.protonbridge.util.DurableFiles.syncFileIfPresent(it)
            }
            io.github.rareities.protonbridge.util.DurableFiles.syncDirectory(directory)
            directory.parentFile?.let { io.github.rareities.protonbridge.util.DurableFiles.syncDirectory(it) }
        }
    }

    private fun retirePending(journal: File) {
        val pending = File(journal, "pending")
        AtomicFile(pending).delete()
        require(!pending.exists() && !File(journal, "pending.bak").exists()) { "Cannot retire restore journal" }
        try {
            io.github.rareities.protonbridge.util.DurableFiles.syncDirectory(journal)
            ConfigurationExclusion.restoreRecovered()
        } catch (failure: Exception) {
            ConfigurationExclusion.requireRestoreRecovery()
            // Keep native admission blocked while commit durability is uncertain.
            try { write(pending, byteArrayOf(1)) }
            catch (restore: Exception) { failure.addSuppressed(restore) }
            throw failure
        }
    }

    private fun cleanupCommitted(directory: File) {
        // The pending-marker barrier has already committed the installed state.
        // Cleanup cannot change that result or roll it back.
        try {
            for (name in listOf("previous.json", "previous.conf", "previous.password")) {
                AtomicFile(File(directory, name)).delete()
                require(!File(directory, name).exists() && !File(directory, "$name.bak").exists()) {
                    "Obsolete restore journal remains"
                }
            }
            io.github.rareities.protonbridge.util.DurableFiles.syncDirectory(directory)
        } catch (_: Exception) {
            android.util.Log.w("BackupRestore", "Restore committed; obsolete journal cleanup is deferred")
        }
    }

    private fun write(file: File, content: ByteArray) {
        val target = AtomicFile(file)
        val stream = target.startWrite()
        try { stream.write(content); stream.fd.sync(); target.finishWrite(stream) }
        catch (e: Exception) { target.failWrite(stream); throw e }
        io.github.rareities.protonbridge.util.DurableFiles.syncDirectory(file.parentFile!!)
        java.io.ByteArrayInputStream(content).use {
            io.github.rareities.protonbridge.util.DurableFiles.verifyContents(target, it, content.size)
        }
    }
}
