package io.github.rareities.protonbridge.util

import android.content.Context
import io.github.rareities.protonbridge.BuildConfig
import io.github.rareities.protonbridge.Items.FilterEntry
import io.github.rareities.protonbridge.Items.Task
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Validation and identity for the engine-owned private bisync baseline. */
object BisyncPolicy {
    @JvmStatic fun parseFlags(input: String): List<String> = AdvancedOptions.parse(input)

    @JvmStatic fun isBisync(task: Task): Boolean = task.direction == 5 || task.direction == 6

    /** Legacy initial-bisync and current bisync are the same baseline mode. */
    @JvmStatic fun normalisedMode(direction: Int): String =
        if (direction == 5 || direction == 6) "bisync" else "one-way:$direction"

    /** A one-way transfer may invalidate every previous two-way listing. */
    @JvmStatic fun identityForEdit(previous: Task?, direction: Int): String {
        val old = previous?.bisyncUUID?.takeIf { isValidIdentity(it) }
        val crossedBisyncBoundary = previous?.let { isBisync(it) != (direction == 5 || direction == 6) } ?: false
        return if (old == null || crossedBisyncBoundary) {
            UUID.randomUUID().toString()
        } else old
    }

    @JvmStatic fun isValidIdentity(value: String): Boolean = try {
        UUID.fromString(value).toString() == value.lowercase(java.util.Locale.ROOT)
    } catch (_: IllegalArgumentException) { false }

    @JvmStatic fun workdir(context: Context, task: Task): File {
        require(isValidIdentity(task.bisyncUUID)) {
            "Missing bisync identity. Review and save the task."
        }
        val directory = File(context.filesDir, "bisync/${task.bisyncUUID}")
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create private bisync state" }
        return directory
    }

    @JvmStatic fun validate(context: Context, task: Task) {
        task.validationError?.let { throw IllegalArgumentException(it) }
        require(task.bisyncMaxDelete in 0..100) { "Deletion limit must be a percentage from 0 to 100" }
        require(task.bisyncResyncMode in setOf("path1", "path2")) { "Select local or remote precedence for initialisation" }
        require(!task.deleteExcluded) { "Delete excluded files is unavailable for bisync. Change filters and review a new baseline instead." }
        require(task.remoteId.isNotBlank() && task.remotePath.isNotBlank()) { "Select a remote and folder" }
        require(listOf(task.localPath, task.remoteId, task.remotePath).none { it.contains('\u0000') }) { "Invalid endpoint" }
        val local = File(task.localPath)
        require(local.isAbsolute && local.isDirectory) { "Local folder is unavailable. Restore its storage permission before continuing." }
        val state = workdir(context, task)
        require(!overlaps(local.canonicalPath, state.canonicalPath)) { "A sync folder cannot contain the app's private bisync state" }
        if (task.bisyncBackupDir1.isNotBlank()) {
            val backup = File(task.bisyncBackupDir1)
            require(backup.isAbsolute && !overlaps(local.canonicalPath, backup.canonicalPath)) {
                "Local backup folder must be an absolute path outside the synchronised folder"
            }
        }
        if (task.bisyncBackupDir2.isNotBlank()) {
            require(task.bisyncBackupDir2.startsWith(task.remoteId + ":")) { "Remote backup must use the same remote" }
            val root = if (task.remotePath == "//${task.remoteId}") "" else remotePath(task.remotePath)
            val backup = remotePath(task.bisyncBackupDir2.substringAfter(':'))
            require(root.isNotEmpty() && backup.isNotEmpty() && !overlaps(root, backup)) {
                "Remote backup folder must be outside the synchronised folder"
            }
        }
        parseFlags(task.bisyncAdvancedOptions)
    }

    private fun remotePath(path: String): String {
        val pieces = path.split('/').filter { it.isNotEmpty() && it != "." }
        require(pieces.none { it == ".." }) { "Parent traversal is not allowed in backup paths" }
        return pieces.joinToString("/")
    }

    @JvmStatic fun overlaps(first: String, second: String): Boolean {
        val a = first.trimEnd('/', '\\')
        val b = second.trimEnd('/', '\\')
        return a == b || b.startsWith("$a/") || a.startsWith("$b/") ||
            b.startsWith("$a\\") || a.startsWith("$b\\")
    }

    /** Preserve every serialized option; data-class copy only copies the constructor ID. */
    @JvmStatic fun effectiveTask(task: Task, transfers: Int): Task {
        require(transfers > 0) { "Choose a positive transfer count" }
        return Task.fromString(task.asJSON().toString()).apply { this.transfers = transfers }
    }

    @JvmStatic fun fingerprint(task: Task, filters: List<FilterEntry>, configIdentity: String): String =
        fingerprint(task, filters, configIdentity, false)

    /** Exact pre-recovery-policy hash, only for recognising an unchanged saved READY baseline. */
    @JvmStatic fun legacyFingerprint(task: Task, filters: List<FilterEntry>, configIdentity: String): String =
        fingerprint(task, filters, configIdentity, true)

    private fun fingerprint(task: Task, filters: List<FilterEntry>, configIdentity: String,
                            includeRecoveryControls: Boolean): String {
        val values = listOf(
            "bisync-policy-v5", normalisedMode(task.direction), task.bisyncUUID,
            File(task.localPath).canonicalPath,
            task.remoteId, task.remotePath, task.remoteType.toString(), task.bisyncMaxDelete.toString(),
            task.bisyncResyncMode, task.bisyncCheckAccess.toString(), task.bisyncBackupDir1,
            task.bisyncBackupDir2, task.transfers?.toString() ?: "default", task.deleteExcluded.toString(),
            BuildConfig.RCLONE_SOURCE_SHA, configIdentity
        ) + parseFlags(task.bisyncAdvancedOptions).filterNot {
            // Recovery controls act on the same listings, not a different file selection.
            !includeRecoveryControls && it.substringBefore('=') in setOf("--recover", "--resilient")
        } + filters.map { "${it.filterType}:${it.filter}" }
        val digest = MessageDigest.getInstance("SHA-256")
        for (value in values) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(4).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** Shared by every mutating transfer worker. OS releases the file lock after process death. */
    class Admission private constructor(private val file: RandomAccessFile, private val lock: FileLock) : AutoCloseable {
        private val closed = AtomicBoolean()
        override fun close() {
            if (closed.compareAndSet(false, true)) {
                try { lock.release() } finally { file.close() }
            }
        }
        fun closeAfterExit(process: Process?) {
            if (process == null) { close(); return }
            try { process.exitValue(); close() }
            catch (_: IllegalThreadStateException) {
                Thread({
                    try { process.waitFor() } catch (_: InterruptedException) { return@Thread }
                    finally {
                        try { process.exitValue(); close() } catch (_: IllegalThreadStateException) { }
                    }
                }, "rclone-admission-release").apply { isDaemon = true; start() }
            }
        }
        companion object {
            fun acquire(context: Context): Admission? {
                val root = File(context.filesDir, "bisync")
                require(root.isDirectory || root.mkdirs()) { "Cannot open task admission state" }
                val file = RandomAccessFile(File(root, "admission.lock"), "rw")
                try {
                    val lock = file.channel.tryLock()
                    if (lock == null) { file.close(); return null }
                    return Admission(file, lock)
                } catch (_: java.nio.channels.OverlappingFileLockException) {
                    file.close()
                    return null
                } catch (e: Exception) { file.close(); throw e }
            }
        }
    }
}
