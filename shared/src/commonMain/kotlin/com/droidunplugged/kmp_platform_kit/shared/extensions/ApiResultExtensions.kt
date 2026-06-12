package com.droidunplugged.kmp_platform_kit.shared.extensions

import com.droidunplugged.kmp_platform_kit.core.ApiResult

fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> {
    return when (this) {
        is ApiResult.Success -> ApiResult.Success(transform(data))
        is ApiResult.Failure -> this
        ApiResult.NetworkError -> ApiResult.NetworkError
        ApiResult.Cancelled -> ApiResult.Cancelled
    }
}

fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> {
    return when (this) {
        is ApiResult.Success -> transform(data)
        is ApiResult.Failure -> this
        ApiResult.NetworkError -> ApiResult.NetworkError
        ApiResult.Cancelled -> ApiResult.Cancelled
    }
}
