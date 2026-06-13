package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.circuit.CircuitBreaker
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class CircuitBreakerTest {

    // ─── helpers ──────────────────────────────────────────────────────────────

    private fun breaker(threshold: Int = 3) =
        CircuitBreaker(failureThreshold = threshold)

    private suspend fun fail(breaker: CircuitBreaker) {
        runCatching {
            breaker.execute<Unit> { throw RuntimeException("fail") }
        }
    }

    // ─── CLOSED state ─────────────────────────────────────────────────────────

    @Test
    fun `starts in CLOSED state and forwards successful calls`() = runTest {
        val cb = breaker()
        val result = cb.execute { 42 }
        assertEquals(42, result)
    }

    @Test
    fun `single failure does not open circuit`() = runTest {
        val cb = breaker(threshold = 3)
        fail(cb)
        // Still works after one failure
        val result = cb.execute { "ok" }
        assertEquals("ok", result)
    }

    // ─── OPEN state ───────────────────────────────────────────────────────────

    @Test
    fun `circuit opens after reaching failure threshold`() = runTest {
        val cb = breaker(threshold = 2)
        fail(cb)
        fail(cb)

        // Now open — should throw without calling the block
        val ex = assertFailsWith<SdkError.CircuitOpen> {
            cb.execute<Unit> { error("should not be called") }
        }
        assertIs<SdkError.CircuitOpen>(ex)
    }

    @Test
    fun `successful call resets failure count`() = runTest {
        val cb = breaker(threshold = 3)
        fail(cb)
        fail(cb)
        // Success resets counter
        cb.execute { "ok" }
        // Two more failures should not open (counter was reset)
        fail(cb)
        fail(cb)
        val result = cb.execute { "still closed" }
        assertEquals("still closed", result)
    }
}
