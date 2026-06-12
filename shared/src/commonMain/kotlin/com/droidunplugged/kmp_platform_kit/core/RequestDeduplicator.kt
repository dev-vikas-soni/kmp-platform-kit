package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RequestDeduplicator {
    private val mutex = Mutex()
    private val inFlightRequests = mutableMapOf<String, CompletableDeferred<Any>>()

    suspend fun <T : Any> deduplicate(key: String, action: suspend () -> T): T {
        val deferred = mutex.withLock {
            inFlightRequests[key]?.let { return@withLock it }
            
            CompletableDeferred<Any>().also { inFlightRequests[key] = it }
        }

        // If we received an existing deferred, await it
        if (deferred.isCompleted || inFlightRequests[key] !== deferred) {
            @Suppress("UNCHECKED_CAST")
            return deferred.await() as T
        }

        // We are the ones executing
        return try {
            val result = action()
            deferred.complete(result)
            result
        } catch (e: Exception) {
            deferred.completeExceptionally(e)
            throw e
        } finally {
            mutex.withLock {
                inFlightRequests.remove(key)
            }
        }
    }
}
