package com.droidunplugged.kmp_platform_kit.features.physicalinventory.facade

import com.droidunplugged.kmp_platform_kit.core.BaseFacade
import com.droidunplugged.kmp_platform_kit.core.SDKState
import com.droidunplugged.kmp_platform_kit.core.sdkStateFlow
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryItem
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryListPayload
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.PhysicalInventoryFilter
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.repository.PhysicalInventoryRepository
import kotlinx.coroutines.flow.Flow

/**
 * Public-facing facade for the Physical Inventory feature.
 *
 * This is the **only** entry point that host apps (Android / iOS) should
 * interact with. It hides the repository, query-builder, and API plumbing
 * behind a clean, coroutines-based API that emits [SDKState].
 *
 * ### Usage (Kotlin / Android ViewModel)
 * ```kotlin
 * val facade = KoinPlatform.getKoin().get<AppFacadePhysicalInventory>()
 *
 * viewModelScope.launch {
 *     facade.getInventoryList(PhysicalInventoryFilter(facilityCode = "FAC01"))
 *         .collect { state ->
 *             when (state) {
 *                 is SDKState.Loading  -> showLoading()
 *                 is SDKState.Success  -> render(state.data)
 *                 is SDKState.Error,
 *                 is SDKState.ErrorBody -> showError(state.errorMessageOrNull())
 *             }
 *         }
 * }
 * ```
 */
class AppFacadePhysicalInventory(
    private val repository: PhysicalInventoryRepository
) : BaseFacade() {

    override val tag: String = "AppFacadePhysicalInventory"

    /**
     * Returns a [Flow] that emits [SDKState.Loading] immediately, followed by
     * [SDKState.Success] with the paged [InventoryListPayload] or an error state.
     *
     * @param filter Optional search criteria; defaults to first page with no filter.
     */
    fun getInventoryList(
        filter: PhysicalInventoryFilter = PhysicalInventoryFilter()
    ): Flow<SDKState<InventoryListPayload>> = sdkStateFlow {
        requireInitialized()
        logCall("getInventoryList")
        repository.getInventoryList(filter)
    }

    /**
     * Returns a [Flow] that emits [SDKState.Loading] followed by
     * [SDKState.Success] with the matching [InventoryItem] or an error state.
     *
     * @param itemNumber Unique item identifier to look up.
     */
    fun getInventoryItem(
        itemNumber: String
    ): Flow<SDKState<InventoryItem>> = sdkStateFlow {
        requireInitialized()
        logCall("getInventoryItem($itemNumber)")
        repository.getInventoryItem(itemNumber)
    }
}