# KMP Platform Kit

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/Multiplatform-Android%20%7C%20iOS-green)](https://www.jetbrains.com/kotlin-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)
[![Build](https://img.shields.io/badge/build-passing-brightgreen)]()

A production-grade **Kotlin Multiplatform SDK framework** for Android and iOS that provides a battle-tested networking, resilience, and observability foundation — so you can focus on building features, not infrastructure.

---

## ✨ Features

| Capability | Details |
|---|---|
| 🌐 **Typed HTTP Client** | Ktor-based `ApiClient` with sealed `ApiResult<T>` returns |
| 🧠 **Smart Caching** | `NETWORK_FIRST`, `CACHE_FIRST`, and `NO_CACHE` policies |
| 🔁 **Request Deduplication** | Coalesces in-flight identical requests via `Mutex + CompletableDeferred` |
| ⚡ **Circuit Breaker** | `CLOSED → OPEN → HALF_OPEN` state machine to fast-fail on server outages |
| 🔐 **Auth / Token Manager** | 401-coalescing refresh with pluggable `TokenRefreshProvider` |
| 📄 **Pagination** | Generic `SdkPager` with `StateFlow`-backed page state |
| 🔭 **Tracing** | Automatic `traceparent` + `x-b3-*` header injection per request |
| 🪵 **Structured Logging** | `PlatformLogger` bridges to `android.util.Log` on Android, no-op on iOS |
| 💉 **Dependency Injection** | Koin-backed `coreModule` + per-feature modules |
| 📡 **Telemetry** | Pluggable `SDKTelemetry` interface with a no-op default |

---

## 🏗 Architecture

The SDK is organized in 5 clean layers:

```
┌──────────────────────────────────────────────┐
│  Layer 5 │  AppFacade         (public API)    │
│  Layer 4 │  Repository        (data access)   │
│  Layer 3 │  Core Subsystems   (resilience)    │
│  Layer 2 │  Platform Specifics (expect/actual)│
│  Layer 1 │  Models & State    (data types)    │
└──────────────────────────────────────────────┘
```

```
shared/src/
├── commonMain/kotlin/com/droidunplugged/kmp_platform_kit/
│   ├── core/                  ← ApiClient, KtorApiClient, CircuitBreaker, …
│   │   ├── config/            ← SdkEnvironment, SslPinConfig, RetryConfig
│   │   ├── di/                ← coreModule (Koin)
│   │   ├── error/             ← SdkError sealed hierarchy
│   │   ├── interceptor/       ← SdkRequestInterceptor
│   │   └── tracing/           ← SdkTraceContext
│   ├── features/
│   │   └── physicalinventory/ ← Example feature (models/repo/facade/di)
│   └── shared/
│       ├── models/            ← BaseApiResponse, PaginationInfo
│       ├── concurrency/       ← JvmSynchronized (expect)
│       └── utils/             ← PlatformLogger (expect), HttpHeaders
├── androidMain/               ← OkHttp engine, ConcurrentHashMap, Log
└── iosMain/                   ← Darwin engine, AtomicReference, no-op log
```

---

## 🚀 Quick Start

### 1. Initialize the SDK

```kotlin
// In Application.onCreate() or iOS AppDelegate
val env = SdkEnvironment(
    id        = "prod",
    baseUrl   = "https://api.example.com",
    clientId  = "your-client-id",
    apiKey    = BuildConfig.API_KEY
)

coroutineScope.launch {
    SDKInitializer.init(
        SDKCredentials(
            environment = env,
            authToken   = null,   // or pass a JWT
            apiGuid     = null
        )
    )
}
```

### 2. Set up Koin

```kotlin
startKoin {
    modules(
        coreModule(env),           // Core HTTP + resilience
        physicalInventoryModule    // Feature module
    )
}
```

### 3. Consume from a ViewModel (Android)

```kotlin
class InventoryViewModel(
    private val facade: AppFacadePhysicalInventory
) : ViewModel() {

    val inventoryState = facade
        .getInventoryList(PhysicalInventoryFilter(facilityCode = "FAC-01"))
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SDKState.Loading)
}
```

### 4. Consume from Swift (iOS via SKIE)

```swift
let facade = KoinApplication.shared.koin.get() as AppFacadePhysicalInventory

for await state in facade.getInventoryList(filter: .init(facilityCode: "FAC-01")) {
    switch state {
    case let success as SDKState.Success<InventoryListPayload>:
        render(success.data.items)
    case is SDKState.Loading:
        showLoader()
    default:
        break
    }
}
```

---

## 🔌 Optional Configuration

### Token Refresh (OAuth / JWT)

```kotlin
SDKInitializer.setTokenRefreshProvider {
    // Call your auth service and return a new token
    authService.refreshToken()
}
```

### Custom Interceptors

```kotlin
SDKInitializer.addInterceptor(object : SdkRequestInterceptor {
    override suspend fun onRequest(request: HttpRequestBuilder) = request.apply {
        header("X-App-Version", BuildConfig.VERSION_NAME)
    }
    override suspend fun onResponse(response: HttpResponse, durationMs: Long) {
        analytics.trackApiCall(response.request.url.toString(), durationMs)
    }
})
```

### Telemetry

```kotlin
SDKInitializer.setTelemetry(object : SDKTelemetry {
    override fun recordApiCall(endpoint: String, durationMs: Long, statusCode: Int, retries: Int) {
        Firebase.analytics.logEvent("api_call") { param("endpoint", endpoint) }
    }
    override fun recordError(type: String, endpoint: String?, message: String) { /* … */ }
    override fun recordSdkEvent(event: SDKEvent, detail: String?) { /* … */ }
})
```

### SSL Pinning

```kotlin
val env = SdkEnvironment(
    // …
    sslPins = SslPinConfig(pins = listOf("sha256/AAAA…", "sha256/BBBB…"))
)
```

---

## 🧩 Adding a New Feature

1. **Create the package** under `features/<yourfeature>/`
2. **Define models** with `@Serializable data class`
3. **Define endpoints** as a `sealed class`
4. **Implement the repository** — `interface` + `internal class Impl(apiClient: ApiClient)`
5. **Write the facade** — `class AppFacadeYourFeature : BaseFacade()` returning `Flow<SDKState<T>>`
6. **Register Koin** — `val yourFeatureModule = module { … }`

That's it — the entire HTTP pipeline, caching, tracing, and circuit breaking is inherited automatically.

---

## 🧪 Testing

```bash
# JVM unit tests
./gradlew :shared:testDebugUnitTest

# iOS unit tests (requires Xcode + Simulator)
./gradlew :shared:iosSimulatorArm64Test

# Android integration build
./gradlew :shared:assembleDebug

# iOS framework
./gradlew :shared:linkDebugFrameworkIosSimulatorArm64
```

---

## 📦 Dependencies

| Library | Version | Purpose |
|---|---|---|
| Ktor Client | 3.1.3 | HTTP engine (OkHttp / Darwin) |
| kotlinx.coroutines | 1.10.2 | Async + Flow |
| kotlinx.serialization | 1.8.1 | JSON parsing |
| kotlinx.datetime | 0.6.1 | Platform-agnostic timestamps |
| Koin | 4.1.0 | Dependency injection |
| SKIE | 0.10.10 | Swift-friendly coroutine/Flow bridging |
| uuid (benasher44) | 0.8.4 | Trace IDs |

---

## 🤝 Contributing

Pull requests are welcome! Please open an issue first to discuss what you'd like to change.

1. Fork the repo
2. Create a feature branch (`git checkout -b feature/my-feature`)
3. Commit your changes
4. Open a PR against `main`

---

## 📄 License

```
Copyright 2024 KMP Platform Kit Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
