package com.droidunplugged.kmp_platform_kit.shared.extensions

import com.droidunplugged.kmp_platform_kit.core.ApiResult


/** Transforms the [Success][ApiResult.Success] data while preserving error variants. */
internal inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(data))
    is ApiResult.Failure -> ApiResult.Failure(code, message)
    ApiResult.NetworkError -> ApiResult.NetworkError
    ApiResult.Cancelled -> ApiResult.Cancelled
}

/**
 * Transforms the [Success][ApiResult.Success] data into another [ApiResult].
 *
 * Unlike [map], the transform can return any [ApiResult] variant -
 * useful when the transform itself may produce a [Failure][ApiResult.Failure] (e.g. API-level error detection).
 */
internal inline fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R> = when (this) {
    is ApiResult.Success -> transform(data)
    is ApiResult.Failure -> ApiResult.Failure(code, message)
    ApiResult.NetworkError -> ApiResult.NetworkError
    ApiResult.Cancelled -> ApiResult.Cancelled
}