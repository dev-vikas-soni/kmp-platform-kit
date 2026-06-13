package com.droidunplugged.kmp_platform_kit.core.auth

import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// ── Token result ──────────────────────────────────────────────────────────────

/**
 * Result of a token refresh attempt provided by the host app.
 *
 * @see TokenRefreshProvider
 */
sealed class TokenResult {
    /**
     * Refresh succeeded.
     *
     * @property newToken        The new JWT Bearer token.
     * @property expiresIn       How long until this token expires.
     */
    data class Success(
        val newToken: String,
        val expiresIn: Duration
    ) : TokenResult()

    /**
     * Refresh failed - but the user is still considered logged in.
     * The SDK will retry with exponential backoff.
     */
    data class RefreshFailed(val reason: String? = null) : TokenResult()

    /**
     * The user logged out or the session is permanently invalid.
     * The SDK will call `SDKInitializer.reset()` automatically.
     */
    data object UserLoggedOut : TokenResult()
}

// ── Provider interface ────────────────────────────────────────────────────────

/**
 * Contract the host app implements to provide token refresh capability to the SDK.
 *
 * Register via [com.droidunplugged.kmp_platform_kit.core.SDKInitializer.setTokenRefreshProvider].
 *
 * ## Android example
 * ```kotlin
 * class MyTokenRefreshProvider(private val authRepository: AuthRepository) : TokenRefreshProvider {
 *     override suspend fun refreshToken(expiredToken: String): TokenResult {
 *         return try {
 *             val response = authRepository.refresh()
 *             TokenResult.Success(
 *                 newToken = response.accessToken,
 *                 expiresIn = response.expiresIn.seconds
 *             )
 *         } catch (e: SessionExpiredException) {
 *             TokenResult.UserLoggedOut
 *         } catch (e: Exception) {
 *             TokenResult.RefreshFailed(reason = e.message)
 *         }
 *     }
 * }
 *
 * SDKInitializer.setTokenRefreshProvider(MyTokenRefreshProvider(authRepo))
 * SDKInitializer.init(...)
 * ```
 *
 * ## iOS example
 * ```swift
 * class TokenRefreshProviderImpl : TokenRefreshProvider {
 *     func refreshToken(expiredToken: String) async throws -> TokenResult {
 *         let response = try await AuthService.shared.refresh()
 *         return TokenResultSuccess(newToken: response.token, expiresIn: .seconds(response.expiresIn))
 *     }
 * }
 * ```
 */
interface TokenRefreshProvider {
    /**
     * Called by the SDK when the current token is about to expire or a 401 is received.
     *
     * This method is called on a background coroutine - it is safe to perform network I/O.
     * Must not return until the refresh is complete (success or failure).
     *
     * @param expiredToken The token that needs refreshing (for PKCE / rotation flows).
     * @return [TokenResult.Success] with the new token, [TokenResult.RefreshFailed], or
     *         [TokenResult.UserLoggedOut] if the session is permanently invalid.
     */
    suspend fun refreshToken(expiredToken: String): TokenResult
}

// ── Configuration ─────────────────────────────────────────────────────────────

/**
 * Configuration for [TokenManager] behaviour.
 *
 * @property proactiveRefreshBuffer Refresh the token this long before it expires.
 *           Default: 60 seconds. Set to `Duration.ZERO` to disable proactive refresh.
 * @property refreshMaxRetries      Max number of retry attempts for a failed refresh.
 * @property refreshRetryBackoffMs  Initial backoff before the first refresh retry (doubles each time).
 */
data class TokenConfig(
    val proactiveRefreshBuffer: Duration = 60.seconds,
    val refreshMaxRetries: Int = 3,
    val refreshRetryBackoffMs: Long = 1_000L
)

// ── Token Manager ─────────────────────────────────────────────────────────────

/**
 * Manages the full lifecycle of the SDK's auth token.
 *
 * ## Proactive refresh
 * When the host app provides an expiry hint (via [TokenResult.Success.expiresIn]),
 * the manager schedules a background coroutine to refresh the token
 * [TokenConfig.proactiveRefreshBuffer] before it expires.
 *
 * ## Reactive refresh (401 handling)
 * When the HTTP client receives a 401, it calls [onUnauthorized]. The manager
 * refreshes the token once and notifies via [onTokenRefreshed] so the original
 * request can be replayed.
 *
 * ## Coalescing
 * If 50 concurrent requests all receive a 401 simultaneously, only **one** refresh
 * call is issued. All 50 requests wait for the single refresh to complete, then
 * replay with the new token.
 *
 * Internal to the SDK - host apps interact only via [TokenRefreshProvider].
 */
