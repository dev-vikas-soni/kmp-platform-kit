package com.droidunplugged.kmp_platform_kit.core

// ── Event types ───────────────────────────────────────────────────────────────

/** Lifecycle events emitted by the SDK itself. */
enum class SDKEvent {
    /** SDK successfully initialized. */
    INITIALIZED,

    /** SDK reset (logout / account switch). */
    RESET,

    /** Auth token was refreshed at runtime. */
    TOKEN_REFRESHED,

    /** A feature facade method was invoked. */
    FEATURE_CALLED
}

/** Category of error tracked by [SDKTelemetry]. */
enum class ErrorType {
    /** Network-level failure (no connectivity, DNS, timeout). */
    NETWORK,

    /** HTTP 4xx client error. */
    CLIENT_ERROR,

    /** HTTP 5xx server error. */
    SERVER_ERROR,

    /** JSON parse / mapping failure. */
    PARSE_ERROR,

    /** An unexpected SDK-internal error. */
    UNEXPECTED
}

// ── Telemetry interface ───────────────────────────────────────────────────────

/**
 * Pluggable telemetry interface for SDK observability.
 *
 * Host apps provide an implementation (Firebase, Datadog, custom) and register
 * it via [SDKInitializer.setTelemetry]. The SDK calls these methods automatically.
 * A [NoOpTelemetry] default ensures **zero impact** when telemetry is not configured.
 *
 * ```kotlin
 * // Example: Firebase Analytics bridge
 * class FirebaseTelemetry(private val analytics: FirebaseAnalytics) : SDKTelemetry {
 *     override fun recordApiCall(endpoint, durationMs, statusCode, retries) {
 *         analytics.logEvent("sdk_api_call") { ... }
 *     }
 *     ...
 * }
 *
 * SDKInitializer.setTelemetry(FirebaseTelemetry(Firebase.analytics))
 * SDKInitializer.init(...)
 * ```
 *
 * All methods are called on the **coroutine thread** that triggered the event -
 * implementations should be non-blocking.
 */
interface SDKTelemetry {

    /**
     * Called after every completed API call (success or error).
     *
     * @param endpoint   The relative path of the request (e.g. `"ecomm/.../inventories"`).
     * @param durationMs Wall-clock time of the complete request, including retries.
     * @param statusCode HTTP status code, or `-1` for network errors, `-2` for unexpected errors.
     * @param retries    Number of retry attempts made (0 = first attempt succeeded).
     */
    fun recordApiCall(endpoint: String, durationMs: Long, statusCode: Int, retries: Int)

    /**
     * Called when an error occurs.
     *
     * @param type     Broad category of the error.
     * @param endpoint Endpoint that produced the error, or `null` for SDK-level errors.
     * @param message  Human-readable description.
     */
    fun recordError(type: ErrorType, endpoint: String?, message: String)

    /**
     * Called when a lifecycle event occurs in the SDK.
     *
     * @param event  The SDK lifecycle event.
     * @param detail Optional additional context (e.g. feature name for [SDKEvent.FEATURE_CALLED]).
     */
    fun recordSdkEvent(event: SDKEvent, detail: String? = null)
}

// ── No-op default ─────────────────────────────────────────────────────────────

/**
 * Default [SDKTelemetry] implementation - all methods are no-ops.
 *
 * Used when the host app has not registered a telemetry provider.
 * Zero allocation, zero performance impact.
 */
object NoOpTelemetry : SDKTelemetry {
    override fun recordApiCall(endpoint: String, durationMs: Long, statusCode: Int, retries: Int) = Unit
    override fun recordError(type: ErrorType, endpoint: String?, message: String) = Unit
    override fun recordSdkEvent(event: SDKEvent, detail: String?) = Unit
}