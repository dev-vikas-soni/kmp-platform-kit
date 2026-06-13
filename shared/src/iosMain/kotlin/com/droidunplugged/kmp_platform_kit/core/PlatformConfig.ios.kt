package com.droidunplugged.kmp_platform_kit.core

import kotlin.concurrent.AtomicReference
import kotlin.concurrent.Volatile

/**
 * iOS actual for PlatformConfig.
 *
 * - **dynamicHeaders**: authorization, x-api-guid (change at runtime)
 * - **envHeaders**: clientid, x-api-key (set once at init, fixed for session)
 *
 * [getHeader] resolves dynamic first → env second.
 */
actual object PlatformConfig {

    private val dynamicRef = AtomicReference<Map<String, String>>(emptyMap())

    @Volatile
    private var envHeaders: Map<String, String> = emptyMap()

    actual fun setDynamicHeaders(map: Map<String, String>) {
        dynamicRef.value = map
    }

    actual fun setEnvHeaders(map: Map<String, String>) {
        envHeaders = map
    }

    actual fun updateAuthToken(token: String) {
        // CAS loop for safe read-modify-write
        while (true) {
            val current = dynamicRef.value
            val updated = current.toMutableMap().apply { put("authorization", token) }
            if (dynamicRef.compareAndSet(current, updated)) break
        }
    }

    actual fun getHeader(key: String): String? =
        dynamicRef.value[key] ?: envHeaders[key]
}