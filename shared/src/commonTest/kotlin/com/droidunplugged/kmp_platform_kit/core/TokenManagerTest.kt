package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.auth.TokenManager
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [com.droidunplugged.kmp_platform_kit.core.auth.TokenManager].
 *
 * We use a fake [PlatformConfig] by toggling actual header state since
 * [PlatformConfig] is the underlying store that [com.droidunplugged.kmp_platform_kit.core.auth.TokenManager] reads from.
 */
class TokenManagerTest {

    @BeforeTest
    fun setUp() {
        // Clear any header state between tests
        PlatformConfig.clear()
    }

    // ─── getValidToken ─────────────────────────────────────────────────────────

    @Test
    fun `getValidToken returns null when no token set and no refresh provider`() = runTest {
        val manager = TokenManager(refreshProvider = null)
        val token = manager.getValidToken()
        assertNull(token)
    }

    @Test
    fun `getValidToken returns stored token from PlatformConfig`() = runTest {
        PlatformConfig.setHeader("authorization", "Bearer abc123")
        val manager = TokenManager(refreshProvider = null)
        val token = manager.getValidToken()
        assertEquals("abc123", token)
    }

    @Test
    fun `getValidToken invokes refresh provider when no token is present`() = runTest {
        var providerCalled = false
        val provider = com.droidunplugged.kmp_platform_kit.core.auth.TokenRefreshProvider {
            providerCalled = true
            "refreshed-token"
        }
        val manager = TokenManager(refreshProvider = provider)
        val token = manager.getValidToken()
        assertEquals("refreshed-token", token)
        assertEquals(true, providerCalled)
    }

    @Test
    fun `notifyUnauthorized clears the stored authorization header`() = runTest {
        PlatformConfig.setHeader("authorization", "Bearer stale")
        val manager = TokenManager(refreshProvider = null)
        manager.notifyUnauthorized()
        assertNull(PlatformConfig.getHeader("authorization"))
    }
}
