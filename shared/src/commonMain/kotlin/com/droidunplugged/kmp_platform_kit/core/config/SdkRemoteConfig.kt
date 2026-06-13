package com.droidunplugged.kmp_platform_kit.core.config

import com.droidunplugged.kmp_platform_kit.core.RetryConfig
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Server-pushed runtime configuration for the SDK.
 *
 * Allows the backend to tune SDK behaviour without a host app update:
 * - Kill-switch a broken feature instantly
 * - Adjust retry/cache parameters in response to server load
 * - Push a maintenance message to the app
 * - Gradually roll out features with per-tenant flags
 *
 * ## How to use
 * 1. Your backend serves a JSON config endpoint (e.g. `GET /sdk/config`).
 * 2. Implement [SdkRemoteConfigProvider] to fetch it.
 * 3. Register: `SDKInitializer.setRemoteConfigProvider(provider)`.
 * 4. The SDK fetches and applies config on init, and optionally refreshes periodically.
 *
 * ## Example config JSON
 * ```json
 * {
 *   "featureFlags": {
 *     "physical_inventory": true,
 *     "orders": false
 *   },
 *   "retryMaxAttempts": 3,
 *   "cacheMaxAgeMinutes": 5,
 *   "maintenanceMessage": null
 * }
 * ```
 *
 * @property featureFlags        Per-feature kill-switches. `false` = feature disabled.
 * @property retryConfig         Override default [RetryConfig] at runtime.
 * @property cacheMaxAge         Override default cache TTL at runtime.
 * @property maintenanceMessage  If non-null, the SDK emits this via telemetry and facades
 *                               return [com.droidunplugged.kmp_platform_kit.core.error.SdkError.ServerError]
 *                               with this message until it is cleared.
 * @property refreshInterval     How often the SDK re-fetches this config. `null` = once on init.
 */
data class SdkRemoteConfig(
    val featureFlags: Map<String, Boolean> = emptyMap(),
    val retryConfig: RetryConfig? = null,
    val cacheMaxAge: Duration? = null,
    val maintenanceMessage: String? = null,
    val refreshInterval: Duration? = 15.minutes
) {
    /**
     * Returns `true` if the feature with [featureId] is enabled.
     * Falls back to `true` (enabled) if the feature is not in [featureFlags] -
     * absence of a flag means "not kill-switched".
     */
    fun isFeatureEnabled(featureId: String): Boolean =
        featureFlags[featureId] ?: true

    /** `true` if the SDK is in maintenance mode and should block all API calls. */
    val isInMaintenance: Boolean get() = maintenanceMessage != null
}

/**
 * Contract the host app implements to fetch [SdkRemoteConfig] from the server.
 *
 * ## Example
 * ```kotlin
 * class MyRemoteConfigProvider(private val configApi: ConfigApi) : SdkRemoteConfigProvider {
 *     override suspend fun fetchConfig(): SdkRemoteConfig? {
 *         return try {
 *             val json = configApi.getSdkConfig()
 *             Json.decodeFromString(json)
 *         } catch (e: Exception) {
 *             null  // return null to keep the last known config
 *         }
 *     }
 * }
 *
 * SDKInitializer.setRemoteConfigProvider(MyRemoteConfigProvider(api))
 * SDKInitializer.init(...)
 * ```
 */
interface SdkRemoteConfigProvider {
    /**
     * Fetch the latest SDK configuration from your server.
     *
     * @return The fetched [SdkRemoteConfig], or `null` to retain the current config.
     */
    suspend fun fetchConfig(): SdkRemoteConfig?
}

/**
 * In-memory holder for the currently active [SdkRemoteConfig].
 * Internal - updated by the SDK when [SdkRemoteConfigProvider.fetchConfig] returns.
 */
internal object SdkRemoteConfigStore {

    @Volatile
    private var current: SdkRemoteConfig = SdkRemoteConfig()

    val config: SdkRemoteConfig get() = current

    fun update(config: SdkRemoteConfig) {
        current = config
    }

    fun reset() {
        current = SdkRemoteConfig()
    }

    fun isFeatureEnabled(featureId: String): Boolean =
        current.isFeatureEnabled(featureId)
}