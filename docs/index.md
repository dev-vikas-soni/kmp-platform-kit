# 📖 KMP Platform Kit - Documentation Index

A production-grade **Kotlin Multiplatform SDK framework** for Android and iOS.

---

## 📚 Documents

| Document | Audience | Description |
|:---|:---|:---|
| [**Architecture**](./architecture.md) | Contributors / Seniors | Layer design, DI, Resilience, and Thread Safety models. |
| [**Android Integration**](./integration-android.md) | App Developers | Guide to integrating the SDK into an Android app. |
| [**iOS Integration**](./integration-ios.md) | App Developers | Guide to integrating the SDK into an iOS app using Swift. |
| [**Technical Concepts**](./technical-concepts.md) | All Developers | Deep-dive into KMP, Coroutines, Koin, and SDK patterns. |
| [**Unit Testing**](./unit-testing.md) | Contributors | How to write and run tests using the SDK's test toolkit. |

---

## 🚀 Quick Reference

### Core Architecture
The SDK is built on a **5-layer architecture**:
1. **Public Facade**: The entry point for host applications.
2. **Internal Repository**: Business logic and data mapping.
3. **Core Subsystems**: Resilience (Circuit Breaker, Deduplication, Cache).
4. **Networking**: Ktor-based `ApiClient` with typed results.
5. **Platform Logic**: Native engines and thread-safe configuration.

### Key Frameworks Used
* **Networking**: Ktor 3.1.3 (OkHttp on Android, Darwin on iOS).
* **DI**: Koin 4.1.0 (Internal/Isolated).
* **Serialization**: kotlinx.serialization 1.8.1.
* **Coroutines**: kotlinx.coroutines 1.10.2.
* **Swift Interop**: SKIE 0.10.10.

---

## 🛠 Quality & Security
Every build is guarded by:
* **Detekt**: Static code analysis.
* **Kover**: 80% Minimum Code Coverage.
* **BCV**: Public API Compatibility Check.
* **OWASP**: Dependency Vulnerability Scanning.

---

## 🎯 Start Here

* **I am an Android dev:** [Read the Android Guide](./integration-android.md)
* **I am an iOS dev:** [Read the iOS Guide](./integration-ios.md)
* **I want to know how the SDK handles failures:** [Architecture -> Networking Pipeline](./architecture.md#7-networking-pipeline)
* **I want to add a new feature:** [Architecture -> Package Structure](./architecture.md#4-package-structure)
