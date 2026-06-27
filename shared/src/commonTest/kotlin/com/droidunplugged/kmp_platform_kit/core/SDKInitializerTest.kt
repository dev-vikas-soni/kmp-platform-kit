package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SDKInitializerTest {

    @BeforeTest
    fun setup() = runTest {
        SDKInitializer.reset()
    }

    @AfterTest
    fun tearDown() = runTest {
        SDKInitializer.reset()
    }

    @Test
    fun `isInitialized is false by default`() {
        assertFalse(SDKInitializer.isInitialized)
    }

    @Test
    fun `init sets isInitialized to true`() = runTest {
        SDKInitializer.init(
            baseUrl = "https://api.example.com",
            authToken = "token",
            apiGuid = "guid",
            clientId = "client",
            apiKey = "key"
        )
        assertTrue(SDKInitializer.isInitialized)
    }

    @Test
    fun `reset sets isInitialized to false`() = runTest {
        SDKInitializer.init(
            baseUrl = "https://api.example.com",
            authToken = "token",
            apiGuid = "guid",
            clientId = "client",
            apiKey = "key"
        )
        SDKInitializer.reset()
        assertFalse(SDKInitializer.isInitialized)
    }
}
