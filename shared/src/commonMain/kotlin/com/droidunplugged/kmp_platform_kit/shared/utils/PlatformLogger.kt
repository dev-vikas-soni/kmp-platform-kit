package com.droidunplugged.kmp_platform_kit.shared.utils

expect object PlatformLogger {
    var logger: Logger
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
