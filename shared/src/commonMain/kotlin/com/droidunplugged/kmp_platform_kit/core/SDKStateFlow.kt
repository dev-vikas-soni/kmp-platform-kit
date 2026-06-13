package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Machine-readable error codes emitted by the SDK.
 *
 * Host apps should use these codes to show **localised** user-facing messages
 * rather than relying on the raw [SDKState.Error.message] string.
 *
 * ```kotlin
 * is SDKState.Error -> when (state.errorCode) {
 *     SDKErrorCode.NETWORK_ERROR    -> showDialog(R.string.no_internet)
 *     SDKErrorCode.REQUEST_CANCELLED -> { /* no-op */ }
 *     else                          -> showDialog(R.string.unexpected_error)
 * }
 * ```
 */
object SDKErrorCode {
    const val NETWORK_ERROR = "SDK_NETWORK_ERROR"
    const val REQUEST_CANCELLED = "SDK_REQUEST_CANCELLED"
    const val UNEXPECTED_ERROR = "SDK_UNEXPECTED_ERROR"
}

/**
 * Converts an [ApiResult] into an [SDKState].
 *
 * Mapping:
 * - [ApiResult.Success]      → [SDKState.Success]
 * - [ApiResult.Failure]      → [SDKState.ErrorBody]
 * - [ApiResult.NetworkError] → [SDKState.Error] with `isNetworkError = true`
 * - [ApiResult.Cancelled]    → [SDKState.Error] with code [SDKErrorCode.REQUEST_CANCELLED]
 */
fun <T> ApiResult<T>.toSDKState(): SDKState<T> = when (this) {
    is ApiResult.Success -> SDKState.Success(data)
    is ApiResult.Failure -> SDKState.ErrorBody(
        code = code,
        message = message ?: "Unknown error"
    )
    is ApiResult.NetworkError -> SDKState.Error(
        message = SDKErrorCode.NETWORK_ERROR,
        isNetworkError = true
    )
    is ApiResult.Cancelled -> SDKState.Error(
        message = SDKErrorCode.REQUEST_CANCELLED
    )
}

/**
 * Wraps a suspend SDK call into a [Flow] that emits:
 * 1. [SDKState.Loading]
 * 2. [SDKState.Success] / [SDKState.ErrorBody] / [SDKState.Error]
 *
 * ## Usage in ViewModel
 * ```kotlin
 * val inventoryState: StateFlow<SDKState<InventoryListModel>> =
 *     sdkStateFlow {
 *         AppFacadePhysicalInventory.getInventories(customerNo = "2057192797")
 *     }.stateIn(viewModelScope, SharingStarted.Lazily, SDKState.Loading)
 * ```
 *
 * @param apiCall The suspend function that returns [ApiResult].
 * @return A cold [Flow] that emits Loading then the final state.
 */
fun <T> sdkStateFlow(apiCall: suspend () -> ApiResult<T>): Flow<SDKState<T>> = flow {
    emit(SDKState.Loading)
    try {
        emit(apiCall().toSDKState())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        PlatformLogger.get().e(TAG, "Unexpected exception: ${e.message}", e)
        emit(
            SDKState.Error(
                message = SDKErrorCode.UNEXPECTED_ERROR,
                isNetworkError = false
            )
        )
    }
}

private const val TAG = "SDKStateFlow"