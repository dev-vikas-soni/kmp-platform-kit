package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.JsonProvider
import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ResponseException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.errors.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.KSerializer
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Ktor-based [ApiClient] implementation.
 *
 * Features:
 * - **Retry** with exponential backoff + full jitter ([RetryConfig])
 * - **Request deduplication** - concurrent identical calls share one network trip
 * - **In-memory cache** - configurable via [CachePolicy] per-request
 * - **Per-call timeout** - override the default 30s per request
 *
 * Internal to the SDK - host apps interact with facades only.
 */
internal class KtorApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
    private val retryConfig: RetryConfig = RetryConfig(),
    private val cache: ResponseCache = ResponseCache(),
    private val deduplicator: RequestDeduplicator = RequestDeduplicator()
) : ApiClient {

    private val log get() = PlatformLogger.get()
    private val timeSource = TimeSource.Monotonic

    // ── Normalised base URL (no trailing slash) ──────────────────────
    private val normalizedBase = baseUrl.trimEnd('/')

    override suspend fun <R> get(
        path: String,
        responseParser: (String) -> R,
        cachePolicy: CachePolicy,
        timeoutMs: Long?
    ): ApiResult<R> {
        val url = buildUrl(path)
        log.d(TAG, "→ GET $url [strategy=${cachePolicy.strategy}]")

        return when (cachePolicy.strategy) {
            CacheStrategy.NETWORK_ONLY -> fetchAndCache(url, "GET", cachePolicy, timeoutMs) {
                val resp: HttpResponse = httpClient.get(url) {
                    timeoutMs?.let { timeout { requestTimeoutMillis = it } }
                }
                parseResponse(url, "GET", resp, responseParser)
            }

            CacheStrategy.CACHE_ONLY -> {
                val hit = cache.get(url, cachePolicy.maxAge)
                    ?: return ApiResult.Failure(ERROR_CODE_CACHE_MISS, "Cache miss for $url (CACHE_ONLY strategy)")
                log.d(TAG, "✦ CACHE HIT (only) $url")
                ApiResult.Success(responseParser(hit))
            }

            CacheStrategy.CACHE_FIRST -> {
                cache.get(url, cachePolicy.maxAge)?.let { hit ->
                    log.d(TAG, "✦ CACHE HIT $url")
                    return ApiResult.Success(responseParser(hit))
                }
                fetchAndCache(url, "GET", cachePolicy, timeoutMs) {
                    val resp: HttpResponse = httpClient.get(url) {
                        timeoutMs?.let { timeout { requestTimeoutMillis = it } }
                    }
                    parseResponse(url, "GET", resp, responseParser)
                }
            }

            CacheStrategy.NETWORK_FIRST -> deduplicator.deduplicate(url) {
                fetchAndCache(url, "GET", cachePolicy, timeoutMs) {
                    val resp: HttpResponse = httpClient.get(url) {
                        timeoutMs?.let { timeout { requestTimeoutMillis = it } }
                    }
                    parseResponse(url, "GET", resp, responseParser)
                }.also { result ->
                    // On network failure fall back to stale cache
                    if (result is ApiResult.NetworkError) {
                        cache.get(url, Duration.INFINITE)?.let { stale ->
                            log.w(TAG, "⚠ Network error - serving stale cache for $url")
                            @Suppress("UNCHECKED_CAST")
                            return@deduplicate ApiResult.Success(responseParser(stale))
                        }
                    }
                }
            }
        }
    }

    override suspend fun <B, R> post(
        path: String,
        body: B,
        serializer: KSerializer<B>,
        responseParser: (String) -> R,
        timeoutMs: Long?
    ): ApiResult<R> {
        val url = buildUrl(path)
        log.d(TAG, "→ POST $url")
        return executeWithRetry(url) {
            val serializedBody = JsonProvider.json.encodeToString(serializer, body)
            val resp: HttpResponse = httpClient.post(url) {
                contentType(ContentType.Application.Json)
                setBody(serializedBody)
                timeoutMs?.let { timeout { requestTimeoutMillis = it } }
            }
            parseResponse(url, "POST", resp, responseParser)
        }
    }

    override suspend fun <B, R> put(
        path: String,
        body: B,
        serializer: KSerializer<B>,
        responseParser: (String) -> R,
        timeoutMs: Long?
    ): ApiResult<R> {
        val url = buildUrl(path)
        log.d(TAG, "→ PUT $url")
        return executeWithRetry(url) {
            val serializedBody = JsonProvider.json.encodeToString(serializer, body)
            val resp: HttpResponse = httpClient.put(url) {
                contentType(ContentType.Application.Json)
                setBody(serializedBody)
                timeoutMs?.let { timeout { requestTimeoutMillis = it } }
            }
            parseResponse(url, "PUT", resp, responseParser)
        }
    }

    override suspend fun <R> delete(
        path: String,
        responseParser: (String) -> R,
        timeoutMs: Long?
    ): ApiResult<R> {
        val url = buildUrl(path)
        log.d(TAG, "→ DELETE $url")
        return executeWithRetry(url) {
            val resp: HttpResponse = httpClient.delete(url) {
                timeoutMs?.let { timeout { requestTimeoutMillis = it } }
            }
            parseResponse(url, "DELETE", resp, responseParser)
        }
    }

    override suspend fun <B, R> patch(
        path: String,
        body: B,
        serializer: KSerializer<B>,
        responseParser: (String) -> R,
        timeoutMs: Long?
    ): ApiResult<R> {
        val url = buildUrl(path)
        log.d(TAG, "→ PATCH $url")
        return executeWithRetry(url) {
            val serializedBody = JsonProvider.json.encodeToString(serializer, body)
            val resp: HttpResponse = httpClient.patch(url) {
                contentType(ContentType.Application.Json)
                setBody(serializedBody)
                timeoutMs?.let { timeout { requestTimeoutMillis = it } }
            }
            parseResponse(url, "PATCH", resp, responseParser)
        }
    }

    /** Cancel in-flight deduplication slots and clear the response cache - called from [SDKInitializer.reset]. */
    suspend fun reset() {
        deduplicator.cancelAll()
        cache.clear()
    }

    // ── URL building ─────────────────────────────────────────────────

    /**
     * Safely combines [normalizedBase] and [path] - prevents double-slash
     * when [path] starts with `/`.
     */
    private fun buildUrl(path: String): String =
        "$normalizedBase/${path.trimStart('/')}"

    // ── Cache helper ─────────────────────────────────────────────────

    /**
     * Executes [block] with retry, caches a successful raw response string,
     * and returns the parsed result.
     */
    @Suppress("UnusedParameter") // Parameters reserved for future per-method cache keying
    private suspend fun <R> fetchAndCache(
        url: String,
        method: String,
        cachePolicy: CachePolicy,
        timeoutMs: Long?,
        block: suspend () -> ApiResult<R>
    ): ApiResult<R> = executeWithRetry(url, block)

    // ── Response parsing ─────────────────────────────────────────────

    private suspend fun <R> parseResponse(
        url: String,
        method: String,
        resp: HttpResponse,
        responseParser: (String) -> R
    ): ApiResult<R> {
        val raw = resp.bodyAsText()
        val statusCode = resp.status.value
        log.i(TAG, "← $method $url - ${resp.status} (${raw.length} chars)")
        return if (resp.status.isSuccess()) {
            cache.put(url, raw)   // ✅ cache the raw response for future CACHE_FIRST / NETWORK_FIRST-fallback hits
            ApiResult.Success(responseParser(raw))
        } else {
            log.e(TAG, "✗ $url - HTTP $statusCode")
            ApiResult.Failure(statusCode, raw.ifBlank { "HTTP error $statusCode" })
        }
    }

    // ── Retry logic ──────────────────────────────────────────────────

    private suspend fun <R> executeWithRetry(
        url: String,
        block: suspend () -> ApiResult<R>
    ): ApiResult<R> {
        val mark = timeSource.markNow()
        var lastResult: ApiResult<R>? = null
        var lastException: Exception? = null

        repeat(retryConfig.maxAttempts) { attempt ->
            applyBackoffDelay(attempt, url)
            try {
                val result = block()
                if (result is ApiResult.Failure && result.code in 500..599
                    && attempt < retryConfig.maxAttempts - 1
                ) {
                    log.w(
                        TAG,
                        "✗ $url - HTTP ${result.code} (retryable, attempt ${attempt + 1}/${retryConfig.maxAttempts})"
                    )
                    lastResult = result
                    return@repeat
                }
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                val terminal = handleAttemptException(e, attempt, url, mark)
                if (terminal != null) return terminal
            }
        }
        return lastResult ?: exhaustedRetriesResult(url, mark, lastException)
    }

    private fun handleAttemptException(
        e: Exception,
        attempt: Int,
        url: String,
        mark: TimeSource.Monotonic.ValueTimeMark
    ): ApiResult<Nothing>? = when {
        e is ResponseException && isRetryableServerError(e, attempt) -> {
            val status = e.response.status.value
            val msg = "✗ $url - HTTP $status " +
                    "(retryable, attempt ${attempt + 1}/${retryConfig.maxAttempts})"
            log.w(TAG, msg)
            null
        }
        e is ResponseException -> {
            val code = e.response.status.value
            log.e(TAG, "✗ $url - HTTP $code (${mark.elapsedNow()})", e)
            ApiResult.Failure(code, e.message ?: "HTTP error")
        }
        e is IOException && attempt < retryConfig.maxAttempts - 1 -> {
            log.w(
                TAG,
                "✗ $url - Network error (retryable, attempt ${attempt + 1}/${retryConfig.maxAttempts}): ${e.message}"
            )
            null
        }
        e is IOException -> {
            log.e(TAG, "✗ $url - Network error (${mark.elapsedNow()}): ${e.message}", e)
            ApiResult.NetworkError
        }
        else -> {
            log.e(TAG, "✗ $url - Unexpected (${mark.elapsedNow()}): ${e.message}", e)
            ApiResult.Failure(ERROR_CODE_UNEXPECTED, e.message ?: "Unexpected error")
        }
    }

    private suspend fun applyBackoffDelay(attempt: Int, url: String) {
        if (attempt == 0) return
        val exponential = retryConfig.initialBackoffMs * (1L shl (attempt - 1))
        val capped = min(exponential, MAX_BACKOFF_MS)
        val jitteredDelay = Random.nextLong(0L, capped + 1L)
        log.d(TAG, "⟳ Retry #$attempt for $url (backoff=${jitteredDelay}ms, max=${capped}ms)")
        delay(jitteredDelay)
    }

    private fun isRetryableServerError(e: ResponseException, attempt: Int): Boolean =
        e.response.status.value in 500..599 && attempt < retryConfig.maxAttempts - 1

    private fun exhaustedRetriesResult(
        url: String,
        mark: TimeSource.Monotonic.ValueTimeMark,
        lastException: Exception?
    ): ApiResult<Nothing> = when (val ex = lastException) {
        is IOException -> {
            log.e(TAG, "✗ $url - Network error after ${retryConfig.maxAttempts} attempts (${mark.elapsedNow()})", ex)
            ApiResult.NetworkError
        }
        is ResponseException -> {
            val code = ex.response.status.value
            log.e(TAG, "✗ $url - HTTP $code after ${retryConfig.maxAttempts} attempts (${mark.elapsedNow()})", ex)
            ApiResult.Failure(code, ex.message ?: "HTTP error")
        }
        else -> {
            log.e(TAG, "✗ $url - Failed after ${retryConfig.maxAttempts} attempts (${mark.elapsedNow()})", ex)
            ApiResult.Failure(ERROR_CODE_UNEXPECTED, ex?.message ?: "Unexpected error")
        }
    }

    private companion object {
        const val TAG = "KtorApiClient"
        const val ERROR_CODE_UNEXPECTED = -2
        const val ERROR_CODE_CACHE_MISS = -3
        const val MAX_BACKOFF_MS = 30_000L
    }
}