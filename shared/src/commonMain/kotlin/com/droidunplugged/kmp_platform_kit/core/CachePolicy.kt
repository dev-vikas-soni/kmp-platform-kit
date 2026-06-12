package com.droidunplugged.kmp_platform_kit.core

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

enum class CacheStrategy {
    NETWORK_ONLY,
    NETWORK_FIRST,
    CACHE_FIRST
}

data class CachePolicy(
    val strategy: CacheStrategy = CacheStrategy.NETWORK_FIRST,
    val maxAge: Duration = 5.minutes
) {
    companion object {
        val DEFAULT = CachePolicy()
        val CACHE_FIRST_5MIN = CachePolicy(strategy = CacheStrategy.CACHE_FIRST)
        val NO_CACHE = CachePolicy(strategy = CacheStrategy.NETWORK_ONLY)
    }
}
