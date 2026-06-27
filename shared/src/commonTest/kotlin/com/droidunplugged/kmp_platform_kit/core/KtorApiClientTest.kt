package com.droidunplugged.kmp_platform_kit.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.builtins.serializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class KtorApiClientTest {

    private fun createClient(mockEngine: MockEngine) = KtorApiClient(
        httpClient = HttpClient(mockEngine),
        baseUrl = "https://api.example.com"
    )

    @Test
    fun `get success returns Success result`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "{\"id\": 1}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val client = createClient(mockEngine)

        val result = client.get(
            path = "test",
            responseParser = { it }
        )

        assertIs<ApiResult.Success<String>>(result)
        assertEquals("{\"id\": 1}", result.data)
    }

    @Test
    fun `get 404 returns Failure result`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "Not Found",
                status = HttpStatusCode.NotFound
            )
        }
        val client = createClient(mockEngine)

        val result = client.get(
            path = "test",
            responseParser = { it }
        )

        assertIs<ApiResult.Failure>(result)
        assertEquals(404, result.code)
        assertEquals("Not Found", result.message)
    }

    @Test
    fun `post success sends body and returns Success`() = runTest {
        val mockEngine = MockEngine { _ ->
            respond(
                content = "{\"status\": \"ok\"}",
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type", "application/json")
            )
        }
        val client = createClient(mockEngine)

        val result = client.post(
            path = "test",
            body = "some body",
            serializer = String.serializer(),
            responseParser = { it }
        )

        assertIs<ApiResult.Success<String>>(result)
    }

    @Test
    fun `get with CACHE_FIRST returns cached data if present`() = runTest {
        val mockEngine = MockEngine { respond("network", HttpStatusCode.OK) }
        val cache = ResponseCache()
        val url = "https://api.example.com/test"
        cache.put(url, "cached")

        val client = KtorApiClient(
            httpClient = HttpClient(mockEngine),
            baseUrl = "https://api.example.com",
            cache = cache
        )

        val result = client.get(
            path = "test",
            responseParser = { it },
            cachePolicy = CachePolicy(strategy = CacheStrategy.CACHE_FIRST)
        )

        assertIs<ApiResult.Success<String>>(result)
        assertEquals("cached", result.data)
    }

    @Test
    fun `get retry on 500 eventually succeeds`() = runTest {
        var attempts = 0
        val mockEngine = MockEngine { _ ->
            attempts++
            if (attempts < 3) {
                respond("Error", HttpStatusCode.InternalServerError)
            } else {
                respond("Success", HttpStatusCode.OK)
            }
        }

        // Use a faster retry config for tests
        val client = KtorApiClient(
            httpClient = HttpClient(mockEngine),
            baseUrl = "https://api.example.com",
            retryConfig = RetryConfig(maxAttempts = 3, initialBackoffMs = 1)
        )

        val result = client.get(
            path = "test",
            responseParser = { it }
        )

        assertEquals(3, attempts)
        assertIs<ApiResult.Success<String>>(result)
        assertEquals("Success", result.data)
    }
}
