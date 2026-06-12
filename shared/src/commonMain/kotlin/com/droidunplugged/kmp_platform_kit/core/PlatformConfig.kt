package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.HttpHeaders

expect object PlatformConfig {
    fun setHeader(key: String, value: String)
    fun getHeader(key: String): String?
    fun getAllHeaders(): Map<String, String>
    fun clear()
}
