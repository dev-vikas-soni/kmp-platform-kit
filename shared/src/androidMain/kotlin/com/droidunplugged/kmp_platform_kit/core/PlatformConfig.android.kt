package com.droidunplugged.kmp_platform_kit.core

import java.util.concurrent.ConcurrentHashMap

actual object PlatformConfig {
    private val headers = ConcurrentHashMap<String, String>()

    actual fun setHeader(key: String, value: String) {
        headers[key] = value
    }

    actual fun getHeader(key: String): String? = headers[key]

    actual fun getAllHeaders(): Map<String, String> = headers.toMap()

    actual fun clear() {
        headers.clear()
    }
}
