package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val recoveryTimeout: Duration = 30.seconds
) {
    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val mutex = Mutex()
    private var state = State.CLOSED
    private var failureCount = 0
    private var lastFailureTime: Instant? = null

    suspend fun <T> execute(action: suspend () -> T): T {
        mutex.withLock {
            when (state) {
                State.OPEN -> {
                    val age = Clock.System.now() - (lastFailureTime ?: Clock.System.now())
                    if (age.compareTo(recoveryTimeout) >= 0) {
                        state = State.HALF_OPEN
                    } else {
                        throw IllegalStateException("Circuit breaker is OPEN. Fast failing.")
                    }
                }
                else -> {}
            }
        }

        return try {
            val result = action()
            onSuccess()
            result
        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private suspend fun onSuccess() {
        mutex.withLock {
            state = State.CLOSED
            failureCount = 0
            lastFailureTime = null
        }
    }

    private suspend fun onFailure() {
        mutex.withLock {
            failureCount++
            lastFailureTime = Clock.System.now()
            if (failureCount >= failureThreshold) {
                state = State.OPEN
            }
        }
    }
}
