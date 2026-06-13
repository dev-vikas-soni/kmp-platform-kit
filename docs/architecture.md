# 🏗️ Architecture - KMP Platform Kit

> **Document Type:** Reference Architecture  
> **SDK Version:** `1.0.0`  
> **Last Updated:** May 2026  
> **Audience:** SDK contributors, platform engineers, senior mobile developers

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [High-Level Overview](#2-high-level-overview)
3. [Layer Architecture](#3-layer-architecture)
4. [Package Structure](#4-package-structure)
5. [Initialization System](#5-initialization-system)
6. [Dependency Injection (Koin)](#6-dependency-injection-koin)
7. [Networking Pipeline](#7-networking-pipeline)
8. [Error Handling Strategy](#8-error-handling-strategy)
9. [State Management (SDKState)](#9-state-management-sdkstate)
10. [Thread Safety Model](#10-thread-safety-model)
11. [Retry Behavior (RetryConfig)](#11-retry-behavior-retryconfig)
12. [Shared Models](#12-shared-models)
13. [Debug Mode & Logging](#13-debug-mode--logging)
14. [Security & Credential Handling](#14-security--credential-handling)

---

## 1. Executive Summary

KMP Platform Kit is a **Kotlin Multiplatform (KMP)** SDK framework that provides a battle-tested foundation for building shared business logic for Android and iOS. It delivers:

| Capability                    | Detail                                                                |
|-------------------------------|-----------------------------------------------------------------------|
| **Single source of truth**    | Business logic, networking, and models written once in Kotlin         |
| **Platform-native output**    | Android `.aar` (OkHttp) · iOS `.xcframework` (Darwin)                 |
| **Zero host-app boilerplate** | Init in one line; DI, headers, retries all automatic                  |
| **UI-ready states**           | `SDKState<T>` maps directly to Loading / Success / Error UI states    |
| **Resilience by default**     | Built-in Circuit Breaker, Deduplication, and Smart Caching            |

---

## 2. High-Level Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│  HOST APPLICATION                                                            │
│                                                                              │
│  Step 1: SDKInitializer.init(SDKCredentials(...))          ← app startup    │
│  Step 2: SDKInitializer.ensureInitialized()                ← any screen     │
│  Step 3: AppFacadeYourFeature.getData()                    ← API call      │
│  Step 4: SDKInitializer.updateAuthToken(newToken)           ← token refresh  │
│  Step 5: SDKInitializer.reset()                             ← logout        │
└───────────────────────────────────┬─────────────────────────────────────────┘
                                    │
               ┌────────────────────┴────────────────────┐
               │ Android (.aar ~3.3 MB)  iOS (.xcframework) │
               └────────────────────┬────────────────────┘
                                    │
┌──────────────────────────────────────────────────────────────────────────────┐
│  SDK INTERNALS (Core Library)                                                │
│                                                                              │
│   Facade  ──▶  Repository  ──▶  KtorApiClient  ──▶  PlatformHttpClient     │
│                                                                              │
│   • Koin DI (isolated instance)                                             │
│   • Header injection (auth token, API key, user-agent, platform)            │
│   • JSON parsing (kotlinx.serialization)                                    │
│   • Error mapping (ApiResult → SDKState)                                    │
│   • Resilience: Circuit Breaker, Deduplication, Cache                      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Layer Architecture

The SDK is organized into **5 strictly separated layers**. Each layer only communicates with the one immediately below it.

```
┌──────────────────────────────────────────────────────┐
│  Layer 5: Host App (Android / iOS)                    │
│  ─ calls SDKInitializer, reads SDKState               │
└──────────────────────┬───────────────────────────────┘
                       │  public API only
┌──────────────────────▼───────────────────────────────┐
│  Layer 4: Facade (AppFacadeYourFeature)               │
│  ─ thin public-facing API surface                     │
│  ─ delegates 100% to Repository                       │
│  ─ resolves deps via isolated Koin DI                 │
└──────────────────────┬───────────────────────────────┘
                       │  internal only
┌──────────────────────▼───────────────────────────────┐
│  Layer 3: Repository (interface + impl)               │
│  ─ owns business logic, request building, mapping     │
│  ─ maps raw DTOs to UI-ready domain models            │
│  ─ calls ApiClient with structured parameters         │
└──────────────────────┬───────────────────────────────┘
                       │  internal only
┌──────────────────────▼───────────────────────────────┐
│  Layer 2: Core (KtorApiClient)                        │
│  ─ HTTP transport via Ktor                            │
│  ─ universal error mapping → ApiResult                │
│  ─ retry logic (exponential backoff)                  │
│  ─ Resilience interceptors (Cache, Deduplicator, etc.) │
└──────────────────────┬───────────────────────────────┘
                       │  expect/actual
┌──────────────────────▼───────────────────────────────┐
│  Layer 1: Platform (expect/actual)                    │
│  ─ Android: OkHttp engine + Synchronized config       │
│  ─ iOS:     Darwin engine + Atomic config             │
│  ─ PlatformLogger → Log.d / NSLog per platform        │
└──────────────────────────────────────────────────────┘
```

---

## 4. Package Structure

```
shared/src/
├── commonMain/kotlin/com/droidunplugged/kmp_platform_kit/
│   ├── core/                               ← Networking + SDK bootstrap
│   │   ├── ApiClient.kt                   (interface - public contract)
│   │   ├── ApiResult.kt                   (sealed result type - public)
│   │   ├── KtorApiClient.kt               (Ktor impl + resilience logic)
│   │   ├── SDKInitializer.kt              (entry point + lifecycle)
│   │   ├── SDKState.kt                    (UI-ready state - public)
│   │   ├── circuit/                       (CircuitBreaker state machine)
│   │   ├── auth/                          (TokenManager refresh logic)
│   │   ├── paging/                        (SdkPager pagination engine)
│   │   └── di/                            (CoreModule.kt - Koin setup)
│   │
│   ├── features/                           ← Where you build your business logic
│   │   └── yourfeature/
│   │       ├── di/                        (Koin module)
│   │       ├── facade/                    (Public Facade)
│   │       ├── repository/                (Internal Repository)
│   │       └── models/                    (DTOs and Domain models)
│   │
│   └── shared/                             ← Cross-cutting concerns
│       ├── models/                        (ApiEnvelope, PaginationInfo)
│       └── utils/                         (JsonProvider, PlatformLogger)
│
├── androidMain/kotlin/                     ← Android specifics
│   └── core/                              (OkHttp, SDKApplicationContext)
│
└── iosMain/kotlin/                         ← iOS specifics
    └── core/                              (Darwin engine, Atomic config)
```

---

## 5. Initialization System

The SDK uses a thread-safe, idempotent initialization system.

### `SDKInitializer` Lifecycle

1.  **`configure { ... }`**: Registers a lazy credential provider.
2.  **`init(credentials)`**: Explicit initialization (suspendable).
3.  **`ensureInitialized()`**: Lazy, thread-safe guard that ensures the SDK is ready before any API call.
4.  **`reset()`**: Tears down the DI graph and clears in-memory credentials.

---

## 6. Dependency Injection (Koin)

The SDK uses an **isolated Koin instance**. This prevents conflicts with the host app's own dependency injection.

*   **`SdkKoinComponent`**: Base interface for resolving internal dependencies.
*   **`CoreModule`**: Provides `HttpClient`, `ApiClient`, `CircuitBreaker`, etc.
*   **Dynamic Modules**: Host apps can provide `additionalModules` during init.

---

## 7. Networking Pipeline

Every HTTP request flows through a sophisticated resilience pipeline:

1.  **Deduplication**: Identical concurrent requests share a single network call.
2.  **Circuit Breaker**: Fast-fails if the backend is down.
3.  **Authentication**: Injects latest tokens from `PlatformConfig`.
4.  **Tracing**: Injects `traceparent` and `x-b3-*` headers.
5.  **Caching**: Returns cached data based on `CachePolicy`.
6.  **Retry**: Exponential backoff with jitter for transient errors.

---

## 8. Error Handling Strategy

The SDK maps all failures into a structured hierarchy:

*   **`ApiResult`**: Low-level transport result (Success, Failure, NetworkError, Cancelled).
*   **`SdkError`**: High-level typed errors (Unauthorized, RateLimited, ServerError, etc.).
*   **`SDKState`**: UI-friendly wrapper (Loading, Success, Error).

---

## 9. State Management (SDKState)

`SDKState<T>` is designed to be consumed directly by ViewModels and SwiftUI views:

*   **`Loading`**: In-flight request.
*   **`Success(data)`**: Valid data returned.
*   **`Error`**: Network or API failure with machine-readable codes.

Use `sdkStateFlow { ... }` to automatically convert any suspend call into a UI-ready Flow.

---

## 10. Thread Safety Model

The SDK is built for multi-threaded environments:

*   **Android**: Uses JVM monitor locks (`synchronized`) and `@Volatile`.
*   **iOS**: Uses `AtomicReference` and CAS (Compare-And-Swap) loops to satisfy the Kotlin/Native memory model.
*   **Lifecycle**: All global state changes are guarded by a `Mutex`.

---

## 11. Retry Behavior (RetryConfig)

Transparent retry logic with **exponential backoff + full jitter**:
*   **`maxAttempts`**: Default 3.
*   **`initialBackoff`**: Default 500ms.
*   **Retry Criteria**: 5xx errors and Network I/O failures.

---

## 12. Shared Models

*   **`BaseApiResponse`**: Generic envelope for standard backend responses.
*   **`PaginationInfo`**: Standardized pagination metadata.
*   **`ErrorInfo`**: Detailed error structures for business logic failures.

---

## 13. Debug Mode & Logging

*   **`SDKConfig.debugMode`**: Toggle for verbose HTTP body logging.
*   **`PlatformLogger`**: Interface for bridging SDK logs to the host's logging system (Logcat/NSLog).
*   **Redaction**: Auth headers are automatically redacted in debug logs.

---

## 14. Security & Credential Handling

*   **Memory-Only**: Credentials are never persisted to disk by the SDK.
*   **SSL Pinning**: Optional certificate pinning per environment.
*   **Supply Chain**: All dependencies are scanned for vulnerabilities via OWASP.
