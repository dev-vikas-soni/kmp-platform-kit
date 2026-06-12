package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

fun <T> ApiResult<T>.toSDKState(): SDKState<T> {
    return when (this) {
        is ApiResult.Success -> SDKState.Success(data)
        is ApiResult.Failure -> SDKState.ErrorBody(code, message ?: "Unknown error", null)
        ApiResult.NetworkError -> SDKState.Error(SDKErrorCode.NETWORK_ERROR, true)
        ApiResult.Cancelled -> SDKState.Error(SDKErrorCode.REQUEST_CANCELLED, false)
    }
}

fun <T> sdkStateFlow(action: suspend () -> ApiResult<T>): Flow<SDKState<T>> = flow {
    emit(SDKState.Loading)
    try {
        val result = action()
        emit(result.toSDKState())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        emit(SDKState.Error(SDKErrorCode.UNEXPECTED_ERROR, false))
    }
}
