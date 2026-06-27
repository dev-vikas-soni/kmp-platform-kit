package com.droidunplugged.kmp_platform_kit.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.minutes

class CachePolicyTest {

    @Test
    fun `default values are correct`() {
        val policy = CachePolicy()
        assertEquals(CacheStrategy.NETWORK_FIRST, policy.strategy)
        assertEquals(5.minutes, policy.maxAge)
        assertEquals(false, policy.staleWhileRevalidate)
    }

    @Test
    fun `convenience constants are correct`() {
        assertEquals(CacheStrategy.NETWORK_ONLY, CachePolicy.NO_CACHE.strategy)
        assertEquals(CacheStrategy.CACHE_FIRST, CachePolicy.CACHE_FIRST_5MIN.strategy)
        assertEquals(CacheStrategy.NETWORK_FIRST, CachePolicy.DEFAULT.strategy)
    }
}
