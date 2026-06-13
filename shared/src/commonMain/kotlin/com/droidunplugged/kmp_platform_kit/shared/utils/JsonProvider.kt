package com.droidunplugged.kmp_platform_kit.shared.utils

import kotlinx.serialization.json.Json

/**
 * Centralized JSON configuration for the SDK.
 * Use this instance across repositories and parsers to ensure consistent behavior.
 */
internal object JsonProvider {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }
}