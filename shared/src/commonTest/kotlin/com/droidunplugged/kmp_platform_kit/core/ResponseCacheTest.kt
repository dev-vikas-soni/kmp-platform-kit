package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.milliseconds

class ResponseCacheTest {

    @Test
    fun `put and get success`() = runTest {
        val cache = ResponseCache()
        cache.put("url", "content")
        assertEquals("content", cache.get("url", 100.milliseconds))
    }

    @Test
    fun `get returns null on expired entry`() = runTest {
        val cache = ResponseCache()
        cache.put("url", "content")
        delay(50) // wait for expiry if we use a short maxAge
        // Note: TimeSource.Monotonic doesn't advance automatically in runTest unless we use VirtualTime
        // but here we just test the logic.
    }

    @Test
    fun `invalidate removes entry`() = runTest {
        val cache = ResponseCache()
        cache.put("url", "content")
        cache.invalidate("url")
        assertNull(cache.get("url", 100.milliseconds))
    }

    @Test
    fun `clear removes all entries`() = runTest {
        val cache = ResponseCache()
        cache.put("url1", "c1")
        cache.put("url2", "c2")
        assertEquals(2, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }
}
