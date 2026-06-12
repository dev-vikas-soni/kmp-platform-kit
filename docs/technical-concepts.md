# 📚 Technical Concepts - KMP Platform Kit

> **Document Type:** Deep-dive technical reference  
> **Last Updated:** March 2026  
> **Audience:** Engineers who want to understand *why* things work the way they do - KMP internals, design decisions, patterns, and trade-offs

---

## Table of Contents

1. [Kotlin Multiplatform (KMP) - How It Works](#1-kotlin-multiplatform-kmp--how-it-works)
2. [expect / actual - Platform Abstraction](#2-expect--actual--platform-abstraction)
3. [Coroutines - Async Without Callbacks](#3-coroutines--async-without-callbacks)
4. [Kotlin Flow - Reactive Streams](#4-kotlin-flow--reactive-streams)
5. [Sealed Classes - Exhaustive Type Hierarchies](#5-sealed-classes--exhaustive-type-hierarchies)
6. [Koin - Dependency Injection](#6-koin--dependency-injection)
7. [kotlinx.serialization - Type-Safe JSON](#7-kotlinxserialization--type-safe-json)
8. [Ktor Client - Multiplatform Networking](#8-ktor-client--multiplatform-networking)
9. [SDKState - The State Machine Pattern](#9-sdkstate--the-state-machine-pattern)
10. [ApiResult - The Result Pattern](#10-apiresult--the-result-pattern)
11. [Facade Pattern - Clean Public API](#11-facade-pattern--clean-public-api)
12. [Repository Pattern - Data Access Abstraction](#12-repository-pattern--data-access-abstraction)
13. [Build-Time Feature Selection](#13-build-time-feature-selection)
14. [Thread Safety - JVM vs Native](#14-thread-safety--jvm-vs-native)
15. [Fat AAR vs Lean AAR - Dependency Packaging](#15-fat-aar-vs-lean-aar--dependency-packaging)
16. [XCFramework - iOS Distribution](#16-xcframework--ios-distribution)
17. [Kotlin/Native → Swift Interop](#17-kotlinnative--swift-interop)
18. [Retry with Exponential Backoff](#18-retry-with-exponential-backoff)
19. [Idempotent Initialization (Mutex + Double-Check)](#19-idempotent-initialization-mutex--double-check)
20. [Pluggable Logging - NoOp Default](#20-pluggable-logging--noop-default)
21. [ProGuard / R8 - Code Shrinking](#21-proguard--r8--code-shrinking)
22. [Gradle Version Catalog](#22-gradle-version-catalog)
23. [Design Decision Log](#23-design-decision-log)

---

## 1. Kotlin Multiplatform (KMP) - How It Works

### What it is

Kotlin Multiplatform (KMP) allows you to write Kotlin code that compiles to **multiple targets** from a single source set. Unlike cross-platform frameworks that render UI (Flutter, React Native), KMP shares only the **business logic layer** - UI is always native.

### The compilation targets

```
Kotlin Source (commonMain)
         │
         ├─── Kotlin/JVM compiler ──────▶ Android (.class / .dex)
         │                                  └─ packages into .aar
         │
         └─── Kotlin/Native compiler ───▶ iOS (LLVM bitcode → .a / .framework)
                                            └─ packages into .xcframework
```

### Source sets in this SDK

| Source Set    | Target            | Contains                                              |
|---------------|-------------------|-------------------------------------------------------|
| `commonMain`  | All platforms     | Shared business logic, interfaces, models             |
| `androidMain` | Android (JVM)     | OkHttp engine, `@Synchronized` actual, Android logger |
| `iosMain`     | iOS (LLVM/Native) | Darwin engine, `AtomicReference` actual, iOS logger   |
| `commonTest`  | All platforms     | Shared tests (run on JVM and iOS Simulator)           |

### Why not just use a Java library?

Java bytecode cannot run on iOS (no JVM). Kotlin/Native compiles to machine code via LLVM, producing a native binary that the iOS runtime can link against directly. This is why the XCFramework has no external dependencies - everything is compiled in.

---

## 2. `expect` / `actual` - Platform Abstraction

### The problem it solves

Some functionality is inherently platform-specific: HTTP engines, threading primitives, loggers. `expect/actual` lets `commonMain` declare an interface and each platform provide its own implementation - all checked at compile time.

### How it works

```
commonMain:
    expect class PlatformConfig {
        fun getHeaders(): Map<String, String>
        fun setAuthToken(token: String)
    }

androidMain:
    actual class PlatformConfig {
        // Uses synchronized {} blocks for thread safety on JVM
        @Volatile private var headers = mapOf<String, String>()
        actual fun getHeaders() = synchronized(this) { headers }
        actual fun setAuthToken(token: String) = synchronized(this) {
            headers = headers + ("authorization" to "Bearer $token")
        }
    }

iosMain:
    actual class PlatformConfig {
        // Uses AtomicReference for thread safety on Kotlin/Native
        private val headersRef = AtomicReference(mapOf<String, String>())
        actual fun getHeaders() = headersRef.value
        actual fun setAuthToken(token: String) {
            // CAS loop: compare-and-swap until successful
            do {
                val current = headersRef.value
                val updated = current + ("authorization" to "Bearer $token")
            } while (!headersRef.compareAndSet(current, updated))
        }
    }
```

### `expect/actual` in this SDK

| Declaration                  | Android actual               | iOS actual                |
|------------------------------|------------------------------|---------------------------|
| `HttpClientFactory`          | `OkHttp` engine              | `Darwin` engine           |
| `PlatformConfig`             | `synchronized` + `@Volatile` | `AtomicReference`         |
| `PlatformLogger`             | `Log.d/i/w/e`                | `platform_log` / `NSLog`  |
| `JvmSynchronized` annotation | `@kotlin.jvm.Synchronized`   | no-op (Native handles it) |

---

## 3. Coroutines - Async Without Callbacks

### What they are

Kotlin coroutines are **lightweight threads** managed by the Kotlin runtime. A coroutine can `suspend` - pause execution without blocking a thread - and resume later, possibly on a different thread.

### Key concepts used in the SDK

#### `suspend fun` - Non-blocking function call

```kotlin
// This function suspends the coroutine (not the thread) while waiting for network response
suspend fun getInventories(customerNo: String): ApiResult<InventoryListModel> {
    return apiClient.get(path = ".../$customerNo/inventories") { json ->
        Json.decodeFromString<InventoryListModel>(json)
    }
    // Execution resumes here after network response - no callback needed
}
```

#### `CoroutineScope` - Lifecycle-bound execution context

```kotlin
// Android ViewModel: viewModelScope cancels all coroutines when ViewModel is destroyed
viewModelScope.launch {
    val result = facade.getInventories(customerNo = "2052008238")
    // handle result - safe, no memory leak
}
```

#### `Mutex` - Cooperative lock for coroutines

```kotlin
private val initMutex = Mutex()

suspend fun ensureInitialized() {
    if (initialized) return  // fast path - no lock needed
    initMutex.withLock {
        if (initialized) return  // double-check after acquiring lock
        // perform initialization
        initialized = true
    }
}
```

A `Mutex` is coroutine-aware: it suspends (not blocks) waiting coroutines, so the thread is free to do other work. This is why `ensureInitialized()` can be called safely from 50 concurrent coroutines without blocking 50 threads.

### Kotlin/Native coroutines

On iOS, Kotlin/Native's strict memory model historically required special handling (frozen objects, `@ThreadLocal`). With modern Kotlin/Native (1.6+) and the new memory manager, this is largely transparent. The SDK's coroutines work the same on both platforms.

---

## 4. Kotlin Flow - Reactive Streams

### What it is

`Flow<T>` is a cold, asynchronous data stream. Cold means it only executes when collected. It can emit multiple values over time, unlike a `suspend fun` that returns one value.

### How the SDK uses Flow

```kotlin
// sdkStateFlow { } creates a Flow that:
//   1. Emits SDKState.Loading immediately
//   2. Calls the suspend lambda
//   3. Emits the result (Success / ErrorBody / Error)
fun sdkStateFlow(
    block: suspend () -> ApiResult<T>
): Flow<SDKState<T>> = flow {
    emit(SDKState.Loading)           // Step 1: subscriber sees Loading immediately
    val result = block()             // Step 2: suspend - waits for network
    emit(result.toSDKState())        // Step 3: emit final state
}
```

### `StateFlow` - UI-friendly variant

```kotlin
// In ViewModel: StateFlow holds the current value and replays it to new subscribers
val inventoryState: StateFlow<SDKState<InventoryListModel>> =
    facade.getInventoriesFlow(customerNo = "2052008238")
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,  // only active when observed
            initialValue = SDKState.Loading
        )
```

`StateFlow` is like `LiveData` but:

- Coroutine-native (no Android lifecycle dependency)
- Works in `commonMain` (no Android imports needed)
- Always has a current value (no `null` initial state)

### Why this matters for host apps

Host apps collect `StateFlow` in Composables or XML views. The SDK emits `Loading` before making any network call, so the UI always has a state to show - no "undefined" period.

---

## 5. Sealed Classes - Exhaustive Type Hierarchies

### What they are

A sealed class is a class with a **closed, known set of subclasses**. The compiler can enforce exhaustive `when` expressions - every possible case must be handled or you get a compile error.

### `ApiResult<T>` - Why each variant exists

```kotlin
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: Int, val message: String?) : ApiResult<Nothing>()
    data object NetworkError : ApiResult<Nothing>()
    data object Cancelled : ApiResult<Nothing>()
}
```

| Variant        | Design reason                                              |
|----------------|------------------------------------------------------------|
| `Success`      | Carries the parsed data - type-safe, no nullability        |
| `Failure`      | HTTP-level error with code + message - actionable          |
| `NetworkError` | Distinguished from HTTP errors - different UI treatment    |
| `Cancelled`    | Coroutine cancellation is not an error - should be ignored |

Why separate `NetworkError` from `Failure`? Because they require different user actions:

- `Failure(401, ...)` → show auth error, trigger re-login
- `NetworkError` → show "check your connection" screen

### `SDKState<T>` - Adding the Loading state

```kotlin
sealed class SDKState<out T> {
    data object Loading : SDKState<Nothing>()
    data class Success<T>(val data: T) : SDKState<T>()
    data class ErrorBody(val code: Int, val message: String, val errorCode: String?) : SDKState<Nothing>()
    data class Error(val message: String, val isNetworkError: Boolean) : SDKState<Nothing>()
}
```

`SDKState` adds `Loading` (which `ApiResult` doesn't have - it's a point-in-time result) and `ErrorBody` (richer error info including the API error code like `"EX_402_115"`).

### Exhaustive when - compile-time safety

```kotlin
// The Kotlin compiler FORCES you to handle all cases
when (result) {
    is ApiResult.Success -> { /* must handle */
    }
    is ApiResult.Failure -> { /* must handle */
    }
    ApiResult.NetworkError -> { /* must handle */
    }
    ApiResult.Cancelled -> { /* must handle */
    }
    // Forget one → compile error, not a runtime crash
}
```

---

## 6. Koin - Dependency Injection

### What Koin is

Koin is a **pure Kotlin DI framework** with no code generation. Unlike Dagger/Hilt, it has no annotation processing - all bindings are declared in plain Kotlin lambdas and resolved at runtime using a service locator.

### Why Koin vs Dagger/Hilt

| Aspect                  | Koin            | Dagger/Hilt           |
|-------------------------|-----------------|-----------------------|
| KMP support             | ✅ Multiplatform | ❌ JVM/Android only    |
| Code generation         | ❌ None          | ✅ Required (kapt/ksp) |
| Compile-time validation | ❌ Runtime       | ✅ Compile-time        |
| Setup complexity        | Low             | High                  |
| iOS compatibility       | ✅ Yes           | ❌ No                  |

Koin's lack of annotation processing makes it the only practical DI framework for `commonMain` code that must compile to both JVM and iOS LLVM.

### How Koin is used in the SDK

```kotlin
// Module declaration (build-time, not runtime)
val yourfeatureModule: Module = module {
    single<YourFeatureRepository> {
        YourFeatureRepositoryImpl(apiClient = get())
        //                                              ^ resolves ApiClient from coreModule
    }
}

// At init time - SDK creates the Koin application
startKoin {
    modules(coreModule + FeatureModules.all + runtimeModule + additionalModules)
}

// Facades use KoinComponent for lazy resolution
object AppFacadeYourFeature : KoinComponent {
    private val repository: YourFeatureRepository by inject()
    //                                                  ^ resolved lazily on first call
}
```

### Why the SDK starts Koin (not the host app)

The SDK is a self-contained library. If the host app had to call `startKoin`, it would need to know about all SDK internals. Instead:

- The SDK owns the DI lifecycle entirely
- Host apps can pass `additionalModules` to merge their own bindings
- This prevents the "Koin already started" conflict in most cases

---

## 7. `kotlinx.serialization` - Type-Safe JSON

### What it is

`kotlinx.serialization` is JetBrains' official serialization library for Kotlin. Unlike Gson or Moshi, it:

- Works with Kotlin/Native (no reflection required)
- Uses compile-time code generation via the Kotlin compiler plugin
- Is fully multiplatform (`commonMain` compatible)

### How it works

```kotlin
// Annotate data class at compile time
@Serializable
data class InventoryDetail(
    val inventoryNumber: Int,
    val inventoryName: String,
    val totalValue: String,
    val status: String,
    val createdDate: String,
    val lastUpdatedOn: String
)

// At runtime - deserialization using the generated serializer
val response = Json.decodeFromString<YourFeatureResponse>(jsonString)
// No reflection. No runtime type-probing. Compile-time safe.
```

### `JsonProvider` - Centralized configuration

The SDK uses one `Json` instance configured for production resilience:

```kotlin
val json = Json {
    ignoreUnknownKeys = true   // New API fields don't break old SDK versions
    encodeDefaults = true    // Always encode default values in requests
    explicitNulls = false   // Don't serialize null fields - smaller payloads
    isLenient = true    // Accept unquoted strings, comments, trailing commas
    coerceInputValues = true   // null → default for non-nullable fields (forward compat)
}
```

**`ignoreUnknownKeys = true` is critical for SDK longevity.** If the API adds a field and the SDK doesn't know about it, it simply ignores it rather than throwing an exception. This prevents SDK version lockout.

---

## 8. Ktor Client - Multiplatform Networking

### What Ktor is

Ktor is JetBrains' multiplatform HTTP client. It has a common API in `commonMain` and **platform-specific engines** as actual implementations:

| Platform | Engine   | Underlying Library                                         |
|----------|----------|------------------------------------------------------------|
| Android  | `OkHttp` | Square's OkHttp - industry-standard Android HTTP client    |
| iOS      | `Darwin` | Apple's `URLSession` / `CFNetwork` - native iOS networking |

### Why this architecture matters

**One networking API, two native implementations.** The SDK never uses Java's `HttpURLConnection` or any emulated network stack. On iOS, requests go through Apple's `URLSession` - the same stack that Swift apps use. On Android, they go through OkHttp. Both provide:

- Native TLS / certificate validation
- System proxy settings
- Background transfer support
- Platform-appropriate connection pooling

### How the SDK uses Ktor

```kotlin
// KtorApiClient wraps Ktor with SDK-specific behavior
class KtorApiClient(
    private val httpClient: HttpClient,  // platform-specific engine injected via DI
    private val retryConfig: RetryConfig
) : ApiClient {

    override suspend fun <T> get(
        path: String,
        params: Map<String, String>,
        responseParser: (String) -> T
    ): ApiResult<T> {
        return executeWithRetry {
            val response = httpClient.get(buildUrl(path, params)) {
                // Headers injected from PlatformConfig for every request
                PlatformConfig.getHeaders().forEach { (key, value) ->
                    header(key, value)
                }
            }
            when {
                response.status.isSuccess() -> ApiResult.Success(
                    responseParser(response.bodyAsText())
                )
                else -> ApiResult.Failure(
                    response.status.value,
                    response.bodyAsText()
                )
            }
        }
    }
}
```

### Ktor plugins used

| Plugin           | Purpose                                                            |
|------------------|--------------------------------------------------------------------|
| `HttpTimeout`    | Per-request connect/socket/request timeout                         |
| `DefaultRequest` | Injects static headers (`accept`, `platform`, `x-external-source`) |
| `Logging`        | Debug logging (active only when `debugMode = true`)                |

---

## 9. `SDKState` - The State Machine Pattern

### The concept

`SDKState<T>` models a UI screen as a **finite state machine** with exactly 4 states. Every screen that loads remote data goes through these states:

```
                    ┌─────────────┐
     Start ────────▶│   Loading   │
                    └──────┬──────┘
                           │ (network call completes)
              ┌────────────┼─────────────┐
              ▼            ▼             ▼
         ┌─────────┐ ┌──────────┐ ┌─────────┐
         │ Success │ │ErrorBody │ │  Error  │
         └─────────┘ └──────────┘ └─────────┘
```

### Why 4 states and not 3 (Success / Loading / Error)?

Single `Error` vs split `ErrorBody + Error`:

| Scenario               | `ErrorBody` | `Error`                  |
|------------------------|-------------|--------------------------|
| HTTP 401 Unauthorized  | ✅           |                          |
| HTTP 403 Forbidden     | ✅           |                          |
| HTTP 500 Server Error  | ✅           |                          |
| No internet connection |             | ✅ `isNetworkError=true`  |
| Timeout                |             | ✅ `isNetworkError=true`  |
| JSON parse error       |             | ✅ `isNetworkError=false` |
| Coroutine cancelled    |             | (not emitted - ignored)  |

The split allows the host app to show:

- `ErrorBody`: "Access denied: [API message]" with a specific action (re-login, contact support)
- `Error + isNetworkError=true`: "No internet connection" with a "Retry" button
- `Error + isNetworkError=false`: "Something went wrong" with a "Try again" button

### The `sdkStateFlow { }` builder

```kotlin
// Implemented as a simple Flow builder
fun <T> sdkStateFlow(block: suspend () -> ApiResult<T>): Flow<SDKState<T>> = flow {
    emit(SDKState.Loading)         // Subscriber sees Loading synchronously
    emit(block().toSDKState())     // Network call; subscriber sees result asynchronously
}
```

This pattern ensures the UI always has something to show immediately (`Loading`), even before the coroutine scheduler runs the actual network call.

---

## 10. `ApiResult` - The Result Pattern

### The concept

`ApiResult<T>` is an implementation of the **Result monad pattern** - a way to represent computations that can succeed or fail, without using exceptions for control flow.

### Why not exceptions?

```kotlin
// ❌ Exception-based approach - problems:
try {
    val data = apiClient.get(path)  // can throw IOException, HttpException, etc.
    // handle data
} catch (e: IOException) {
    // handle network error
} catch (e: HttpException) {
    // handle HTTP error - but what code? Is it 401? 500?
} catch (e: SerializationException) {
    // handle parse error
}
// Problem: caller must know which exceptions to catch. Errors are invisible in type signatures.
```

```kotlin
// ✅ ApiResult approach - problems solved:
val result = apiClient.get(path)   // return type tells you it can fail
when (result) {
    is ApiResult.Success -> handle(result.data)
    is ApiResult.Failure -> handleError(result.code, result.message)
    ApiResult.NetworkError -> handleNetworkError()
    ApiResult.Cancelled -> { /* ignore */
    }
}
// Compiler forces you to handle all cases. Error handling is explicit and type-safe.
```

### `map` and `flatMap` - Functional composition

```kotlin
// map: transform Success data without handling errors
val inventoryNames: ApiResult<List<String>> = result
    .map { model -> model.inventories.map { it.inventoryName } }

// flatMap: chain operations that can each fail
val processedResult: ApiResult<ProcessedModel> = result
    .flatMap { model ->
        if (model.isSuccess) {
            ApiResult.Success(processModel(model))
        } else {
            ApiResult.Failure(-1, model.errorMessage)
        }
    }
```

These are internal utilities (not exposed to host apps) that keep repository code clean and functional.

---

## 11. Facade Pattern - Clean Public API

### The concept

The Facade pattern provides a **simplified, unified interface** to a complex subsystem. In the SDK, facades are the only public-facing classes for feature functionality. They hide the repository, DI, error mapping, and everything else.

### Implementation

```kotlin
// The facade is an object (singleton) that implements KoinComponent
object AppFacadeYourFeature : KoinComponent {

    // DI: repository is resolved lazily from Koin - never null, never manually created
    private val repository: YourFeatureRepository by inject()

    // --- Suspend-based API ---
    suspend fun getInventories(
        customerNo: String,
        order: String? = null
    ): ApiResult<InventoryListModel> {
        return repository.getInventories(
            YourFeatureQuery(customerNo = customerNo, order = order)
        )
    }

    // --- Flow-based API (recommended for UI) ---
    fun getInventoriesFlow(
        customerNo: String,
        order: String? = null
    ): Flow<SDKState<InventoryListModel>> = sdkStateFlow {
        getInventories(customerNo, order)
    }
}
```

### Why object (singleton)?

- No instantiation needed by the host app - just call `AppFacadeYourFeature.getInventories(...)`
- On iOS, Kotlin `object` maps to a `.shared` singleton: `AppFacadeYourFeature.shared.getInventories(...)`
- DI dependencies are resolved lazily on first use - no initialization ordering issues

### Why two API styles?

| Style                          | When to use                                                                 |
|--------------------------------|-----------------------------------------------------------------------------|
| `suspend fun getInventories()` | When the caller manages the coroutine scope and loading state               |
| `fun getInventoriesFlow()`     | When the caller wants `Loading` emitted automatically (ViewModel → Compose) |

---

## 12. Repository Pattern - Data Access Abstraction

### The concept

The Repository pattern abstracts the data source behind an interface. Code that uses repositories doesn't know (or care) whether data comes from network, cache, or a test double.

### Structure in the SDK

```
YourFeatureRepository (interface - internal)
    ├── defines: suspend fun getInventories(query: YourFeatureQuery): ApiResult<InventoryListModel>
    │
    └── YourFeatureRepositoryImpl (class - internal)
            ├── takes: ApiClient (injected via DI)
            ├── builds: URL path from query parameters
            ├── calls: ApiClient.get(path, params, parser)
            └── maps: YourFeatureResponse DTO → InventoryListModel (via InventoryMapper)
```

### Why interface + implementation?

1. **Testability:** Tests can use `FakeApiClient` instead of the real one without changing any production code
2. **Future flexibility:** A caching layer, offline database, or mock server could be added by swapping the implementation
3. **DI contract:** Koin binds `YourFeatureRepository → YourFeatureRepositoryImpl`; tests can override this binding

### DTO → Domain model mapping

The repository is responsible for mapping raw API DTOs (Data Transfer Objects) to domain models:

```
Raw API JSON
    ↓ (kotlinx.serialization)
YourFeatureResponse (DTO - @Serializable, internal)
    ↓ (InventoryMapper.toModel())
InventoryListModel (domain model - data class, public)
```

Why two separate models?

- **DTO** matches the API wire format exactly (raw timestamps, snake_case, etc.)
- **Domain model** matches what the host app needs (formatted dates, renamed fields, computed properties)
- Separation means API changes don't force host app model changes

---

## 13. Build-Time Feature Selection

### The problem it solves

A large SDK with many features adds binary size even if the host app only needs one feature. Build-time selection ensures unused code is **never compiled into the artifact**.

### How it works

```
gradle.properties or command line:
    sdk.features=yourfeature

shared/build.gradle.kts build script:
    1. Lists all feature folders under features/
    2. Filters: only include folders listed in sdk.features
    3. Excludes source files of disabled features from compilation
    4. Generates FeatureModules.kt containing ONLY enabled modules

Compilation:
    → Disabled features never enter the Kotlin compiler
    → They don't exist in the binary - not even as stubs
```

### Auto-generated `FeatureModules.kt`

```kotlin
// Generated by the build script - DO NOT EDIT MANUALLY
// This file is overwritten on every build
object FeatureModules {
    val all: List<Module> = listOf(
        yourfeatureModule   // only modules whose feature folder is enabled
    )
}
```

### Why this is better than conditional imports

If `FeatureModules.kt` were hand-written:

- Engineers might forget to add new features
- Removing a feature would require code changes in the core module
- It's error-prone and violates the open/closed principle

With auto-generation:

- Add a folder → feature is automatically included in the next build
- Remove a folder → feature is automatically excluded
- No changes to core code ever needed

---

## 14. Thread Safety - JVM vs Native

### The fundamental difference

On **JVM (Android):** Multiple threads share a heap. Memory visibility is guaranteed by `synchronized`, `@Volatile`, or `java.util.concurrent` primitives.

On **Kotlin/Native (iOS):** The old memory model used "frozen" objects (immutable once passed to another thread). The new memory model (Kotlin 1.7.20+) is more permissive, but `synchronized {}` blocks are still no-ops because there's no JVM monitor lock.

### The SDK's approach

```
PlatformConfig (commonMain expect declaration)
├── androidMain actual → uses synchronized {} + @Volatile
│       synchronized(lock) { headers = headers + (key to value) }
│       @Volatile var headers → safe cross-thread reads
│
└── iosMain actual → uses AtomicReference + CAS loop
        val ref = AtomicReference(emptyMap<String, String>())
        do {
            val current = ref.value
            val updated = current + (key to value)
        } while (!ref.compareAndSet(current, updated))
        // Compare-And-Swap: atomic update without locks
```

### Why AtomicReference on iOS?

`AtomicReference` provides **lock-free thread safety** via hardware CAS (Compare-And-Swap) instructions. CAS atomically checks if a value is still what you expect and updates it only if so. If another thread updated it first, the CAS fails and you retry with the new value. No locks → no deadlocks → no thread-blocking.

### `@JvmSynchronized` on iOS

The `JvmSynchronized` annotation is a no-op on Kotlin/Native because:

1. There's no JVM monitor lock concept
2. Kotlin/Native's memory model ensures that properly structured concurrent access is safe without it
3. `SDKInitializer.init()` is designed to be called from the main thread where calling it on multiple threads simultaneously is not a valid use case

---

## 15. Fat AAR vs Lean AAR - Dependency Packaging

### What an AAR is

An Android Archive (AAR) is a ZIP file containing:

- `classes.jar` - compiled Kotlin/Java bytecode
- `res/` - Android resources
- `AndroidManifest.xml`
- `consumer-rules.pro` - ProGuard rules for consuming apps

### The dependency problem

The SDK depends on Ktor, Koin, and kotlinx-serialization. Normally, these would be declared as transitive dependencies - the host app would download them separately and ensure version compatibility.

**Problem:** If the host app already uses Ktor `2.x` and the SDK requires `3.1.3`, there could be a version conflict - or worse, the host app might need to upgrade all their Ktor code just to use the SDK.

### Fat AAR - The solution

The `packageFatAar` Gradle task uses the `fat-aar.gradle.kts` script to:

1. Resolve all SDK transitive dependencies to their JAR files
2. Merge them into `classes.jar` inside the AAR
3. Strip `META-INF/` signatures (required for successful merging)
4. Exclude libraries the host app always has (kotlin-stdlib, coroutines-android, OkHttp)

```
shared-release-fat.aar
└── classes.jar
    ├── com.droidunplugged.kmp_platform_kit/...     (SDK code)
    ├── io/ktor/...                            (Ktor - merged in)
    ├── org/koin/...                           (Koin - merged in)
    └── kotlinx/serialization/...             (kotlinx.serialization - merged in)
```

**Result:** Host app adds one dependency, zero version conflicts, zero extra configuration.

### Why not always use fat AAR?

If the host app also uses Ktor (directly), including it in the fat AAR would create two copies of Ktor classes at runtime - a `DuplicateClassException`. That's why the lean AAR exists for teams with fine-grained control.

---

## 16. XCFramework - iOS Distribution

### What an XCFramework is

An XCFramework is Apple's multi-architecture binary format that can contain:

- `arm64` - real device (iPhone, iPad)
- `x86_64` - Intel Mac simulator
- `arm64` simulator - Apple Silicon Mac simulator

One `.xcframework` bundle works everywhere.

### Why static framework (`isStatic = true`)

The SDK is built as a **static framework** (`.a` static library packaged as a framework):

```
// shared/build.gradle.kts
kotlin {
    iosX64 {
        binaries {
            framework("Shared") {
                isStatic = true  // ← static linking
            }
        }
    }
    // same for iosArm64, iosSimulatorArm64
}
```

Static linking means:

1. All SDK code + Ktor + Koin + kotlinx-serialization is compiled into a single binary at build time
2. At runtime, there's no dynamic loader - everything is already in the app binary
3. No external dependencies are needed (equivalent to Android fat AAR)
4. Slightly larger app binary vs dynamic framework, but much simpler distribution

### Why not dynamic framework?

Dynamic frameworks require Embed & Sign AND runtime dependency management. Since Kotlin doesn't ship shared libraries for its standard library on iOS (it's always statically linked), using a dynamic framework would create complexities with duplicate symbols. Static is the idiomatic choice for Kotlin/Native iOS frameworks.

---

## 17. Kotlin/Native → Swift Interop

### How Kotlin/Native generates Swift-compatible APIs

Kotlin/Native produces Objective-C headers (`.h`) that Swift can import via the Objective-C runtime bridge. The Kotlin compiler applies **name mangling rules** to make the API idiomatic in Swift/Objective-C.

### Key transformations

#### Kotlin `object` (singleton) → `.shared` property

```kotlin
// Kotlin (commonMain)
object SDKInitializer {
    fun init(...) {}
}
```

```swift
// Swift (generated)
SDKInitializer.shared.doInit(...)
//             ^^^^^^ Kotlin companion/object singleton → .shared
```

#### `init` function → `doInit`

The Kotlin compiler cannot expose a function named `init` to Swift because `init` is a Swift/Objective-C constructor keyword. It's automatically renamed to `doInit`.

#### `suspend fun` → `async throws`

```kotlin
// Kotlin
suspend fun getInventories(customerNo: String): ApiResult<InventoryListModel>
```

```swift
// Swift (generated via Swift concurrency bridge)
func getInventories(customerNo: String) async throws -> ApiResult<InventoryListModel>
```

Kotlin coroutines are bridged to Swift's `async/await` via the Kotlin/Native coroutine interop layer.

#### Sealed classes → class hierarchy

```kotlin
// Kotlin sealed class
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Failure(val code: Int, val message: String?) : ApiResult<Nothing>()
    data object NetworkError : ApiResult<Nothing>()
    data object Cancelled : ApiResult<Nothing>()
}
```

```swift
// Swift (generated) - NOT a Swift enum
class ApiResult<T>: KotlinBase {
}

class ApiResultSuccess<T>: ApiResult<T> {
    var data: T
}

class ApiResultFailure: ApiResult<KotlinNothing> {
    var code: Int32;
    var message: String?
}

class ApiResultNetworkError: ApiResult<KotlinNothing> {
}

class ApiResultCancelled: ApiResult<KotlinNothing> {
}

// Pattern matching in Swift:
switch result {
case let s as ApiResultSuccess<InventoryListModel>: use(s.data)
case let f as ApiResultFailure:                     handleError(f.code)
case is ApiResultNetworkError:                      showNoInternet()
default: break
}
```

#### `Int` → `Int32`, `Long` → `Int64`

Kotlin `Int` is 32-bit and maps to Swift `Int32`. Kotlin `Long` is 64-bit and maps to `Int64`. This is why `inventoryNumber` in Swift is `Int32`, not Swift's native `Int` (which is 64-bit on 64-bit devices).

---

## 18. Retry with Exponential Backoff

### The concept

Transient failures (network blips, server overload) are common in mobile apps. Retrying immediately often fails again. **Exponential backoff** increases the wait time between retries, giving the server or network time to recover.

### Implementation in `KtorApiClient`

```kotlin
private suspend fun <T> executeWithRetry(block: suspend () -> ApiResult<T>): ApiResult<T> {
    var attempt = 0
    var delayMs = retryConfig.initialBackoffMs

    while (true) {
        val result = try {
            block()
        } catch (e: CancellationException) {
            return ApiResult.Cancelled  // propagate immediately, never retry
        } catch (e: IOException) {
            ApiResult.NetworkError       // network error - may be retriable
        } catch (e: Exception) {
            ApiResult.Failure(-2, e.message)  // unexpected - not retriable
        }

        // Decide whether to retry
        val shouldRetry = attempt < retryConfig.maxAttempts - 1 && when (result) {
            is ApiResult.Failure -> result.code >= 500    // 5xx only
            ApiResult.NetworkError -> true                   // always retry
            else -> false                  // 4xx, Success - no retry
        }

        if (!shouldRetry) return result

        delay(delayMs)         // suspend (not block) - thread is free
        delayMs *= 2           // exponential: 500ms → 1000ms → 2000ms → ...
        attempt++
    }
}
```

### Why not retry 4xx?

HTTP 4xx errors mean the **client** made a wrong request (bad auth, bad parameters, resource not found). Retrying the same bad request will always get the same 4xx response. Retrying only wastes bandwidth and battery.

### `RetryConfig.NO_RETRY`

```kotlin
object NO_RETRY : RetryConfig(maxAttempts = 1, initialBackoffMs = 0L)
```

`maxAttempts = 1` means the while loop runs once and returns immediately - effectively disabling retry with zero overhead.

---

## 19. Idempotent Initialization (Mutex + Double-Check)

### The problem

```kotlin
// What if ensureInitialized() is called from 3 coroutines simultaneously?
// Coroutine 1: initialized = false → starts init()
// Coroutine 2: initialized = false → starts init() AGAIN ← problem!
// Coroutine 3: initialized = false → starts init() AGAIN ← problem!
```

### The solution: Double-checked locking with coroutine Mutex

```kotlin
private var initialized = false
private val initMutex = Mutex()

suspend fun ensureInitialized() {
    if (initialized) return  // Fast path: no lock, no suspend (O(1))

    initMutex.withLock {     // Only one coroutine enters at a time
        if (initialized) return  // Second check inside lock (double-check pattern)

        val credentials = credentialProvider?.invoke()
            ?: error("SDKInitializer: credentialProvider not set")

        init(
            baseUrl = credentials.baseUrl,
            authToken = credentials.authToken,
            apiGuid = credentials.apiGuid,
            clientId = credentials.clientId,
            apiKey = credentials.apiKey
        )

        initialized = true
    }
    // Lock released - all suspended coroutines wake up and hit the fast path
}
```

### Why the double-check?

1. Coroutine A and B both see `initialized = false`
2. Both call `initMutex.withLock { }`
3. A gets the lock first, B suspends
4. A initializes, sets `initialized = true`, releases lock
5. B acquires lock → checks `initialized` again → sees `true` → returns without reinitializing

Without the second check inside the lock, B would reinitialize even though A already did.

### Why `Mutex` instead of `synchronized`?

`synchronized {}` **blocks** the thread. If `init()` takes 200ms (loading Koin, creating HttpClient), the thread is blocked for 200ms, preventing other work. `Mutex.withLock {}` **suspends** the coroutine - the thread is free to execute other coroutines. This is the coroutine-idiomatic approach and avoids thread starvation.

---

## 20. Pluggable Logging - NoOp Default

### The concept

A logger that does nothing by default is called a **Null Object pattern** implementation. The `NoOpLogger` class implements `Logger` with empty method bodies, satisfying the interface contract with zero overhead.

### Why NoOp by default?

In production, logging sensitive data (even accidentally) is a security risk. By defaulting to NoOp:

- No logs ever leak without explicit opt-in
- Security auditors see a deterministic "no logging" default
- App Store / Play Store reviewers see no unexpected data flows

### The Logger interface

```kotlin
interface Logger {
    fun d(tag: String, message: String)                          // Debug
    fun i(tag: String, message: String)                          // Info
    fun w(tag: String, message: String)                          // Warning
    fun e(tag: String, message: String, throwable: Throwable?)   // Error
}

object NoOpLogger : Logger {
    override fun d(tag: String, message: String) = Unit
    override fun i(tag: String, message: String) = Unit
    override fun w(tag: String, message: String) = Unit
    override fun e(tag: String, message: String, throwable: Throwable?) = Unit
}
```

### Thread safety

`PlatformLogger` holds a reference to the current `Logger`. On Android, this is `@Volatile` - if the logger is set from one thread, all other threads see the update immediately without synchronization.

---

## 21. ProGuard / R8 - Code Shrinking

### What ProGuard/R8 does

When building a release Android app, R8 (Google's replacement for ProGuard):

1. **Shrinks** - removes unused classes and methods
2. **Obfuscates** - renames classes/methods to short names (`a`, `b`, etc.)
3. **Optimizes** - inlines methods, removes dead branches

### Why the SDK bundles consumer rules

Without rules, R8 would:

- Remove SDK public API classes (they look "unused" from R8's perspective, as they're called by reflection or from Koin)
- Obfuscate class names, breaking Koin's service locator which uses class names at runtime
- Remove `@Serializable`-generated serializers, breaking JSON parsing

The SDK's `consumer-rules.pro` tells R8 what to keep:

```proguard
# Keep all public SDK API classes
-keep class com.droidunplugged.kmp_platform_kit.core.** { public *; }
-keep class com.droidunplugged.kmp_platform_kit.features.**.facade.** { public *; }
-keep class com.droidunplugged.kmp_platform_kit.features.**.models.** { public *; }
-keep class com.droidunplugged.kmp_platform_kit.features.**.endpoints.** { public *; }

# Keep kotlinx.serialization companion objects (serializers are generated classes)
-keepclassmembers class ** implements kotlinx.serialization.KSerializer { *; }

# Keep Koin
-keep class org.koin.** { *; }
```

Consumer rules are automatically applied when the host app minifies - no manual R8 configuration needed.

---

## 22. Gradle Version Catalog

### What it is

A **Version Catalog** is a centralized dependency declaration file (`gradle/libs.versions.toml`) that defines all library versions in one place. All `build.gradle.kts` files reference versions by alias, not by hardcoded strings.

### `libs.versions.toml` structure

```toml
[versions]
kotlin = "2.1.21"
ktor = "3.1.3"
koin = "4.1.0"
coroutines = "1.10.2"
serialization = "1.10.0"

[libraries]
ktor-client-core = { module = "io.ktor:ktor-client-core", version.ref = "ktor" }
ktor-client-okhttp = { module = "io.ktor:ktor-client-okhttp", version.ref = "ktor" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
```

### Benefits

1. **Single source of truth**: Upgrade Ktor in one line - all modules pick it up automatically
2. **Type-safe access in Gradle**: `libs.ktor.client.core` - IDE completion, no typos
3. **Conflict detection**: Gradle warns if two modules use different versions of the same library
4. **Shared with test code**: Same aliases work in `commonTest.dependencies`

---

## 23. Design Decision Log

Important decisions made during SDK design and the reasoning behind them.

### Decision 1: Koin over Dagger/Hilt

**Problem:** Need a DI framework that works in `commonMain` (both JVM and iOS LLVM).  
**Decision:** Use Koin.  
**Reason:** Koin is the only production-ready DI framework with Kotlin Multiplatform support. Dagger/Hilt requires annotation processing (kapt/ksp) which only works on JVM. Hilt is Android-specific.  
**Trade-off:** Koin validates DI bindings at runtime, not compile time. This means a missing binding throws at the first API call, not at build time.

---

### Decision 2: Fat AAR over lean AAR as default

**Problem:** Host apps must add many transitive dependencies to use the lean AAR.  
**Decision:** Build `shared-release-fat.aar` as the primary distribution artifact.  
**Reason:** Reduces integration friction from 6+ dependency lines to 1. Prevents version conflicts. The 3.3 MB size is acceptable given that modern apps commonly have 30–100 MB APKs.  
**Trade-off:** If host app also uses Ktor/Koin, there may be duplicate classes. Solution: use lean AAR or source module for those cases.

---

### Decision 3: In-memory credential storage only

**Problem:** SDK needs auth token, API key, client ID.  
**Decision:** Store only in `PlatformConfig` memory - never write to disk.  
**Reason:** Credentials stored on disk (SharedPreferences, UserDefaults, Keychain) require explicit security configuration. Host apps already manage credential persistence. The SDK is a stateless business logic layer, not an auth manager.  
**Trade-off:** SDK must be re-initialized after process restart. `SDKInitializer.configure` + `ensureInitialized` makes this seamless.

---

### Decision 4: `ApiResult` vs Exceptions for error handling

**Problem:** How should API errors be communicated to callers?  
**Decision:** Use `sealed class ApiResult<T>` instead of throwing exceptions.  
**Reason:** Exceptions in coroutine code can escape silently if not caught correctly. The sealed class forces the compiler to verify all error cases are handled. Error types are visible in the function signature.  
**Trade-off:** More verbose call sites than try/catch, but safer and more predictable.

---

### Decision 5: Static XCFramework (not dynamic)

**Problem:** How to distribute Kotlin/Native for iOS?  
**Decision:** Always build as static framework (`isStatic = true`).  
**Reason:** Kotlin's standard library and runtime are always statically linked on iOS. Mixing static Kotlin runtime with dynamic framework creates symbol duplication issues. Static framework is the idiomatic and supported configuration.  
**Trade-off:** Slightly larger host app binary vs separate dynamic framework, but no runtime symbol conflicts.

---

### Decision 6: Auto-generated `FeatureModules.kt` over manual registration

**Problem:** Adding a new feature requires updating `SDKInitializer` to register the Koin module.  
**Decision:** Auto-generate `FeatureModules.kt` from the `features/` folder at build time.  
**Reason:** Manual registration is error-prone (forgotten, misspelled) and violates the open/closed principle (modifying core for every new feature). Auto-generation makes adding features entirely additive.  
**Trade-off:** Requires strict naming convention (`val ${featureName}Module`) and build script involvement.

---

### Decision 7: NoOpLogger as default

**Problem:** Should the SDK log anything by default?  
**Decision:** Default to `NoOpLogger` - no logs emitted unless explicitly configured.  
**Reason:** Production apps must not leak data to logcat/console. Logs containing URLs, customer IDs, or response shapes could violate security policies. Opt-in is safer than opt-out.  
**Trade-off:** Engineers new to the SDK won't see any output until they explicitly set a logger - but this is documented and the behavior is intentional.