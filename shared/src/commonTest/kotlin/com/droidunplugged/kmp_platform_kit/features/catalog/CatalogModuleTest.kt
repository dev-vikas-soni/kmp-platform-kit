package com.droidunplugged.kmp_platform_kit.features.catalog

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.core.CachePolicy
import com.droidunplugged.kmp_platform_kit.features.catalog.di.catalogModule
import com.droidunplugged.kmp_platform_kit.features.catalog.remote.CatalogApi
import kotlinx.serialization.KSerializer
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertNotNull

class CatalogModuleTest {

    private class MockApiClient : ApiClient {
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

    @Test
    fun `catalogModule provides CatalogApi`() {
        val koin = koinApplication {
            modules(
                catalogModule,
                module {
                    single<ApiClient> { MockApiClient() }
                }
            )
        }.koin

        val api = koin.get<CatalogApi>()
        assertNotNull(api)
    }
}