internal class TokenManager(
    private val provider: TokenRefreshProvider,
    private val config: TokenConfig = TokenConfig(),
    /** Called when a new token is available - updates PlatformConfig. */
    private val onTokenRefreshed: (newToken: String) -> Unit,
    /** Called when the user logs out during refresh - triggers SDK reset. */
    private val onUserLoggedOut: () -> Unit
) {
    private val log get() = PlatformLogger.get()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val refreshMutex = Mutex()

    @Volatile
    private var currentToken: String = ""

    @Volatile
    private var proactiveRefreshJob: Job? = null

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Register the current token and schedule a proactive refresh if [expiresIn] is set.
     */
    fun onTokenAcquired(token: String, expiresIn: Duration? = null) {
        currentToken = token
        scheduleProactiveRefresh(expiresIn)
    }

    /**
     * Called by the HTTP interceptor when a 401 Unauthorized is received.
     *
     * Coalesces concurrent 401s into a single refresh call.
     *
     * @return `true` if the token was refreshed successfully and the request should be replayed.
     *         `false` if the session is invalid and the user must log in.
     */
    suspend fun onUnauthorized(): Boolean = refreshMutex.withLock {
        log.d(TAG, "401 received - attempting token refresh")
        return@withLock attemptRefresh()
    }

    /**
     * Cancel the proactive refresh job and release all coroutines.
     * Called from SDKInitializer.reset().
     */
    fun cancel() {
        proactiveRefreshJob?.cancel()
        scope.cancel()
        log.d(TAG, "TokenManager cancelled")
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun scheduleProactiveRefresh(expiresIn: Duration?) {
        if (expiresIn == null || config.proactiveRefreshBuffer == Duration.ZERO) return

        val refreshAt = expiresIn - config.proactiveRefreshBuffer
        if (refreshAt.isNegative()) {
            log.w(TAG, "Token expires in ${expiresIn.inWholeSeconds}s - less than buffer, refreshing now")
            scope.launch { refreshMutex.withLock { attemptRefresh() } }
            return
        }

        proactiveRefreshJob?.cancel()
        proactiveRefreshJob = scope.launch {
            log.d(TAG, "Proactive refresh scheduled in ${refreshAt.inWholeSeconds}s")
            delay(refreshAt)
            refreshMutex.withLock { attemptRefresh() }
        }
    }

    /**
     * Calls [TokenRefreshProvider.refreshToken] with exponential backoff.
     * **Must be called while [refreshMutex] is held.**
     *
     * @return `true` if refresh succeeded, `false` if user logged out.
     */
    private suspend fun attemptRefresh(): Boolean {
        var attempt = 0
        var backoffMs = config.refreshRetryBackoffMs

        while (attempt < config.refreshMaxRetries) {
            if (attempt > 0) {
                log.d(TAG, "Refresh retry $attempt/${config.refreshMaxRetries} after ${backoffMs}ms")
                delay(backoffMs)
                backoffMs = minOf(backoffMs * 2, MAX_BACKOFF_MS)
            }

            when (val result = provider.refreshToken(currentToken)) {
                is TokenResult.Success -> {
                    log.i(TAG, "Token refreshed successfully (expires in ${result.expiresIn.inWholeSeconds}s)")
                    currentToken = result.newToken
                    onTokenRefreshed(result.newToken)
                    scheduleProactiveRefresh(result.expiresIn)
                    return true
                }

                is TokenResult.RefreshFailed -> {
                    log.w(TAG, "Refresh attempt ${attempt + 1} failed: ${result.reason}")
                    attempt++
                }

                TokenResult.UserLoggedOut -> {
                    log.w(TAG, "Token refresh indicates user logged out - triggering SDK reset")
                    onUserLoggedOut()
                    return false
                }
            }
        }

        log.e(TAG, "All $config.refreshMaxRetries refresh attempts failed")
        return false
    }

    companion object {
        private const val TAG = "TokenManager"

        /** Maximum backoff ceiling for token refresh retries (ms). */
        private const val MAX_BACKOFF_MS = 30_000L
    }
}