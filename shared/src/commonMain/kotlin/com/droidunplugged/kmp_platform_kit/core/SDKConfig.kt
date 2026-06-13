package com.droidunplugged.kmp_platform_kit.core

import kotlin.concurrent.Volatile

/**
 * SDK-wide configuration flags.
 *
 * Host apps should call [enableDebugMode] **before** initializing the SDK.
 * Direct write to [debugMode] is intentionally private - use [enableDebugMode]
 * which enforces a debug-build-only guard.
 *
 * ```kotlin
 * // In Application.onCreate() - safe, no-op in release builds
 * if (BuildConfig.DEBUG) SDKConfig.enableDebugMode()
 * SDKInitializer.init(...)
 * ```
 *
 * Flags are read by internal components (e.g. `KtorApiClient`, loggers).
 */
object SDKConfig {

    /**
     * When `true`, the SDK logs request/response bodies, headers,
     * and timing details at DEBUG level.
     *
     * **Read-only externally.** Use [enableDebugMode] to enable.
     * The SDK never enables this itself - it must be opt-in by the host app.
     *
     * Default: `false`.
     */
    @Volatile
    var debugMode: Boolean = false
        private set

    /**
     * Optional SSL certificate pinning configuration.
     *
     * Set before [SDKInitializer.init] so the HTTP client picks it up on creation.
     * `null` = no pinning (default).
     *
     * See [SslPinConfig] for pin format and generation instructions.
     */
    @Volatile
    var sslPins: SslPinConfig? = null

    /**
     * Enable verbose debug logging.
     *
     * This is a **no-op in release builds** - safe to call unconditionally.
     * Only has effect when [isDebugBuild] returns `true` (platform-specific).
     *
     * ```kotlin
     * SDKConfig.enableDebugMode()   // no-op in release, verbose in debug
     * ```
     */
    fun enableDebugMode() {
        if (isDebugBuild()) {
            debugMode = true
        }
    }

    /**
     * Force-disable debug mode at runtime (e.g. in tests after setup).
     * Primarily used in test teardown.
     */
    @Suppress("unused")
    fun disableDebugMode() {
        debugMode = false
    }
}

/**
 * Platform-specific check for whether the current build is a debug build.
 *
 * - **Android:** checks `ApplicationInfo.FLAG_DEBUGGABLE`
 * - **iOS:** checks `Platform.isDebugBinary`
 *
 * Implemented as an `expect` to avoid pulling platform types into `commonMain`.
 */
expect fun isDebugBuild(): Boolean