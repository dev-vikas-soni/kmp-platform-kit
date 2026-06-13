package com.droidunplugged.kmp_platform_kit.shared.utils

/**
 * Common header keys used across the SDK.
 * Single source of truth - used by SDKInitializer, HttpClientFactory, and features.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  HEADER CLASSIFICATION
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  ┌──────────────────┬──────────────────────┬──────────────────────────────┐
 *  │ Category         │ Headers              │ Lifecycle                    │
 *  ├──────────────────┼──────────────────────┼──────────────────────────────┤
 *  │ DYNAMIC          │ authorization        │ Token refreshes every ~15min │
 *  │ (host app)       │ x-cah-api-guid       │ Changes every login          │
 *  ├──────────────────┼──────────────────────┼──────────────────────────────┤
 *  │ ENV-SPECIFIC     │ clientid             │ Fixed per env (DEV/STAGE/…)  │
 *  │ (host app)       │ x-api-key            │ Fixed per env (DEV/STAGE/…)  │
 *  ├──────────────────┼──────────────────────┼──────────────────────────────┤
 *  │ STATIC           │ platform             │ SDK auto-sets, never changes │
 *  │ (SDK)            │ user-agent           │                              │
 *  │                  │ x-external-source    │                              │
 *  ├──────────────────┼──────────────────────┼──────────────────────────────┤
 *  │ AUTO-MANAGED     │ accept-encoding      │ OkHttp / Darwin engine       │
 *  │ (HTTP engine)    │ connection, host     │ Don't touch                  │
 *  └──────────────────┴──────────────────────┴──────────────────────────────┘
 * ═══════════════════════════════════════════════════════════════════════════
 */

/** Header keys read from [PlatformConfig] on every request. */
internal val ALL_REQUEST_HEADER_KEYS: List<String> =
    listOf("authorization", "x-cah-api-guid", "clientid", "x-api-key")

/** Static headers baked into the SDK - same value for all requests on a platform. */
internal object StaticHeaders {
    const val PLATFORM = "platform"
    const val USER_AGENT = "user-agent"
    const val EXTERNAL_SOURCE = "x-external-source"
    const val EXTERNAL_SOURCE_VALUE = "mobile"
    const val ACCEPT = "accept"
    const val ACCEPT_VALUE = "application/json"
}