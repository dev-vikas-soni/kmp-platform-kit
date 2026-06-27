package com.droidunplugged.kmp_platform_kit.features.catalog

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.core.CachePolicy
import com.droidunplugged.kmp_platform_kit.features.catalog.models.Product
import com.droidunplugged.kmp_platform_kit.features.catalog.remote.CatalogApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CatalogApiTest {

    private class MockApiClient : ApiClient {
        var getResponse: Any? = null
        var lastPath: String? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <R> get(
            path: String,
            responseParser: (String) -> R,
            cachePolicy: CachePolicy,
            timeoutMs: Long?
        ): ApiResult<R> {
            lastPath = path
            return getResponse as ApiResult<R>
        }

        override suspend fun <B, R> post(
            path: String,
            body: B,
            serializer: KSerializer<B>,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("Not implemented")

        override suspend fun <B, R> put(
            path: String,
            body: B,
            serializer: KSerializer<B>,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("Not implemented")

        override suspend fun <R> delete(
            path: String,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("Not implemented")

        override suspend fun <B, R> patch(
            path: String,
            body: B,
            serializer: KSerializer<B>,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("Not implemented")
    }

    @Test
    fun `getProducts calls correct path`() = runTest {
        val mockClient = MockApiClient()
        val products = listOf(Product("1", "P1", "D1", 10.0, "url"))
        mockClient.getResponse = ApiResult.Success(products)

        val api = CatalogApi(mockClient)
        val result = api.getProducts()

        assertEquals("products", mockClient.lastPath)
        assertIs<ApiResult.Success<List<Product>>>(result)
        assertEquals(products, result.data)
    }

    @Test
    fun `getProductDetails calls correct path`() = runTest {
        val mockClient = MockApiClient()
        val product = Product("1", "P1", "D1", 10.0, "url")
        mockClient.getResponse = ApiResult.Success(product)

        val api = CatalogApi(mockClient)
        val result = api.getProductDetails("123")

        assertEquals("products/123", mockClient.lastPath)
        assertIs<ApiResult.Success<Product>>(result)
        assertEquals(product, result.data)
    }
}
