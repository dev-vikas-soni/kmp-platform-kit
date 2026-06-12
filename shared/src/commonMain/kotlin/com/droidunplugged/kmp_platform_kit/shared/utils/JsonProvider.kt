package com.droidunplugged.kmp_platform_kit.shared.utils

import kotlinx.serialization.json.Json

object JsonProvider {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        isLenient = true
        coerceInputValues = true
    }
}
