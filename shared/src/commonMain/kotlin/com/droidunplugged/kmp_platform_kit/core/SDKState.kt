package com.droidunplugged.kmp_platform_kit.core

/**
 * UI-ready state wrapper exposed by the SDK to host apps.
 *
 * Maps to the 4 standard states every host app already handles:
 *
 * | SDK State      | Host App State | When                                            |
 * |----------------|----------------|-------------------------------------------------|
 * | `Loading`      | Loading        | Request in flight                               |
 * | `Success<T>`   | Success        | API returned 2xx with valid data                |
 * | `ErrorBody`    | ErrorBody      | API returned a structured error (e.g. 4xx/5xx   |
 * |                |                | with `{"status":"ERROR","error":{...}}`)         |
 * | `Error`        | Error          | Unexpected failure - network, parse, timeout     |
 *
 * ## Usage (Android - Compose)
 * ```kotlin
 * val state by viewModel.inventoryState.collectAsStateWithLifecycle()
 * when (state) {
 *     is SDKState.Loading  -> CircularProgressIndicator()
 *     is SDKState.Success  -> InventoryList((state as SDKState.Success).data)
 *     is SDKState.ErrorBody -> ErrorDialog((state as SDKState.ErrorBody).code, ...)
 *     is SDKState.Error    -> RetryScreen((state as SDKState.Error).message)
 * }
 * ```
 *
 * ## Usage (iOS - Swift)
 * ```swift
 * switch state {
 * case is SDKState.Loading:  showLoader()
 * case let s as SDKState.Success<InventoryListModel>: showList(s.data)
 * case let e as SDKState.ErrorBody: showError(e.code, e.message)
 * case let e as SDKState.Error:     showRetry(e.message)
 * }
 * ```
 */
sealed class SDKState<out T> {

    /**
     * Request is in progress. Show a loader / shimmer in the UI.
     */
    data object Loading : SDKState<Nothing>()

    /**
     * API returned a successful response with data.
     *
     * @property data The parsed domain model.
     */
    data class Success<T>(val data: T) : SDKState<T>()

    /**
     * API returned a **structured error response** (HTTP 4xx/5xx with a JSON body).
     *
     * Example: `{ "status": "ERROR", "error": { "errorDetails": [{ "code": "EX_402_115", ... }] } }`
     *
     * @property code    HTTP status code (e.g. 401, 403, 500) or API error code string.
     * @property message Human-readable error message from the API.
     * @property errorCode Optional API-specific error code (e.g. "EX_402_115").
     */
    data class ErrorBody(
        val code: Int,
        val message: String,
        val errorCode: String? = null
    ) : SDKState<Nothing>()

    /**
     * Unexpected failure - no structured error body available.
     *
     * Covers: network unreachable, DNS failure, timeout, JSON parse error, etc.
     *
     * @property message Description of the error.
     * @property isNetworkError `true` when caused by connectivity issues (host app may show
     *           "Check your internet connection" UI).
     */
    data class Error(
        val message: String,
        val isNetworkError: Boolean = false
    ) : SDKState<Nothing>()

    // ── Convenience properties ───────────────────────────────────

    /** `true` when this state is [Loading]. */
    val isLoading: Boolean get() = this is Loading

    /** `true` when this state is [Success]. */
    val isSuccess: Boolean get() = this is Success

    /** `true` when this state is any error ([ErrorBody] or [Error]). */
    val isError: Boolean get() = this is ErrorBody || this is Error

    /**
     * Returns the data if [Success], otherwise `null`.
     * Convenient for safe access without casting.
     */
    fun dataOrNull(): T? = (this as? Success)?.data

    /**
     * Returns the error message if [ErrorBody] or [Error], otherwise `null`.
     */
    fun errorMessageOrNull(): String? = when (this) {
        is ErrorBody -> message
        is Error -> message
        else -> null
    }
}