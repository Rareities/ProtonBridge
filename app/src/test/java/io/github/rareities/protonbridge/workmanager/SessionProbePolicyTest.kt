package io.github.rareities.protonbridge.workmanager

import org.junit.Assert.*
import org.junit.Test

class SessionProbePolicyTest {
    @Test fun absentOrEmptyCredentialsDoNotBootstrapMaintenanceLogin() {
        assertFalse(SessionProbePolicy.hasCachedSession(null, null))
        assertFalse(SessionProbePolicy.hasCachedSession("", ""))
        assertFalse(SessionProbePolicy.hasCachedSession("  \t", "\n"))
    }

    @Test fun actualCachedCredentialRemainsEligibleForOptedInChecks() {
        assertTrue(SessionProbePolicy.hasCachedSession("cached-token", null))
        assertTrue(SessionProbePolicy.hasCachedSession(null, "cached-access-token"))
    }

    @Test fun malformedNonStringValuesAreNotSessions() {
        assertFalse(SessionProbePolicy.hasCachedSession(false, 42))
        assertFalse(SessionProbePolicy.hasCachedSession(Any(), null))
    }
}
