package com.droidunplugged.kmp_platform_kit.features.physicalinventory.repository

import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryItem
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryListPayload
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.PhysicalInventoryFilter

/**
 * Repository contract for physical inventory data.
 *
 * All methods return [ApiResult] so callers can pattern-match on
 * [ApiResult.Success], [ApiResult.Failure], [ApiResult.NetworkError], etc.
 * without needing to catch exceptions.
 */
interface PhysicalInventoryRepository {

    /**
     * Returns a paged list of inventory items matching the supplied [filter].
     */
    suspend fun getInventoryList(filter: PhysicalInventoryFilter): ApiResult<InventoryListPayload>

    /**
     * Returns the detail for a single inventory item identified by [itemNumber].
     */
    suspend fun getInventoryItem(itemNumber: String): ApiResult<InventoryItem>
}