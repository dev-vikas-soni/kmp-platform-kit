package com.droidunplugged.kmp_platform_kit.shared.utils

actual object PlatformLogger {
    actual var logger: Logger = NoOpLogger // Fallback for iOS, can be wired to os_log if needed

    actual fun d(tag: String, message: String) = logger.d(tag, message)
    actual fun i(tag: String, message: String) = logger.i(tag, message)
    actual fun w(tag: String, message: String) = logger.w(tag, message)
    actual fun e(tag: String, message: String, throwable: Throwable?) = logger.e(tag, message, throwable)
}
