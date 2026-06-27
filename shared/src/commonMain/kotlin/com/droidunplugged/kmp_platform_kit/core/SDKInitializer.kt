package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.configure
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.credentialProvider
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.ensureInitialized
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.init
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.initMutex
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.initUnderLock
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer.reset
import com.droidunplugged.kmp_platform_kit.core.auth.TokenManager
import com.droidunplugged.kmp_platform_kit.core.auth.TokenRefreshProvider
import com.droidunplugged.kmp_platform_kit.core.circuit.CircuitBreakerConfig
import com.droidunplugged.kmp_platform_kit.core.config.SdkEnvironment
import com.droidunplugged.kmp_platform_kit.core.config.SdkRemoteConfigProvider
import com.droidunplugged.kmp_platform_kit.core.config.SdkRemoteConfigStore
import com.droidunplugged.kmp_platform_kit.core.di.BASE_URL_QUALIFIER
import com.droidunplugged.kmp_platform_kit.core.di.FeatureModules
import com.droidunplugged.kmp_platform_kit.core.di.coreModule
import com.droidunplugged.kmp_platform_kit.core.interceptor.SdkInterceptorRegistry
import com.droidunplugged.kmp_platform_kit.core.interceptor.SdkRequestInterceptor
import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.koin.core.KoinApplication
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.concurrent.Volatile
import kotlin.time.Duration

/**
 * SDK entry point - the only class the host app needs to touch.
 *
 * Responsibilities:
 * - captures SDK credentials and environment configuration
 * - bootstraps the SDK's isolated Koin graph
 * - wires token refresh, remote config, tracing, telemetry, and plugins
 * - exposes safe runtime update hooks (`updateAuthToken`, `reset`, etc.)
 *
 * ## Two ways to initialize
 *
 * ### Option 1 - Direct init (credentials available immediately)
 * ```kotlin
 * SDKInitializer.init(baseUrl, authToken, apiGuid, clientId, apiKey)
 * ```
 *
 * ### Option 1b - Environment-based init (recommended for multi-env apps)
 * ```kotlin
 * SDKInitializer.init(environment = Environments.STAGING, authToken = token, apiGuid = guid)
 * ```
 *
 * ### Option 2 - Deferred init (credentials arrive after login)
 * ```kotlin
 * SDKInitializer.configure { SDKCredentials(baseUrl, authToken, ...) }
 * // Later:
 * SDKInitializer.ensureInitialized()
 * ```
 *
 * ## Optional extensions
 * ```kotlin
 * SDKInitializer.setTelemetry(MyTelemetryImpl())
 * SDKInitializer.setTokenRefreshProvider(MyTokenRefreshProvider())
 * SDKInitializer.setRemoteConfigProvider(MyRemoteConfigProvider())
 * SDKInitializer.setCircuitBreakerConfig(CircuitBreakerConfig(failureThreshold = 3))
 * SDKInitializer.addInterceptor(DeviceFingerprintInterceptor())
 * SDKInitializer.registerPlugin(MyFeaturePlugin())
 * ```
 *
 * ## iOS
 * ```swift
 * SDKInitializer.shared.doInit(baseUrl:authToken:apiGuid:clientId:apiKey:)
 * ```
 */
@Suppress("TooManyFunctions") // SDKInitializer is the central SDK coordinator - functions are justified
object SDKInitializer {

    private const val TAG = "SDKInitializer"

    // ── State ──────────────────────────────────────────────────────────
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false
    @Volatile
    private var credentialProvider: (() -> SDKCredentials)? = null

    @Volatile
    internal var koinApp: KoinApplication? = null
        private set

    @Volatile
    private var telemetry: SDKTelemetry = NoOpTelemetry

    @Volatile
    private var tokenRefreshProvider: TokenRefreshProvider? = null

    @Volatile
    private var tokenManager: TokenManager? = null

    @Volatile
    private var remoteConfigProvider: SdkRemoteConfigProvider? = null

    @Volatile
    private var circuitBreakerConfig: CircuitBreakerConfig = CircuitBreakerConfig()

    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Whether the SDK has been initialized. */
    val isInitialized: Boolean get() = initialized

