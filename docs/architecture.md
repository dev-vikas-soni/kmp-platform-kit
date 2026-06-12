# 🏗️ Architecture - KMP Platform Kit

> **Document Type:** Reference Architecture  
> **SDK Version:** `0.x`  
> **Last Updated:** March 2026  
> **Audience:** SDK contributors, platform engineers, senior mobile developers

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [High-Level Overview](#2-high-level-overview)
3. [Layer Architecture](#3-layer-architecture)
4. [Package Structure](#4-package-structure)
5. [Initialization System](#5-initialization-system)
6. [Selective Feature Builds](#6-selective-feature-builds)
7. [Dependency Injection (Koin)](#7-dependency-injection-koin)
8. [Networking Pipeline](#8-networking-pipeline)
9. [Error Handling Strategy](#9-error-handling-strategy)
10. [State Management (SDKState)](#10-state-management-sdkstate)
11. [Thread Safety Model](#11-thread-safety-model)
12. [Retry Behavior (RetryConfig)](#12-retry-behavior-retryconfig)
13. [Shared Models](#13-shared-models)
14. [Debug Mode & Logging](#14-debug-mode--logging)
15. [Security & Credential Handling](#15-security--credential-handling)
16. [SDK Metadata (SDKInfo)](#16-sdk-metadata-sdkinfo)
17. [Internal Utilities](#17-internal-utilities)
18. [Artifact Distribution](#18-artifact-distribution)
19. [Adding a New Feature](#19-adding-a-new-feature)

---

## 1. Executive Summary

KMP Platform Kit is a **Kotlin Multiplatform (KMP)** SDK that provides shared business logic for Android and iOS host applications from a single codebase. It delivers:

| Capability                    | Detail                                                                |
|-------------------------------|-----------------------------------------------------------------------|
| **Single source of truth**    | Business logic, networking, and models written once in Kotlin         |
| **Platform-native output**    | Android `.aar` (OkHttp) · iOS `.xcframework` (Darwin)                 |
| **Selective compilation**     | Include only the features you need - smaller artifacts, faster builds |
| **Zero host-app boilerplate** | Init in one line; DI, headers, retries all automatic                  |
| **UI-ready states**           | `SDKState<T>` maps directly to Loading / Success / Error UI states    |

---

## 2. High-Level Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  HOST APPLICATION                                                            │
│                                                                              │
│  Step 1: SDKInitializer.configure { SDKCredentials(...) }  ← app startup    │
│  Step 2: SDKInitializer.ensureInitialized()                ← any screen     │
│  Step 3: AppFacadeYourFeature.getInventoriesFlow(...) ← API call      │
│  Step 4: SDKInitializer.updateAuthToken(newToken)           ← token refresh  │
│  Step 5: SDKInitializer.reset()                             ← logout        │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
               ┌────────────────────┴────────────────────┐
               │ Android (.aar ~3.3 MB)  iOS (.xcframework) │
               └────────────────────┬────────────────────┘
                                    │
┌──────────────────────────────────────────────────────────────────────────────┐
│  SDK INTERNALS  (host app never accesses these directly)                     │
│                                                                              │
│   Facade  ──▶  Repository  ──▶  KtorApiClient  ──▶  PlatformHttpClient     │
│                                                                              │
│   • Koin DI (auto-configured, auto-generated FeatureModules.kt)             │
│   • Header injection (auth token, API key, user-agent, platform)            │
│   • JSON parsing (kotlinx.serialization)                                    │
│   • Error mapping (ApiResult → SDKState)                                    │
│   • Retry with exponential backoff                                          │
│   • Pluggable logging (NoOpLogger by default)                               │
└──────────────────────────────────────────────────────────────────────────────┘
```

### What the host app provides (5 values at init)

| Value       | HTTP Header      | Lifecycle                   |
|-------------|------------------|-----------------------------|
| `baseUrl`   | _(URL prefix)_   | Fixed per environment       |
| `authToken` | `authorization`  | Refreshes ~every 15 min     |
| `apiGuid`   | `x-cah-api-guid` | Changes every login session |
| `clientId`  | `clientid`       | Fixed per environment       |
| `apiKey`    | `x-api-key`      | Fixed per environment       |

### What the SDK handles automatically

| Header              | Value                                        | Set By                  |
|---------------------|----------------------------------------------|-------------------------|
| `platform`          | `android` / `ios`                            | `HttpClientFactory`     |
| `user-agent`        | `KmpPlatformKit/x.y.z (Android; API-34; Pixel 7)` | `HttpClientFactory`     |
| `x-external-source` | `mobile`                                     | `HttpClientFactory`     |
| `accept`            | `application/json`                           | `HttpClientFactory`     |
| `accept-encoding`   | `gzip`                                       | HTTP engine (automatic) |
| `connection`        | `Keep-Alive`                                 | HTTP engine (automatic) |
| `host`              | derived from `baseUrl`                       | HTTP engine (automatic) |

---

## 3. Layer Architecture

The SDK is organized into **5 strictly separated layers**. Each layer only communicates with the one immediately below it.

```
┌──────────────────────────────────────────────────────┐
│  Layer 5: Host App  (Android / iOS)                   │
│  ─ calls SDKInitializer, reads SDKState               │
└──────────────────────┬───────────────────────────────┘
                       │  public API only
┌──────────────────────▼───────────────────────────────┐
│  Layer 4: Facade  (AppFacade<Feature>)                │
│  ─ thin public-facing API surface                     │
│  ─ no business logic; delegates 100% to Repository   │
│  ─ implements KoinComponent; resolves deps via DI     │
└──────────────────────┬───────────────────────────────┘
                       │  internal only
┌──────────────────────▼───────────────────────────────┐
│  Layer 3: Repository  (interface + impl)              │
│  ─ owns business logic, request building, mapping     │
│  ─ builds URL paths via Endpoints objects             │
│  ─ maps raw DTOs to UI-ready domain models            │
│  ─ calls ApiClient with structured parameters         │
└──────────────────────┬───────────────────────────────┘
                       │  internal only
┌──────────────────────▼───────────────────────────────┐
│  Layer 2: Core  (KtorApiClient)                       │
│  ─ HTTP transport via Ktor                            │
│  ─ universal error mapping → ApiResult                │
│  ─ JSON serialization / deserialization               │
│  ─ retry logic (exponential backoff)                  │
│  ─ debug logging interceptor (opt-in)                 │
└──────────────────────┬───────────────────────────────┘
                       │  expect/actual
┌──────────────────────▼───────────────────────────────┐
│  Layer 1: Platform  (expect/actual)                   │
│  ─ Android: OkHttp engine + synchronized PlatformConfig│
│  ─ iOS:     Darwin engine + AtomicReference PlatformConfig│
│  ─ PlatformLogger → Log.d / NSLog per platform        │
└──────────────────────────────────────────────────────┘
```

### Responsibilities at a glance

| Layer          | Visibility | Responsibility                                      |
|----------------|------------|-----------------------------------------------------|
| **Host App**   | -          | Provides credentials, consumes `SDKState<T>`        |
| **Facade**     | `public`   | Entry point for all API calls; KoinComponent        |
| **Repository** | `internal` | Business logic, URL construction, DTO→model mapping |
| **Core**       | `internal` | HTTP, serialization, error mapping, retry           |
| **Platform**   | `internal` | Engine, thread-safe header store, native logger     |

---

## 4. Package Structure

```
shared/src/
├── commonMain/kotlin/
│   ├── core/                               ← Networking + SDK bootstrap
│   │   ├── ApiClient.kt                   (interface - public contract)
│   │   ├── ApiResult.kt                   (sealed result type - public)
│   │   ├── DebugLoggingInterceptor.kt     (opt-in HTTP logging - internal)
│   │   ├── HttpClientFactory.kt           (platform-bridged factory - internal)
│   │   ├── KtorApiClient.kt               (Ktor impl + retry - internal)
│   │   ├── PlatformConfig.kt              (expect - runtime header store)
│   │   ├── RetryConfig.kt                 (configurable retry - public)
│   │   ├── SDKConfig.kt                   (debugMode toggle - public)
│   │   ├── SDKInfo.kt                     (version + name - public)
│   │   ├── SDKInitializer.kt              (entry point + SDKCredentials - public)
│   │   ├── SDKState.kt                    (UI-ready state - public)
│   │   ├── SDKStateFlow.kt                (toSDKState() + sdkStateFlow{} - public)
│   │   └── di/CoreModule.kt               (Koin module - internal)
│   │
│   ├── features/
│   │   └── yourfeature/             ← compiled only when sdk.features includes it
│   │       ├── di/                        (yourfeatureModule - internal)
│   │       ├── endpoints/                 (YourFeatureEndpoints - public)
│   │       ├── facade/                    (AppFacadeYourFeature - public)
│   │       ├── models/                    (DTOs internal · UI models public · mapper internal)
│   │       ├── repository/                (interface + impl - internal)
│   │       └── requestbuilder/            (YourFeatureQuery - internal)
│   │
│   └── shared/
│       ├── concurrency/                   (JvmSynchronized expect annotation)
│       ├── extensions/                    (ApiResult.map(), ApiResult.flatMap() - internal)
│       ├── models/                        (ApiEnvelope, PaginationInfo - public)
│       └── utils/                         (Logger, PlatformLogger, JsonProvider, HttpHeaders)
│
├── androidMain/kotlin/
│   ├── core/                              (OkHttp engine, PlatformConfig synchronized)
│   └── shared/
│       ├── concurrency/                   (JvmSynchronized → @kotlin.jvm.Synchronized)
│       └── utils/                         (Android PlatformLogger → Log.x)
│
├── iosMain/kotlin/
│   ├── core/                              (Darwin engine, PlatformConfig AtomicReference)
│   └── shared/utils/                      (iOS PlatformLogger → platform_log / NSLog)
│
└── commonTest/kotlin/                     ← All shared tests (see unit-testing-guide.md)
```

---

## 5. Initialization System

The SDK exposes **three** mutually exclusive initialization paths.

### Path A - Direct Init (credentials available immediately)

```kotlin
SDKInitializer.init(
    baseUrl = "https://api.stage.example.com",
    authToken = jwtToken,
    apiGuid = transactionId,
    clientId = clientId,
    apiKey = apiKey
)
```

Use this when credentials are available at `Application.onCreate()`.

> **Note:** `init()` is a `suspend fun` - it acquires a `Mutex` internally for thread safety. Call it from a coroutine scope. Repeated calls are idempotent (instant no-ops).

### Path A-alt - Environment-based Init (recommended for multi-env apps)

```kotlin
SDKInitializer.init(
    environment = Environments.PRODUCTION,
    authToken = sessionManager.token,
    apiGuid = sessionManager.guid
)
```

Uses a strongly-typed `SdkEnvironment` object that bundles `baseUrl`, `clientId`, `apiKey`, and optional `SslPinConfig`. This eliminates raw strings and makes staging/production switching type-safe.

```kotlin
object Environments {
    val STAGING = SdkEnvironment(
        id = "staging",
        baseUrl = "https://api.stage.example.com",
        clientId = BuildConfig.STAGING_CLIENT_ID,
        apiKey = BuildConfig.STAGING_API_KEY
    )
    val PRODUCTION = SdkEnvironment(
        id = "production",
        baseUrl = "https://api.example.com",
        clientId = BuildConfig.PROD_CLIENT_ID,
        apiKey = BuildConfig.PROD_API_KEY,
        sslPins = SslPinConfig(
            hostname = "api.example.com",
            pins = listOf("sha256/AAAA...", "sha256/BBBB...")
        )
    )
}
```

### Path B - Deferred Init (credentials arrive after login)

```kotlin
// ─── Step 1: App startup - register provider, no credentials needed yet ───
SDKInitializer.configure {
    SDKCredentials(
        baseUrl = resolveBaseUrl(),
        authToken = sessionManager.getToken(),
        apiGuid = prefs.getTransactionId(),
        clientId = clientId,
        apiKey = apiKey
    )
}

// ─── Step 2: Any screen - safe to call from many coroutines simultaneously ─
SDKInitializer.ensureInitialized()
// → now safe to call facades
```

### `ensureInitialized()` - Internal state machine

```
ensureInitialized() called
        │
        ├─ already initialized? → instant no-op (zero overhead)
        │
        └─ not yet initialized
               │
               ├─ acquires kotlinx.coroutines.sync.Mutex
               │         (concurrent callers suspend here)
               │
               ├─ double-checks initialization flag (prevent race)
               │
               ├─ calls credentialProvider lambda → SDKCredentials
               │
               ├─ runs init(baseUrl, authToken, apiGuid, clientId, apiKey)
               │         (starts Koin, loads FeatureModules, creates HttpClient)
               │
               └─ releases Mutex → all suspended callers continue as no-op
```

**Key guarantee:** Even if 50 coroutines call `ensureInitialized()` simultaneously, `init()` is executed exactly once.

### Header Lifecycle

```
App Start
  │
  ├─ init(baseUrl, token, guid, clientId, apiKey, tokenExpiresIn?)
  │    Sets all 5 values in PlatformConfig
  │    If tokenExpiresIn is set + TokenRefreshProvider registered → schedules proactive refresh
  │
  ├─ Every ~15 min → updateAuthToken(newToken)
  │    Only replaces: authorization header
  │
  ├─ After re-login → updateDynamicHeaders(token, guid)
  │    Replaces: authorization + x-cah-api-guid
  │
  └─ Logout → reset()
       Clears everything (stops Koin, cancels TokenManager, clears interceptors, resets cache)
       Call init() or ensureInitialized() after next login
```

### Pre-Init Registration APIs

The following APIs are designed to be called **before** `init()`. They configure optional subsystems:

```kotlin
// Optional - register telemetry (Firebase, Datadog, custom)
SDKInitializer.setTelemetry(MyFirebaseTelemetry())

// Optional - automatic token lifecycle management
SDKInitializer.setTokenRefreshProvider(MyTokenRefreshProvider())

// Optional - server-pushed kill-switches and tuning
SDKInitializer.setRemoteConfigProvider(MyRemoteConfigProvider())

// Optional - tune circuit breaker thresholds
SDKInitializer.setCircuitBreakerConfig(CircuitBreakerConfig(failureThreshold = 3))

// Optional - custom HTTP middleware (headers, signing, logging)
SDKInitializer.addInterceptor(DeviceFingerprintInterceptor(deviceId))

// Optional - self-registering feature plugin
SDKInitializer.registerPlugin(MyFeaturePlugin())

// THEN initialize
SDKInitializer.init(baseUrl, authToken, apiGuid, clientId, apiKey)
```

All pre-init calls are safe - they store configuration that is applied when `init()` runs.

---

## 6. Selective Feature Builds

Features are compiled selectively at build time via a Gradle property.

```bash
# Single feature
./gradlew :shared:assembleRelease -Psdk.features=yourfeature

# Multiple features
./gradlew :shared:assembleRelease -Psdk.features=yourfeature,orders

# All features (default when property is omitted)
./gradlew :shared:assembleRelease
```

### What happens at build time

```
Build starts with -Psdk.features=yourfeature
          │
          ├─ Gradle reads all folders under features/
          │
          ├─ Any folder NOT in the feature list is EXCLUDED
          │       → its source files are never compiled
          │       → it never enters the binary
          │
          ├─ FeatureModules.kt is AUTO-GENERATED:
          │
          │   // AUTO-GENERATED - controlled by sdk.features property
          │   object FeatureModules {
          │       val all: List<Module> = listOf(
          │           yourfeatureModule  // only included features
          │       )
          │   }
          │
          └─ SDKInitializer loads FeatureModules.all - no hardcoded imports
```

### Feature discovery convention

| Convention      | Example                                                    |
|-----------------|------------------------------------------------------------|
| Folder name     | `features/orders/`                                         |
| Module val name | `val ordersModule` (top-level in `di/OrdersModule.kt`)     |
| Auto-discovery  | Build script scans folder name → looks for `${name}Module` |

**Result:** Adding a new feature requires zero changes to `SDKInitializer` - just create the folder and implement the `val <name>Module` property.

---

## 7. Dependency Injection (Koin)

The SDK uses Koin internally. The **host app never calls `startKoin`** - the SDK fully owns the DI lifecycle.

### Module hierarchy

```
SDKInitializer.init()
      │
      ├─ runtimeModule (created per init() call)
      │       └─ single(named("baseUrl")) { baseUrl }
      │
      ├─ coreModule (always loaded)
      │       ├─ single<HttpClient> { HttpClientFactory.create(get()) }
      │       ├─ single<ApiClient> { KtorApiClient(get(), get(), get(), get(), get()) }
      │       ├─ single<RetryConfig> { RetryConfig() }  ← overrideable
      │       ├─ single<ResponseCache> { ResponseCache() }
      │       └─ single<RequestDeduplicator> { RequestDeduplicator() }
      │
      ├─ FeatureModules.all (auto-generated, only enabled features)
      │       └─ yourfeatureModule
      │               └─ single<YourFeatureRepository> { YourFeatureRepositoryImpl(get()) }
      │
      ├─ SDKPluginRegistry.koinModules (from registered SDKPlugins)
      │
      └─ additionalModules (host app supplied)
```

### How facades resolve dependencies

Facades extend `BaseFacade` (which implements `SdkKoinComponent`) and use lazy delegation:

```kotlin
object AppFacadeYourFeature : BaseFacade() {
    override val tag = "YourFeatureFacade"
    private val repository: YourFeatureRepository by inject()

    suspend fun getInventories(...): ApiResult<InventoryListModel> {
        requireInitialized()  // ← guard from BaseFacade
        // ...delegates to repository
    }
}
```

This means:

- Dependencies are resolved **lazily** - only when the first API call is made
- `requireInitialized()` throws immediately if the SDK hasn't been initialized (clear error message)
- No circular dependencies possible (Koin validates on startup)
- Full testability - swap any binding via a test Koin module
- `SdkKoinComponent` resolves from the SDK's **isolated** Koin instance (not the host app's)

### Host app Koin conflict resolution

If the host app also uses Koin, pass its modules to `additionalModules`:

```kotlin
SDKInitializer.init(
    baseUrl = "...", authToken = "...", apiGuid = "...",
    clientId = "...", apiKey = "...",
    additionalModules = listOf(yourAppModule, yourNetworkModule)
)
```

> Apps using **Hilt** or **Dagger** have no conflict - the SDK's Koin instance runs independently.

---

## 8. Networking Pipeline

Every HTTP request flows through this pipeline:

```
Repository calls ApiClient.get(path, parser, cachePolicy, timeoutMs)
          │
          ▼
KtorApiClient.get()
          │
          ├─ Check CachePolicy strategy:
          │       ├─ CACHE_ONLY   → return from cache or fail
          │       ├─ CACHE_FIRST  → return cached if fresh; else fall through
          │       ├─ NETWORK_FIRST→ deduplicate → fetch → on failure, try stale cache
          │       └─ NETWORK_ONLY → fetch, never read/write cache
          │
          ├─ RequestDeduplicator (for NETWORK_FIRST):
          │       ├─ In-flight request for same URL? → await existing result
          │       └─ No → register this as the owner, proceed
          │
          ├─ Build URL: baseUrl + path (normalised, no double slashes)
          │
          ├─ Inject headers from PlatformConfig:
          │       authorization, x-cah-api-guid, clientid, x-api-key,
          │       platform, user-agent, x-external-source, accept
          │
          ├─ Run SdkRequestInterceptor pipeline (if any registered)
          │
          ├─ Inject SdkTraceContext headers (traceparent, x-b3-traceid, x-b3-spanid)
          │
          ├─ DebugLoggingInterceptor (if debugMode=true)
          │       → log request URL, headers (auth REDACTED), body
          │
          ├─ HTTP Engine (OkHttp on Android, Darwin on iOS)
          │       → actual network call (with optional per-call timeout override)
          │
          ├─ Response received
          │       ├─ 200 → cache raw response → deserialize JSON via responseParser lambda
          │       │           → ApiResult.Success(T)
          │       ├─ 4xx/5xx → map to ApiResult.Failure(code, message)
          │       └─ Network exception → ApiResult.NetworkError
          │
          ├─ RetryConfig check (exponential backoff + full jitter):
          │       ├─ 5xx or IOException → retry with jittered backoff (up to maxAttempts)
          │       │    Backoff: random(0, min(initialBackoff * 2^attempt, 30s))
          │       └─ 4xx / CancellationException / other → no retry
          │
          ├─ Run SdkRequestInterceptor.onResponse() pipeline
          │
          └─ Return ApiResult<T> to caller
```

### `expect/actual` - Platform-specific HTTP engines

| Declaration         | Android (`androidMain`) | iOS (`iosMain`)          |
|---------------------|-------------------------|--------------------------|
| `HttpClientFactory` | Creates `OkHttp` engine | Creates `Darwin` engine  |
| `PlatformConfig`    | `synchronized {}` block | `AtomicReference` + CAS  |
| `PlatformLogger`    | `Log.d/i/w/e`           | `platform_log` / `NSLog` |

---

## 9. Error Handling Strategy

### `ApiResult` - Raw transport-layer result

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: Int, val message: String?) : ApiResult<Nothing>()
    data object NetworkError : ApiResult<Nothing>()
    data object Cancelled : ApiResult<Nothing>()
}
```

| Variant        | When It Occurs                        | Example Scenario                   |
|----------------|---------------------------------------|------------------------------------|
| `Success`      | HTTP 200 + valid JSON                 | Inventory list returned            |
| `Failure`      | HTTP error or parse error             | 401 Unauthorized, 500 Server Error |
| `NetworkError` | No connectivity, DNS failure, timeout | Device in airplane mode            |
| `Cancelled`    | Coroutine was cancelled               | User navigated away                |

> **Important:** API-level errors (HTTP 200 but `"status": "ERROR"`) arrive as `Success`. Check `response.isSuccess` / `response.errorMessage` for business-logic failures.

### `ApiResult.map()` / `ApiResult.flatMap()`

Internal extension functions for transforming results in the repository layer:

```kotlin
// map: transform Success data, pass errors through unchanged
fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R>

// flatMap: transform Success into another ApiResult (useful when transform can fail)
fun <T, R> ApiResult<T>.flatMap(transform: (T) -> ApiResult<R>): ApiResult<R>
```

---

## 10. State Management (`SDKState`)

`SDKState<T>` is the **recommended UI-layer type**. It wraps `ApiResult` with a `Loading` state and convenience helpers.

```kotlin
sealed class SDKState<out T> {
    data object Loading : SDKState<Nothing>()
    data class Success<T>(val data: T) : SDKState<T>()
    data class ErrorBody(val code: Int, val message: String, val errorCode: String?) : SDKState<Nothing>()
    data class Error(val message: String, val isNetworkError: Boolean) : SDKState<Nothing>()
}
```

| State       | UI Action             | When                                           |
|-------------|-----------------------|------------------------------------------------|
| `Loading`   | Show loader/shimmer   | Request in flight                              |
| `Success`   | Show data             | HTTP 200 + valid data                          |
| `ErrorBody` | Show API error dialog | Structured error returned (4xx/5xx with body)  |
| `Error`     | Show retry screen     | Network failure, timeout, unexpected exception |

### Convenience helpers

```kotlin
state.isLoading            // Boolean
state.isSuccess            // Boolean
state.isError              // Boolean
state.dataOrNull()         // T? - data if Success, null otherwise
state.errorMessageOrNull() // String? - message if any error state, null otherwise
```

### `sdkStateFlow { }` - Emit Loading then result automatically

```kotlin
// Wrap any suspend facade call - emits Loading immediately, then the final state
val flow: Flow<SDKState<InventoryListModel>> = sdkStateFlow {
    AppFacadeYourFeature.getInventories(customerNo = "2052008238")
}
```

### `toSDKState()` - Convert `ApiResult` manually

```kotlin
val result: ApiResult<InventoryListModel> = AppFacadeYourFeature.getInventories(...)
val state: SDKState<InventoryListModel> = result.toSDKState()
```

### `SDKErrorCode` - Machine-readable error codes

The SDK emits standardised error codes via `SDKState.Error.message` for use in i18n:

| Constant                         | Value                     | When                                     |
|----------------------------------|---------------------------|------------------------------------------|
| `SDKErrorCode.NETWORK_ERROR`     | `"SDK_NETWORK_ERROR"`     | `ApiResult.NetworkError`                 |
| `SDKErrorCode.REQUEST_CANCELLED` | `"SDK_REQUEST_CANCELLED"` | `ApiResult.Cancelled`                    |
| `SDKErrorCode.UNEXPECTED_ERROR`  | `"SDK_UNEXPECTED_ERROR"`  | Unexpected exception in `sdkStateFlow{}` |

Host apps should use these codes to show **localised** UI messages rather than raw strings:

```kotlin
is SDKState.Error -> when (state.message) {
    SDKErrorCode.NETWORK_ERROR    -> showDialog(R.string.no_internet)
    SDKErrorCode.REQUEST_CANCELLED -> { /* no-op */ }
    else                          -> showDialog(R.string.unexpected_error)
}
```

---

## 11. Thread Safety Model

| Component                            | Mechanism                             | Guarantee                                               |
|--------------------------------------|---------------------------------------|---------------------------------------------------------|
| `SDKInitializer.init()`              | `Mutex` + double-checked locking      | Idempotent; safe from any coroutine; exactly-once       |
| `SDKInitializer.ensureInitialized()` | `Mutex` + double-checked locking      | Exactly-once initialization across N concurrent callers |
| `SDKInitializer.reset()`             | `Mutex` + double-checked locking      | Safe to call from any coroutine; no-op if not init'd    |
| `PlatformConfig.set()` (Android)     | `synchronized {}` block               | No lost updates on token refresh                        |
| `PlatformConfig.set()` (iOS)         | `AtomicReference` + CAS loop          | Lock-free, no lost updates on token refresh             |
| `PlatformConfig.getHeader()`         | `@Volatile` / `AtomicReference.get()` | Lock-free concurrent reads                              |
| `credentialProvider`                 | `@Volatile`                           | Safe publish from main, read from coroutine             |
| `TokenManager.onUnauthorized()`      | `Mutex` (refreshMutex)                | Coalesces concurrent 401s into a single refresh call    |
| `ResponseCache`                      | `Mutex`-guarded map                   | All reads and writes are atomic                         |
| `RequestDeduplicator`                | `Mutex` + `CompletableDeferred`       | Concurrent identical requests share one network call    |
| `CircuitBreaker`                     | `Mutex`                               | State transitions are atomic                            |
| `SdkPager`                           | `Mutex`                               | Page loads are serialised, no double-fetch              |

### Why `Mutex` instead of `synchronized`?

`synchronized {}` **blocks** the thread. `Mutex.withLock {}` **suspends** the coroutine - the thread is free to execute other coroutines while waiting. Since `init()` and `reset()` involve I/O-like operations (creating Koin, creating HttpClient), using `Mutex` avoids thread starvation. All three lifecycle methods (`init`, `ensureInitialized`, `reset`) use the same `initMutex` - preventing any race between them.

### Why different header store primitives on iOS?

Kotlin/Native's memory model means `synchronized {}` is a no-op on Native (no JVM monitor locks). The iOS implementation uses `AtomicReference<Map<String,String>>` with a CAS (Compare-And-Swap) loop to achieve the same safety without JVM locks.

---

## 12. Retry Behavior (`RetryConfig`)

`KtorApiClient` automatically retries transient failures with **exponential backoff + full jitter**.

### Default configuration

| Setting            | Default | Valid Range | Description                        |
|--------------------|---------|-------------|------------------------------------|
| `maxAttempts`      | `3`     | `1–10`      | Total attempts (1 = no retry)      |
| `initialBackoffMs` | `500`   | `0–30000`   | Base delay before first retry (ms) |

### Backoff strategy - Exponential with full jitter

The SDK uses **full jitter** to prevent thundering-herd problems. Instead of a fixed exponential delay, it picks a random value between 0 and the exponential cap:

```
delay = random(0, min(initialBackoff × 2^(attempt-1), 30 seconds))
```

This distributes retries across time, preventing all clients from retrying at the same instant.

### Retry decision table

| Error Type                    | Retried? | Reason                                 |
|-------------------------------|----------|----------------------------------------|
| HTTP 5xx (server error)       | ✅ Yes    | Transient - server may recover         |
| `IOException` / network error | ✅ Yes    | Transient - connectivity may restore   |
| HTTP 4xx (client error)       | ❌ No     | Client's fault - retrying won't fix it |
| `CancellationException`       | ❌ No     | Propagated immediately                 |
| Other exceptions              | ❌ No     | Returned as `ApiResult.Failure(-2)`    |

### Backoff progression (defaults, showing max possible delay)

| Attempt       | Delay Before This Attempt |
|---------------|---------------------------|
| 1 (first try) | 0 ms                      |
| 2 (1st retry) | random(0, 500 ms)         |
| 3 (2nd retry) | random(0, 1000 ms)        |

Max cap: **30 seconds** (prevents unbounded waits).

### Customizing retry behavior

```kotlin
// 5 attempts, 1 second initial backoff
val aggressiveRetry = module {
    single { RetryConfig(maxAttempts = 5, initialBackoffMs = 1000L) }
}

SDKInitializer.init(
    baseUrl = "...", authToken = "...", apiGuid = "...",
    clientId = "...", apiKey = "...",
    additionalModules = listOf(aggressiveRetry)
)

// Disable retries entirely
val noRetry = module {
    single { RetryConfig.NO_RETRY }
}
```

---

## 13. Shared Models

### API Envelope (`BaseApiResponse`, `ErrorInfo`, `ErrorDetail`)

Every your organization API wraps responses in a common envelope:

```json
{
    "status": "SUCCESS",
    "error": null,
    "data": {
        ...
    }
}
```

```json
{
    "status": "ERROR",
    "error": {
        "errorDetails": [
            {
                "code": "EX_402_115",
                "message": "Request Not Allowed"
            }
        ]
    },
    "data": null
}
```

| Class             | Package                                | Purpose                                                            |
|-------------------|----------------------------------------|--------------------------------------------------------------------|
| `BaseApiResponse` | `com.droidunplugged.kmp_platform_kit.models` | Top-level envelope: `status`, `error`, `isSuccess`, `errorMessage` |
| `ErrorInfo`       | same                                   | Contains `errorDetails: List<ErrorDetail>`                         |
| `ErrorDetail`     | same                                   | Individual entry: `code: String`, `message: String`                |

These are reused across all features - no duplication.

### `PaginationInfo`

Generic, feature-agnostic pagination model:

| Property        | Type      | Description                  |
|-----------------|-----------|------------------------------|
| `currentPage`   | `Int`     | Current page (0-based)       |
| `totalPages`    | `Int`     | Total number of pages        |
| `totalElements` | `Int`     | Total items across all pages |
| `pageSize`      | `Int`     | Items per page               |
| `hasNext`       | `Boolean` | Next page available?         |
| `hasPrevious`   | `Boolean` | Previous page available?     |
| `isLastPage`    | `Boolean` | Computed: `!hasNext`         |
| `isFirstPage`   | `Boolean` | Computed: `!hasPrevious`     |
| `isPaginated`   | `Boolean` | Computed: `totalPages > 1`   |

---

## 14. Debug Mode & Logging

### `SDKConfig`

```kotlin
// Recommended approach - safe, no-op in release builds
if (BuildConfig.DEBUG) SDKConfig.enableDebugMode()

// Or force it (not recommended for production)
SDKConfig.debugMode = true   // enable BEFORE SDKInitializer.init()
```

| Property    | Type            | Default | Effect                                                                            |
|-------------|-----------------|---------|-----------------------------------------------------------------------------------|
| `debugMode` | `Boolean`       | `false` | When `true`, logs full HTTP request/response bodies via `DebugLoggingInterceptor` |
| `sslPins`   | `SslPinConfig?` | `null`  | When set, enables SSL certificate pinning on the HTTP engine                      |

**`enableDebugMode()`** calls the platform-specific `isDebugBuild()` function (checks `ApplicationInfo.FLAG_DEBUGGABLE` on Android, `Platform.isDebugBinary` on iOS). If the current build is not a debug build, the call is a no-op - safe to call unconditionally.

### `DebugLoggingInterceptor` output format

```
┌── GET https://api.stage.example.com/v1/customer/2052008238/inventories
│ authorization: ***REDACTED***
│ clientid: XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj
│ x-api-key: XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj
├── 200 OK
│ Body: {"status":"SUCCESS","data":{"customer":2057192797,"inventories":[...]}}
└──────────────────────────────
```

**Security safeguards:**

- `authorization` headers → always redacted to `***REDACTED***`
- Request/response bodies → truncated to 2048 characters
- `debugMode = false` (default) → interceptor is a complete no-op

> **⚠️ Never enable `debugMode` in production.** It logs sensitive request/response bodies.

### Pluggable Logging (`PlatformLogger`)

The SDK ships with a `NoOpLogger` by default - no logs ever leak in production.

```kotlin
// Android - inject before init()
PlatformLogger.set(object : Logger {
    override fun d(tag: String, message: String) = Log.d(tag, message)
    override fun i(tag: String, message: String) = Log.i(tag, message)
    override fun w(tag: String, message: String) = Log.w(tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = Log.e(tag, message, throwable)
})
```

### Log tags reference

| Tag                       | What it logs                                 |
|---------------------------|----------------------------------------------|
| `SDKInitializer`          | Init lifecycle, reset, version               |
| `KtorApiClient`           | Every HTTP request/response with timing      |
| `HTTP`                    | Full bodies (debug mode only)                |
| `YourFeatureRepo`   | Path building, response mapping, item counts |
| `YourFeatureFacade` | Facade-level call summaries                  |
| `InventoryMapper`         | Date format warnings                         |

---

## 15. Security & Credential Handling

### Storage - in-memory only

All credentials (`authToken`, `apiKey`, `clientId`, `apiGuid`) are stored in `PlatformConfig` **in memory only**. The SDK never writes to:

- Android: `SharedPreferences`, files, databases
- iOS: `UserDefaults`, Keychain, files

### Logging policy

The SDK **never logs**:

- Auth tokens / Bearer values
- API keys or client IDs
- User-identifiable GUIDs
- Request/response bodies (only URL paths, status codes, item counts in normal mode)

### Network transport

- All requests use HTTPS (TLS 1.2+)
- Certificate validation is **not modified** - no custom `TrustManager`
- `baseUrl` is validated at init: `require(baseUrl.isNotBlank())`

### Dependency supply chain

- All versions pinned in `gradle/libs.versions.toml`
- No dynamic ranges (`+`, `latest.release`) used anywhere
- Fat AAR strips `META-INF` signatures and repackages cleanly
- OWASP dependency vulnerability scanning configured in `config/owasp/`

---

## 16. SDK Metadata (`SDKInfo`)

```kotlin
SDKInfo.NAME       // "KmpPlatformKit"
SDKInfo.VERSION    // "0.0.1-SNAPSHOT"
SDKInfo.fullName   // "KmpPlatformKit/0.0.1-SNAPSHOT"
```

Used internally by `HttpClientFactory` to build the `user-agent` header:

```
KmpPlatformKit/0.0.1-SNAPSHOT (Android; API-34; Pixel 7)
KmpPlatformKit/0.0.1-SNAPSHOT (iOS; 17.4; iPhone 15 Pro)
```

Must be updated before each release and must match the published Maven / XCFramework version.

---

## 17. Internal Utilities

### `JsonProvider`

Centralized `kotlinx.serialization.Json` instance used for all serialization:

| Setting             | Value   | Purpose                               |
|---------------------|---------|---------------------------------------|
| `ignoreUnknownKeys` | `true`  | New API fields won't break parsing    |
| `encodeDefaults`    | `true`  | Always encode default values          |
| `explicitNulls`     | `false` | Omit null fields from output          |
| `isLenient`         | `true`  | Accept non-standard JSON              |
| `coerceInputValues` | `true`  | Null → default for non-nullable types |

### `JvmSynchronized`

A multiplatform `@Synchronized`-equivalent annotation:

- **Android/JVM:** → `@kotlin.jvm.Synchronized` (JVM monitor lock)
- **iOS/Native:** → no-op (Kotlin/Native strict memory model provides safety)

### `HttpHeaders`

Constant definitions for all header keys used by the SDK, ensuring no typos and easy global changes.

---

## 18. Artifact Distribution

### Android

| Variant                   | File                                          | Size    | Dependencies                                        |
|---------------------------|-----------------------------------------------|---------|-----------------------------------------------------|
| **Fat AAR** (recommended) | `shared-release-fat.aar`                      | ~3.3 MB | None - Ktor, Koin, kotlinx-serialization bundled    |
| Lean AAR                  | `shared-release.aar`                          | ~115 KB | Must add Ktor, Koin, kotlinx-serialization manually |
| Maven                     | `com.droidunplugged.kmp_platform_kit:shared-core:<ver>` | -       | Resolved from POM automatically                     |

### iOS

| Variant                | File                 | Dependencies                             |
|------------------------|----------------------|------------------------------------------|
| **Static XCFramework** | `Shared.xcframework` | None - everything linked at compile time |

> iOS XCFramework is always self-contained because Kotlin/Native compiles to a static LLVM binary - all dependencies are linked in automatically. This is architecturally equivalent to the Android fat AAR.

---

## 19. Adding a New Feature

Follow these steps to add a new feature (example: `orders`):

### Step 1 - Create the feature folder structure

```
features/orders/
├── di/
│   └── OrdersModule.kt          ← val ordersModule: Module = module { ... }
├── endpoints/
│   └── OrderEndpoints.kt        ← path constants + query param constants
├── facade/
│   └── AppFacadeOrders.kt       ← object : KoinComponent { ... }
├── models/
│   ├── OrderResponse.kt         ← @Serializable DTO (internal)
│   ├── OrderListModel.kt        ← UI domain model (public)
│   └── OrderMapper.kt           ← extension fun OrderResponse.toModel() (internal)
├── repository/
│   ├── OrdersRepository.kt      ← interface (internal)
│   └── OrdersRepositoryImpl.kt  ← implementation (internal)
└── requestbuilder/
    └── OrderQuery.kt            ← query parameter data class (internal)
```

### Step 2 - Follow naming conventions

| Convention           | Example                                       |
|----------------------|-----------------------------------------------|
| Folder name          | `orders`                                      |
| DI val name          | `val ordersModule` (auto-discovered by build) |
| Facade name          | `AppFacadeOrders`                             |
| Repository interface | `OrdersRepository`                            |
| Repository impl      | `OrdersRepositoryImpl`                        |

### Step 3 - Write tests

See `docs/unit-testing-guide.md` for the full testing guide. Minimum structure:

```
commonTest/kotlin/features/orders/
├── OrdersIntegrationTest.kt              ← extends BaseIntegrationTest<OrderListModel>
├── repository/
│   └── OrdersRepositoryImplTest.kt       ← extends BaseRepositoryTest<OrderListModel>
├── models/
│   ├── OrderResponseTest.kt
│   └── OrderMapperTest.kt
└── endpoints/
    └── OrderEndpointsTest.kt
```

### Step 4 - Update documentation

- Add endpoint table to `docs/endpoints/ENDPOINTS.md`
- No changes to `SDKInitializer`, `FeatureModules`, or any SDK core file

### Step 5 - Build and verify

```bash
./gradlew :shared:assembleRelease -Psdk.features=orders
./gradlew :shared:allTests --tests "*.features.orders.*"
```

**The build system auto-discovers `ordersModule` and includes it in `FeatureModules.all`. Zero manual wiring required.**