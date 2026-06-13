package com.droidunplugged.kmp_platform_kit.core.error

/**
 * Structured error taxonomy for the KMP Platform Kit.
 *
 * Replaces the flat `ApiResult.Failure(code: Int, message: String?)` with a
 * sealed hierarchy that gives host apps machine-readable error categories and
 * actionable recovery hints - no more guessing what `code = -2` means.
 *
 * ## Migration from flat Failure
 * ```kotlin
 * // Before
 * is ApiResult.Failure -> showError("Error ${result.code}: ${result.message}")
 *
 * // After - full context, localizable, actionable
 * is ApiResult.Failure -> when (result.error) {
 *     is SdkError.Unauthorized     -> navigateToLogin()
 *     is SdkError.NoConnectivity   -> showOfflineBanner()
 *     is SdkError.BusinessError    -> showApiError(result.error.apiCode, result.error.message)
 *     is SdkError.ServerError      -> showRetryButton(result.error.retryAfterMs)
 *     else                         -> showGenericError()
 * }
 * ```
 */
sealed class SdkError {

    // ── Auth / Identity ────────────────────────────────────────────────────

    /**
     * HTTP 401 - Token expired or invalid.
     *
     * **Recovery:** Call `SDKInitializer.reset()`, prompt the user to log in,
     * then re-initialize with new credentials.
     */
    data class Unauthorized(
        val message: String = "Session expired. Please log in again.",
        val hint: String = "Call SDKInitializer.reset() and re-login"
    ) : SdkError()

    /**
     * HTTP 403 - Authenticated but insufficient permissions.
     *
     * **Recovery:** Show an access-denied message. Do NOT retry automatically.
     *
     * @property requiredPermission The missing permission scope, if available from the API.
     */
    data class Forbidden(
        val message: String = "Access denied.",
        val requiredPermission: String? = null
    ) : SdkError()

    // ── Network ────────────────────────────────────────────────────────────

    /**
     * No network connectivity - device is offline.
     *
     * **Recovery:** Show an offline banner. Retry automatically when connectivity returns.
     */
    data object NoConnectivity : SdkError()

    /**
     * Request timed out after [timeoutMs] milliseconds.
     *
     * **Recovery:** Retry after a short delay, or increase the timeout via `CachePolicy.timeoutMs`.
     */
    data class Timeout(
        val timeoutMs: Long,
        val endpoint: String? = null
    ) : SdkError()

    /**
     * Network error that doesn't fit other categories (DNS failure, socket error, etc.)
     *
     * **Recovery:** Retry after a delay.
     */
    data class NetworkFailure(
        val message: String,
        val cause: Throwable? = null
    ) : SdkError()

    // ── Server ────────────────────────────────────────────────────────────

    /**
     * HTTP 5xx - The server returned an error.
     *
     * **Recovery:** Retry after [retryAfterMs] if set (from `Retry-After` header).
     * The SDK's built-in exponential backoff already retries 5xx automatically.
     *
     * @property retryAfterMs Milliseconds to wait before retrying (from Retry-After header), or null.
     */
    data class ServerError(
        val httpCode: Int,
        val message: String? = null,
        val retryAfterMs: Long? = null
    ) : SdkError()

    // ── Business Logic ────────────────────────────────────────────────────

    /**
     * HTTP 200 with `{ "status": "ERROR", "error": { ... } }` body.
     *
     * The server processed the request but returned a structured business error.
     *
     * **Recovery:** Show [message] to the user. Log [apiCode] for debugging.
     *
     * @property apiCode    API-specific error code (e.g. `"EX_402_115"`). Use for i18n.
     * @property message    Human-readable message from the API.
     * @property field      Field that caused the validation error (for form validation), or null.
     * @property httpCode   HTTP status code (usually 200, 400, 409, etc.).
     */
    data class BusinessError(
        val apiCode: String,
        val message: String,
        val field: String? = null,
        val httpCode: Int = 200
    ) : SdkError()

    /**
     * HTTP 404 - The requested resource does not exist.
     *
     * @property resourceType Optional hint about what was not found (e.g. "inventory", "order").
     */
    data class NotFound(
        val message: String = "The requested resource was not found.",
        val resourceType: String? = null
    ) : SdkError()

    /**
     * HTTP 409 - Conflict (e.g. duplicate resource, optimistic lock failure).
     */
    data class Conflict(
        val message: String,
        val conflictingId: String? = null
    ) : SdkError()

    /**
     * HTTP 429 - Rate limited by the server.
     *
     * @property retryAfterMs Milliseconds to wait before retrying.
     */
    data class RateLimited(
        val retryAfterMs: Long? = null,
        val message: String = "Too many requests. Please slow down."
    ) : SdkError()

    // ── SDK Internal ──────────────────────────────────────────────────────

    /**
     * The SDK was not initialized when a facade method was called.
     *
     * **Recovery:** Call `SDKInitializer.init(...)` or `SDKInitializer.ensureInitialized()`
     * before calling any facade method.
     */
    data object SdkNotInitialized : SdkError()

    /**
     * JSON deserialization failed - the server response didn't match the expected schema.
     *
     * This typically indicates a server-side API change that the SDK hasn't been
     * updated to handle yet.
     *
     * @property expectedType  The Kotlin type that was expected (e.g. `"PhysicalInventoryResponse"`).
     * @property receivedSnippet First 200 characters of the received JSON, for diagnostics.
     */
    data class ParseError(
        val expectedType: String,
        val receivedSnippet: String,
        val cause: Throwable? = null
    ) : SdkError()

    /**
     * An unexpected error occurred inside the SDK.
     *
     * If this appears in production, it is a bug - please file an issue.
     */
    data class Unexpected(
        val message: String,
        val cause: Throwable? = null
    ) : SdkError()

    // ── Convenience ───────────────────────────────────────────────────────

    /** `true` if this error is likely transient and worth retrying. */
    val isRetryable: Boolean
        get() = when (this) {
            is Timeout, is NetworkFailure, NoConnectivity -> true
            is ServerError -> httpCode in 500..599
            is RateLimited -> true
            else -> false
        }

    /** `true` if the user's session is invalid and they must log in again. */
    val requiresReLogin: Boolean
        get() = this is Unauthorized
}