    /** The active circuit breaker configuration. Internal - used by KtorApiClient. */
    internal val activeCircuitBreakerConfig: CircuitBreakerConfig get() = circuitBreakerConfig

    // ═══════════════════════════════════════════════════════════════════
    //  Pre-init registration APIs
    //  (all safe to call before init - no-op if called after)
    // ═══════════════════════════════════════════════════════════════════

    /** Register a telemetry provider. Call before [init]. */
    fun setTelemetry(impl: SDKTelemetry) {
        telemetry = impl
        PlatformLogger.get().d(TAG, "Telemetry registered: ${impl::class.simpleName}")
    }

    internal fun telemetry(): SDKTelemetry = telemetry

    /**
     * Register a token refresh provider for automatic token lifecycle management.
     *
     * When registered, the SDK will:
     * - Proactively refresh the token before it expires (if [tokenExpiresIn] is provided to [init])
     * - Automatically retry 401 responses by refreshing and replaying the request
     * - Coalesce concurrent 401s into a single refresh call
     *
     * Call before [init].
     *
     * ```kotlin
     * SDKInitializer.setTokenRefreshProvider(MyTokenRefreshProvider(authRepo))
     * SDKInitializer.init(...)
     * ```
     */
    fun setTokenRefreshProvider(provider: TokenRefreshProvider) {
        tokenRefreshProvider = provider
        PlatformLogger.get().d(TAG, "TokenRefreshProvider registered")
    }

    /**
     * Register a remote config provider to enable server-pushed kill-switches and tuning.
     *
     * ```kotlin
     * SDKInitializer.setRemoteConfigProvider(MyRemoteConfigProvider(configApi))
     * SDKInitializer.init(...)
     * ```
     */
    fun setRemoteConfigProvider(provider: SdkRemoteConfigProvider) {
        remoteConfigProvider = provider
        PlatformLogger.get().d(TAG, "RemoteConfigProvider registered")
    }

    /**
     * Override the default [CircuitBreakerConfig].
     *
     * ```kotlin
     * SDKInitializer.setCircuitBreakerConfig(CircuitBreakerConfig(failureThreshold = 3, openDuration = 60.seconds))
     * SDKInitializer.init(...)
     * ```
     */
    fun setCircuitBreakerConfig(config: CircuitBreakerConfig) {
        circuitBreakerConfig = config
        PlatformLogger.get().d(TAG, "CircuitBreakerConfig set: $config")
    }

    /**
     * Add a custom request/response interceptor to the SDK's HTTP pipeline.
     *
     * Interceptors run in registration order on every outgoing request.
     * Safe to call before or after [init] - interceptors registered after init
     * take effect on the next request.
     *
     * ```kotlin
     * SDKInitializer.addInterceptor(DeviceFingerprintInterceptor(deviceId))
     * SDKInitializer.addInterceptor(RequestSigningInterceptor(signingKey))
     * ```
     */
    fun addInterceptor(interceptor: SdkRequestInterceptor) {
        SdkInterceptorRegistry.register(interceptor)
        PlatformLogger.get().d(TAG, "Interceptor registered: ${interceptor.id}")
    }

    /** Remove a previously added interceptor by ID. */
    fun removeInterceptor(id: String) {
        SdkInterceptorRegistry.unregister(id)
        PlatformLogger.get().d(TAG, "Interceptor removed: $id")
    }

    /** Register an [SDKPlugin] before [init]. */
    fun registerPlugin(plugin: SDKPlugin) {
        SDKPluginRegistry.register(plugin)
        PlatformLogger.get().d(TAG, "Plugin registered: ${plugin.id}")
    }

