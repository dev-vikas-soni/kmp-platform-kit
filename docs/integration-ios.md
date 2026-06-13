# 🍎 iOS Integration Guide - KMP Platform Kit

The SDK is distributed as a static **XCFramework**, making it easy to consume in Swift projects.

---

## 1. Prerequisites
*   **Xcode:** 15.0+
*   **iOS Target:** 15.0+
*   **Swift:** 5.9+

---

## 2. Integration
1.  Add `SharedCore.xcframework` to your Xcode project.
2.  Ensure it is set to **"Embed & Sign"** in General -> Frameworks, Libraries, and Embedded Content.

---

## 3. Initialization
Call the SDK initializer in your `AppDelegate` or `App` struct.

```swift
import SharedCore

@main
struct MyApp: App {
    init() {
        let env = SdkEnvironment(
            id: "prod",
            baseUrl: "https://api.example.com",
            clientId: "your-client-id",
            apiKey: "your-api-key"
        )
        
        Task {
            try? await SDKInitializer.shared.init(
                credentials: SDKCredentials(
                    environment: env,
                    authToken: "jwt-token",
                    apiGuid: "session-guid"
                )
            )
        }
    }
}
```

---

## 4. Consuming Data (SwiftUI)
Thanks to **SKIE**, Kotlin `Flow` is converted to Swift `AsyncSequence` automatically.

```swift
class MyViewModel: ObservableObject {
    @Published var state: SDKState<MyData> = .loading

    func fetchData() {
        Task {
            let facade = AppFacadeYourFeature.shared
            for await currentState in facade.getDataFlow() {
                self.state = currentState
            }
        }
    }
}
```

---

## 5. UI Implementation
```swift
struct MyView: View {
    @StateObject var viewModel = MyViewModel()

    var body: some View {
        Group {
            if viewModel.state is SDKStateLoading {
                ProgressView()
            } else if let success = viewModel.state as? SDKStateSuccess<MyData> {
                DataView(success.data)
            } else if let error = viewModel.state as? SDKStateError {
                ErrorView(error.message)
            }
        }
        .onAppear { viewModel.fetchData() }
    }
}
```

---

## 6. Memory Management
*   The SDK uses internal `CoroutineScopes` that are tied to the lifecycle of the `ApiClient`.
*   Call `SDKInitializer.shared.reset()` during logout to release resources and clear in-memory caches.
