package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Deduplicates in-flight coroutine requests by a string key.
 *
 * When two callers invoke the same request simultaneously (e.g. two ViewModels
 * both calling `getInventories("123")`), only **one** network call is made.
 * The second caller suspends and shares the result of the first.
 *
 * ## How it works
 * 1. First caller → no entry in [inFlight] → creates a [CompletableDeferred], registers it, executes block.
 * 2. Concurrent caller → entry exists → awaits the existing [CompletableDeferred].
 * 3. On completion (success or exception) → entry is removed; next call starts fresh.
 *
 * **Coroutine-safe:** [Mutex] guards the map; [CompletableDeferred] propagates results cross-coroutine.
 *
 * Internal to the SDK - host apps benefit transparently via the facade.
 */
internal class RequestDeduplicator {

    private val mutex = Mutex()

    @Suppress("UNCHECKED_CAST")
    private val inFlight = mutableMapOf<String, CompletableDeferred<*>>()

    /**
     * Executes [block] for [key], or awaits an in-flight execution with the same key.
     *
     * @param key    Unique identifier for this request (e.g. full URL).
     * @param block  The suspend function to deduplicate.
     * @return The result of [block] - shared across all concurrent callers with the same [key].
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> deduplicate(key: String, block: suspend () -> T): T {
        // Check if there's an already in-flight deferred for this key
        val existingDeferred = mutex.withLock { inFlight[key] as? CompletableDeferred<T> }
        if (existingDeferred != null) return existingDeferred.await()

        // Create and register a new deferred
        val deferred = CompletableDeferred<T>()
        val isOwner = mutex.withLock {
            if (inFlight.containsKey(key)) {
                false  // Another coroutine beat us to it
            } else {
                inFlight[key] = deferred
                true
            }
        }

        if (!isOwner) {
            // Lost the race - await the winner's deferred
            val winner = mutex.withLock { inFlight[key] as? CompletableDeferred<T> }
            return winner?.await() ?: block()  // fallback if winner already cleaned up
        }

        return try {
            val result = block()
            deferred.complete(result)
            result
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            deferred.completeExceptionally(t)
            throw t
        } finally {
            mutex.withLock { inFlight.remove(key) }
        }
    }

    /** Cancels all in-flight requests - called from [SDKInitializer.reset]. */
    fun cancelAll() {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
    }
}