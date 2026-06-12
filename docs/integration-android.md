# 📱 Android Integration Guide - KMP Platform Kit

> **SDK Version:** `0.x` · **Kotlin:** `2.1.21` · **minSdk:** `24` · **compileSdk:** `36` · **Last Updated:** March 2026
>
> Complete guide - from building the AAR to production-ready usage patterns.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build the AAR](#2-build-the-aar)
3. [Add to Your Project](#3-add-to-your-project)
4. [Kotlin Version Compatibility](#4-kotlin-version-compatibility)
5. [Initialize the SDK](#5-initialize-the-sdk)
6. [Keep Headers Up to Date](#6-keep-headers-up-to-date)
7. [Make API Calls](#7-make-api-calls)
8. [Handle Logout / Account Switch](#8-handle-logout--account-switch)
9. [Enable Logging](#9-enable-logging)
10. [Query SDK Version (SDKInfo)](#10-query-sdk-version-sdkinfo)
11. [Retry Behavior](#11-retry-behavior)
12. [Koin Conflict Resolution](#12-koin-conflict-resolution)
13. [ProGuard / R8](#13-proguard--r8)
14. [Troubleshooting](#14-troubleshooting)
15. [Quick Reference](#15-quick-reference)

---

## 1. Prerequisites

| Requirement          | Minimum Version    | Notes                                        |
|----------------------|--------------------|----------------------------------------------|
| Kotlin               | `2.1.21`           | Must match or exceed SDK compilation version |
| Android `minSdk`     | `24` (Android 7.0) | Hard requirement - OkHttp engine             |
| Android `compileSdk` | `36`               | Required for compatibility                   |
| Gradle               | `8.x`              | Required for KMP artifact compatibility      |
| Java                 | `17`               | Required for Kotlin 2.x                      |

---

## 2. Build the AAR

Choose only the features you need - smaller AAR, faster builds.

### Using the convenience script

```bash
# All features
./scripts/build-android.sh

# Physical inventory only (recommended for POC)
./scripts/build-android.sh yourfeature

# Multiple features
./scripts/build-android.sh yourfeature,orders
```

### Using Gradle directly

```bash
# Build AAR
./gradlew :shared:assembleRelease -Psdk.features=yourfeature

# Then package as fat AAR (bundles all transitive dependencies)
./gradlew :shared:packageFatAar
```

### Build outputs

| File                                              | Size    | Use Case                                           |
|---------------------------------------------------|---------|----------------------------------------------------|
| `shared/build/outputs/aar/shared-release-fat.aar` | ~3.3 MB | **Recommended** - self-contained, zero extra deps  |
| `shared/build/outputs/aar/shared-release.aar`     | ~115 KB | Advanced - needs transitive deps declared manually |

> **How fat AAR works:** The `packageFatAar` task merges Ktor, Koin, and kotlinx-serialization JARs directly into the AAR's `classes.jar`. Libraries every Android app already has (kotlin-stdlib, coroutines, OkHttp) are intentionally excluded to avoid version conflicts and keep the size minimal.

---

## 3. Add to Your Project

### Option A - Fat AAR (recommended for local development)

Copy `shared-release-fat.aar` into your app's `libs/` folder, then:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/shared-release-fat.aar"))
    // ✅ That's it - no other SDK dependencies required
}
```

<details>
<summary><b>Option A-alt - Lean AAR (fine-grained version control)</b></summary>

If your app already uses Ktor/Koin/kotlinx-serialization and you want to control their
exact versions, use `shared-release.aar` and declare transitive deps manually:

```kotlin
// app/build.gradle.kts
dependencies {
    implementation(files("libs/shared-release.aar"))

    // Required - lean AAR does NOT bundle these
    implementation("io.ktor:ktor-client-core:3.1.3")
    implementation("io.ktor:ktor-client-okhttp:3.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
    implementation("io.insert-koin:koin-core:4.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
```

</details>

### Option B - Source Module (active development / contribution)

```kotlin
// settings.gradle.kts
include(":shared")
project(":shared").projectDir = File("/path/to/kmp-platform-kit/shared")

// app/build.gradle.kts
dependencies {
    implementation(project(":shared"))
    // ✅ Transitive deps resolved automatically
}
```

### Option C - Published Maven Artifact (production)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven {
            url = uri("https://maven.pkg.github.com/your-org/kmp-platform-kit")
            credentials {
                username = providers.gradleProperty("MAVEN_USERNAME").orNull ?: ""
                password = providers.gradleProperty("MAVEN_PASSWORD").orNull ?: ""
            }
        }
    }
}

// app/build.gradle.kts
dependencies {
    implementation("com.droidunplugged.kmp_platform_kit:shared-core:<version>")
    // ✅ Transitive deps resolved from POM automatically
}
```

### How to access SDK API docs

| Consumption Mode | How docs are delivered | Recommended experience |
|------------------|------------------------|------------------------|
| Maven artifact   | Dokka-generated `javadoc.jar` is attached to the publication | **Best** - Android Studio / IntelliJ Quick Documentation works automatically |
| Fat / lean AAR   | Binary only; IDE doc resolution is limited for file-based dependencies | Use generated HTML docs from `shared/build/docs/html/` or the CI `sdk-docs` artifact |

To generate docs locally:

```bash
bundle exec fastlane generate_docs

# or directly with Gradle
./gradlew :shared:dokkaHtml :shared:dokkaGfm
```

---

## 4. Kotlin Version Compatibility

The SDK is compiled with **Kotlin 2.1.21**. Your host app must use a compatible version.

| Host App Kotlin    | Compatible? | Notes                                         |
|--------------------|-------------|-----------------------------------------------|
| `2.1.21` or newer  | ✅ Yes       | Perfect match                                 |
| `2.1.0` – `2.1.20` | ⚠️ Maybe    | May fail with `Incompatible metadata version` |
| `< 2.1.0`          | ❌ No        | Must upgrade                                  |

**If you see this error:**

```
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is 2.2.0, expected version is 2.0.0.
```

**Fix:** Upgrade Kotlin in `build.gradle.kts`:

```kotlin
plugins {
    kotlin("android") version "2.1.21"
}
```

---

## 5. Initialize the SDK

Call `SDKInitializer.init(...)` **once**. Repeat calls are no-ops.

> **Note:** `init()` is a `suspend fun` - call from a coroutine scope (e.g. `lifecycleScope.launch { }`).

### Option A - Direct Init (credentials at app startup)

Use this when your credentials are available in `Application.onCreate()`:

```kotlin
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        SDKInitializer.init(
            baseUrl = "https://api.stage.example.com",  // env-specific
            authToken = jwtToken,                                  // Bearer JWT
            apiGuid = transactionId,                             // x-cah-api-guid
            clientId = BuildConfig.CLIENT_ID,                    // from env config
            apiKey = BuildConfig.API_KEY                        // from env config
        )
    }
}
```

### Option A-alt - Environment-based Init (recommended for multi-env apps)

Use strongly-typed `SdkEnvironment` objects instead of raw strings:

```kotlin
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer
import com.droidunplugged.kmp_platform_kit.core.config.SdkEnvironment
import com.droidunplugged.kmp_platform_kit.core.SslPinConfig

// Define environments once in your app
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

// At init
lifecycleScope.launch {
    SDKInitializer.init(
        environment = if (BuildConfig.DEBUG) Environments.STAGING else Environments.PRODUCTION,
        authToken = sessionManager.token,
        apiGuid = sessionManager.guid
    )
}
```

### Optional - Pre-Init Registration (call BEFORE init)

Configure advanced subsystems before initialising:

```kotlin
// Automatic token refresh (proactive + reactive 401 handling)
SDKInitializer.setTokenRefreshProvider(MyTokenRefreshProvider(authRepo))

// Telemetry (Firebase, Datadog, custom)
SDKInitializer.setTelemetry(MyFirebaseTelemetry(analytics))

// Server-pushed config (kill-switches, retry tuning)
SDKInitializer.setRemoteConfigProvider(MyRemoteConfigProvider(configApi))

// Circuit breaker tuning
SDKInitializer.setCircuitBreakerConfig(CircuitBreakerConfig(failureThreshold = 3))

// Custom HTTP middleware
SDKInitializer.addInterceptor(DeviceFingerprintInterceptor(deviceId))

// THEN init
SDKInitializer.init(...)
```

### Option B - Deferred Init (credentials available after login)

Use this when users must log in before you have valid credentials:

**Step 1 - Register the credential provider at app startup (no credentials needed yet):**

```kotlin
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer
import com.droidunplugged.kmp_platform_kit.core.SDKCredentials

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Register now - init() will be called lazily when first needed
        SDKInitializer.configure {
            SDKCredentials(
                baseUrl = resolveBaseUrl(prefsDataSource.getServerLocation()),
                authToken = sessionManager.getToken(),    // called lazily after login
                apiGuid = prefs.getTransactionId(),
                clientId = getDecodedClientId(),
                apiKey = getDecodedApiKey()
            )
        }
    }
}
```

**Step 2 - Call `ensureInitialized()` before any API call (safe from any coroutine):**

```kotlin
class InventoryViewModel : ViewModel() {

    fun loadInventories() {
        viewModelScope.launch {
            // Idempotent: first call initializes; subsequent calls are instant no-ops
            SDKInitializer.ensureInitialized()

            val result = AppFacadeYourFeature.getInventories(
                customerNo = "2052008238"
            )
            // handle result...
        }
    }
}
```

**How `ensureInitialized()` works:**

| Call                                 | Behavior                                                                     |
|--------------------------------------|------------------------------------------------------------------------------|
| First call                           | Acquires Mutex → invokes credential lambda → calls `init()` → releases Mutex |
| Concurrent calls (during first init) | Suspend until Mutex is released → then instant no-op                         |
| All subsequent calls                 | Instant no-op (flag check, no lock acquisition)                              |

### What each parameter becomes

| Parameter   | HTTP Header                     | Set By                                         |
|-------------|---------------------------------|------------------------------------------------|
| `baseUrl`   | _(URL prefix for all requests)_ | Your app - environment config                  |
| `authToken` | `authorization: Bearer <token>` | Your app - auth/login flow                     |
| `apiGuid`   | `x-cah-api-guid`                | Your app - correlation ID per session          |
| `clientId`  | `clientid`                      | Your app - Firebase Remote Config / env config |
| `apiKey`    | `x-api-key`                     | Your app - Firebase Remote Config / env config |

### Base URL per environment

| Environment | Base URL                               |
|-------------|----------------------------------------|
| Dev         | `https://api.dev.example.com`   |
| QA          | `https://api.qa.example.com`    |
| Stage       | `https://api.stage.example.com` |
| Production  | `https://api.example.com`       |

### Headers the SDK adds automatically (you never touch these)

| Header              | Value                                               | Source                            |
|---------------------|-----------------------------------------------------|-----------------------------------|
| `platform`          | `android`                                           | SDK constant                      |
| `user-agent`        | `KmpPlatformKit/x.y.z (Android; API-{SDK_INT}; {model})` | SDK reads `Build.VERSION.SDK_INT` |
| `x-external-source` | `mobile`                                            | SDK constant                      |
| `accept`            | `application/json`                                  | SDK constant                      |
| `accept-encoding`   | `gzip`                                              | OkHttp automatic                  |
| `connection`        | `Keep-Alive`                                        | OkHttp automatic                  |
| `host`              | derived from `baseUrl`                              | OkHttp automatic                  |

---

## 6. Keep Headers Up to Date

### Token refresh (~every 15 minutes)

```kotlin
// Only replaces `authorization` header - all other values remain unchanged
SDKInitializer.updateAuthToken(newJwtToken)
```

### After re-login (new session GUID + fresh token)

```kotlin
// Replaces both `authorization` and `x-cah-api-guid`
SDKInitializer.updateDynamicHeaders(
    authToken = freshToken,
    apiGuid = "new-guid-after-login"
)
```

### Header lifecycle

```
App Start
  │
  ├─ SDKInitializer.init(baseUrl, token, guid, clientId, apiKey)
  │    → PlatformConfig stores all 5 values in memory
  │
  ├─ Every ~15 min → SDKInitializer.updateAuthToken(newToken)
  │    → Only authorization header updated (thread-safe CAS on Android)
  │
  ├─ Re-login → SDKInitializer.updateDynamicHeaders(token, guid)
  │    → authorization + x-cah-api-guid updated atomically
  │
  └─ Logout → SDKInitializer.reset()
       → All values cleared, Koin stopped
       → Use configure() + ensureInitialized() or init() after next login
```

---

## 7. Make API Calls

The SDK provides **two call styles**:

| Style                                 | Best For                                     | Returns             | Loading State                         |
|---------------------------------------|----------------------------------------------|---------------------|---------------------------------------|
| **Flow-based** (`getInventoriesFlow`) | UI / Compose / ViewModel                     | `Flow<SDKState<T>>` | ✅ Automatic (`Loading` emitted first) |
| **Suspend** (`getInventories`)        | Repositories / non-UI / custom orchestration | `ApiResult<T>`      | ❌ You manage it                       |

---

### Style A - Flow-based with `SDKState` ✅ Recommended for UI

`SDKState<T>` directly maps to the 4 UI states your app must handle:

| `SDKState`           | Your UI State               | When                                  |
|----------------------|-----------------------------|---------------------------------------|
| `SDKState.Loading`   | Shimmer / spinner           | Request in flight                     |
| `SDKState.Success`   | Show data                   | HTTP 200 + valid data                 |
| `SDKState.ErrorBody` | API error dialog            | Structured error (4xx/5xx with body)  |
| `SDKState.Error`     | Retry / connectivity screen | Network error, timeout, parse failure |

**ViewModel:**

```kotlin
import com.droidunplugged.kmp_platform_kit.core.SDKState
import com.droidunplugged.kmp_platform_kit.features.yourfeature.facade.AppFacadeYourFeature
import com.droidunplugged.kmp_platform_kit.features.yourfeature.endpoints.YourFeatureEndpoints
import com.droidunplugged.kmp_platform_kit.features.yourfeature.models.InventoryListModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class InventoryViewModel : ViewModel() {

    // One-liner: automatically emits Loading, then Success/ErrorBody/Error
    val inventoryState: StateFlow<SDKState<InventoryListModel>> =
        AppFacadeYourFeature.getInventoriesFlow(
            customerNo = "2052008238",
            order = YourFeatureEndpoints.ORDER_BY_LAST_UPDATED  // optional
        ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = SDKState.Loading
        )
}
```

**Compose UI:**

```kotlin
@Composable
fun InventoryScreen(viewModel: InventoryViewModel = viewModel()) {
    val state by viewModel.inventoryState.collectAsStateWithLifecycle()

    when (state) {
        is SDKState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        is SDKState.Success -> {
            val model = (state as SDKState.Success<InventoryListModel>).data
            // model.status        → "SUCCESS"
            // model.inventories   → List<Inventories>
            InventoryList(inventories = model.inventories)
        }

        is SDKState.ErrorBody -> {
            val error = state as SDKState.ErrorBody
            // error.code      → HTTP status code (401, 403, 500…)
            // error.message   → human-readable message from API
            // error.errorCode → API-specific error code (e.g. "EX_402_115")
            ApiErrorDialog(
                code = error.code,
                message = error.message
            )
        }

        is SDKState.Error -> {
            val error = state as SDKState.Error
            // error.isNetworkError → true if connectivity issue
            // error.message        → "Network error - check your internet connection"
            if (error.isNetworkError) {
                NoInternetScreen()
            } else {
                RetryScreen(message = error.message)
            }
        }
    }
}
```

**Convenience properties on `SDKState`:**

```kotlin
state.isLoading            // Boolean - true when Loading
state.isSuccess            // Boolean - true when Success
state.isError              // Boolean - true when ErrorBody or Error
state.dataOrNull()         // T?      - returns data if Success, null otherwise
state.errorMessageOrNull() // String? - returns message if any error state, null otherwise
```

**Use `sdkStateFlow { }` to wrap any suspend call:**

```kotlin
import com.droidunplugged.kmp_platform_kit.core.sdkStateFlow

// Wraps ANY suspend facade call - emits Loading → then result
val myFlow: Flow<SDKState<InventoryListModel>> = sdkStateFlow {
    AppFacadeYourFeature.getInventories(customerNo = "2052008238")
}
```

---

### Style B - Suspend with `ApiResult` (manual loading management)

```kotlin
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.features.yourfeature.facade.AppFacadeYourFeature
import com.droidunplugged.kmp_platform_kit.features.yourfeature.models.InventoryListModel

class InventoryViewModel : ViewModel() {

    private val _loading = MutableStateFlow(false)
    private val _data = MutableStateFlow<InventoryListModel?>(null)
    private val _error = MutableStateFlow<String?>(null)

    fun loadInventories() {
        viewModelScope.launch {
            _loading.value = true

            val result = AppFacadeYourFeature.getInventories(
                customerNo = "2052008238",
                order = YourFeatureEndpoints.ORDER_BY_LAST_UPDATED  // optional
            )

            _loading.value = false

            when (result) {
                is ApiResult.Success -> {
                    val model: InventoryListModel = result.data
                    // model.status                   → "SUCCESS"
                    // model.inventories              → List<Inventories>
                    _data.value = model
                }

                is ApiResult.Failure -> {
                    // HTTP 4xx/5xx or API-level error
                    // result.code    → HTTP status (401, 403, 500…)
                    // result.message → error message string
                    _error.value = "Error ${result.code}: ${result.message}"
                }

                ApiResult.NetworkError -> {
                    // No internet, DNS failure, or timeout (after all retries)
                    _error.value = "No internet connection. Please try again."
                }

                ApiResult.Cancelled -> {
                    // Coroutine was cancelled - typically a no-op
                }
            }
        }
    }
}
```

**Convert `ApiResult` to `SDKState` manually:**

```kotlin
import com.droidunplugged.kmp_platform_kit.core.toSDKState

val result = AppFacadeYourFeature.getInventories(customerNo = "2052008238")
val state: SDKState<InventoryListModel> = result.toSDKState()
// Then use `when (state)` with the standard 4-branch pattern
```

---

### Response Model Reference

**`InventoryListModel`** (returned in `SDKState.Success.data` or `ApiResult.Success.data`):

| Field         | Type                | Example                 |
|---------------|---------------------|-------------------------|
| `status`      | `String`            | `"SUCCESS"` / `"ERROR"` |
| `inventories` | `List<Inventories>` | List of inventory items |

**`Inventories`** (each item in the list):

| Field              | Type     | Example                        | Notes                       |
|--------------------|----------|--------------------------------|-----------------------------|
| `inventoryNumber`  | `Int`    | `20154115`                     | Unique identifier           |
| `inventoryName`    | `String` | `"June Inventory"`             | Display name                |
| `totalValue`       | `String` | `"2597.37"`                    | Monetary value as string    |
| `countOfLocations` | `Int`    | `1`                            | Number of locations         |
| `status`           | `String` | `"Open"` / `"Closed"`          | Current status              |
| `createdDate`      | `String` | `"03/02/2026"`                 | Formatted `MM/dd/yyyy`      |
| `lastUpdatedOn`    | `String` | `"2026-03-02 09:06:43.000742"` | Raw timestamp (for sorting) |

### Sorting options

| Constant                                               | Value               | Effect                   |
|--------------------------------------------------------|---------------------|--------------------------|
| `YourFeatureEndpoints.ORDER_BY_INVENTORY_NUMBER` | `"inventoryNumber"` | Sort by inventory number |
| `YourFeatureEndpoints.ORDER_BY_LAST_UPDATED`     | `"lastUpdatedOn"`   | Sort by last update date |
| `null`                                                 | -                   | API default ordering     |

### Drop-in import replacement

The SDK's `InventoryListModel` and `Inventories` are **field-for-field identical** to the Host App's own models:

```kotlin
// ── Before (Host App's own model) ──────────────────────────────────
import com.droidunplugged.kmp_platform_kit.feature.inventory.model.InventoryListModel
import com.droidunplugged.kmp_platform_kit.feature.inventory.model.Inventories

// ── After (SDK model - identical structure) ─────────────────────────
import com.droidunplugged.kmp_platform_kit.features.yourfeature.models.InventoryListModel
import com.droidunplugged.kmp_platform_kit.features.yourfeature.models.Inventories
```

---

## 8. Handle Logout / Account Switch

```kotlin
// Step 1 - Stop the SDK, clear all stored credentials
SDKInitializer.reset()

// Step 2A - If you used configure() earlier:
//    Just call ensureInitialized() from the next screen.
//    The credential lambda runs again with fresh post-login values.
viewModelScope.launch {
    SDKInitializer.ensureInitialized()
    // SDK is re-initialized with fresh credentials
}

// Step 2B - If you used init() directly:
SDKInitializer.init(
    baseUrl = newBaseUrl,
    authToken = freshToken,
    apiGuid = newGuid,
    clientId = clientId,
    apiKey = apiKey
)
```

---

## 9. Enable Logging

By default, **no logs are emitted** (NoOp logger). Inject a logger before calling `init()` to capture SDK logs.

```kotlin
import com.droidunplugged.kmp_platform_kit.utils.Logger
import com.droidunplugged.kmp_platform_kit.utils.PlatformLogger

// Call BEFORE SDKInitializer.init() so all init logs are captured
PlatformLogger.set(object : Logger {
    override fun d(tag: String, message: String) = Log.d(tag, message)
    override fun i(tag: String, message: String) = Log.i(tag, message)
    override fun w(tag: String, message: String) = Log.w(tag, message)
    override fun e(tag: String, message: String, throwable: Throwable?) = Log.e(tag, message, throwable)
})
```

### Log tags you'll see

| Tag                       | What it logs                                    |
|---------------------------|-------------------------------------------------|
| `SDKInitializer`          | Init lifecycle, reset, SDK version              |
| `KtorApiClient`           | Every HTTP request/response with timing         |
| `HTTP`                    | Full request/response bodies (debug mode only)  |
| `YourFeatureRepo`   | Path building, mapping, API status, item counts |
| `YourFeatureFacade` | Facade-level call summaries                     |
| `InventoryMapper`         | Date format warnings                            |

### Sample Logcat output

```
I/SDKInitializer: Initializing SDK
I/SDKInitializer: ✓ SDK initialized - KmpPlatformKit/0.0.1-SNAPSHOT, 1 feature module(s)
D/KtorApiClient: → GET https://api.stage.../v1/customer/2052008238/inventories
I/KtorApiClient: ← GET .../inventories - 200 OK (4521 chars, 342 ms)
I/YourFeatureRepo: getInventories ✓ - 12 items
I/YourFeatureFacade: getInventories ✓ - 12 items
```

### Enable verbose HTTP logging (`debugMode`)

```kotlin
import com.droidunplugged.kmp_platform_kit.core.SDKConfig

// Call BEFORE SDKInitializer.init()
SDKConfig.debugMode = true
```

When enabled, full request/response bodies are logged:

```
D/HTTP: ┌── GET https://api.stage.../v1/customer/2052008238/inventories
D/HTTP: │ authorization: ***REDACTED***
D/HTTP: │ clientid: XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj
D/HTTP: ├── 200 OK
D/HTTP: │ Body: {"status":"SUCCESS","data":{"customer":2057192797,...}}
D/HTTP: └──────────────────────────────────────────────────────────────
```

> **⚠️ Never enable `debugMode` in production.** Auth headers are redacted, but request/response bodies may contain sensitive data.

---

## 10. Query SDK Version (`SDKInfo`)

```kotlin
import com.droidunplugged.kmp_platform_kit.core.SDKInfo

Log.d("App", SDKInfo.fullName)   // "KmpPlatformKit/0.0.1-SNAPSHOT"
Log.d("App", SDKInfo.VERSION)    // "0.0.1-SNAPSHOT"
Log.d("App", SDKInfo.NAME)       // "KmpPlatformKit"
```

Useful for crash reporters, analytics dashboards, and debug screens. The SDK also embeds this in the `user-agent` header automatically:

```
KmpPlatformKit/0.0.1-SNAPSHOT (Android; API-34; Pixel 7)
```

---

## 11. Retry Behavior

The SDK automatically retries transient failures with exponential backoff. This is transparent - you receive the final `ApiResult` after all retries are exhausted.

| Setting         | Default  | Description                               |
|-----------------|----------|-------------------------------------------|
| Max attempts    | `3`      | Total call attempts (including the first) |
| Initial backoff | `500 ms` | Wait before 1st retry; doubles each time  |

**What gets retried:**

- HTTP 5xx errors ✅
- Network I/O errors (no internet, timeout) ✅

**What does NOT get retried:**

- HTTP 4xx errors (your credentials or request are wrong) ❌
- Coroutine cancellation ❌

**Timeline with defaults:**

```
Attempt 1 → HTTP 503
  wait 500 ms
Attempt 2 → HTTP 503
  wait 1000 ms
Attempt 3 → HTTP 503 → returns ApiResult.Failure(503, "Service Unavailable")
```

**Override retry settings:**

```kotlin
import com.droidunplugged.kmp_platform_kit.core.RetryConfig

// Aggressive retry: 5 attempts, 1 second initial backoff
val retryModule = module {
    single { RetryConfig(maxAttempts = 5, initialBackoffMs = 1000L) }
}

SDKInitializer.init(
    baseUrl = "...", authToken = "...", apiGuid = "...",
    clientId = "...", apiKey = "...",
    additionalModules = listOf(retryModule)
)

// Disable retries completely
val noRetry = module { single { RetryConfig.NO_RETRY } }
```

---

## 12. Koin Conflict Resolution

The SDK calls `startKoin { }` inside `SDKInitializer.init()`. If your host app also uses Koin, you'll see:

```
A Koin Application has already been started
```

### Fix - Pass your modules into the SDK

```kotlin
SDKInitializer.init(
    baseUrl = "...",
    authToken = "...",
    apiGuid = "...",
    clientId = "...",
    apiKey = "...",
    additionalModules = listOf(yourAppModule, yourNetworkModule)
    // The SDK starts one shared Koin instance with all modules combined
)
```

> **Apps using Hilt or Dagger:** No conflict at all - the SDK's Koin instance runs in complete isolation.

---

## 13. ProGuard / R8

The SDK bundles consumer ProGuard rules inside the AAR (`consumer-rules.pro`). These are applied automatically when your app is built with R8 / ProGuard enabled - **no manual configuration required**.

The bundled rules protect:

- All public SDK API classes (facades, models, `SDKInitializer`, `SDKState`, etc.)
- Koin reflection usage
- kotlinx.serialization annotations

If you observe missing class warnings from the SDK package during minification, ensure you are using the **fat AAR** and that your build tools are on a current version.

---

## 14. Troubleshooting

| Error                                                      | Cause                                    | Fix                                                                      |
|------------------------------------------------------------|------------------------------------------|--------------------------------------------------------------------------|
| `NoClassDefFoundError: io/ktor/client/HttpClient`          | Using lean AAR without transitive deps   | Switch to fat AAR (`shared-release-fat.aar`)                             |
| `Incompatible version of Kotlin. Binary metadata is 2.2.0` | Host Kotlin too old                      | Upgrade to `kotlin("android") version "2.1.21"`                          |
| `Field 'status' is required but it was missing`            | Kotlin version mismatch in serialization | Align Kotlin versions - see [Section 4](#4-kotlin-version-compatibility) |
| `Expected beginning of the string, but got {`              | Old SDK modeled `error` as `String?`     | Rebuild AAR with latest SDK source                                       |
| `A Koin Application has already been started`              | Host + SDK both calling `startKoin`      | See [Section 12](#12-koin-conflict-resolution)                           |
| `SDK not initialized. Call SDKInitializer.init(...)`       | Facade called before init completes      | Use `configure()` + `ensureInitialized()`                                |
| SDK classes not found / import fails                       | SDK not on classpath                     | Verify AAR is in `libs/` and declared in `dependencies {}`               |
| 401 / 403 on every request                                 | Wrong credentials or environment         | Check `baseUrl` matches where the `authToken` was issued                 |

---

## 15. Quick Reference

### Imports you'll use most

```kotlin
// Initialization
import com.droidunplugged.kmp_platform_kit.core.SDKInitializer
import com.droidunplugged.kmp_platform_kit.core.SDKCredentials        // deferred init

// SDK metadata & configuration
import com.droidunplugged.kmp_platform_kit.core.SDKInfo               // version, name, fullName
import com.droidunplugged.kmp_platform_kit.core.SDKConfig             // debugMode toggle
import com.droidunplugged.kmp_platform_kit.core.RetryConfig           // retry customization

// Flow-based API calls (recommended for UI)
import com.droidunplugged.kmp_platform_kit.core.SDKState
import com.droidunplugged.kmp_platform_kit.core.sdkStateFlow          // wrap suspend → Flow<SDKState>
import com.droidunplugged.kmp_platform_kit.core.toSDKState            // ApiResult → SDKState

// Suspend-based API calls
import com.droidunplugged.kmp_platform_kit.core.ApiResult

// Physical Inventory feature
import com.droidunplugged.kmp_platform_kit.features.yourfeature.facade.AppFacadeYourFeature
import com.droidunplugged.kmp_platform_kit.features.yourfeature.endpoints.YourFeatureEndpoints
import com.droidunplugged.kmp_platform_kit.features.yourfeature.models.InventoryListModel
import com.droidunplugged.kmp_platform_kit.features.yourfeature.models.Inventories

// Logging
import com.droidunplugged.kmp_platform_kit.utils.Logger
import com.droidunplugged.kmp_platform_kit.utils.PlatformLogger
```

### Minimum integration - Flow-based (7 lines)

```kotlin
// 1. Init
SDKInitializer.init(baseUrl, authToken, apiGuid, clientId, apiKey)

// 2. ViewModel - one-liner, Loading emitted automatically
val state = AppFacadeYourFeature
    .getInventoriesFlow(customerNo = "2052008238")
    .stateIn(viewModelScope, SharingStarted.Lazily, SDKState.Loading)

// 3. Composable - all 4 states handled
val s by viewModel.state.collectAsStateWithLifecycle()
when (s) {
    is SDKState.Loading -> ShowLoader()
    is SDKState.Success -> ShowData((s as SDKState.Success).data)
    is SDKState.ErrorBody -> ShowApiError((s as SDKState.ErrorBody).message)
    is SDKState.Error -> ShowRetry((s as SDKState.Error).message)
}
```

### Minimum integration - Suspend-based (5 lines)

```kotlin
// 1. Init
SDKInitializer.init(baseUrl, authToken, apiGuid, clientId, apiKey)

// 2. Call
val result = AppFacadeYourFeature.getInventories(customerNo = "2052008238")

// 3. Use
if (result is ApiResult.Success) {
    val model: InventoryListModel = result.data
    // model.inventories → same List<Inventories> as Host App model
}
```

### Dependency versions (for lean AAR)

| Library                    | Version  |
|----------------------------|----------|
| Kotlin                     | `2.1.21` |
| Ktor                       | `3.1.3`  |
| kotlinx-coroutines-core    | `1.10.2` |
| kotlinx-serialization-json | `1.10.0` |
| Koin                       | `4.1.0`  |

> Source of truth: `gradle/libs.versions.toml` in the SDK repository.