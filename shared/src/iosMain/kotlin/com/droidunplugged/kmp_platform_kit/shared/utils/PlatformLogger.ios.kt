package com.droidunplugged.kmp_platform_kit.shared.utils

import kotlin.concurrent.Volatile

/**
 * iOS actual for PlatformLogger expect in commonMain.
 * Host app can call PlatformLogger.set(...) to provide a real logger implementation.
 * Uses @Volatile for thread safety in Kotlin/Native. Assignment is atomic and visibility is ensured for object references.
 */
actual object PlatformLogger {
    @Volatile
    private var logger: Logger = NoOpLogger

    actual fun set(logger: Logger) {
        this.logger = logger
    }

    actual fun get(): Logger = logger
}