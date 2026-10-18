package io.github.rareities.protonbridge.util

import org.junit.Assert.*
import org.junit.Test

class BisyncFailureReportTest {
    @Test fun checksumAbortExplainsRecoveryAndPreservesActualFailure() {
        val report = BisyncFailureReport.exited(7,
            "\u001b[35mChecksum not equal in listing. Path1: abc, Path2: def\u001b[0m - .nomedia\n" +
                "Bisync critical error: path1 and path2 are out of sync\nBisync aborted. Must run --resync to recover.")
        assertTrue(report.contains("could not verify matching contents"))
        assertTrue(report.contains("did not complete successfully"))
        assertTrue(report.contains("confirm Resync"))
        assertTrue(report.contains("status 7"))
        assertTrue(report.contains(".nomedia"))
        assertFalse(report.contains('\u001b'))
    }

    @Test fun otherFailuresKeepTheirMeaningAndSecretsAreRedacted() {
        val report = BisyncFailureReport.exited(1, "network failed password=CANARY")
        assertTrue(report.startsWith("rclone exited with status 1"))
        assertFalse(report.contains("could not verify matching contents"))
        assertFalse(report.contains("CANARY"))
    }
}
