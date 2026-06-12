# 🍎 iOS Integration Guide - KMP Platform Kit

> **SDK Version:** `0.x` · **Kotlin:** `2.1.21` · **iOS:** `15+` · **Xcode:** `15+` · **Last Updated:** March 2026
>
> Complete guide - from building the XCFramework to production-ready usage patterns.

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Build the XCFramework](#2-build-the-xcframework)
3. [Add to Your Xcode Project](#3-add-to-your-xcode-project)
4. [Initialize the SDK](#4-initialize-the-sdk)
5. [Keep Headers Up to Date](#5-keep-headers-up-to-date)
6. [Make API Calls](#6-make-api-calls)
7. [Handle Logout / Account Switch](#7-handle-logout--account-switch)
8. [Enable Logging](#8-enable-logging)
9. [Query SDK Version (SDKInfo)](#9-query-sdk-version-sdkinfo)
10. [Retry Behavior](#10-retry-behavior)
11. [Kotlin → Swift Type Mapping](#11-kotlin--swift-type-mapping)
12. [Troubleshooting](#12-troubleshooting)
13. [Quick Reference](#13-quick-reference)

---

## 1. Prerequisites

| Requirement           | Minimum        | Notes                                 |
|-----------------------|----------------|---------------------------------------|
| iOS Deployment Target | `15.0`         | Hard requirement                      |
| Xcode                 | `15.0+`        | Required for XCFramework support      |
| Swift                 | `5.9+`         | Recommended                           |
| macOS (build machine) | `14+` (Sonoma) | Kotlin/Native compilation requirement |

---

## 2. Build the XCFramework

Choose only the features you need - smaller binary, faster builds.

### Using the convenience script

```bash
# All features - Release build
./scripts/build-ios.sh

# Physical inventory only (recommended for POC)
./scripts/build-ios.sh yourfeature

# Multiple features
./scripts/build-ios.sh yourfeature,orders

# Debug builds (for development - includes debug symbols)
./scripts/build-ios-debug.sh
./scripts/build-ios-debug.sh yourfeature
```

### Using Gradle directly

```bash
# Release
./gradlew :shared:assembleSharedReleaseXCFramework -Psdk.features=yourfeature

# Debug
./gradlew :shared:assembleSharedDebugXCFramework -Psdk.features=yourfeature
```

### Build outputs

| Build Type | Location                                               |
|------------|--------------------------------------------------------|
| Release    | `shared/build/XCFrameworks/release/Shared.xcframework` |
| Debug      | `shared/build/XCFrameworks/debug/Shared.xcframework`   |

> **Why no extra dependencies?**  
> The XCFramework is a **static framework** (`isStatic = true`). Kotlin/Native compiles all SDK code, Ktor, Koin, and kotlinx-serialization into a single self-contained LLVM binary. Just drag it into Xcode and you're done - iOS equivalent of the Android fat AAR.

---

## 3. Add to Your Xcode Project

1. Drag `Shared.xcframework` into your Xcode project navigator
2. Navigate to your target → **General** → **Frameworks, Libraries, and Embedded Content**
3. Find `Shared.xcframework` → set to **Embed & Sign**
4. Add `import Shared` to any Swift file to access SDK classes

That's it. No CocoaPods, no Swift Package Manager configuration required.

### How to access SDK API docs

Unlike Android Maven consumers, Xcode does **not** automatically consume the Dokka-generated `javadoc.jar`.
For iOS teams, the recommended API reference is the generated Dokka site:

- `shared/build/docs/html/` - browsable HTML docs
- `shared/build/docs/gfm/` - GitHub-flavoured Markdown output
- CI `sdk-docs` artifact - downloadable docs bundle from each pipeline run

Generate docs locally with:

```bash
bundle exec fastlane generate_docs

# or directly with Gradle
./gradlew :shared:dokkaHtml :shared:dokkaGfm
```

---

## 4. Initialize the SDK

Call `SDKInitializer.shared.doInit(...)` **once**. Repeated calls are no-ops.

> **Note:** The Swift method is `doInit` (not `init`) because `init` is a reserved Swift keyword.
> `doInit` is an `async` method - call from an `async` context or wrap in a `Task {}`.

### Option A - Direct Init (credentials at app startup)

```swift
import Shared

@main
struct MyApp: App {
    init() {
        SDKInitializer.shared.doInit(
            baseUrl: "https://api.stage.example.com", // env-specific
            authToken: jwtToken, // Bearer JWT
            apiGuid: "a-7969-cf04-bea9-4c0e", // x-cah-api-guid
            clientId: "XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj", // from env config
            apiKey: "XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj", // from env config
            additionalModules: []
        )
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

### Option A-alt - Environment-based Init (recommended for multi-env apps)

Use strongly-typed `SdkEnvironment` objects instead of raw strings:

```swift
import Shared

// Define environments once in your app
struct Environments {
    static let staging = SdkEnvironment(
        id: "staging",
        baseUrl: "https://api.stage.example.com",
        clientId: AppConfig.stagingClientId,
        apiKey: AppConfig.stagingApiKey,
        sslPins: nil
    )
    static let production = SdkEnvironment(
        id: "production",
        baseUrl: "https://api.example.com",
        clientId: AppConfig.prodClientId,
        apiKey: AppConfig.prodApiKey,
        sslPins: SslPinConfig(
            hostname: "api.example.com",
            pins: ["sha256/AAAA...", "sha256/BBBB..."]
        )
    )
}

// At init
try await SDKInitializer.shared.doInit(
    environment: isDebug ? Environments.staging : Environments.production,
    authToken: sessionManager.token,
    apiGuid: sessionManager.guid,
    tokenExpiresIn: nil,
    additionalModules: []
)
```

### Optional - Pre-Init Registration (call BEFORE doInit)

Configure advanced subsystems before initialising:

```swift
// Automatic token refresh (proactive + reactive 401 handling)
SDKInitializer.shared.setTokenRefreshProvider(provider: MyTokenRefreshProvider())

// Telemetry
SDKInitializer.shared.setTelemetry(impl: MyAnalyticsTelemetry())

// Server-pushed config
SDKInitializer.shared.setRemoteConfigProvider(provider: MyRemoteConfigProvider())

// Custom HTTP middleware
SDKInitializer.shared.addInterceptor(interceptor: DeviceFingerprintInterceptor(deviceId: id))

// THEN init
SDKInitializer.shared.doInit(...)
```

### Option B - Deferred Init (credentials available after login)

Use this when users must log in before you have valid credentials:

**Step 1 - Register the credential provider at app startup:**

```swift
import Shared

@main
struct MyApp: App {
    init() {
        // Register now - doInit() will be called lazily when first needed
        SDKInitializer.shared.configure { () -> SDKCredentials in
            return SDKCredentials(
                baseUrl: resolveBaseUrl(),
                authToken: SessionManager.shared.getToken(), // called lazily
                apiGuid: PreferencesManager.shared.transactionId,
                clientId: AppConfig.clientId,
                apiKey: AppConfig.apiKey
            )
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
```

**Step 2 - Call `ensureInitialized()` before any API call:**

```swift
// In any View or ViewModel - safe to call from multiple places
func loadData() async {
    do {
        // Idempotent: first call initializes; subsequent calls are instant no-ops
        try await SDKInitializer.shared.ensureInitialized()

        let result = try await AppFacadeYourFeature.shared.getInventories(
            customerNo: "2052008238",
            order: nil
        )
        // handle result...
    } catch {
        // handle initialization error
    }
}
```

**How `ensureInitialized()` works:**

| Call                                 | Behavior                                                                        |
|--------------------------------------|---------------------------------------------------------------------------------|
| First call                           | Acquires Mutex → invokes credential closure → calls `doInit()` → releases Mutex |
| Concurrent calls (during first init) | Suspend until Mutex released → instant no-op                                    |
| All subsequent calls                 | Instant no-op (zero overhead)                                                   |

### What each parameter becomes

| Parameter   | HTTP Header                     | Set By                                |
|-------------|---------------------------------|---------------------------------------|
| `baseUrl`   | _(URL prefix for all requests)_ | Your app - environment config         |
| `authToken` | `authorization: Bearer <token>` | Your app - auth/login flow            |
| `apiGuid`   | `x-cah-api-guid`                | Your app - correlation ID per session |
| `clientId`  | `clientid`                      | Your app - env config                 |
| `apiKey`    | `x-api-key`                     | Your app - env config                 |

### Base URL per environment

| Environment | Base URL                               |
|-------------|----------------------------------------|
| Dev         | `https://api.dev.example.com`   |
| QA          | `https://api.qa.example.com`    |
| Stage       | `https://api.stage.example.com` |
| Production  | `https://api.example.com`       |

### Headers the SDK adds automatically (you never touch these)

| Header              | Value                                             | Source                             |
|---------------------|---------------------------------------------------|------------------------------------|
| `platform`          | `ios`                                             | SDK constant                       |
| `user-agent`        | `KmpPlatformKit/x.y.z (iOS; {systemVersion}; {model})` | SDK reads `UIDevice.systemVersion` |
| `x-external-source` | `mobile`                                          | SDK constant                       |
| `accept`            | `application/json`                                | SDK constant                       |
| `accept-encoding`   | `gzip`                                            | Darwin engine automatic            |
| `connection`        | `Keep-Alive`                                      | Darwin engine automatic            |
| `host`              | derived from `baseUrl`                            | Darwin engine automatic            |

---

## 5. Keep Headers Up to Date

### Token refresh (~every 15 minutes)

```swift
// Only replaces `authorization` header - all other values remain unchanged
SDKInitializer.shared.updateAuthToken(token: newJwtToken)
```

### After re-login (new session GUID + fresh token)

```swift
// Replaces both `authorization` and `x-cah-api-guid`
SDKInitializer.shared.updateDynamicHeaders(
    authToken: freshToken,
    apiGuid: "new-guid-after-login"
)
```

### Header lifecycle

```
App Start
  │
  ├─ SDKInitializer.shared.doInit(baseUrl, token, guid, clientId, apiKey, [])
  │    → PlatformConfig stores all 5 values in AtomicReference (thread-safe)
  │
  ├─ Every ~15 min → .updateAuthToken(token: newToken)
  │    → Only authorization updated via AtomicReference CAS
  │
  ├─ Re-login → .updateDynamicHeaders(authToken:, apiGuid:)
  │    → authorization + x-cah-api-guid updated atomically
  │
  └─ Logout → .reset()
       → All values cleared, Koin stopped
       → Use configure() + ensureInitialized() or doInit() after next login
```

---

## 6. Make API Calls

The SDK provides **two call styles**:

| Style                      | Best For                      | Returns              | Loading State                  |
|----------------------------|-------------------------------|----------------------|--------------------------------|
| **SDKState** (recommended) | SwiftUI / UI code             | `SDKState` subclass  | ✅ Manage via `SDKStateLoading` |
| **ApiResult** (suspend)    | Non-UI / custom orchestration | `ApiResult` subclass | ❌ You manage it                |

---

### Style A - SDKState ✅ Recommended for UI

`SDKState` maps to the 4 UI states your app must handle:

| Swift Class          | Your UI State               | When                                  |
|----------------------|-----------------------------|---------------------------------------|
| `SDKStateLoading`    | Spinner / shimmer           | Request in flight                     |
| `SDKStateSuccess<T>` | Show data                   | HTTP 200 + valid data                 |
| `SDKStateErrorBody`  | API error dialog            | Structured error (4xx/5xx with body)  |
| `SDKStateError`      | Retry / connectivity screen | Network error, timeout, parse failure |

**ViewModel:**

```swift
import Shared

@MainActor
class InventoryViewModel: ObservableObject {

    @Published var state: SDKState<InventoryListModel> = SDKStateLoading()

    func loadInventories(customerNo: String) async {
        // Emit loading immediately
        state = SDKStateLoading()

        do {
            let result = try await AppFacadeYourFeature.shared.getInventories(
                customerNo: customerNo,
                order: YourFeatureEndpoints.shared.ORDER_BY_LAST_UPDATED  // optional
            )
            // Convert ApiResult → SDKState
            state = SDKStateFlowKt.toSDKState(result)
        } catch {
            state = SDKStateError(
                message: "Unexpected error: \(error.localizedDescription)",
                isNetworkError: false
            )
        }
    }
}
```

**SwiftUI View:**

```swift
import Shared

struct InventoryListView: View {
    @StateObject private var viewModel = InventoryViewModel()

    var body: some View {
        Group {
            switch viewModel.state {

            case is SDKStateLoading:
                ProgressView("Loading inventories…")
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

            case let success as SDKStateSuccess<InventoryListModel>:
                // success.data.status       → "SUCCESS"
                // success.data.inventories  → [Inventories]
                List(success.data.inventories, id: \.inventoryNumber) { item in
                    VStack(alignment: .leading, spacing: 4) {
                        Text(item.inventoryName)
                            .font(.headline)
                        Text("$\(item.totalValue) · \(item.countOfLocations) locations")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("Created: \(item.createdDate) · \(item.status)")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                }

            case let error as SDKStateErrorBody:
                // error.code       → HTTP status code (401, 403, 500…)
                // error.message    → human-readable message from API
                // error.errorCode  → API-specific code (e.g. "EX_402_115")
                VStack(spacing: 16) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.largeTitle)
                        .foregroundColor(.orange)
                    Text("Error \(error.code)")
                        .font(.headline)
                    Text(error.message)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        Task {
                            await viewModel.loadInventories(customerNo: "2052008238")
                        }
                    }
                }
                .padding()

            case let error as SDKStateError:
                // error.isNetworkError → true if connectivity issue
                // error.message        → descriptive error message
                VStack(spacing: 16) {
                    Image(systemName: error.isNetworkError ? "wifi.slash" : "exclamationmark.circle")
                        .font(.largeTitle)
                        .foregroundColor(.red)
                    Text(error.message)
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                    Button("Retry") {
                        Task {
                            await viewModel.loadInventories(customerNo: "2052008238")
                        }
                    }
                }
                .padding()

            default:
                EmptyView()
            }
        }
        .task {
            await viewModel.loadInventories(customerNo: "2052008238")
        }
    }
}
```

**Convenience helpers on `SDKState`:**

```swift
state.isLoading            // Bool - true when SDKStateLoading
state.isSuccess            // Bool - true when SDKStateSuccess
state.isError              // Bool - true when SDKStateErrorBody or SDKStateError
state.dataOrNull()         // T?   - data if Success, nil otherwise
state.errorMessageOrNull() // String? - message if any error state, nil otherwise
```

---

### Style B - ApiResult (manual loading management)

```swift
import Shared

func loadInventories() async {
    do {
        let result = try await AppFacadeYourFeature.shared.getInventories(
            customerNo: "2052008238",
            order: YourFeatureEndpoints.shared.ORDER_BY_LAST_UPDATED  // optional
        )

        switch result {
        case let success as ApiResultSuccess<InventoryListModel>:
            let model = success.data
            // model.status       → "SUCCESS"
            // model.inventories  → [Inventories]
            for item in model.inventories {
                print("\(item.inventoryName) - $\(item.totalValue)")
                print("  Created: \(item.createdDate)")         // MM/dd/yyyy
                print("  Status:  \(item.status)")              // "Open" / "Closed"
                print("  Locations: \(item.countOfLocations)")
            }

        case let failure as ApiResultFailure:
            // HTTP 4xx/5xx or API-level error
            // failure.code    → HTTP status (401, 403, 500…)
            // failure.message → error message string (optional)
            print("Error \(failure.code): \(failure.message ?? "Unknown error")")

        case is ApiResultNetworkError:
            // No internet, DNS failure, or timeout (after all retries)
            print("Network error - check your internet connection")

        case is ApiResultCancelled:
            // Task was cancelled - typically a no-op
            break

        default:
            break
        }
    } catch {
        print("Unexpected error: \(error)")
    }
}
```

---

### Response Model Reference

**`InventoryListModel`** (returned in `SDKStateSuccess.data` or `ApiResultSuccess.data`):

| Field         | Swift Type      | Example                  |
|---------------|-----------------|--------------------------|
| `status`      | `String`        | `"SUCCESS"` / `"ERROR"`  |
| `inventories` | `[Inventories]` | Array of inventory items |

**`Inventories`** (each item in the array):

| Field              | Swift Type | Example                        | Notes                       |
|--------------------|------------|--------------------------------|-----------------------------|
| `inventoryNumber`  | `Int32`    | `20154115`                     | Unique identifier           |
| `inventoryName`    | `String`   | `"June Inventory"`             | Display name                |
| `totalValue`       | `String`   | `"2597.37"`                    | Monetary value as string    |
| `countOfLocations` | `Int32`    | `1`                            | Number of locations         |
| `status`           | `String`   | `"Open"` / `"Closed"`          | Current status              |
| `createdDate`      | `String`   | `"03/02/2026"`                 | Formatted `MM/dd/yyyy`      |
| `lastUpdatedOn`    | `String`   | `"2026-03-02 09:06:43.000742"` | Raw timestamp (for sorting) |

> **Note:** Kotlin `Int` maps to Swift `Int32`. Kotlin `Long` maps to Swift `Int64`.

### Sorting options

| Constant                                                      | Value               | Effect                   |
|---------------------------------------------------------------|---------------------|--------------------------|
| `YourFeatureEndpoints.shared.ORDER_BY_INVENTORY_NUMBER` | `"inventoryNumber"` | Sort by inventory number |
| `YourFeatureEndpoints.shared.ORDER_BY_LAST_UPDATED`     | `"lastUpdatedOn"`   | Sort by last update date |
| `nil`                                                         | -                   | API default ordering     |

---

## 7. Handle Logout / Account Switch

```swift
// Step 1 - Stop the SDK, clear all stored credentials
SDKInitializer.shared.reset()

// Step 2A - If you used configure() earlier:
//    Just call ensureInitialized() from the next screen.
//    The credential closure runs again with fresh post-login values.
try await SDKInitializer.shared.ensureInitialized()

// Step 2B - If you used doInit() directly:
SDKInitializer.shared.doInit(
    baseUrl:   newBaseUrl,
    authToken: freshToken,
    apiGuid:   newGuid,
    clientId:  clientId,
    apiKey:    apiKey,
    additionalModules: []
)
```

---

## 8. Enable Logging

By default, **no logs are emitted** (NoOp logger). Implement the `Logger` protocol and inject it before calling `doInit()`.

```swift
import Shared

final class AppLogger: Logger {
    func d(tag: String, message: String) {
        #if DEBUG
        print("💬 [\(tag)] \(message)")
        #endif
    }

    func i(tag: String, message: String) {
        print("ℹ️ [\(tag)] \(message)")
    }

    func w(tag: String, message: String) {
        print("⚠️ [\(tag)] \(message)")
    }

    func e(tag: String, message: String, throwable: KotlinThrowable?) {
        print("❌ [\(tag)] \(message) \(throwable?.message ?? "")")
    }
}

// Call BEFORE SDKInitializer.shared.doInit() so all init logs are captured
PlatformLogger.shared.set(logger: AppLogger())
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

### Sample output (with AppLogger above)

```
ℹ️ [SDKInitializer] Initializing SDK
ℹ️ [SDKInitializer] ✓ SDK initialized - KmpPlatformKit/0.0.1-SNAPSHOT, 1 feature module(s)
💬 [KtorApiClient] → GET https://api.stage.../v1/customer/2052008238/inventories
ℹ️ [KtorApiClient] ← GET .../inventories - 200 OK (4521 chars, 289 ms)
ℹ️ [YourFeatureRepo] getInventories ✓ - 12 items
ℹ️ [YourFeatureFacade] getInventories ✓ - 12 items
```

### Enable verbose HTTP logging (`debugMode`)

```swift
import Shared

// Call BEFORE doInit() so ALL requests are captured
SDKConfig.shared.debugMode = true
```

When enabled:

```
💬 [HTTP] ┌── GET https://api.stage.../v1/customer/2052008238/inventories
💬 [HTTP] │ authorization: ***REDACTED***
💬 [HTTP] │ clientid: XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj
💬 [HTTP] ├── 200 OK
💬 [HTTP] │ Body: {"status":"SUCCESS","data":{"customer":2057192797,...}}
💬 [HTTP] └──────────────────────────────────────────────────────────────
```

> **⚠️ Never enable `debugMode` in production.** Auth headers are always redacted, but request/response bodies may contain sensitive data.

---

## 9. Query SDK Version (`SDKInfo`)

```swift
import Shared

print(SDKInfo.shared.fullName)   // "KmpPlatformKit/0.0.1-SNAPSHOT"
print(SDKInfo.shared.VERSION)    // "0.0.1-SNAPSHOT"
print(SDKInfo.shared.NAME)       // "KmpPlatformKit"
```

Useful for crash reporters, analytics, and debug screens. The SDK also embeds this automatically in the `user-agent` header:

```
KmpPlatformKit/0.0.1-SNAPSHOT (iOS; 17.4; iPhone 15 Pro)
```

---

## 10. Retry Behavior

The SDK automatically retries transient failures with exponential backoff. This is transparent - you receive the final `ApiResult` after all retries are exhausted.

| Setting         | Default  | Description                               |
|-----------------|----------|-------------------------------------------|
| Max attempts    | `3`      | Total call attempts (including the first) |
| Initial backoff | `500 ms` | Wait before 1st retry; doubles each time  |

**What gets retried:**

- HTTP 5xx errors ✅
- Network I/O errors (no internet, timeout) ✅

**What does NOT get retried:**

- HTTP 4xx errors ❌
- Task cancellation ❌

**Timeline with defaults:**

```
Attempt 1 → HTTP 503
  wait 500 ms
Attempt 2 → HTTP 503
  wait 1000 ms
Attempt 3 → HTTP 503 → returns ApiResultFailure(503, "Service Unavailable")
```

---

## 11. Kotlin → Swift Type Mapping

Kotlin/Native automatically bridges Kotlin types to Swift. Key mappings:

| Kotlin                            | Swift                                   | Notes                              |
|-----------------------------------|-----------------------------------------|------------------------------------|
| `object SDKInitializer`           | `SDKInitializer.shared`                 | Kotlin singletons → `.shared`      |
| `fun init(...)`                   | `func doInit(...)`                      | `init` is reserved in Swift        |
| `object SDKInfo`                  | `SDKInfo.shared`                        |                                    |
| `object SDKConfig`                | `SDKConfig.shared`                      |                                    |
| `suspend fun getInventories(...)` | `func getInventories(...) async throws` | Coroutines → async/await           |
| `SDKState.Loading`                | `SDKStateLoading` (class instance)      | Sealed class → class hierarchy     |
| `SDKState.Success<T>`             | `SDKStateSuccess<T>`                    | Cast with `as? SDKStateSuccess<T>` |
| `SDKState.ErrorBody`              | `SDKStateErrorBody`                     |                                    |
| `SDKState.Error`                  | `SDKStateError`                         |                                    |
| `ApiResult.Success<T>`            | `ApiResultSuccess<T>`                   |                                    |
| `ApiResult.Failure`               | `ApiResultFailure`                      |                                    |
| `ApiResult.NetworkError`          | `ApiResultNetworkError`                 |                                    |
| `ApiResult.Cancelled`             | `ApiResultCancelled`                    |                                    |
| `Int`                             | `Int32`                                 | Kotlin Int = 32-bit                |
| `Long`                            | `Int64`                                 | Kotlin Long = 64-bit               |
| `Boolean`                         | `Bool` (via `KotlinBoolean`)            |                                    |
| `String?`                         | `String?`                               | Same nullability                   |
| `List<T>`                         | `[T]` (Swift Array)                     |                                    |

**Sealed class pattern in Swift:**

```swift
// Kotlin sealed classes become a base class with subclasses in Swift
// Use is / as? for pattern matching - NOT switch with enum cases

// ✅ Correct pattern
switch result {
case let success as ApiResultSuccess<InventoryListModel>:
    let model = success.data
case let failure as ApiResultFailure:
    print("Error \(failure.code)")
case is ApiResultNetworkError:
    print("No internet")
default:
    break
}

// ❌ Incorrect (sealed class is NOT a Swift enum)
// switch result { case .success: ... }  ← will not compile
```

---

## 12. Troubleshooting

| Error                               | Cause                                   | Fix                                                                                              |
|-------------------------------------|-----------------------------------------|--------------------------------------------------------------------------------------------------|
| `Undefined symbol` / linker error   | XCFramework not embedded correctly      | Target → General → set **Embed & Sign**                                                          |
| `No such module 'Shared'`           | Framework not added to target           | Re-drag `Shared.xcframework` into Xcode project                                                  |
| `SDK not initialized` at runtime    | `doInit()` not called before facade use | Ensure init completes before API calls - use deferred init pattern                               |
| Wrong data / 401 errors             | `baseUrl` doesn't match auth token env  | Check that `baseUrl` matches where the `authToken` was issued                                    |
| `Cannot cast to SDKStateSuccess`    | Incorrect casting pattern               | Use `as? SDKStateSuccess<InventoryListModel>` - see [Section 11](#11-kotlin--swift-type-mapping) |
| Framework loads but classes missing | Features not included at build time     | Rebuild with `-Psdk.features=<yourfeature>`                                                      |

---

## 13. Quick Reference

### Import

```swift
import Shared   // All SDK classes are under this single module
```

### Key classes

| Class                               | What it does                         |
|-------------------------------------|--------------------------------------|
| `SDKInitializer.shared`             | Init (`doInit`), token update, reset |
| `AppFacadeYourFeature.shared` | Physical inventory API calls         |
| `YourFeatureEndpoints.shared` | Sort order constants                 |
| `SDKInfo.shared`                    | SDK version and name                 |
| `SDKConfig.shared`                  | Debug mode toggle (`debugMode`)      |
| `PlatformLogger.shared`             | Inject custom logger                 |

### Minimum integration - SDKState (recommended)

```swift
// 1. Init
SDKInitializer.shared.doInit(
    baseUrl: url, authToken: token, apiGuid: guid,
    clientId: cid, apiKey: key, additionalModules: []
)

// 2. Call
let result = try? await AppFacadeYourFeature.shared
    .getInventories(customerNo: "2052008238", order: nil)

// 3. Convert and use
let state = result.map {
    SDKStateFlowKt.toSDKState($0)
}
    ?? SDKStateError(message: "Unexpected", isNetworkError: false)

switch state {
case is SDKStateLoading:                         showLoader()
case let s as SDKStateSuccess<InventoryListModel>: showList(s.data)
case let e as SDKStateErrorBody:                   showApiError(e.code, e.message)
case let e as SDKStateError:                       showRetry(e.message)
default: break
}
```

### Minimum integration - ApiResult

```swift
// 1. Init
SDKInitializer.shared.doInit(
    baseUrl: url, authToken: token, apiGuid: guid,
    clientId: cid, apiKey: key, additionalModules: []
)

// 2. Call
let result = try? await AppFacadeYourFeature.shared
    .getInventories(customerNo: "2052008238", order: nil)

// 3. Use
if let success = result as? ApiResultSuccess<InventoryListModel> {
    let model = success.data
    // model.status, model.inventories - same shape as Host App
}
```

### Important notes

- The framework module name is `Shared` - all SDK classes live under `import Shared`
- Kotlin `suspend fun` → Swift `async throws`
- Kotlin sealed classes → base class with subclasses; use `as?` casting, not enum-style matching
- Only features included at build time (`-Psdk.features=...`) are available in the framework
- No CocoaPods or SPM required - XCFramework is fully self-contained