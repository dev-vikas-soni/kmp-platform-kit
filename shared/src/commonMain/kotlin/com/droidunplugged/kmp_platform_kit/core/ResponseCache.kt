package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

class ResponseCache {
    private val mutex = Mutex()
    private val store = mutableMapOf<String, CacheEntry>()

    data class CacheEntry(val response: Any, val timestamp: Instant)

    suspend fun put(key: String, response: Any) {
        mutex.withLock {
            store[key] = CacheEntry(response, Clock.System.now())
        }
    }

    suspend fun get(key: String, policy: CachePolicy): Any? {
        if (policy.strategy == CacheStrategy.NETWORK_ONLY) return null
        
        return mutex.withLock {
            val entry = store[key] ?: return@withLock null
            val age = Clock.System.now() - entry.timestamp
            
            if (age.compareTo(policy.maxAge) <= 0) entry.response else {
                store.remove(key)
                null
            }
        }
    }

    suspend fun clear() {
        mutex.withLock { store.clear() }
    }
}
