package com.droidunplugged.kmp_platform_kit.core

import kotlinx.serialization.KSerializer

/**
 * HTTP client abstraction used by all feature repositories.
 *
 * The SDK provides a single implementation ([KtorApiClient]) configured
 * with platform-specific engines (OkHttp on Android, Darwin on iOS).
 *
 * Every method supports:
 * - **[cachePolicy]** - controls cache strategy and TTL (default: NETWORK_FIRST)
 * - **[timeoutMs]** - per-call timeout override in milliseconds (`null` = use engine default)
 *
 * Internal to the SDK - host apps interact with facades only.
 *
 * @see KtorApiClient for the Ktor-based implementation.
 */
interface ApiClient {

    /**
     * Execute an HTTP GET request.
     *
     * @param path           Relative URL path (appended to the configured base URL).
     * @param responseParser Transforms the raw response body string into a domain object [R].
     * @param cachePolicy    Controls cache strategy and TTL (default: NETWORK_FIRST).
     * @param timeoutMs      Per-call timeout override in milliseconds (`null` = use engine default).
     * @return [ApiResult.Success] with parsed data, or an error variant.
     */
    suspend fun <R> get(
        path: String,
        responseParser: (String) -> R,
        cachePolicy: CachePolicy = CachePolicy.DEFAULT,
        timeoutMs: Long? = null
    ): ApiResult<R>

    /**
     * Execute an HTTP POST request with a JSON body.
     *
     * @param path           Relative URL path (appended to the configured base URL).
     * @param body           Request body object of type [B].
     * @param serializer     kotlinx.serialization serializer for [B].
     * @param responseParser Transforms the raw response body string into a domain object [R].
     * @param timeoutMs      Per-call timeout override in milliseconds (`null` = use engine default).
     * @return [ApiResult.Success] with parsed data, or an error variant.
     */
    suspend fun <B, R> post(
        path: String,
        body: B,
        serializer: KSerializer<B>,
        responseParser: (String) -> R,
        timeoutMs: Long? = null
    ): ApiResult<R>

    /**
     * Execute an HTTP PUT request with a JSON body.
     *
     * @param path           Relative URL path (appended to the configured base URL).
     * @param body           Request body object of type [B].
     * @param serializer     kotlinx.serialization serializer for [B].
     * @param responseParser Transforms the raw response body string into a domain object [R].
     * @param timeoutMs      Per-call timeout override in milliseconds (`null` = use engine default).
     * @return [ApiResult.Success] with parsed data, or an error variant.
     */
    suspend fun <B, R> put(
        path: String,
        body: B,
        serializer: KSerializer<B>,
        responseParser: (String) -> R,
        timeoutMs: Long? = null
    ): ApiResult<R>

    /**
     * Execute an HTTP DELETE request.
     *
     * @param path           Relative URL path (appended to the configured base URL).
     * @param responseParser Transforms the raw response body string into a domain object [R].
     * @param timeoutMs      Per-call timeout override in milliseconds (`null` = use engine default).
     * @return [ApiResult.Success] with parsed data, or an error variant.
     */
    suspend fun <R> delete(
        path: String,
        responseParser: (String) -> R,
        timeoutMs: Long? = null
    ): ApiResult<R>

    /**
     * Execute an HTTP PATCH request with a JSON body.
     *
     * @param path           Relative URL path (appended to the configured base URL).
     * @param body           Request body object of type [B].
     * @param serializer     kotlinx.serialization serializer for [B].
     * @param responseParser Transforms the raw response body string into a domain object [R].
     * @param timeoutMs      Per-call timeout override in milliseconds (`null` = use engine default).
     * @return [ApiResult.Success] with parsed data, or an error variant.
     */
    suspend fun <B, R> patch(
        path: String,
        body: B,
        serializer: KSerializer<B>,
        responseParser: (String) -> R,
        timeoutMs: Long? = null
    ): ApiResult<R>
}