package com.droidunplugged.kmp_platform_kit.core.interceptor

import com.droidunplugged.kmp_platform_kit.core.error.SdkError
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.statement.HttpResponse

interface SdkRequestInterceptor {
    val id: String
    suspend fun onRequest(request: HttpRequestBuilder): HttpRequestBuilder = request
    suspend fun onResponse(response: HttpResponse, durationMs: Long) {}
    suspend fun onError(error: SdkError) {}
}
