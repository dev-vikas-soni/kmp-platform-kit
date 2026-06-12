package com.droidunplugged.kmp_platform_kit.core

interface ApiClient {
    suspend fun <T : Any> get(
        path: String,
        queryParams: Map<String, String> = emptyMap(),
        cachePolicy: CachePolicy = CachePolicy.DEFAULT
    ): ApiResult<T>

    suspend fun <T : Any, R : Any> post(
        path: String,
        body: T,
        queryParams: Map<String, String> = emptyMap()
    ): ApiResult<R>
}
