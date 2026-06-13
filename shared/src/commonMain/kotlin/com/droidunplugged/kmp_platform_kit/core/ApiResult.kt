package com.droidunplugged.kmp_platform_kit.core

/**
 * Unified API response wrapper used across the SDK.
 *
 * Every facade method returns `ApiResult<T>`, allowing host apps to handle
 * all outcomes with a single `when` expression.
 *
 * ## Usage
 * ```kotlin
 * when (result) {
 *     is ApiResult.Success      -> showData(result.data)
 *     is ApiResult.Failure      -> showError(result.code, result.message)
 *     is ApiResult.NetworkError -> showRetry("No internet")
 *     is ApiResult.Cancelled    -> { /* no-op */ }
 * }
 * ```
 *
 * @param T The type of data on success.
 * @see SDKState for a UI-ready wrapper with built-in `Loading` state.
 * @see toSDKState to convert `ApiResult` to `SDKState`.
 */
sealed class ApiResult<out T> {

    /**
     * The API call succeeded - HTTP 2xx with a valid response body.
     *
     * @property data The parsed domain model.
     */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * The API returned an error - HTTP 4xx/5xx, or an API-level error
     * (e.g. `{"status":"ERROR", "error":{...}}`).
     *
     * @property code    HTTP status code (e.g. 401, 403, 500), or `-2` for parse errors.
     * @property message Human-readable error description, or `null` if unavailable.
     */
    data class Failure(val code: Int, val message: String?) : ApiResult<Nothing>()

    /**
     * Network-level failure - no response received.
     *
     * Covers: no internet, DNS resolution failure, connection timeout, socket error.
     * Host apps should typically show a "Check your internet connection" UI.
     */
    data object NetworkError : ApiResult<Nothing>()

    /**
     * The coroutine was cancelled before the response arrived.
     *
     * This typically happens when the user navigates away from a screen.
     * Host apps should usually treat this as a no-op.
     */
    data object Cancelled : ApiResult<Nothing>()
}