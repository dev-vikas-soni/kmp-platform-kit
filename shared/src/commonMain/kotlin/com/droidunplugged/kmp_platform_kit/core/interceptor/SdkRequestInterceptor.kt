package com.droidunplugged.kmp_platform_kit.core.interceptor

/**
 * Allows host apps to inject custom middleware into the SDK's HTTP pipeline.
 *
 * Common use cases:
 * - Adding custom headers (e.g. device fingerprint, A/B test variant)
 * - Request signing (HMAC, AWS SigV4)
 * - Custom logging or analytics
 * - Response mutation / response body inspection
 *
 * Interceptors run **in registration order** on every SDK HTTP request.
 *
 * ## Registration
 * ```kotlin
 * SDKInitializer.addInterceptor(RequestSigningInterceptor(signingKey))
 * SDKInitializer.addInterceptor(DeviceFingerprintInterceptor())
 * SDKInitializer.init(...)
 * ```
 *
 * ## Implementation example
 * ```kotlin
 * class DeviceFingerprintInterceptor(
 *     private val deviceId: String
 * ) : SdkRequestInterceptor {
 *
 *     override val id = "device_fingerprint"
 *
 *     override suspend fun onRequest(request: SdkMutableRequest): SdkMutableRequest {
 *         return request.withHeader("x-device-id", deviceId)
 *     }
 * }
 * ```
 *
 * All methods are **optional** - only override what you need.
 */
interface SdkRequestInterceptor {

    /**
     * Unique ID for this interceptor.
     * Used to prevent duplicate registration and for logging.
     */
    val id: String

    /**
     * Called before every outgoing request.
     *
     * Modify headers, add query parameters, or inspect the request.
     * Must not perform blocking I/O - use coroutines.
     *
     * @param request Mutable request object. Return it (possibly modified).
     * @return The (possibly modified) request to continue the chain.
     */
    suspend fun onRequest(request: SdkMutableRequest): SdkMutableRequest = request

    /**
     * Called after every completed response (success or error).
     *
     * Inspect response headers, log timing, or trigger side effects.
     *
     * @param response Immutable response snapshot.
     */
    /**
     * Called after every completed response (success or error).
     * Default implementation is a no-op - override only if you need to inspect responses.
     */
    suspend fun onResponse(response: SdkResponse) { /* no-op by default */
    }

    /**
     * Called when a network error occurs (before any retry logic).
     * Default implementation is a no-op - override only if you need error-level hooks.
     */
    suspend fun onError(endpoint: String, error: Throwable) { /* no-op by default */
    }
}

// ── Request / Response models ─────────────────────────────────────────────────

/**
 * Mutable snapshot of an outgoing SDK HTTP request.
 * Passed to [SdkRequestInterceptor.onRequest] for modification.
 */
data class SdkMutableRequest(
    val url: String,
    val method: String,
    val headers: MutableMap<String, String> = mutableMapOf(),
    val queryParams: MutableMap<String, String> = mutableMapOf()
) {
    /** Returns a copy of this request with an additional or replaced header. */
    fun withHeader(key: String, value: String): SdkMutableRequest =
        copy(headers = (headers + (key to value)).toMutableMap())

    /** Returns a copy of this request with an additional or replaced query parameter. */
    fun withQueryParam(key: String, value: String): SdkMutableRequest =
        copy(queryParams = (queryParams + (key to value)).toMutableMap())

    /** Returns a copy of this request with the given header removed. */
    fun withoutHeader(key: String): SdkMutableRequest =
        copy(headers = headers.filterKeys { it != key }.toMutableMap())
}

/**
 * Immutable snapshot of a completed SDK HTTP response.
 * Passed to [SdkRequestInterceptor.onResponse] for inspection.
 */
data class SdkResponse(
    val url: String,
    val method: String,
    val statusCode: Int,
    val headers: Map<String, String>,
    val durationMs: Long,
    val cacheHit: Boolean = false
)

// ── Registry ──────────────────────────────────────────────────────────────────

/**
 * Registry for [SdkRequestInterceptor] instances.
 *
 * Internal - managed exclusively by [com.cardinalhealth.vantus.sdk.core.SDKInitializer].
 */
internal object SdkInterceptorRegistry {

    private val interceptors = LinkedHashMap<String, SdkRequestInterceptor>()

    /** Register an interceptor. Replaces any existing interceptor with the same [id]. */
    fun register(interceptor: SdkRequestInterceptor) {
        interceptors[interceptor.id] = interceptor
    }

    /** Remove an interceptor by ID. */
    fun unregister(id: String) {
        interceptors.remove(id)
    }

    /** All registered interceptors in registration order. */
    val all: List<SdkRequestInterceptor> get() = interceptors.values.toList()

    /** Remove all interceptors - called on SDKInitializer.reset(). */
    fun clear() = interceptors.clear()

    /**
     * Run all interceptors' [SdkRequestInterceptor.onRequest] in order.
     * Returns the (possibly modified) final request.
     */
    suspend fun applyRequest(request: SdkMutableRequest): SdkMutableRequest {
        var current = request
        interceptors.values.forEach { interceptor ->
            current = interceptor.onRequest(current)
        }
        return current
    }

    /**
     * Notify all interceptors of a completed [SdkResponse].
     */
    suspend fun applyResponse(response: SdkResponse) {
        interceptors.values.forEach { it.onResponse(response) }
    }

    /**
     * Notify all interceptors of a network error.
     */
    suspend fun applyError(endpoint: String, error: Throwable) {
        interceptors.values.forEach { it.onError(endpoint, error) }
    }
}