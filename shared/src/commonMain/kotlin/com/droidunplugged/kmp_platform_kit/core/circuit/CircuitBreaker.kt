package com.droidunplugged.kmp_platform_kit.core.circuit

import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Configuration for [CircuitBreaker].
 *
 * @property failureThreshold    Number of consecutive failures before the circuit opens. Default: 5.
 * @property successThreshold    Number of consecutive successes in HALF_OPEN before the circuit closes. Default: 2.
 * @property openDuration        How long the circuit stays OPEN before transitioning to HALF_OPEN. Default: 30s.
 */
data class CircuitBreakerConfig(
    val failureThreshold: Int = 5,
    val successThreshold: Int = 2,
    val openDuration: Duration = 30.seconds
) {
    init {
        require(failureThreshold in 1..100) { "failureThreshold must be 1..100" }
        require(successThreshold in 1..100) { "successThreshold must be 1..100" }
    }
}

/**
 * Exception thrown when a call is attempted while the circuit is OPEN.
 */
class CircuitOpenException(
    val endpoint: String,
    val retryAfterMs: Long
) : Exception("Circuit breaker OPEN for '$endpoint'. Retry after ${retryAfterMs}ms.")

/**
 * Coroutine-safe Circuit Breaker for SDK HTTP calls.
 *
 * Prevents hammering a degraded backend by temporarily short-circuiting requests
 * when too many consecutive failures are detected.
 *
 * ## States
 * ```
 *  ┌──────────┐  failureThreshold   ┌──────────┐
 *  │  CLOSED  │─────────────────────│   OPEN   │
 *  │ (normal) │                     │ (blocked)│
 *  └──────────┘                     └──────────┘
 *       ▲           openDuration         │
 *       │           ────────────►        │
 *  ┌──────────┐                     ┌──────────┐
 *  │  CLOSED  │◄────────────────────│HALF_OPEN │
 *  │          │  successThreshold   │  (probe) │
 *  └──────────┘                     └──────────┘
 * ```
 *
 * ## Usage
 * ```kotlin
 * val circuitBreaker = CircuitBreaker("inventories", config)
 *
 * val result = circuitBreaker.execute {
 *     apiClient.get(path = "/inventories/...")
 * }
 * ```
 *
 * ## Integration
 * Register via `SDKInitializer.setCircuitBreakerConfig(config)` - the SDK wires
 * a shared [CircuitBreaker] per feature endpoint automatically.
 */
class CircuitBreaker(
    val name: String,
    private val config: CircuitBreakerConfig = CircuitBreakerConfig()
) {
    private val tag = "CircuitBreaker[$name]"
    private val log get() = PlatformLogger.get()
    private val clock = TimeSource.Monotonic

    enum class State { CLOSED, OPEN, HALF_OPEN }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(State.CLOSED)

    /** Observable circuit state - host apps can react to OPEN/CLOSED transitions. */
    val state: StateFlow<State> = _state.asStateFlow()

    private var consecutiveFailures = 0
    private var consecutiveSuccesses = 0
    private var openedAt: TimeSource.Monotonic.ValueTimeMark? = null

    // ── Execute ───────────────────────────────────────────────────────────

    /**
     * Executes [block] subject to the circuit breaker logic.
     *
     * - **CLOSED:** Passes through. Records success/failure.
     * - **OPEN:** Throws [CircuitOpenException] immediately without calling [block].
     * - **HALF_OPEN:** Allows one probe call. Closes on success, reopens on failure.
     *
     * @throws CircuitOpenException if the circuit is OPEN.
     * @throws Throwable any exception thrown by [block].
     */
    suspend fun <T> execute(block: suspend () -> T): T {
        checkCircuitState()

        return try {
            val result = block()
            recordSuccess()
            result
        } catch (e: CircuitOpenException) {
            throw e
        } catch (e: Exception) {
            recordFailure()
            throw e
        }
    }

    /** Manually reset the circuit to CLOSED state (e.g. after confirming server health). */
    suspend fun reset(): Unit = mutex.withLock {
        consecutiveFailures = 0
        consecutiveSuccesses = 0
        openedAt = null
        _state.value = State.CLOSED
        log.i(tag, "Circuit manually reset to CLOSED")
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private suspend fun checkCircuitState(): Unit = mutex.withLock {
        when (_state.value) {
            State.CLOSED -> { /* allow through */
            }

            State.OPEN -> {
                val elapsed = openedAt?.elapsedNow() ?: config.openDuration
                if (elapsed >= config.openDuration) {
                    log.d(tag, "Circuit transitioning OPEN → HALF_OPEN after ${elapsed.inWholeSeconds}s")
                    _state.value = State.HALF_OPEN
                    consecutiveSuccesses = 0
                } else {
                    val retryAfterMs = (config.openDuration - elapsed).inWholeMilliseconds
                    log.w(tag, "Circuit OPEN - failing fast (retry in ${retryAfterMs}ms)")
                    throw CircuitOpenException(name, retryAfterMs)
                }
            }

            State.HALF_OPEN -> { /* allow probe through */
            }
        }
    }

    private suspend fun recordSuccess(): Unit = mutex.withLock {
        when (_state.value) {
            State.HALF_OPEN -> {
                consecutiveSuccesses++
                log.d(tag, "HALF_OPEN success $consecutiveSuccesses/${config.successThreshold}")
                if (consecutiveSuccesses >= config.successThreshold) {
                    consecutiveFailures = 0
                    consecutiveSuccesses = 0
                    openedAt = null
                    _state.value = State.CLOSED
                    log.i(tag, "Circuit CLOSED - backend recovered")
                }
            }

            State.CLOSED -> {
                if (consecutiveFailures > 0) {
                    consecutiveFailures = 0
                    log.d(tag, "Consecutive failure streak reset")
                }
            }

            State.OPEN -> { /* shouldn't happen, but no-op */
            }
        }
    }

    private suspend fun recordFailure(): Unit = mutex.withLock {
        when (_state.value) {
            State.CLOSED -> {
                consecutiveFailures++
                log.w(tag, "Failure $consecutiveFailures/${config.failureThreshold}")
                if (consecutiveFailures >= config.failureThreshold) {
                    openedAt = clock.markNow()
                    _state.value = State.OPEN
                    log.e(tag, "Circuit OPEN after $consecutiveFailures consecutive failures")
                }
            }

            State.HALF_OPEN -> {
                log.w(tag, "HALF_OPEN probe failed - reopening circuit")
                openedAt = clock.markNow()
                consecutiveSuccesses = 0
                _state.value = State.OPEN
            }

            State.OPEN -> { /* already open */
            }
        }
    }
}