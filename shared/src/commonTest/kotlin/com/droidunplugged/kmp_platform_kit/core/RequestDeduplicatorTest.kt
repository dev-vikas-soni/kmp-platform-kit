package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RequestDeduplicatorTest {

    @Test
    fun `sequential calls with same key are executed independently`() = runTest {
        val deduplicator = RequestDeduplicator()
        var callCount = 0
        val block = suspend {
            callCount++
            "result$callCount"
        }

        val res1 = deduplicator.deduplicate("key", block)
        val res2 = deduplicator.deduplicate("key", block)

        assertEquals("result1", res1)
        assertEquals("result2", res2)
        assertEquals(2, callCount)
    }

    @Test
    fun `concurrent calls with same key share the same result`() = runTest {
        val deduplicator = RequestDeduplicator()
        var callCount = 0
        val block = suspend {
            delay(100)
            callCount++
            "result"
        }

        val def1 = async { deduplicator.deduplicate("key", block) }
        val def2 = async { deduplicator.deduplicate("key", block) }

        val res1 = def1.await()
        val res2 = def2.await()

        assertEquals("result", res1)
        assertEquals("result", res2)
        assertEquals(1, callCount)
    }

    @Test
    fun `different keys are not deduplicated`() = runTest {
        val deduplicator = RequestDeduplicator()
        var callCount = 0
        val block = suspend {
            delay(100)
            callCount++
            "result"
        }

        val def1 = async { deduplicator.deduplicate("key1", block) }
        val def2 = async { deduplicator.deduplicate("key2", block) }

        def1.await()
        def2.await()

        assertEquals(2, callCount)
    }
}
