package com.droidunplugged.kmp_platform_kit.core

sealed class SDKState<out T> {
    data object Loading : SDKState<Nothing>()
    data class Success<T>(val data: T) : SDKState<T>()
    data class ErrorBody(val code: Int, val message: String, val errorCode: String?) : SDKState<Nothing>()
    data class Error(val message: String, val isNetworkError: Boolean) : SDKState<Nothing>()
    
    val isLoading: Boolean get() = this is Loading
    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is ErrorBody || this is Error
    
    fun dataOrNull(): T? = (this as? Success)?.data
    fun errorMessageOrNull(): String? = when (this) {
        is ErrorBody -> message
        is Error -> message
        else -> null
    }
}

object SDKErrorCode {
    const val NETWORK_ERROR = "SDK_NETWORK_ERROR"
    const val REQUEST_CANCELLED = "SDK_REQUEST_CANCELLED"
    const val UNEXPECTED_ERROR = "SDK_UNEXPECTED_ERROR"
}
