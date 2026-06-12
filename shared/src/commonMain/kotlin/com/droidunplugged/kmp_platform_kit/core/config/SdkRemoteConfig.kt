package com.droidunplugged.kmp_platform_kit.core.config

import com.droidunplugged.kmp_platform_kit.core.RetryConfig
import kotlin.time.Duration

data class SdkRemoteConfig(
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val retryConfig: RetryConfig? = null,
    val cacheMaxAge: Duration? = null,
    val maintenanceMessage: String? = null,
    val refreshInterval: Duration? = null
)

interface SdkRemoteConfigProvider {
    suspend fun fetchConfig(): SdkRemoteConfig?
}
