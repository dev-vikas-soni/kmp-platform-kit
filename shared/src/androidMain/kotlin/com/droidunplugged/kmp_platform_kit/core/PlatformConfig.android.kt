package com.droidunplugged.kmp_platform_kit.core

/**
 * Android actual for PlatformConfig.
 *
 * Two @Volatile maps for atomic swap:
 * - **dynamicHeaders**: authorization, x-api-guid (change at runtime)
 * - **envHeaders**: clientid, x-api-key (set once at init, fixed for session)
 *
 * [getHeader] resolves dynamic first → env second.
 */
actual object PlatformConfig {

    private val lock = Any()

    @Volatile
    private var dynamicHeaders: Map<String, String> = emptyMap()

    @Volatile
    private var envHeaders: Map<String, String> = emptyMap()

    actual fun setDynamicHeaders(map: Map<String, String>) {
        synchronized(lock) {
            dynamicHeaders = map.toMap()
        }
    }

    actual fun setEnvHeaders(map: Map<String, String>) {
        synchronized(lock) {
            envHeaders = map.toMap()
        }
    }

    actual fun updateAuthToken(token: String) {
        synchronized(lock) {
            dynamicHeaders = dynamicHeaders.toMutableMap().apply {
                put("authorization", token)
            }
        }
    }

    actual fun getHeader(key: String): String? =
        dynamicHeaders[key] ?: envHeaders[key]
}