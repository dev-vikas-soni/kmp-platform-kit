package com.droidunplugged.kmp_platform_kit.features.physicalinventory.models

import com.droidunplugged.kmp_platform_kit.shared.models.BaseApiResponse
import com.droidunplugged.kmp_platform_kit.shared.models.PaginationInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ─── Request Models ────────────────────────────────────────────────────────────

/**
 * Criteria used to query physical inventory. All fields are optional filters.
 */
@Serializable
data class PhysicalInventoryFilter(
    @SerialName("facilityCode") val facilityCode: String? = null,
    @SerialName("itemNumber")   val itemNumber: String? = null,
    @SerialName("lotNumber")    val lotNumber: String? = null,
    @SerialName("locationCode") val locationCode: String? = null,
    @SerialName("pageNumber")   val pageNumber: Int = 1,
    @SerialName("pageSize")     val pageSize: Int = 20
)

// ─── Response Models ───────────────────────────────────────────────────────────

/**
 * A single inventory line item returned by the API.
 */
@Serializable
data class InventoryItem(
    @SerialName("itemNumber")         val itemNumber: String,
    @SerialName("description")        val itemDescription: String,
    @SerialName("facilityCode")       val facilityCode: String,
    @SerialName("locationCode")       val locationCode: String? = null,
    @SerialName("lotNumber")          val lotNumber: String? = null,
    @SerialName("expirationDate")     val expirationDate: String? = null,
    @SerialName("quantityOnHand")     val quantityOnHand: Double,
    @SerialName("unitOfMeasure")      val unitOfMeasure: String,
    @SerialName("lastUpdated")        val lastUpdated: String? = null
)

/**
 * Paged inventory list payload.
 */
@Serializable
data class InventoryListPayload(
    @SerialName("items")      val items: List<InventoryItem> = emptyList(),
    @SerialName("pagination") val pagination: PaginationInfo? = null
)

/**
 * Top-level API response wrapper for paged inventory queries.
 * Extends [BaseApiResponse] to inherit standard envelope fields.
 */
@Serializable
data class InventoryListResponse(
    @SerialName("data") override val data: InventoryListPayload? = null
) : BaseApiResponse<InventoryListPayload>()

/**
 * Top-level API response wrapper for a single inventory item lookup.
 */
@Serializable
data class InventoryItemResponse(
    @SerialName("data") override val data: InventoryItem? = null
) : BaseApiResponse<InventoryItem>()