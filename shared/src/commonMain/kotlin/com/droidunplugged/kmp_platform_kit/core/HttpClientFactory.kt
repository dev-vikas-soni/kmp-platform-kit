package com.droidunplugged.kmp_platform_kit.core

import io.ktor.client.HttpClient

/**
 * Factory facade for creating HttpClient instances.
 * Platform-specific engines and configuration live in platform modules.
 *
 * Internal to the SDK - consumers should resolve HttpClient from Koin.
 */
internal object HttpClientFactory {
    /**
     * Create a configured HttpClient.
     * Actual implementation must be provided per platform.
     */
    fun create(): HttpClient = createClientImpl()
}

// Top-level expect declaration, no private modifier, no body
expect fun createClientImpl(): HttpClient