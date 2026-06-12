package com.droidunplugged.kmp_platform_kit.core.error

sealed class SdkError(
    override val message: String,
    val isRetryable: Boolean = false,
    val requiresReLogin: Boolean = false
) : Exception(message) {

    data class Unauthorized(override val message: String = "Session expired. Please log in again.") : 
        SdkError(message, requiresReLogin = true)

    data class Forbidden(override val message: String = "Access denied.") : 
        SdkError(message)

    data class NoConnectivity(override val message: String = "No internet connection.") : 
        SdkError(message, isRetryable = true)

    data class Timeout(override val message: String = "Request timed out.") : 
        SdkError(message, isRetryable = true)

    data class NetworkFailure(override val message: String = "Network error occurred.") : 
        SdkError(message, isRetryable = true)

    data class ServerError(val code: Int, override val message: String = "Server error.") : 
        SdkError(message, isRetryable = true)

    data class BusinessError(val apiCode: String, override val message: String) : 
        SdkError(message)

    data class NotFound(override val message: String = "Resource not found.") : 
        SdkError(message)

    data class Conflict(override val message: String = "Resource conflict.") : 
        SdkError(message)

    data class RateLimited(val retryAfterMs: Long, override val message: String = "Rate limited.") : 
        SdkError(message, isRetryable = true)

    data class SdkNotInitialized(override val message: String = "SDK not initialized. Call init() first.") : 
        SdkError(message)

    data class ParseError(override val message: String = "Failed to parse API response.") : 
        SdkError(message)

    data class Unexpected(val causeException: Throwable? = null, override val message: String = "An unexpected error occurred.") : 
        SdkError(message)
}
