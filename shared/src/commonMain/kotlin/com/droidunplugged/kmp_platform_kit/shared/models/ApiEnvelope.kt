package com.droidunplugged.kmp_platform_kit.shared.models

import kotlinx.serialization.Serializable

/**
 * Standard API response envelope shared by all SDK endpoints.
 *
 * Designed as an **abstract** class so feature-level response types can
 * subclass it and provide a concrete, strongly-typed [data] field:
 *
 * ```kotlin
 * @Serializable
 * data class InventoryListResponse(
 *     @SerialName("data") override val data: InventoryListPayload? = null
 * ) : BaseApiResponse<InventoryListPayload>()
 * ```
 *
 * Fields common to every API response:
 * @property status   "SUCCESS" | "ERROR" – set by the server.
 * @property error    Present when [status] is "ERROR".
 * @property data     Typed payload; null on error responses.
 */
@Serializable
abstract class BaseApiResponse<T> {
    abstract val data: T?
    open val status: String = ""
    open val error: ErrorInfo? = null

    val isSuccess: Boolean get() = status.equals("SUCCESS", ignoreCase = true)
    val errorMessage: String? get() = error?.errorDetails?.firstOrNull()?.message
}

// ─── Error envelope types ──────────────────────────────────────────────────────

@Serializable
data class ErrorInfo(
    val errorDetails: List<ErrorDetail> = emptyList()
)

@Serializable
data class ErrorDetail(
    val code: String,
    val message: String
)
