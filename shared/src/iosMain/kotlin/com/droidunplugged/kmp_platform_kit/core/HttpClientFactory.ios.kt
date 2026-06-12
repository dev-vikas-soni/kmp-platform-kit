package com.droidunplugged.kmp_platform_kit.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import com.droidunplugged.kmp_platform_kit.shared.utils.JsonProvider

actual class HttpClientFactory actual constructor() {
    actual fun create(): HttpClient = HttpClient(Darwin) {
        install(ContentNegotiation) {
            json(JsonProvider.json)
        }
    }
}
