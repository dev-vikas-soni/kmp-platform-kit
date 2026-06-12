package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.config.SdkEnvironment
import com.droidunplugged.kmp_platform_kit.core.error.SdkError
import com.droidunplugged.kmp_platform_kit.core.interceptor.SdkRequestInterceptor
import com.droidunplugged.kmp_platform_kit.core.tracing.SdkTraceContext
import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.delay

class KtorApiClient(
    private val httpClient: HttpClient,
    private val environment: SdkEnvironment,
    private val tokenManager: TokenManager,
    private val cache: ResponseCache,
    private val deduplicator: RequestDeduplicator,
    private val circuitBreaker: CircuitBreaker,
    private val telemetry: SDKTelemetry,
    private val interceptors: List<SdkRequestInterceptor> = emptyList()
) : ApiClient {

    override suspend fun <T : Any> get(path: String, queryParams: Map<String, String>, cachePolicy: CachePolicy): ApiResult<T> {
        val url = buildUrl(path, queryParams)
        
        return circuitBreaker.execute {
            deduplicator.deduplicate(url) {
                // Check Cache First
                val cached = cache.get(url, cachePolicy)
                if (cached != null) {
                    @Suppress("UNCHECKED_CAST")
                    return@deduplicate ApiResult.Success(cached as T)
                }

                // Network Call
                val response = executeWithRetry { 
                    val builder = HttpRequestBuilder().apply { method = HttpMethod.Get }
                    executeHttpRequest(url, builder)
                }
                
                // TODO: Parse Response properly based on reified type (omitted for brevity, requires inline/reified in actual impl)
                @Suppress("UNCHECKED_CAST")
                val result = ApiResult.Success(response as T)
                
                // Save to cache
                if (result is ApiResult.Success) {
                    cache.put(url, result.data)
                }
                
                result
            }
        }
    }

    override suspend fun <T : Any, R : Any> post(path: String, body: T, queryParams: Map<String, String>): ApiResult<R> {
        val url = buildUrl(path, queryParams)
        
        return circuitBreaker.execute {
            val response = executeWithRetry {
                val builder = HttpRequestBuilder().apply { 
                    method = HttpMethod.Post
                    // setBody(body) 
                }
                executeHttpRequest(url, builder)
            }
            @Suppress("UNCHECKED_CAST")
            ApiResult.Success(response as R)
        }
    }

    private suspend fun executeHttpRequest(url: String, requestBuilder: HttpRequestBuilder): HttpResponse {
        val token = tokenManager.getValidToken()
        requestBuilder.url(url)
        
        if (token != null) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        
        SdkTraceContext.injectTracingHeaders(requestBuilder)
        
        var modifiedRequest = requestBuilder
        for (interceptor in interceptors) {
            modifiedRequest = interceptor.onRequest(modifiedRequest)
        }
        
        PlatformLogger.d("KtorApiClient", "Executing ${modifiedRequest.method.value} $url")
        val start = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        
        val response = httpClient.request(modifiedRequest)
        
        val duration = kotlinx.datetime.Clock.System.now().toEpochMilliseconds() - start
        for (interceptor in interceptors) {
            interceptor.onResponse(response, duration)
        }
        
        telemetry.recordApiCall(url, duration, response.status.value, 0)
        
        if (response.status.value in 200..299) {
            return response
        } else if (response.status.value == 401) {
            tokenManager.notifyUnauthorized()
            throw SdkError.Unauthorized()
        } else {
            throw SdkError.ServerError(response.status.value)
        }
    }
    
    private suspend fun <R> executeWithRetry(block: suspend () -> R): R {
        val retryConfig = RetryConfig() // Or fetch from RemoteConfig
        var attempt = 1
        while (true) {
            try {
                return block()
            } catch (e: Exception) {
                if (attempt >= retryConfig.maxAttempts) throw e
                if (e is SdkError && !e.isRetryable) throw e
                
                val delayMs = retryConfig.delayForAttempt(attempt)
                delay(delayMs)
                attempt++
            }
        }
    }

    private fun buildUrl(path: String, queryParams: Map<String, String>): String {
        val baseUrl = environment.baseUrl.trimEnd('/')
        val cleanPath = path.trimStart('/')
        val queryStr = if (queryParams.isEmpty()) "" else "?" + queryParams.entries.joinToString("&") { "${it.key}=${it.value}" }
        return "$baseUrl/$cleanPath$queryStr"
    }
}
