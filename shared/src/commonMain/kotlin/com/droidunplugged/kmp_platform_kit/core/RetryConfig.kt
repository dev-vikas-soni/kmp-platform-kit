package com.droidunplugged.kmp_platform_kit.core

/**
 * Configures retry behavior for transient HTTP failures.
 *
 * Passed to [KtorApiClient] via Koin DI. Host apps can override
 * defaults through [SDKInitializer.init] by providing a custom module.
 *
 * ## What is retried
 * - HTTP `5xx` server errors
 * - network I/O failures (timeouts, DNS, connectivity)
 *
 * ## What is NOT retried
 * - HTTP `4xx` client errors
 * - coroutine cancellation
 *
 * ## Backoff model
 * Retry delay starts at [initialBackoffMs] and doubles after each failed attempt.
 * Example with defaults:
 *
 * ```text
 * Attempt 1 → fail
 * wait 500 ms
 * Attempt 2 → fail
 * wait 1000 ms
 * Attempt 3 → final result returned
 * ```
 *
 * ## Example override
 * ```kotlin
 * val retryModule = module {
 *     single { RetryConfig(maxAttempts = 5, initialBackoffMs = 1_000) }
 * }
 *
 * SDKInitializer.init(
 *     baseUrl = baseUrl,
 *     authToken = token,
 *     apiGuid = guid,
 *     clientId = clientId,
 *     apiKey = apiKey,
 *     additionalModules = listOf(retryModule)
 * )
 * ```
 *
 * @property maxAttempts  Total attempts (1 = no retry). Default: 3.
 * @property initialBackoffMs Initial delay before the first retry (ms). Doubles on each subsequent retry. Default: 500.
 * @throws IllegalArgumentException if [maxAttempts] or [initialBackoffMs] is outside the supported range.
 */
data class RetryConfig(
    val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    val initialBackoffMs: Long = DEFAULT_INITIAL_BACKOFF_MS
) {
    init {
        require(maxAttempts in 1..10) { "maxAttempts must be between 1 and 10, was $maxAttempts" }
        require(initialBackoffMs in 0..30_000) { "initialBackoffMs must be between 0 and 30000, was $initialBackoffMs" }
    }

    companion object {
        /** Default total attempts (first attempt + 2 retries). */
        const val DEFAULT_MAX_ATTEMPTS = 3

        /** Default first retry delay in milliseconds. */
        const val DEFAULT_INITIAL_BACKOFF_MS = 500L

        /** No retries - fail immediately on the first error. */
        val NO_RETRY = RetryConfig(maxAttempts = 1, initialBackoffMs = 0)
    }
}