    // ═══════════════════════════════════════════════════════════════════
    //  OPTION 1 - Direct init (raw params)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initialize the SDK immediately with all credentials.
     *
     * @param baseUrl           Full base URL (e.g. "https://api.stage.example.com").
     * @param authToken         JWT Bearer token.
     * @param apiGuid           Correlation GUID for `x-api-guid`.
     * @param clientId          OAuth client ID for `clientid` header.
     * @param apiKey            API gateway key for `x-api-key` header.
     * @param tokenExpiresIn    Optional: how long [authToken] is valid for. When provided,
     *                          the SDK schedules a proactive token refresh via [TokenRefreshProvider].
     * @param additionalModules Extra Koin modules the host app can supply.
     * @throws IllegalArgumentException if any required credential is blank.
     * @throws Exception if SDK startup fails and the failure should bridge to Swift/Objective-C.
     */
    @Throws(Exception::class)
    suspend fun init(
        baseUrl: String,
        authToken: String,
        apiGuid: String,
        clientId: String,
        apiKey: String,
        tokenExpiresIn: Duration? = null,
        additionalModules: List<Module> = emptyList()
    ) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            initUnderLock(baseUrl, authToken, apiGuid, clientId, apiKey, tokenExpiresIn, additionalModules)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  OPTION 1b - Environment-based init (recommended)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Initialize the SDK using a strongly-typed [SdkEnvironment].
     *
     * This is the **preferred** init path for multi-environment apps - avoids
     * passing raw strings and makes staging/production switching type-safe.
     *
     * ```kotlin
     * SDKInitializer.init(
     *     environment = Environments.PRODUCTION,
     *     authToken = sessionManager.token,
     *     apiGuid = sessionManager.guid
     * )
     * ```
     *
     * If [SdkEnvironment.sslPins] is present, it is applied to [SDKConfig] before
     * the underlying raw-parameter [init] path runs.
     *
     * @param environment       Strongly-typed environment containing `baseUrl`, `clientId`, `apiKey`, and optional pinning config.
     * @param authToken         JWT Bearer token.
     * @param apiGuid           Correlation GUID for `x-api-guid`.
     * @param tokenExpiresIn    Optional auth token lifetime used for proactive refresh scheduling.
     * @param additionalModules Extra Koin modules the host app can supply.
     */
    @Throws(Exception::class)
    suspend fun init(
        environment: SdkEnvironment,
        authToken: String,
        apiGuid: String,
        tokenExpiresIn: Duration? = null,
        additionalModules: List<Module> = emptyList()
    ) {
        // Apply environment's SSL pins to SDKConfig before init
        if (environment.sslPins != null) {
            SDKConfig.sslPins = environment.sslPins
        }
        init(
            baseUrl = environment.baseUrl,
            authToken = authToken,
            apiGuid = apiGuid,
            clientId = environment.clientId,
            apiKey = environment.apiKey,
            tokenExpiresIn = tokenExpiresIn,
            additionalModules = additionalModules
        )
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Core init logic (lock-free, must be called while initMutex is held)
    // ═══════════════════════════════════════════════════════════════════

    @Suppress("LongParameterList") // All parameters are required credentials/config - cannot be reduced
    private fun initUnderLock(
        baseUrl: String,
        authToken: String,
        apiGuid: String,
        clientId: String,
        apiKey: String,
        tokenExpiresIn: Duration?,
        additionalModules: List<Module> = emptyList()
    ) {
        val log = PlatformLogger.get()
        log.i(TAG, "Initializing SDK - ${SDKInfo.fullName}")

        require(baseUrl.isNotBlank()) { "baseUrl must not be blank" }
        require(authToken.isNotBlank()) { "authToken must not be blank" }
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        require(apiKey.isNotBlank()) { "apiKey must not be blank" }

        PlatformConfig.setEnvHeaders(mapOf("clientid" to clientId, "x-api-key" to apiKey))
        PlatformConfig.setDynamicHeaders(mapOf("authorization" to authToken, "x-api-guid" to apiGuid))
        log.d(TAG, "Headers configured")

        val runtimeModule = module {
            single(named(BASE_URL_QUALIFIER)) { baseUrl }
        }

        val allModules = mutableListOf(runtimeModule, coreModule)
        allModules.addAll(FeatureModules.all)
        allModules.addAll(SDKPluginRegistry.koinModules)
        allModules.addAll(additionalModules)

        koinApp = koinApplication {
            allowOverride(true)
            modules(allModules)
        }

        // Wire up TokenManager if a provider was registered
        tokenRefreshProvider?.let { provider ->
            tokenManager = TokenManager(
                provider = provider,
                onTokenRefreshed = { newToken ->
                    PlatformConfig.updateAuthToken(newToken)
                    telemetry.recordSdkEvent(SDKEvent.TOKEN_REFRESHED)
                    log.i(TAG, "Token refreshed via TokenManager")
                },
                onUserLoggedOut = {
                    log.w(TAG, "User logged out during token refresh - resetting SDK")
                    backgroundScope.launch { reset() }
                }
            ).also { it.onTokenAcquired(authToken, tokenExpiresIn) }
            log.d(TAG, "TokenManager started")
        }

        // Fetch remote config in background (non-blocking)
        remoteConfigProvider?.let { provider ->
            backgroundScope.launch {
                try {
                    provider.fetchConfig()?.let { config ->
                        SdkRemoteConfigStore.update(config)
                        log.i(TAG, "Remote config applied: ${config.featureFlags.size} feature flags")
                    }
                } catch (e: Exception) {
                    log.w(TAG, "Remote config fetch failed (using defaults): ${e.message}")
                }
            }
        }

        initialized = true

        SDKPluginRegistry.dispatchInitialized()
        telemetry.recordSdkEvent(SDKEvent.INITIALIZED)

        val features = FeatureModules.all.size
        val interceptors = SdkInterceptorRegistry.all.size
        log.i(TAG, "✓ SDK initialized - $features feature(s), $interceptors interceptor(s)")
    }

    // ═══════════════════════════════════════════════════════════════════
    //  OPTION 2 - Deferred init
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Register a credential provider for deferred initialization.
     *
     * Use this when credentials are unavailable at app startup and only become
     * available after login, environment selection, or secure storage unlock.
     * The provider is retained across [reset] calls so a logout → re-login flow
     * can reuse the same deferred initialization path.
     *
     * ```kotlin
     * SDKInitializer.configure {
     *     SDKCredentials(baseUrl, authToken, apiGuid, clientId, apiKey)
     * }
     * ```
     */
    fun configure(provider: () -> SDKCredentials) {
        credentialProvider = provider
        PlatformLogger.get().d(TAG, "Credential provider registered (deferred init)")
    }

    /**
     * Ensure the SDK is initialized - coroutine-safe and idempotent.
     *
     * **Deadlock-free:** resolves credentials inside the lock, then calls [initUnderLock]
     * (non-locking helper) - never re-enters the non-reentrant [initMutex].
     *
     * The credential provider is **retained** after use so that the deferred-init
     * pattern continues to work across [reset] → re-init cycles without requiring
     * the host app to call [configure] again.
     *
     * @throws SdkNotConfiguredException if no provider has been registered with [configure].
     */
    @Throws(Exception::class)
    suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return

            val provider = credentialProvider
            if (provider == null) {
                val msg = "SDK not initialized and no credential provider registered. " +
                        "Call SDKInitializer.configure { ... } at app startup, " +
                        "or call SDKInitializer.init(...) directly."
                PlatformLogger.get().e(TAG, msg, null)
                throw SdkNotConfiguredException(msg)
            }

            val log = PlatformLogger.get()
            log.d(TAG, "ensureInitialized → resolving credentials")

            val creds = provider()
            // NOTE: credentialProvider is intentionally NOT cleared here.
            // It must survive reset() → ensureInitialized() cycles so the host
            // app doesn't have to re-register configure {} after every logout.
            log.d(TAG, "Credentials resolved")

            initUnderLock(creds.baseUrl, creds.authToken, creds.apiGuid, creds.clientId, creds.apiKey, null)
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Runtime updates
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Update the authorization token after a manual refresh.
     *
     * This replaces only the `authorization` header and preserves all other
     * configured environment and session values.
     */
    fun updateAuthToken(token: String) {
        require(token.isNotBlank()) { "token must not be blank" }
        PlatformConfig.updateAuthToken(token)
        tokenManager?.onTokenAcquired(token)
        telemetry.recordSdkEvent(SDKEvent.TOKEN_REFRESHED)
    }

    /**
     * Update both dynamic headers after re-login or account switch.
     *
     * This replaces `authorization` and `x-api-guid` atomically so subsequent
     * requests run with the new authenticated session context.
     */
    fun updateDynamicHeaders(authToken: String, apiGuid: String) {
        require(authToken.isNotBlank()) { "authToken must not be blank" }
        PlatformConfig.setDynamicHeaders(
            mapOf("authorization" to authToken, "x-api-guid" to apiGuid)
        )
        telemetry.recordSdkEvent(SDKEvent.TOKEN_REFRESHED)
    }

    // ═══════════════════════════════════════════════════════════════════
    //  Reset
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Reset the SDK - closes HttpClient, Koin, and clears all state.
     * After this, [init] or [ensureInitialized] can be called again.
     *
     * **Note:** The [credentialProvider] registered via [configure] is intentionally
     * preserved so the host app doesn't need to re-register it after logout.
     * Call [configure] with `null` or a new closure if you need to change it.
     *
     * Typical use cases:
     * - user logout
     * - account switch
     * - test teardown between scenarios
     */
    @Throws(Exception::class)
    suspend fun reset() {
        if (!initialized) return
        initMutex.withLock {
            if (!initialized) return
            val log = PlatformLogger.get()
            log.i(TAG, "Resetting SDK")

            tokenManager?.cancel()
            tokenManager = null

            try {
                koinApp?.koin?.getOrNull<KtorApiClient>()?.reset()
            } catch (_: Exception) {
            }
            try {
                koinApp?.koin?.getOrNull<HttpClient>()?.close()
                log.d(TAG, "HttpClient closed")
            } catch (e: Exception) {
                log.w(TAG, "Failed to close HttpClient: ${e.message}")
            }

            PlatformConfig.setDynamicHeaders(emptyMap())
            PlatformConfig.setEnvHeaders(emptyMap())

            koinApp?.close()
            koinApp = null

            SDKPluginRegistry.dispatchReset()
            SDKPluginRegistry.clear()
            SdkInterceptorRegistry.clear()
            SdkRemoteConfigStore.reset()
            telemetry.recordSdkEvent(SDKEvent.RESET)

            // credentialProvider is intentionally NOT cleared - it must
            // survive reset so ensureInitialized() works after logout/relaunch.
            initialized = false
            log.i(TAG, "✓ SDK reset complete")
        }
    }

    /**
     * Clear any previously registered deferred credential provider.
     *
     * Typical use: full app teardown or test cleanup where you want to ensure
     * no stale provider remains. For normal logout → re-login flows, use [reset]
     * alone - it preserves the provider intentionally.
     */
    fun clearDeferredProvider() {
        credentialProvider = null
        PlatformLogger.get().d(TAG, "Deferred credential provider cleared")
    }
}

// ── SDKCredentials ─────────────────────────────────────────────────────────────

/**
 * Credentials the host app provides to the SDK.
 * [toString] redacts all sensitive values - safe for logs.
 * Not a data class - no `.copy()` that could expose secrets.
 *
 * Commonly returned from [SDKInitializer.configure] for deferred initialization.
 */
class SDKCredentials(
    val baseUrl: String,
    val authToken: String,
    val apiGuid: String,
    val clientId: String,
    val apiKey: String
) {
    override fun toString() =
        "SDKCredentials(baseUrl=$baseUrl, authToken=***, apiGuid=***, clientId=***, apiKey=***)"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SDKCredentials) return false
        return baseUrl == other.baseUrl && authToken == other.authToken &&
                apiGuid == other.apiGuid && clientId == other.clientId && apiKey == other.apiKey
    }

    override fun hashCode(): Int {
        var result = baseUrl.hashCode()
        result = 31 * result + authToken.hashCode()
        result = 31 * result + apiGuid.hashCode()
        result = 31 * result + clientId.hashCode()
        result = 31 * result + apiKey.hashCode()
        return result
    }
}

// ── SdkNotConfiguredException ──────────────────────────────────────────────────

/**
 * Thrown by [SDKInitializer.ensureInitialized] when no credential provider has
 * been registered via [SDKInitializer.configure] and no direct [SDKInitializer.init]
 * call has been made.
 *
 * Extends [Exception] directly so it is always covered by
 * `@Throws(Exception::class)` and correctly bridged to `NSError` on iOS.
 *
 * On Android, catch with `catch (e: SdkNotConfiguredException)`.
 * On iOS, catch with `do { try await ... } catch { ... }`.
 */
class SdkNotConfiguredException(message: String) : Exception(message)