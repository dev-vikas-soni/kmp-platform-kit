# 🛡️ KMP Platform Kit

[![Kotlin](https://img.shields.io/badge/Kotlin-2.2-blueviolet?logo=kotlin)](https://kotlinlang.org)
[![KMP](https://img.shields.io/badge/Multiplatform-Android%20%7C%20iOS-green)](https://www.jetbrains.com/kotlin-multiplatform/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue)](LICENSE)

**KMP Platform Kit** is a production-grade Kotlin Multiplatform (KMP) SDK framework. It provides the "boring but critical" infrastructure — networking, resilience, security, and observability — so you can focus on building features, not plumbing.

Built for the real world, it solves common SDK challenges like **401 coalescing**, **request deduplication**, **circuit breaking**, and **binary compatibility**.

---

## 📖 Documentation

*   [**Architecture**](./docs/architecture.md) — Layer design, DI isolation, and thread safety.
*   [**Android Integration**](./docs/integration-android.md) — Step-by-step guide for Android apps.
*   [**iOS Integration**](./docs/integration-ios.md) — Swift-friendly patterns using SKIE.
*   [**Technical Concepts**](./docs/technical-concepts.md) — Deep-dive into the "Why" and "How".
*   [**Unit Testing**](./docs/unit-testing.md) — Using our built-in test toolkit.

---

## ✨ Key Features

| Capability | Details |
|:---|:---|
| 🌐 **Typed Networking** | Ktor-based `ApiClient` with sealed `ApiResult<T>` and `SdkError`. |
| 🧠 **Resilience** | Built-in **Circuit Breaker**, **Request Deduplicator**, and **Smart Caching**. |
| 🔐 **Auth Lifecycle** | Automatic token refresh (proactive & reactive) with 401 coalescing. |
| 📄 **Pagination** | Generic `SdkPager` with `StateFlow`-backed page state. |
| 🔭 **Observability** | Automatic W3C/B3 tracing and pluggable `SDKTelemetry`. |
| 💉 **DI Isolation** | Isolated Koin instance to prevent host-app dependency conflicts. |
| 🍏 **Swift First** | Optimized for iOS via **SKIE** (Flow → AsyncSequence, Enums). |

---

## 🏗 Architecture

The SDK follows a strict 5-layer architecture to ensure testability and isolation:

```
shared/src/
├── commonMain/
│   ├── core/           ← Resilience, Auth, Paging, DI, Tracing
│   ├── features/       ← Your feature modules go here
│   └── shared/         ← Models, Utils, Extensions
├── androidMain/        ← OkHttp engine, Android Context integration
└── iosMain/            ← Darwin engine, Atomic thread-safety logic
```

---

## 🚀 Quick Start

### 1. Initialize (Android Example)
```kotlin
val env = SdkEnvironment(
    id = "prod",
    baseUrl = "https://api.example.com",
    clientId = "client-id",
    apiKey = "api-key"
)

// One-time initialization
SDKInitializer.init(
    SDKCredentials(environment = env, authToken = "jwt", apiGuid = "guid")
)
```

### 2. Consume in UI (Compose)
```kotlin
val state by viewModel.sdkStateFlow.collectAsState()

when (state) {
    is SDKState.Loading -> ShowSpinner()
    is SDKState.Success -> RenderData(state.data)
    is SDKState.Error -> HandleError(state.message)
}
```

---

## 🧪 Quality Gates

We don't just write code; we enforce quality:
*   **Detekt**: Static analysis for code smells.
*   **Kover**: Mandatory 80% line coverage.
*   **BCV**: Public API compatibility validation.
*   **OWASP**: Automated dependency vulnerability scanning.

---

## 🤝 Contributing

Contributions are what make the open-source community an amazing place to learn, inspire, and create.

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

Distributed under the Apache 2.0 License. See `LICENSE` for more information.

---

**Built with ❤️ for the BlrKotlin Conference.**
