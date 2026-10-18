package io.github.rareities.protonbridge.util

import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException

/** App state is separate from rclone's listing files. All transitions are persisted atomically. */
class BisyncStateStore(private val directory: File) {
    companion object {
        const val RUN = "run"
        const val INITIALISE = "initialise"
        const val NEEDS_INITIALISATION = "NEEDS_INITIALISATION"
        const val NEEDS_RECOVERY = "NEEDS_RECOVERY"
        const val READY = "READY"
        private val ioLock = Any()

        @JvmStatic fun isReady(state: JSONObject, fingerprint: String): Boolean =
            state.optString("status") == READY && state.optString("fingerprint") == fingerprint &&
                BisyncPolicy.isValidIdentity(state.optString("baselineId"))

        /** Recovery never creates a baseline; the native engine validates its existing listings. */
        @JvmStatic fun isRunnable(state: JSONObject, fingerprint: String, recoveryEnabled: Boolean = false): Boolean =
            isReady(state, fingerprint) || (recoveryEnabled &&
                state.optString("status") in setOf(NEEDS_RECOVERY, "RUNNING") &&
                state.optString("fingerprint") == fingerprint &&
                BisyncPolicy.isValidIdentity(state.optString("baselineId")))

        @JvmStatic fun baselineForRun(state: JSONObject, fingerprint: String, recoveryEnabled: Boolean = false): String {
            require(isRunnable(state, fingerprint, recoveryEnabled)) {
                val reason = when {
                    state.optString("fingerprint").isNotBlank() && state.optString("fingerprint") != fingerprint ->
                        "The task settings, filters, configuration or bundled engine have changed since Resync."
                    state.optString("status") == READY -> "The saved bisync baseline identity is missing or invalid."
                    state.optString("status") in setOf(NEEDS_RECOVERY, "RUNNING", "INITIALISING") ->
                        "The previous bisync did not complete. Recovery requires enabled --recover or --resilient and a matching saved baseline."
                    else -> "Bisync has no completed baseline."
                }
                "$reason Review the settings and select Resync to create a new baseline."
            }
            return state.getString("baselineId")
        }

    }

    private val file = AtomicFile(File(directory, "protonbridge.json"))

    fun read(): JSONObject = synchronized(ioLock) {
        try {
            val bytes = file.openRead().use { stream ->
                val data = ByteArray(65537)
                var size = 0
                while (size < data.size) {
                    val n = stream.read(data, size, data.size - size)
                    if (n < 0) break
                    size += n
                }
                require(size <= 65536) { "State file is too large" }
                data.copyOf(size)
            }
            JSONObject(String(bytes, Charsets.UTF_8)).also {
                require(it.getInt("version") == 1) { "Unknown state version" }
                // The retired advisory operation copied listings and never mutated the live baseline.
                if (it.optString("status") == "PREVIEWING" && it.optBoolean("previewWasReady") &&
                    it.optString("fingerprint").matches(Regex("[0-9a-f]{64}")) &&
                    BisyncPolicy.isValidIdentity(it.optString("baselineId"))) it.put("status", READY)
                listOf("preview", "previewAt", "previewFingerprint", "previewWasReady").forEach { key -> it.remove(key) }
            }
        } catch (_: FileNotFoundException) {
            JSONObject().put("version", 1).put("status", NEEDS_INITIALISATION)
        } catch (_: Exception) {
            JSONObject().put("version", 1).put("status", NEEDS_RECOVERY)
                .put("message", "Saved bisync state could not be read. Review recovery before running.")
        }
    }

    /** Called under worker admission: only an exact unchanged legacy READY policy can migrate. */
    fun migrateLegacyFingerprint(fingerprint: String, legacyFingerprint: String) = synchronized(ioLock) {
        val state = read()
        if (fingerprint != legacyFingerprint && isReady(state, legacyFingerprint)) {
            state.put("fingerprint", fingerprint)
            write(state)
        }
    }

    fun begin(operation: String, fingerprint: String, approval: String?, runId: String,
              recoveryEnabled: Boolean = false) = synchronized(ioLock) {
        val state = read()
        when (operation) {
            RUN -> baselineForRun(state, fingerprint, recoveryEnabled)
            INITIALISE -> require(approval == fingerprint) {
                "Resync confirmation no longer matches the task, filters, configuration or engine. Review the current settings and confirm Resync again."
            }
            else -> throw IllegalArgumentException("Unknown bisync operation")
        }
        state.put("status", if (operation == INITIALISE) "INITIALISING" else "RUNNING")
            .put("runId", runId).put("operation", operation)
            .put("startedAt", System.currentTimeMillis()).put("message", "")
        write(state)
    }

    fun finish(operation: String, fingerprint: String, runId: String, success: Boolean,
               exitCode: Int?, message: String) = synchronized(ioLock) {
        val state = read()
        if (state.optString("runId") != runId) return@synchronized
        state.put("finishedAt", System.currentTimeMillis())
            .put("exitCode", exitCode ?: JSONObject.NULL)
            .put("message", message.take(2048))
        if (success) {
            state.put("status", READY).put("fingerprint", fingerprint)
                .put("lastSuccessfulRun", System.currentTimeMillis())
            if (operation == INITIALISE) state.put("baselineId", runId)
        } else state.put("status", NEEDS_RECOVERY)
        write(state)
    }

    private fun write(value: JSONObject) {
        require(directory.isDirectory || directory.mkdirs()) { "Cannot create private bisync state" }
        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65536) { "Bisync state exceeds the private storage limit" }
        val stream = file.startWrite()
        try {
            stream.write(bytes)
            file.finishWrite(stream)
        } catch (error: Exception) {
            file.failWrite(stream)
            throw error
        }
    }
}
