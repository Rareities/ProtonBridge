package io.github.rareities.protonbridge.workmanager

/** Optional health checks use cached sessions; they must not bootstrap password/TOTP logins. */
internal object SessionProbePolicy {
    fun hasCachedSession(token: Any?, accessToken: Any?): Boolean =
        token is String && token.isNotBlank() || accessToken is String && accessToken.isNotBlank()
}
