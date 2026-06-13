package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

// ── Cache strategy ────────────────────────────────────────────────────────────

/**
 * Determines how the SDK resolves a request against the cache.
 *
 * | Strategy       | Behaviour                                                         |
 * |----------------|-------------------------------------------------------------------|
 * | NETWORK_FIRST  | Always fetch fresh; cache only used when network fails (default) |
 * | CACHE_FIRST    | Serve cached data if not expired; fall back to network           |
 * | CACHE_ONLY     | Never go to network; return [ApiResult.Failure] if not cached    |
 * | NETWORK_ONLY   | Always fetch; never read or write the cache                      |
 */
enum class CacheStrategy {
    NETWORK_FIRST,
    CACHE_FIRST,
    CACHE_ONLY,
    NETWORK_ONLY
}

/**
 * Configures caching behaviour for a single API call.
 *
 * Pass a [CachePolicy] to any `ApiClient` call to override the global default.
 *
 * ```kotlin
 * val result = apiClient.get(
 *     path = "...",
 *     responseParser = { ... },
 *     cachePolicy = CachePolicy(
 *         strategy = CacheStrategy.CACHE_FIRST,
 *         maxAge   = 5.minutes
 *     )
 * )
 * ```
 *
 * @property strategy   How cache vs network should be prioritised.
 * @property maxAge     How long a cached entry is considered fresh. Default: 5 minutes.
 * @property staleWhileRevalidate  When `true`, return stale data immediately and
 *           refresh in the background (not yet implemented - reserved for future use).
 */
data class CachePolicy(
    val strategy: CacheStrategy = CacheStrategy.NETWORK_FIRST,
    val maxAge: Duration = DEFAULT_MAX_AGE,
    val staleWhileRevalidate: Boolean = false
) {
    companion object {
        val DEFAULT_MAX_AGE: Duration = 5.minutes

        /** Convenience: never cache (always go to network). */
        val NO_CACHE = CachePolicy(strategy = CacheStrategy.NETWORK_ONLY)

        /** Convenience: cache-first with 5-minute freshness window. */
        val CACHE_FIRST_5MIN = CachePolicy(strategy = CacheStrategy.CACHE_FIRST)

        /** Convenience: network-first (SDK default). */
        val DEFAULT = CachePolicy(strategy = CacheStrategy.NETWORK_FIRST)
    }
}

// ── In-memory cache ──────────────────────────────────────────────────────────

/**
 * Thread-safe in-process response cache - compatible with both JVM (Android) and Kotlin/Native (iOS).
 *
 * Stores serialised response strings keyed by the full request URL.
 * Entries expire after their configured [CachePolicy.maxAge].
 *
 * **Lifetime:** cleared entirely on [SDKInitializer.reset].
 *
 * Internal to the SDK - host apps interact via facade parameters only.
 *
 * **Concurrency:** every read **and** write is guarded by a [Mutex] so there
 * are no data races on either JVM (Android) or Kotlin/Native (iOS).
 * The previous implementation left [put], [invalidate], [clear], and [size]
 * completely unguarded - a data race / ConcurrentModificationException hazard.
 */
internal class ResponseCache {

    private data class Entry(
        val value: String,
        val cachedAt: TimeSource.Monotonic.ValueTimeMark
    )

    private val store = mutableMapOf<String, Entry>()
    private val mutex = kotlinx.coroutines.sync.Mutex()

    /**
     * Returns the cached raw response string for [key] if it exists and is not older
     * than [maxAge]. Returns `null` if there is no entry or the entry has expired.
     */
    suspend fun get(key: String, maxAge: Duration): String? = mutex.withLock {
        val entry = store[key] ?: return@withLock null
        if (entry.cachedAt.elapsedNow() <= maxAge) entry.value else null
    }

    /** Stores [value] under [key], overwriting any existing entry. */
    suspend fun put(key: String, value: String) = mutex.withLock {
        store[key] = Entry(value = value, cachedAt = TimeSource.Monotonic.markNow())
    }

    /** Removes a single entry - useful for explicit invalidation after mutations. */
    suspend fun invalidate(key: String): Unit = mutex.withLock {
        store.remove(key)
    }

    /** Removes all cached entries - called on [SDKInitializer.reset]. */
    suspend fun clear() = mutex.withLock {
        store.clear()
    }

    /** Number of currently stored entries (for diagnostics / health checks). */
    suspend fun size(): Int = mutex.withLock { store.size }
}