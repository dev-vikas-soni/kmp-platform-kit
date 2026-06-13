package com.droidunplugged.kmp_platform_kit.shared.models

import kotlinx.serialization.Serializable

/**
 * Standard API response envelope used by all Cardinal Health APIs.
 *
 * Every API returns:
 * ```json
 * { "status": "SUCCESS|ERROR", "error": { ... } | null, "data": { ... } | null }
 * ```
 *
 * Feature-specific response DTOs should embed this envelope or reuse
 * [ErrorInfo] and [ErrorDetail] directly.
 *
 * @see ErrorInfo
 * @see ErrorDetail
 */
@Serializable
data class BaseApiResponse(
    val status: String = "",
    val error: ErrorInfo? = null
) {
    /** `true` when the API returned a success status. */
    val isSuccess: Boolean get() = status.uppercase() == "SUCCESS"

    /** First error message from the error details list, or `null`. */
    val errorMessage: String?
        get() = error?.errorDetails?.firstOrNull()?.message
}

/**
 * Error information block within the standard API envelope.
 *
 * Shared across all features - defined once, reused everywhere.
 */
@Serializable
data class ErrorInfo(
    val errorDetails: List<ErrorDetail> = emptyList()
)

/**
 * Individual error detail within an [ErrorInfo] block.
 *
 * @property code    API error code (e.g. `"EX_402_115"`).
 * @property message Human-readable error description.
 */
@Serializable
data class ErrorDetail(
    val code: String = "",
    val message: String = ""
)