package ca.pkay.rcloneexplorer.workmanager

import android.content.Context
import android.os.Build
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.AtomicFile
import ca.pkay.rcloneexplorer.BuildConfig
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.Rclone
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.channels.OverlappingFileLockException
import java.security.MessageDigest

/** Android-side consent and run state. rclone owns, and separately verifies, its listings. */
internal class ProtonBisync(private val context: Context, private val rclone: Rclone) {
    data class Endpoints(val local: String, val remote: String, val digest: String)
    data class Approval(val endpoints: Endpoints, val protonIdentity: String)

    enum class Result { COMPLETE, NEEDS_RESYNC, FAILED, BUSY }

    private fun endpoints(task: Task): Endpoints? {
        if (task.id <= 0 || task.filterId != null || task.deleteExcluded ||
            task.localPath.contains('\u0000') || task.remotePath.contains('\u0000') ||
            task.remoteId.isBlank() || task.remoteId.any { it == ':' || it == ',' || it == '\u0000' }) return null
        val local = File(task.localPath)
        if (!local.isAbsolute || !local.isDirectory) return null
        val canonical = local.canonicalFile
        val appData = File(context.applicationInfo.dataDir).canonicalPath
        if (overlaps(canonical.path, appData)) return null
        if (Build.VERSION.SDK_INT >= 24) {
            val protectedData = context.createDeviceProtectedStorageContext().dataDir.canonicalPath
            if (overlaps(canonical.path, protectedData)) return null
        }
        val location = try {
            Os.stat(canonical.path)
        } catch (_: ErrnoException) {
            return null
        }
        if (!OsConstants.S_ISDIR(location.st_mode)) return null
        val settings = JSONObject(rclone.readBisyncConfig()).optJSONObject(task.remoteId) ?: return null
        if (settings.optString("type") != "protondrive" || settings.optString("username").isBlank()) return null
        val folder = if (task.remotePath == "//${task.remoteId}") "" else task.remotePath
        val remote = "${task.remoteId}:$folder"
        val inputs = JSONArray().put(BuildConfig.RCLONE_SOURCE_SHA).put(task.id).put(canonical.path)
            .put(location.st_dev).put(location.st_ino).put(remote).put(configurationIdentity(settings))
        return Endpoints(canonical.path, remote, sha256(inputs.toString()))
    }

    fun prepareApproval(task: Task): Approval? {
        val before = endpoints(task) ?: return null
        val protonIdentity = rclone.identifyProtonBisyncRoot(before.remote)
        val after = endpoints(task) ?: return null
        return if (before == after) Approval(after, protonIdentity) else null
    }

    fun run(task: Task, approval: String?, cancelled: () -> Boolean,
            execute: (Endpoints, File) -> Boolean): Result {
        val root = File(context.noBackupFilesDir, "proton-bisync")
        if (!root.isDirectory && !root.mkdirs()) return Result.FAILED
        RandomAccessFile(File(root, "run.lock"), "rw").channel.use { channel ->
            val lock = try {
                channel.tryLock()
            } catch (_: OverlappingFileLockException) {
                null
            } ?: return Result.BUSY
            lock.use {
                val directory = File(root, task.id.toString())
                if (!directory.isDirectory && !directory.mkdir()) return Result.FAILED
                val mark = AtomicFile(File(directory, "ready"))
                val current = endpoints(task) ?: return Result.NEEDS_RESYNC
                val expected = approval ?: try {
                    String(mark.readFully(), Charsets.UTF_8)
                } catch (_: IOException) {
                    ""
                }
                if (current.digest != expected || cancelled()) return Result.NEEDS_RESYNC
                // Invalidate durably before spawning a child. A killed or failed run requires
                // fresh consent, even when the native listings happen to survive intact.
                write(mark, "")
                if (!execute(current, directory) || cancelled()) return Result.FAILED
                write(mark, current.digest)
                return Result.COMPLETE
            }
        }
    }

    private fun overlaps(left: String, right: String): Boolean =
        left == right || left.startsWith(right.trimEnd('/') + "/") ||
            right.startsWith(left.trimEnd('/') + "/")

    private fun write(file: AtomicFile, value: String) {
        val stream = file.startWrite()
        try {
            stream.write(value.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
            file.finishWrite(stream)
        } catch (e: IOException) {
            file.failWrite(stream)
            throw e
        }
        if (String(file.readFully(), Charsets.UTF_8) != value) {
            throw IOException("Bisync state could not be committed")
        }
    }

    companion object {
        private val rotating = setOf("client_uid", "client_access_token", "client_refresh_token",
            "client_salted_key_pass")

        internal fun configurationIdentity(settings: JSONObject): String {
            val values = JSONArray()
            for (key in settings.keys().asSequence().filterNot { it in rotating }.sorted()) {
                values.put(JSONArray().put(key).put(settings.get(key)))
            }
            return sha256(values.toString())
        }

        private fun sha256(value: String): String =
            MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) }
    }
}
