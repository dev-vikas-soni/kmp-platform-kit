# 🧪 Unit Testing - KMP Platform Kit

The SDK includes a comprehensive testing toolkit designed to make testing business logic and networking both easy and reliable.

---

## 🏗 Test Infrastructure

The testing framework is located in `shared/src/commonTest/`.

### 1. `FakeApiClient`
A high-performance, in-memory mock of the `ApiClient`. It allows you to:
*   Queue response results (`Success`, `Failure`, `NetworkError`).
*   Verify URL paths and headers.
*   Count calls to specific endpoints.

### 2. `BaseRepositoryTest<T>`
An abstract base class for testing repositories. By extending this, you automatically get tests for:
*   Standard success mapping.
*   API Error handling (4xx/5xx).
*   Network failure scenarios.
*   Coroutine cancellation propagation.

---

## 📁 Test Package Structure

```
shared/src/commonTest/kotlin/com/droidunplugged/kmp_platform_kit/
├── core/                           ← Tests for SDK internals
│   ├── CircuitBreakerTest.kt
│   ├── TokenManagerTest.kt
│   └── KtorApiClientTest.kt
└── testutil/                       ← Reusable test utilities
    ├── FakeApiClient.kt
    ├── BaseRepositoryTest.kt
    └── Assertions.kt
```

---

## ✍️ Writing a Repository Test

```kotlin
class MyRepositoryTest : BaseRepositoryTest<MyDomainModel>() {
    
    private lateinit var repository: MyRepositoryImpl

    override fun createRepository(client: FakeApiClient) {
        repository = MyRepositoryImpl(apiClient = client)
    }

    override suspend fun callRepository(): ApiResult<MyDomainModel> {
        return repository.fetchData()
    }

    @Test
    fun `should map raw JSON to domain model correctly`() = runTest {
        val json = """{"status": "SUCCESS", "data": {"id": 1, "name": "Test"}}"""
        apiClient.enqueueResponse(json)

        val result = callRepository()
        
        result.assertSuccess { data ->
            assertEquals(1, data.id)
            assertEquals("Test", data.name)
        }
    }
}
```

---

## 🚀 Running Tests

### All Shared Tests
```bash
./gradlew :shared:allTests
```

### Android-Specific Tests
```bash
./gradlew :shared:testDebugUnitTest
```

### iOS-Specific Tests (Requires macOS)
```bash
./gradlew :shared:iosSimulatorArm64Test
```

---

## 📊 Coverage Requirements

We use **Kover** to enforce code quality.
*   **Threshold:** 80% line coverage.
*   **Verification:** Run `./gradlew :shared:koverVerify`.
*   **Reports:** Run `./gradlew :shared:koverHtmlReport` and view in `shared/build/reports/kover/html/`.
