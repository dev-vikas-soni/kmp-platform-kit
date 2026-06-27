package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFailsWith

class BaseFacadeTest {

    private object TestFacade : BaseFacade() {
        override val tag: String = "TestFacade"
        fun test() = requireInitialized()
    }

    @BeforeTest
    fun setup() = runTest {
        SDKInitializer.reset()
    }

    @AfterTest
    fun tearDown() = runTest {
        SDKInitializer.reset()
    }

    @Test
    fun `requireInitialized throws when SDK not initialized`() {
        assertFailsWith<IllegalStateException> {
            TestFacade.test()
        }
    }

    @Test
    fun `requireInitialized does not throw when SDK is initialized`() = runTest {
        SDKInitializer.init(
            baseUrl = "https://api.example.com",
            authToken = "token",
            apiGuid = "guid",
            clientId = "client",
            apiKey = "key"
        )
        TestFacade.test() // should not throw
    }
}
