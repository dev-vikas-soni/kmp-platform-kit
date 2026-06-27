package com.droidunplugged.kmp_platform_kit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SslPinConfigTest {

    @Test
    fun `constructor succeeds for valid pins`() {
        val pins = listOf("sha256/abc", "sha256/def")
        val config = SslPinConfig("api.example.com", pins)
        assertEquals("api.example.com", config.hostname)
        assertEquals(pins, config.pins)
    }

    @Test
    fun `constructor fails for empty hostname`() {
        assertFailsWith<IllegalArgumentException> {
            SslPinConfig("", listOf("sha256/abc"))
        }
    }

    @Test
    fun `constructor fails for empty pins`() {
        assertFailsWith<IllegalArgumentException> {
            SslPinConfig("api.example.com", emptyList())
        }
    }
}
