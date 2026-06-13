package com.droidunplugged.kmp_platform_kit.core.config

import com.droidunplugged.kmp_platform_kit.core.SslPinConfig

/**
 * Represents a deployment environment the SDK can connect to.
 *
 * Host apps define one or more environments and pass the active one to
 * [com.droidunplugged.kmp_platform_kit.core.SDKInitializer]. This replaces the raw
 * `baseUrl` + `clientId` + `apiKey` parameters with a strongly-typed,
 * named configuration object.
 *
 * ## Example
 * ```kotlin
 * object Environments {
 *     val STAGING = SdkEnvironment(
 *         id = "staging",
 *         baseUrl = "https://api.stage.example.com",
 *         clientId = BuildConfig.STAGING_CLIENT_ID,
 *         apiKey = BuildConfig.STAGING_API_KEY
 *     )
 *
 *     val PRODUCTION = SdkEnvironment(
 *         id = "production",
 *         baseUrl = "https://api.example.com",
 *         clientId = BuildConfig.PROD_CLIENT_ID,
 *         apiKey = BuildConfig.PROD_API_KEY,
 *         sslPins = SslPinConfig(
 *             hostname = "api.example.com",
 *             pins = listOf("sha256/AAAA...", "sha256/BBBB...")
 *         )
 *     )
 * }
 *
 * // At app startup:
 * SDKInitializer.init(
 *     environment = if (BuildConfig.DEBUG) Environments.STAGING else Environments.PRODUCTION,
 *     authToken = sessionManager.token,
 *     apiGuid = sessionManager.guid
 * )
 * ```
 *
 * @property id       Machine-readable identifier (e.g. `"staging"`, `"production"`).
 * @property baseUrl  Full API base URL - no trailing slash.
 * @property clientId OAuth client ID (`clientid` header).
 * @property apiKey   API gateway key (`x-api-key` header).
 * @property sslPins  Optional SSL certificate pinning config. `null` = no pinning.
 *                    **Strongly recommended for production.**
 * @throws IllegalArgumentException if required values are blank or [baseUrl] ends with `/`.
 */
data class SdkEnvironment(
    val id: String,
    val baseUrl: String,
    val clientId: String,
    val apiKey: String,
    val sslPins: SslPinConfig? = null
) {
    init {
        require(id.isNotBlank()) { "environment id must not be blank" }
        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }
        require(!baseUrl.endsWith("/")) {
            "baseUrl must not end with a trailing slash. Got: $baseUrl"
        }
    }

    override fun toString() = "SdkEnvironment(id=$id, baseUrl=$baseUrl)"
}

/**
 * Well-known environment IDs.
 *
 * Use these constants to avoid magic strings in host app code, analytics labels,
 * or environment switchers.
 */
object SdkEnvironmentId {
    /** Development / local integration environment. */
    const val DEVELOPMENT = "development"

    /** Shared staging environment used for QA and developer validation. */
    const val STAGING = "staging"

    /** User acceptance testing environment. */
    const val UAT = "uat"

    /** Production environment used by released host apps. */
    const val PRODUCTION = "production"
}