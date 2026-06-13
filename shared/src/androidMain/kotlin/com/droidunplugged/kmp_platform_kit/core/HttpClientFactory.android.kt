package com.droidunplugged.kmp_platform_kit.core

import com.cardinalhealth.vantus.sdk.core.SDKInfo
import com.droidunplugged.kmp_platform_kit.shared.utils.StaticHeaders
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import okhttp3.CertificatePinner
import okhttp3.ConnectionPool
import java.util.concurrent.TimeUnit

/** Engine-level timeout for connect / read / write (seconds). */
private const val ENGINE_TIMEOUT_SECONDS = 30L

/** Maximum idle connections in the OkHttp connection pool. */
private const val POOL_MAX_IDLE = 5

/** Keep-alive duration for idle connections (minutes). */
private const val POOL_KEEP_ALIVE_MINUTES = 5L

/** Per-request Ktor timeout (milliseconds). */
private const val KTOR_TIMEOUT_MS = 30_000L

/**
 * Android actual implementation of [createClientImpl].
 *
 * Configures OkHttp engine with:
 * - Engine-level connect/read/write timeouts + connection pool
 * - Ktor [HttpTimeout] plugin for per-request timeout override capability
 * - Static headers via [DefaultRequest]
 * - Dynamic headers via [installDynamicHeaders] (shared with iOS)
 * - **SSL certificate pinning** via [SDKConfig.sslPins] when configured
 */
actual fun createClientImpl(): HttpClient {
    val client = HttpClient(OkHttp) {
        engine {
            config {
                connectTimeout(ENGINE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                readTimeout(ENGINE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                writeTimeout(ENGINE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                connectionPool(ConnectionPool(POOL_MAX_IDLE, POOL_KEEP_ALIVE_MINUTES, TimeUnit.MINUTES))

                // ✅ SSL Certificate Pinning - active when SDKConfig.sslPins is set
                SDKConfig.sslPins?.let { pinConfig ->
                    val pinner = CertificatePinner.Builder().apply {
                        pinConfig.pins.forEach { pin -> add(pinConfig.hostname, pin) }
                    }.build()
                    certificatePinner(pinner)
                }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = KTOR_TIMEOUT_MS
            connectTimeoutMillis = KTOR_TIMEOUT_MS
            socketTimeoutMillis = KTOR_TIMEOUT_MS
        }

        install(DefaultRequest) {
            // ── Static headers (same for all requests on this platform) ──
            header(StaticHeaders.PLATFORM, "android")
            header(
                StaticHeaders.USER_AGENT,
                "${SDKInfo.fullName} (Android; API-${android.os.Build.VERSION.SDK_INT}; ${android.os.Build.MODEL})"
            )
            header(StaticHeaders.EXTERNAL_SOURCE, StaticHeaders.EXTERNAL_SOURCE_VALUE)
            header(StaticHeaders.ACCEPT, StaticHeaders.ACCEPT_VALUE)
        }
    }

    // ✅ Shared header-injection logic - no longer duplicated between platforms
    installDynamicHeaders(client)
    return client
}