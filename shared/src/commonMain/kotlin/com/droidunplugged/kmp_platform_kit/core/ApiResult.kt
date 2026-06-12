package com.droidunplugged.kmp_platform_kit.core

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: Int, val message: String?) : ApiResult<Nothing>()
    data object NetworkError : ApiResult<Nothing>()
    data object Cancelled : ApiResult<Nothing>()
}
