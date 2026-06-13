# 📱 Android Integration Guide - KMP Platform Kit

---

## 1. Prerequisites
*   **Kotlin:** 2.1.0+
*   **minSdk:** 24+
*   **compileSdk:** 36

---

## 2. Dependency Setup
If using the Fat AAR (recommended for local development):
1.  Copy `shared-core-fat.aar` to your app's `libs/` folder.
2.  Add to your `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation(files("libs/shared-core-fat.aar"))
}
```

---

## 3. Initialization
Initialize the SDK in your `Application` class or main entry point.

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val env = SdkEnvironment(
            id = "prod",
            baseUrl = "https://api.example.com",
            clientId = "your-client-id",
            apiKey = "your-api-key"
        )

        lifecycleScope.launch {
            SDKInitializer.init(
                SDKCredentials(
                    environment = env,
                    authToken = "initial-jwt-token",
                    apiGuid = "session-guid"
                )
            )
        }
    }
}
```

---

## 4. Consuming Data (ViewModel)
The SDK provides `Flow`-based states for easy UI integration.

```kotlin
class MyViewModel(private val facade: MyFeatureFacade) : ViewModel() {

    // Automatically handles Loading, Success, and Error states
    val uiState: StateFlow<SDKState<MyData>> = facade.getDataFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, SDKState.Loading)
}
```

---

## 5. UI Implementation (Compose)
```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel) {
    val state by viewModel.uiState.collectAsState()

    when (state) {
        is SDKState.Loading -> ShowSpinner()
        is SDKState.Success -> ShowList((state as SDKState.Success).data)
        is SDKState.Error -> ShowError((state as SDKState.Error).message)
    }
}
```

---

## 6. ProGuard / R8
The SDK bundles its own consumer rules. You do not need to add manual rules for the SDK's internal classes. If you encounter issues with shrinking, ensure `minifyEnabled` is tested thoroughly with the Fat AAR.
