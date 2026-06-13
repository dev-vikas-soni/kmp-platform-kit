package com.droidunplugged.kmp_platform_kit.core.di

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.HttpClientFactory
import com.droidunplugged.kmp_platform_kit.core.KtorApiClient
import io.ktor.client.HttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * Named qualifier for the base URL string binding in Koin.
 * Must match the qualifier used in [SDKInitializer.init] runtimeModule.
 *
 * Centralised here (single source of truth) to avoid magic strings.
 */
internal const val BASE_URL_QUALIFIER = "kmp_platform_kit_baseUrl"

/**
 * Core Koin module - provides networking dependencies.
 *
 * Requires a `String` named [BASE_URL_QUALIFIER] to be supplied
 * at init time (see `SDKInitializer`).
 *
 * The [HttpClient] is explicitly closed in `SDKInitializer.reset()`
 * to release connections, threads, and sockets.
 *
 * Internal - host apps never interact with this directly.
 */
internal val coreModule = module {
    single<HttpClient> { HttpClientFactory.create() }
    single<ApiClient> {
        KtorApiClient(
            httpClient = get(),
            baseUrl = get(named(BASE_URL_QUALIFIER))
        )
    }
}