package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.models.PaginationInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SdkPager<T, Query>(
    private val fetcher: suspend (Query, Int) -> ApiResult<PaginatedResult<T>>
) {
    private val _state = MutableStateFlow<SDKState<List<T>>>(SDKState.Loading)
    val state: StateFlow<SDKState<List<T>>> = _state.asStateFlow()

    private var currentList = mutableListOf<T>()
    private var currentPage = 1
    private var hasNextPage = true
    private var isFetching = false

    suspend fun loadFirstPage(query: Query) {
        currentList.clear()
        currentPage = 1
        hasNextPage = true
        _state.value = SDKState.Loading
        loadNext(query)
    }

    suspend fun loadNextPage(query: Query) {
        if (!hasNextPage || isFetching) return
        loadNext(query)
    }

    private suspend fun loadNext(query: Query) {
        isFetching = true
        val result = fetcher(query, currentPage)
        
        when (result) {
            is ApiResult.Success -> {
                val paginatedResult = result.data
                currentList.addAll(paginatedResult.items)
                currentPage++
                hasNextPage = paginatedResult.paginationInfo.hasNext
                _state.value = SDKState.Success(currentList.toList())
            }
            is ApiResult.Failure -> _state.value = SDKState.ErrorBody(result.code, result.message ?: "Error", null)
            ApiResult.NetworkError -> _state.value = SDKState.Error(SDKErrorCode.NETWORK_ERROR, true)
            ApiResult.Cancelled -> _state.value = SDKState.Error(SDKErrorCode.REQUEST_CANCELLED, false)
        }
        isFetching = false
    }
}

data class PaginatedResult<T>(
    val items: List<T>,
    val paginationInfo: PaginationInfo
)
