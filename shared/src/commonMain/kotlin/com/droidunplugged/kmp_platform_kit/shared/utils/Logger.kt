package com.droidunplugged.kmp_platform_kit.shared.utils

/**
 * Lightweight logger interface for SDK internals.
 * Host apps can provide a platform logger by calling PlatformLogger.set(...) in platform actuals.
 *
 * Keep logs free of secrets.
 */
interface Logger {
    fun d(tag: String, message: String)
    fun i(tag: String, message: String)
    fun w(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}

/**
 * Default no-op logger to avoid NPEs when host app doesn't provide one.
 */
object NoOpLogger : Logger {
    override fun d(tag: String, message: String) { /* intentionally no-op */
    }

    override fun i(tag: String, message: String) { /* intentionally no-op */
    }

    override fun w(tag: String, message: String) { /* intentionally no-op */
    }

    override fun e(tag: String, message: String, throwable: Throwable?) { /* intentionally no-op */
    }
}

/**
 * Platform-bridge to allow host apps to inject a logger implementation.
 * Provide an actual implementation in androidMain/iosMain that holds a mutable logger.
 */
expect object PlatformLogger {
    fun set(logger: Logger)
    fun get(): Logger
}