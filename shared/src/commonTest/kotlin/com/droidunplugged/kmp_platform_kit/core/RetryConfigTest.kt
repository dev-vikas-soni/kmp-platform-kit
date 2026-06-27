package com.droidunplugged.kmp_platform_kit.core

import kotlin.test.Test
import kotlin.test.assertEquals

class RetryConfigTest {

    @Test
    fun `default values are correct`() {
        val config = RetryConfig()
        assertEquals(3, config.maxAttempts)
        assertEquals(500L, config.initialBackoffMs)
    }

    @Test
    fun `NO_RETRY constant is correct`() {
        assertEquals(1, RetryConfig.NO_RETRY.maxAttempts)
        assertEquals(0L, RetryConfig.NO_RETRY.initialBackoffMs)
    }
}
