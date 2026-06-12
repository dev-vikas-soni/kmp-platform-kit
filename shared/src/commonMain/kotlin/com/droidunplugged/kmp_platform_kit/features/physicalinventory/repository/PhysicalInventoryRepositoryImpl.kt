package com.droidunplugged.kmp_platform_kit.features.physicalinventory.repository

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.core.CachePolicy
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.endpoints.PhysicalInventoryEndpoints
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryItem
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryListPayload
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.PhysicalInventoryFilter
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.requestbuilder.PhysicalInventoryQuery

/**
 * Concrete implementation of [PhysicalInventoryRepository].
 *
 * Delegates all HTTP work to [ApiClient]. It is responsible for:
 * - Selecting the correct endpoint constant from [PhysicalInventoryEndpoints].
 * - Building the query-parameter map via [PhysicalInventoryQuery].
 * - Selecting an appropriate [CachePolicy] per request type.
 *
 * It intentionally contains **no business logic** – that belongs in the
 * facade or the caller's ViewModel.
 */
internal class PhysicalInventoryRepositoryImpl(
    private val apiClient: ApiClient
) : PhysicalInventoryRepository {

    override suspend fun getInventoryList(
        filter: PhysicalInventoryFilter
    ): ApiResult<InventoryListPayload> =
        apiClient.get(
            path        = PhysicalInventoryEndpoints.List.path,
            queryParams = PhysicalInventoryQuery.fromFilter(filter),
            cachePolicy = CachePolicy.DEFAULT
        )

    override suspend fun getInventoryItem(
        itemNumber: String
    ): ApiResult<InventoryItem> =
        apiClient.get(
            path        = PhysicalInventoryEndpoints.ItemDetail(itemNumber).path,
            queryParams = PhysicalInventoryQuery.forItemDetail(),
            cachePolicy = CachePolicy.CACHE_FIRST_5MIN
        )
}