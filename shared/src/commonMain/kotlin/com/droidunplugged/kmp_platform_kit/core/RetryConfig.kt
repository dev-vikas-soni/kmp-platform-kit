package com.droidunplugged.kmp_platform_kit.core

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

data class RetryConfig(
    val maxAttempts: Int = 3,
    val initialDelay: Duration = 500.milliseconds,
    val maxDelay: Duration = 10000.milliseconds,
    val backoffFactor: Double = 2.0
) {
    fun delayForAttempt(attempt: Int): Duration {
        if (attempt <= 1) return initialDelay
        val delayMs = initialDelay.inWholeMilliseconds * backoffFactor.pow(attempt - 1.0)
        return minOf(delayMs.toLong(), maxDelay.inWholeMilliseconds).milliseconds
    }
}
