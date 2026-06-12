package com.droidunplugged.kmp_platform_kit.core.di

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.CircuitBreaker
import com.droidunplugged.kmp_platform_kit.core.HttpClientFactory
import com.droidunplugged.kmp_platform_kit.core.KtorApiClient
import com.droidunplugged.kmp_platform_kit.core.NoOpTelemetry
import com.droidunplugged.kmp_platform_kit.core.RequestDeduplicator
import com.droidunplugged.kmp_platform_kit.core.ResponseCache
import com.droidunplugged.kmp_platform_kit.core.SDKTelemetry
import com.droidunplugged.kmp_platform_kit.core.TokenManager
import com.droidunplugged.kmp_platform_kit.core.config.SdkEnvironment
import org.koin.dsl.module

/**
 * Core Koin module.
 *
 * Provides singletons for the HTTP engine, resilience subsystems, and the
 * [ApiClient] implementation. Every feature module's Koin module can depend
 * on `get<ApiClient>()` being present once this module is loaded.
 *
 * ### Required caller setup
 * Before loading this module the host app must call [SDKInitializer.init] with
 * a valid [SDKCredentials] + [SdkEnvironment]. The environment is extracted via
 * [SDKInitializer.environment] and injected here at graph creation time.
 *
 * ### Typical bootstrap
 * ```kotlin
 * // In your Application.onCreate() / iOS AppDelegate
 * val env = SdkEnvironment(
 *     id       = "prod",
 *     baseUrl  = "https://api.example.com",
 *     clientId = "my-client",
 *     apiKey   = BuildConfig.API_KEY
 * )
 * coroutineScope.launch {
 *     SDKInitializer.init(SDKCredentials(env, authToken = null, apiGuid = null))
 * }
 * startKoin {
 *     modules(coreModule(env))
 * }
 * ```
 */
fun coreModule(environment: SdkEnvironment) = module {

    // ── HTTP engine ────────────────────────────────────────────────────────────
    single { HttpClientFactory().create() }

    // ── Resilience subsystems ──────────────────────────────────────────────────
    single { ResponseCache() }
    single { RequestDeduplicator() }
    single { CircuitBreaker() }
    single { TokenManager(refreshProvider = null) }

    // ── Telemetry (no-op by default; swap via SDKInitializer.setTelemetry) ────
    single<SDKTelemetry> { NoOpTelemetry }

    // ── ApiClient (fully wired KtorApiClient) ─────────────────────────────────
    single<ApiClient> {
        KtorApiClient(
            httpClient     = get(),
            environment    = environment,
            tokenManager   = get(),
            cache          = get(),
            deduplicator   = get(),
            circuitBreaker = get(),
            telemetry      = get(),
            interceptors   = emptyList()
        )
    }
}
