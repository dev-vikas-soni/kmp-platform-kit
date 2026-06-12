package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.config.SdkEnvironment
import com.droidunplugged.kmp_platform_kit.core.config.SdkRemoteConfigProvider
import com.droidunplugged.kmp_platform_kit.core.interceptor.SdkRequestInterceptor
import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SDKCredentials(
    val environment: SdkEnvironment,
    val authToken: String?,
    val apiGuid: String?
) {
    override fun toString(): String = "SDKCredentials(environment=${environment.id}, authToken=***, apiGuid=***)"
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SDKCredentials) return false
        return environment == other.environment &&
               authToken == other.authToken &&
               apiGuid == other.apiGuid
    }
    
    override fun hashCode(): Int {
        var result = environment.hashCode()
        result = 31 * result + (authToken?.hashCode() ?: 0)
        result = 31 * result + (apiGuid?.hashCode() ?: 0)
        return result
    }
}

object SDKInitializer {
    private val mutex = Mutex()
    private var isInitialized = false
    
    private val interceptors = mutableListOf<SdkRequestInterceptor>()
    private val plugins = mutableListOf<SDKPlugin>()
    private var telemetry: SDKTelemetry = NoOpTelemetry
    private var tokenRefreshProvider: TokenRefreshProvider? = null
    private var remoteConfigProvider: SdkRemoteConfigProvider? = null

    suspend fun init(credentials: SDKCredentials) {
        mutex.withLock {
            if (isInitialized) return
            
            PlatformConfig.setHeader("clientid", credentials.environment.clientId)
            PlatformConfig.setHeader("x-api-key", credentials.environment.apiKey)
            credentials.authToken?.let { PlatformConfig.setHeader("authorization", "Bearer $it") }
            credentials.apiGuid?.let { PlatformConfig.setHeader("x-cah-api-guid", it) }
            
            plugins.forEach { it.onSDKInitialized() }
            telemetry.recordSdkEvent(SDKEvent.INITIALIZED)
            
            isInitialized = true
            PlatformLogger.i("SDKInitializer", "SDK Initialized successfully.")
        }
    }

    suspend fun reset() {
        mutex.withLock {
            PlatformConfig.clear()
            plugins.forEach { it.onSDKReset() }
            telemetry.recordSdkEvent(SDKEvent.RESET)
            isInitialized = false
            PlatformLogger.i("SDKInitializer", "SDK Reset complete.")
        }
    }

    fun addInterceptor(interceptor: SdkRequestInterceptor) { interceptors.add(interceptor) }
    fun registerPlugin(plugin: SDKPlugin) { plugins.add(plugin) }
    fun setTelemetry(t: SDKTelemetry) { telemetry = t }
    fun setTokenRefreshProvider(p: TokenRefreshProvider) { tokenRefreshProvider = p }
    fun setRemoteConfigProvider(p: SdkRemoteConfigProvider) { remoteConfigProvider = p }
    
    fun requireInitialized() {
        check(isInitialized) { "SDK is not initialized. Call SDKInitializer.init() first." }
    }
}
