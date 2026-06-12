package com.droidunplugged.kmp_platform_kit.core.config

data class SdkEnvironment(
    val id: String,
    val baseUrl: String,
    val clientId: String,
    val apiKey: String,
    val sslPins: SslPinConfig? = null
) {
    init {
        require(id.isNotBlank()) { "Environment id cannot be blank." }
        require(baseUrl.isNotBlank() && !baseUrl.endsWith("/")) { "BaseUrl must be valid and must not end with a slash." }
        require(clientId.isNotBlank()) { "ClientId cannot be blank." }
        require(apiKey.isNotBlank()) { "ApiKey cannot be blank." }
    }
}
