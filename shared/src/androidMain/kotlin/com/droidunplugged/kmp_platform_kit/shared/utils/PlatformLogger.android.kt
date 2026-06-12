package com.droidunplugged.kmp_platform_kit.shared.utils

import android.util.Log

actual object PlatformLogger {
    actual var logger: Logger = object : Logger {
        override fun d(tag: String, message: String) { Log.d(tag, message) }
        override fun i(tag: String, message: String) { Log.i(tag, message) }
        override fun w(tag: String, message: String) { Log.w(tag, message) }
        override fun e(tag: String, message: String, throwable: Throwable?) { Log.e(tag, message, throwable) }
    }

    actual fun d(tag: String, message: String) = logger.d(tag, message)
    actual fun i(tag: String, message: String) = logger.i(tag, message)
    actual fun w(tag: String, message: String) = logger.w(tag, message)
    actual fun e(tag: String, message: String, throwable: Throwable?) = logger.e(tag, message, throwable)
}
