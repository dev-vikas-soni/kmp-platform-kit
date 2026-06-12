package com.droidunplugged.kmp_platform_kit.core

import kotlin.concurrent.AtomicReference

actual object PlatformConfig {
    private val headers = AtomicReference<Map<String, String>>(emptyMap())

    actual fun setHeader(key: String, value: String) {
        var success = false
        while (!success) {
            val current = headers.value
            val next = current + (key to value)
            success = headers.compareAndSet(current, next)
        }
    }

    actual fun getHeader(key: String): String? = headers.value[key]

    actual fun getAllHeaders(): Map<String, String> = headers.value

    actual fun clear() {
        headers.value = emptyMap()
    }
}
