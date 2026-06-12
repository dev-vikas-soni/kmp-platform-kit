package com.droidunplugged.kmp_platform_kit.core.config

data class SslPinConfig(
    val hostname: String,
    val pins: List<String>
) {
    init {
        require(pins.isNotEmpty()) { "SslPinConfig requires at least one pin." }
        require(hostname.isNotBlank()) { "SslPinConfig requires a valid hostname." }
    }
}
