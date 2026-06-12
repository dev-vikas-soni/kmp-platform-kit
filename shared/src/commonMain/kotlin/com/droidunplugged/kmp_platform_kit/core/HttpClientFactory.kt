package com.droidunplugged.kmp_platform_kit.core

import io.ktor.client.HttpClient

expect class HttpClientFactory() {
    fun create(): HttpClient
}
