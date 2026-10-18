package io.github.rareities.protonbridge.util

/** Explain verified listing failures without treating them as a completed sync. */
object BisyncFailureReport {
    fun exited(code: Int, diagnostic: String): String {
        val clean = DiagnosticRedactor.redact(diagnostic)
        val explanation = if (clean.contains("Checksum not equal in listing.") &&
            clean.contains("Bisync aborted")) {
            "The recorded checksums disagree; the sync could not verify matching contents. " +
                "This run did not complete successfully. Review the task before recovery. " +
                "Close apps editing this folder, compare " +
                "the affected local and remote files, then review the settings and " +
                "confirm Resync. Do not bypass checksum checks.\n"
        } else ""
        return explanation + "rclone exited with status $code.\n" + clean.takeLast(1400)
    }
}
