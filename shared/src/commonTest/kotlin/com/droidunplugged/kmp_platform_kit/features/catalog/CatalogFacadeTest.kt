package com.droidunplugged.kmp_platform_kit.features.catalog

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.core.CachePolicy
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer
import com.droidunplugged.kmp_platform_kit.features.catalog.facade.CatalogFacade
import com.droidunplugged.kmp_platform_kit.features.catalog.models.Product
import com.droidunplugged.kmp_platform_kit.features.catalog.remote.CatalogApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.KSerializer
import org.koin.dsl.module
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CatalogFacadeTest {

    private open class DummyApiClient : ApiClient {
        override suspend fun <R> get(
            path: String,
            responseParser: (String) -> R,
            cachePolicy: CachePolicy,
            timeoutMs: Long?
        ): ApiResult<R> = error("")

        override suspend fun <B, R> post(
            path: String,
            body: B,
            serializer: KSerializer<B>,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("")

        override suspend fun <B, R> put(
            path: String,
            body: B,
            serializer: KSerializer<B>,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("")

        override suspend fun <R> delete(
            path: String,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("")

        override suspend fun <B, R> patch(
            path: String,
            body: B,
            serializer: KSerializer<B>,
            responseParser: (String) -> R,
            timeoutMs: Long?
        ): ApiResult<R> = error("")
    }

    private class MockCatalogApi : CatalogApi(apiClient = DummyApiClient()) {
        var productsResult: ApiResult<List<Product>> = ApiResult.NetworkError
        var detailsResult: ApiResult<Product> = ApiResult.NetworkError

        override suspend fun getProducts(): ApiResult<List<Product>> = productsResult
        override suspend fun getProductDetails(id: String): ApiResult<Product> = detailsResult
    }

    private val mockApi = MockCatalogApi()

    @BeforeTest
    fun setup() = runTest {
        SDKInitializer.reset()
        SDKInitializer.init(
            baseUrl = "https://test.com",
            authToken = "token",
            apiGuid = "guid",
            clientId = "client",
            apiKey = "key",
            additionalModules = listOf(module {
                single<CatalogApi> { mockApi }
            })
        )
    }

    @AfterTest
    fun tearDown() = runTest {
        SDKInitializer.reset()
    }

    @Test
    fun `getProducts returns result from API`() = runTest {
        val products = listOf(Product("1", "P1", "D", 10.0, "url"))
        mockApi.productsResult = ApiResult.Success(products)

        val result = CatalogFacade.getProducts()

        assertIs<ApiResult.Success<List<Product>>>(result)
        assertEquals(products, result.data)
    }

    @Test
    fun `getProductDetails returns result from API`() = runTest {
        val product = Product("1", "P1", "D", 10.0, "url")
        mockApi.detailsResult = ApiResult.Success(product)

        val result = CatalogFacade.getProductDetails("1")

        assertIs<ApiResult.Success<Product>>(result)
        assertEquals(product, result.data)
    }

    @Test
    fun `getProducts fails if SDK not initialized`() = runTest {
        SDKInitializer.reset()
        assertFailsWith<IllegalStateException> {
            CatalogFacade.getProducts()
        }
    }

    @Test
    fun `getProductDetails throws if id is blank`() = runTest {
        assertFailsWith<IllegalArgumentException> {
            CatalogFacade.getProductDetails(" ")
        }
    }
}
