package com.droidunplugged.kmp_platform_kit.shared.utils

/**
 * Android actual for PlatformLogger expect in commonMain.
 * Host app can call PlatformLogger.set(...) to provide a real logger.
 */

actual object PlatformLogger {
    @Volatile
    private var logger: Logger = NoOpLogger

    actual fun set(logger: Logger) {
        this.logger = logger
    }

    actual fun get(): Logger = logger
}