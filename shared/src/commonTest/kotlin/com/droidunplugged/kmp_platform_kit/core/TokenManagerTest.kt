package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.auth.TokenManager
import com.droidunplugged.kmp_platform_kit.core.auth.TokenRefreshProvider
import com.droidunplugged.kmp_platform_kit.core.auth.TokenResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class TokenManagerTest {

    private class MockTokenRefreshProvider : TokenRefreshProvider {
        var refreshResult: TokenResult = TokenResult.RefreshFailed("No result set")
        var callCount = 0

        override suspend fun refreshToken(expiredToken: String): TokenResult {
            callCount++
            return refreshResult
        }
    }

    @Test
    fun `onUnauthorized triggers refresh via provider`() = runTest {
        val provider = MockTokenRefreshProvider()
        provider.refreshResult = TokenResult.Success("new-token", 60.seconds)

        var refreshedToken: String? = null
        val manager = TokenManager(
            provider = provider,
            onTokenRefreshed = { refreshedToken = it },
            onUserLoggedOut = {}
        )

        val result = manager.onUnauthorized()

        assertEquals(true, result)
        assertEquals(1, provider.callCount)
        assertEquals("new-token", refreshedToken)
    }

    @Test
    fun `onUnauthorized returns false when refresh fails`() = runTest {
        val provider = MockTokenRefreshProvider()
        provider.refreshResult = TokenResult.RefreshFailed("Network error")

        val manager = TokenManager(
            provider = provider,
            onTokenRefreshed = {},
            onUserLoggedOut = {}
        )

        val result = manager.onUnauthorized()

        assertEquals(false, result)
        // TokenManager retries 3 times by default
        assertEquals(3, provider.callCount)
    }

    @Test
    fun `onUnauthorized triggers logout when provider returns UserLoggedOut`() = runTest {
        val provider = MockTokenRefreshProvider()
        provider.refreshResult = TokenResult.UserLoggedOut

        var logoutCalled = false
        val manager = TokenManager(
            provider = provider,
            onTokenRefreshed = {},
            onUserLoggedOut = { logoutCalled = true }
        )

        val result = manager.onUnauthorized()

        assertEquals(false, result)
        assertEquals(1, provider.callCount)
        assertEquals(true, logoutCalled)
    }
}
