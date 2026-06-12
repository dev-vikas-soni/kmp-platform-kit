package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.droidunplugged.kmp_platform_kit.shared.utils.HttpHeaders

class TokenManager(private val refreshProvider: TokenRefreshProvider?) {
    private val mutex = Mutex()

    suspend fun getValidToken(): String? {
        val currentToken = PlatformConfig.getHeader(HttpHeaders.AUTHORIZATION)
        if (!currentToken.isNullOrBlank()) return currentToken
        return refreshToken()
    }

    suspend fun notifyUnauthorized(): String? {
        return refreshToken()
    }

    private suspend fun refreshToken(): String? {
        if (refreshProvider == null) return null
        
        return mutex.withLock {
            // Double-check inside lock
            val tokenBefore = PlatformConfig.getHeader(HttpHeaders.AUTHORIZATION)
            
            try {
                val newToken = refreshProvider.refreshToken()
                if (newToken != null) {
                    PlatformConfig.setHeader(HttpHeaders.AUTHORIZATION, "Bearer $newToken")
                } else {
                    PlatformConfig.clear()
                }
                newToken
            } catch (e: Exception) {
                null
            }
        }
    }
}

interface TokenRefreshProvider {
    suspend fun refreshToken(): String?
}
