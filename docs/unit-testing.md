# 🧪 Unit Testing Guide - KMP Platform Kit

> **Location:** `shared/src/commonTest/kotlin/testutil/`  
> **Last Updated:** March 2026  
> **Audience:** SDK contributors adding new features

---

## Table of Contents

1. [Overview](#1-overview)
2. [Directory Structure](#2-directory-structure)
3. [Test Dependencies](#3-test-dependencies)
4. [Test Infrastructure Components](#4-test-infrastructure-components)
    - 4.1 [FakeApiClient](#41-fakeapiclient)
    - 4.2 [BaseRepositoryTest](#42-baserepositorytestt)
    - 4.3 [BaseIntegrationTest](#43-baseintegrationtestt)
    - 4.4 [Assertion Helpers](#44-assertion-helpers)
    - 4.5 [CommonFixtures](#45-commonfixtures)
    - 4.6 [TestFixtures](#46-testfixtures)
    - 4.7 [TestLogger](#47-testlogger)
5. [Test Organization Per Feature](#5-test-organization-per-feature)
6. [Step-by-Step: Adding Tests for a New Module](#6-step-by-step-adding-tests-for-a-new-module)
7. [Complete Example - Hypothetical "Orders" Module](#7-complete-example--hypothetical-orders-module)
8. [Cheat Sheet](#8-cheat-sheet)
9. [Best Practices & Common Mistakes](#9-best-practices--common-mistakes)
10. [Running Tests](#10-running-tests)

---

## 1. Overview

The SDK ships with a **reusable test framework** built on `commonTest`. The goal: write only feature-specific tests manually; inherit everything generic from base classes.

| Capability            | What it provides                                                                                        |
|-----------------------|---------------------------------------------------------------------------------------------------------|
| **Base test classes** | 13+ standard tests (error propagation, SDKState mapping, flow emissions) per feature - zero copy-paste  |
| **Fake API client**   | In-memory `ApiClient` that exercises real deserialization without network calls                         |
| **Assertion DSL**     | Fluent extension functions for `ApiResult`, `SDKState`, and Flow emissions                              |
| **Shared fixtures**   | Centralized JSON strings (success, error, malformed, edge-case) - update once when API contract changes |
| **Test logger**       | Captures all log output in-memory for assertion                                                         |

**Design principle:** For any new feature, implement 4–5 abstract members → get 21+ tests automatically.

---

## 2. Directory Structure

```
shared/src/commonTest/kotlin/
├── testutil/                              ← Reusable test infrastructure
│   ├── BaseRepositoryTest.kt             (abstract base - 13 inherited tests)
│   ├── BaseIntegrationTest.kt            (abstract base - 8 inherited tests)
│   ├── Assertions.kt                     (ApiResult / SDKState / Flow helpers)
│   ├── CommonFixtures.kt                 (feature-agnostic JSON fixtures)
│   ├── TestFixtures.kt                   (feature-specific JSON fixtures)
│   ├── FakeApiClient.kt                  (in-memory ApiClient implementation)
│   ├── TestLogger.kt                     (in-memory Logger for assertions)
│   └── TestLoggerTest.kt                 (tests for the test infra itself)
│
├── core/                                  ← Tests for SDK core layer
│   ├── ApiResultTest.kt
│   ├── ApiResultExtensionsTest.kt
│   ├── KtorApiClientTest.kt
│   ├── SDKConfigAndInfoTest.kt
│   ├── SDKCredentialsTest.kt
│   ├── SDKStateFlowTest.kt
│   └── SDKStateTest.kt
│
├── shared/                                ← Tests for shared utilities
│   ├── models/
│   │   ├── ApiEnvelopeTest.kt
│   │   └── PaginationInfoTest.kt
│   └── utils/
│       ├── HttpHeadersTest.kt
│       ├── JsonProviderTest.kt
│       └── NoOpLoggerTest.kt
│
└── features/
    └── yourfeature/                 ← Reference implementation
        ├── YourFeatureTest.kt       (integration - extends BaseIntegrationTest)
        ├── repository/
        │   └── YourFeatureRepositoryImplTest.kt  (extends BaseRepositoryTest)
        ├── models/
        │   ├── YourFeatureResponseTest.kt        (JSON parsing)
        │   ├── InventoryMapperTest.kt                  (DTO → UI model)
        │   └── InventoriesModelTest.kt                 (UI model contract)
        └── endpoints/
            └── YourFeatureEndpointsTest.kt       (URL/path builder)
```

---

## 3. Test Dependencies

Declared in `shared/build.gradle.kts` under `commonTest`:

```kotlin
commonTest.dependencies {
    implementation(libs.kotlin.test)        // @Test, assertEquals, assertIs
    implementation(libs.koin.test)          // Koin DI test utilities
    implementation(libs.coroutines.test)    // runTest { }, TestDispatcher
    implementation(libs.ktor.client.mock)   // MockEngine (used by FakeApiClient)
}
```

| Library            | Version  |
|--------------------|----------|
| `kotlin-test`      | `2.1.21` |
| `koin-test`        | `4.1.0`  |
| `coroutines-test`  | `1.10.2` |
| `ktor-client-mock` | `3.1.3`  |

---

## 4. Test Infrastructure Components

### 4.1 `FakeApiClient`

**File:** `testutil/FakeApiClient.kt`

A complete in-memory replacement for `ApiClient`. Returns raw JSON strings and invokes the same `responseParser` lambda as production code - so **real deserialization is exercised** without any network calls.

#### API

| Method / Property                      | Purpose                                        |
|----------------------------------------|------------------------------------------------|
| `enqueueGet(pathPrefix) { response }`  | Register a response for a specific GET path    |
| `enqueuePost(pathPrefix) { response }` | Register a response for a specific POST path   |
| `setDefaultGetResponse { response }`   | Fallback for any unmatched GET                 |
| `setDefaultPostResponse { response }`  | Fallback for any unmatched POST                |
| `getCallCount`                         | Number of GET calls made                       |
| `postCallCount`                        | Number of POST calls made                      |
| `lastGetPath`                          | Last GET path requested                        |
| `lastPostPath`                         | Last POST path requested                       |
| `allGetPaths`                          | All GET paths (for inspection)                 |
| `allPostCalls`                         | All POST calls as `List<PostCall(path, body)>` |
| `reset()`                              | Clear all state                                |

#### Usage example

```kotlin
val client = FakeApiClient()

// Path-specific response
client.enqueueGet("customer/123/inventories") {
    ApiResult.Success("""{"status":"SUCCESS","data":{}}""")
}

// Fallback for any unmatched path
client.setDefaultGetResponse {
    ApiResult.Failure(401, "Unauthorized")
}

// After test execution
assertEquals(1, client.getCallCount)
assertTrue(client.lastGetPath!!.contains("customer/123"))
```

---

### 4.2 `BaseRepositoryTest<T>`

**File:** `testutil/BaseRepositoryTest.kt`

Abstract base class that provides **13 automatic tests** for any repository. Subclass it, implement 4 members, and you're covered.

#### What you must implement

| Member                                    | Type          | Purpose                                               |
|-------------------------------------------|---------------|-------------------------------------------------------|
| `createRepository(client: FakeApiClient)` | `fun`         | Create the repository under test with the fake client |
| `callRepository(): ApiResult<T>`          | `suspend fun` | Call the specific repository method being tested      |
| `successJson: String`                     | `val`         | Valid JSON for the happy-path test                    |
| `validateSuccessData(data: T)`            | `fun`         | Assert fields on the parsed domain model              |

#### 13 tests you get for free

| Category              | Test                                               | What it verifies                              |
|-----------------------|----------------------------------------------------|-----------------------------------------------|
| **Error Propagation** | API Failure propagates code and message            | `401 → Failure(401, "Unauthorized")`          |
|                       | NetworkError propagates unchanged                  | Network error pass-through                    |
|                       | Cancelled propagates unchanged                     | Cancellation pass-through                     |
|                       | Server error propagates 500                        | `500 → Failure(500, ...)`                     |
| **Success Path**      | Success response is parsed and mapped              | JSON → domain model via `validateSuccessData` |
| **SDKState Mapping**  | Success → SDKState.Success                         | `toSDKState()` result verified                |
|                       | Failure → SDKState.ErrorBody                       | `toSDKState()` with code=403                  |
|                       | NetworkError → SDKState.Error(isNetworkError=true) | Network flag verified                         |
|                       | Cancelled → SDKState.Error(isNetworkError=false)   | Non-network error                             |
| **sdkStateFlow**      | Emits Loading then Success                         | `[Loading, Success<T>]`                       |
|                       | Emits Loading then ErrorBody on failure            | `[Loading, ErrorBody(500)]`                   |
|                       | Emits Loading then Error on network failure        | `[Loading, Error(isNetworkError=true)]`       |
|                       | (varies by impl)                                   | Additional edge cases                         |

#### Minimal subclass

```kotlin
class OrderRepositoryImplTest : BaseRepositoryTest<OrderListModel>() {

    private lateinit var repository: OrderRepositoryImpl

    override fun createRepository(client: FakeApiClient) {
        repository = OrderRepositoryImpl(apiClient = client)
    }

    override suspend fun callRepository(): ApiResult<OrderListModel> =
        repository.getOrders(OrderQuery(customerId = "C-100"))

    override val successJson: String
        get() = TestFixtures.ORDER_SUCCESS_JSON

    override fun validateSuccessData(data: OrderListModel) {
        assertEquals("SUCCESS", data.status)
        assertTrue(data.orders.isNotEmpty())
        assertEquals("ORD-001", data.orders[0].orderId)
    }

    // ── Feature-specific tests below - fakeClient is accessible ──
    @Test
    fun `builds correct URL path with customer ID`() = runTest {
        fakeClient.setDefaultGetResponse { ApiResult.Success(successJson) }
        repository.getOrders(OrderQuery(customerId = "C-100"))
        assertTrue(fakeClient.lastGetPath!!.contains("customer/C-100/orders"))
    }
}
```

> **Result:** 4 members implemented → **13 inherited + custom tests** - virtually no boilerplate.

---

### 4.3 `BaseIntegrationTest<T>`

**File:** `testutil/BaseIntegrationTest.kt`

Similar to `BaseRepositoryTest` but designed for **end-to-end pipeline tests** (JSON → Repository → Domain Model → SDKState → Flow). Provides **8 automatic tests**.

#### What you must implement

| Member                                    | Type          | Purpose                                       |
|-------------------------------------------|---------------|-----------------------------------------------|
| `createRepository(client: FakeApiClient)` | `fun`         | Create the repo under test                    |
| `callRepository(): ApiResult<T>`          | `suspend fun` | Invoke the method under test                  |
| `successJson: String`                     | `val`         | Valid success JSON fixture                    |
| `errorJson: String`                       | `val`         | API-level error JSON (HTTP 200, status=ERROR) |
| `validateSuccessData(data: T)`            | `fun`         | Full end-to-end field assertions              |

#### 8 tests you get for free

| Category         | Test                                                              |
|------------------|-------------------------------------------------------------------|
| **Pipeline**     | Full pipeline produces correctly mapped UI model                  |
| **SDKState**     | Success result converts to SDKState.Success                       |
|                  | Failure result converts to SDKState.ErrorBody                     |
|                  | Network error converts to SDKState.Error with isNetworkError=true |
| **sdkStateFlow** | Emits Loading then Success with mapped model                      |
|                  | Emits Loading then ErrorBody on API failure                       |
|                  | Emits Loading then Error on network failure                       |
| **Edge Case**    | API error body is parsed into model with ERROR status             |

#### Minimal subclass

```kotlin
class OrderIntegrationTest : BaseIntegrationTest<OrderListModel>() {

    private lateinit var repo: OrderRepositoryImpl

    override fun createRepository(client: FakeApiClient) {
        repo = OrderRepositoryImpl(apiClient = client)
    }

    override suspend fun callRepository() =
        repo.getOrders(OrderQuery(customerId = "C-100"))

    override val successJson get() = TestFixtures.ORDER_SUCCESS_JSON
    override val errorJson get() = TestFixtures.ORDER_ERROR_JSON

    override fun validateSuccessData(data: OrderListModel) {
        assertEquals("SUCCESS", data.status)
        assertEquals(1, data.orders.size)
        assertEquals("Shipped", data.orders[0].status)
    }
}
```

> **Result:** 5 members implemented → **8 more tests** - zero boilerplate.

---

### 4.4 Assertion Helpers

**File:** `testutil/Assertions.kt`

Fluent extension functions that make test code readable and reduce assertion boilerplate.

#### `ApiResult` assertions

```kotlin
// Unwrap Success data or fail the test immediately
val model = result.assertSuccess()

// Assert Failure with specific code and optional message
result.assertFailure(code = 401, message = "Unauthorized")
result.assertFailure(code = 401)  // message check is optional

// Assert specific error variants
result.assertNetworkError()
result.assertCancelled()
```

#### `SDKState` assertions

```kotlin
// Assert Loading state (and verify convenience flags)
state.assertLoading()

// Assert Success and unwrap data for further assertions
val data = state.assertSDKSuccess()

// Assert ErrorBody with optional field checks
state.assertErrorBody(code = 403)
state.assertErrorBody(code = 403, message = "Forbidden")
state.assertErrorBody(code = 403, message = "Forbidden", errorCode = "EX_402_115")

// Assert generic Error
state.assertSDKError(isNetworkError = true)
state.assertSDKError(messageContains = "timeout")
```

#### Flow assertions

```kotlin
// Validate Loading → Result pattern and return the final emission
val emissions = sdkStateFlow { callRepository() }.toList()
val finalState = emissions.assertLoadingThenResult()

// Then assert the final state type
assertIs<SDKState.Success<OrderListModel>>(finalState)
```

---

### 4.5 `CommonFixtures`

**File:** `testutil/CommonFixtures.kt`

Feature-agnostic JSON fixtures. Use for error scenarios that have the same envelope shape across all APIs.

| Member                                  | Description                                         |
|-----------------------------------------|-----------------------------------------------------|
| `errorJson(code, message)`              | Structured API error with one error detail          |
| `multiErrorJson(errors)`                | Error with multiple detail entries                  |
| `ERROR_EMPTY_DETAILS_JSON`              | Error with empty `errorDetails` array               |
| `FAILURE_NULL_JSON`                     | `status: "FAILURE"` with null error and data        |
| `MINIMAL_SUCCESS_JSON`                  | `{"status":"SUCCESS"}` - no data block              |
| `MALFORMED_JSON`                        | Invalid JSON (for parse-error tests)                |
| `wrapSuccess(dataBlock)`                | Wraps a raw data block in the standard API envelope |
| `wrapSuccessWithExtraFields(dataBlock)` | Same + unknown fields (tests `ignoreUnknownKeys`)   |

#### Examples

```kotlin
// Generate error JSON with specific code and message
val json = CommonFixtures.errorJson(code = "AUTH_001", message = "Token expired")

// Wrap feature-specific data in the standard API envelope
val json = CommonFixtures.wrapSuccess(
    """
    "orderId": "ORD-001",
    "items": []
    """
)
// → {"status":"SUCCESS","error":null,"data":{"orderId":"ORD-001","items":[]}}
```

---

### 4.6 `TestFixtures`

**File:** `testutil/TestFixtures.kt`

Feature-specific JSON fixtures organized by feature. Centralized here so contract changes are updated in one place.

#### Current fixtures (Physical Inventory)

| Constant                               | Scenario                                         |
|----------------------------------------|--------------------------------------------------|
| `INVENTORY_SUCCESS_JSON`               | Full success with 2 items + pagination           |
| `INVENTORY_SUCCESS_EMPTY_JSON`         | Success with empty list                          |
| `INVENTORY_SUCCESS_NO_PAGINATION_JSON` | Success without pagination block                 |
| `INVENTORY_ERROR_JSON`                 | API error with structured error body             |
| `INVENTORY_MULTI_ERROR_JSON`           | API error with multiple detail entries           |
| `INVENTORY_ERROR_EMPTY_DETAILS_JSON`   | Error with empty details array                   |
| `INVENTORY_FAILURE_NULL_JSON`          | Failure with all-null fields                     |
| `INVENTORY_EXTRA_FIELDS_JSON`          | Extra unknown fields (tests `ignoreUnknownKeys`) |
| `INVENTORY_MINIMAL_JSON`               | Just status, no data block                       |
| `MALFORMED_JSON`                       | Invalid JSON                                     |

#### Convention for new features

```kotlin
object TestFixtures {
    // Existing inventory fixtures...

    // ── Orders ─────────────────────────────────────────────────────
    val ORDER_SUCCESS_JSON = """
        {
            "status": "SUCCESS",
            "error": null,
            "data": {
                "orders": [
                    { "orderId": "ORD-001", "status": "Shipped", "total": "149.99" }
                ]
            }
        }
    """.trimIndent()

    val ORDER_SUCCESS_EMPTY_JSON = """
        {"status":"SUCCESS","error":null,"data":{"orders":[]}}
    """.trimIndent()

    val ORDER_ERROR_JSON = CommonFixtures.errorJson("ORD_403", "Access denied")
}
```

---

### 4.7 `TestLogger`

**File:** `testutil/TestLogger.kt`

An in-memory `Logger` implementation that captures all log entries for test assertions.

#### API

```kotlin
val logger = TestLogger()

// Used automatically when injected into code under test
logger.d("Tag", "debug message")
logger.i("Tag", "info message")
logger.w("Tag", "warning message")
logger.e("Tag", "error message", throwable = null)

// Query helpers
logger.hasErrors()                         // Boolean - any ERROR entries?
logger.hasWarning("InventoryMapper")       // Boolean - warning with this tag?
logger.hasMessageContaining("timeout")     // Boolean - any entry with this substring?
logger.entriesAt(TestLogger.Level.DEBUG)   // List<LogEntry> - filtered by level
logger.entries                             // List<LogEntry> - all entries
logger.clear()                             // Reset all captured entries
```

---

## 5. Test Organization Per Feature

Each feature mirrors the production code structure:

```
features/<feature-name>/
├── <Feature>IntegrationTest.kt          ← extends BaseIntegrationTest<T>
├── repository/
│   └── <Feature>RepositoryImplTest.kt   ← extends BaseRepositoryTest<T>
├── models/
│   ├── <Feature>ResponseTest.kt         ← JSON deserialization tests
│   ├── <Feature>MapperTest.kt           ← DTO → UI model mapping tests
│   └── <Feature>ModelTest.kt            ← UI model contract tests (defaults, equality)
└── endpoints/
    └── <Feature>EndpointsTest.kt        ← URL / path builder tests
```

### What to test at each layer

| Layer              | What to test                                                  | Tool                             |
|--------------------|---------------------------------------------------------------|----------------------------------|
| **Repository**     | Error propagation, path construction, successful mapping      | `BaseRepositoryTest<T>`          |
| **Integration**    | Full pipeline: JSON → Model → SDKState → Flow                 | `BaseIntegrationTest<T>`         |
| **Response model** | JSON deserialization, nullable fields, `ignoreUnknownKeys`    | Direct `Json.decodeFromString()` |
| **Mapper**         | DTO → UI model field mapping, date formatting, default values | Direct function-call tests       |
| **UI model**       | Data class defaults, equality, `copy()`                       | Direct constructor tests         |
| **Endpoints**      | URL path construction with various query parameters           | Direct function-call tests       |

---

## 6. Step-by-Step: Adding Tests for a New Module

### Step 1 - Add JSON fixtures to `TestFixtures.kt`

```kotlin
val ORDER_SUCCESS_JSON = """
{
    "status": "SUCCESS",
    "error": null,
    "data": {
        "orders": [
            { "orderId": "ORD-001", "status": "Shipped", "total": "149.99" }
        ],
        "paginationDetails": { "totalPages": 1, "hasNext": false }
    }
}
""".trimIndent()

val ORDER_SUCCESS_EMPTY_JSON = """
    {"status":"SUCCESS","error":null,"data":{"orders":[]}}
""".trimIndent()

val ORDER_ERROR_JSON = CommonFixtures.errorJson("ORD_403", "Access denied")
```

### Step 2 - Create the repository test

```kotlin
// features/orders/repository/OrderRepositoryImplTest.kt

class OrderRepositoryImplTest : BaseRepositoryTest<OrderListModel>() {

    private lateinit var repository: OrderRepositoryImpl

    // ── 4 required overrides ─────────────────────────────────────

    override fun createRepository(client: FakeApiClient) {
        repository = OrderRepositoryImpl(apiClient = client)
    }

    override suspend fun callRepository(): ApiResult<OrderListModel> =
        repository.getOrders(OrderQuery(customerId = "C-100"))

    override val successJson: String
        get() = TestFixtures.ORDER_SUCCESS_JSON

    override fun validateSuccessData(data: OrderListModel) {
        assertEquals("SUCCESS", data.status)
        assertTrue(data.orders.isNotEmpty())
        assertEquals("ORD-001", data.orders[0].orderId)
    }

    // ── Feature-specific tests ───────────────────────────────────

    @Test
    fun `builds correct URL path with customer ID`() = runTest {
        fakeClient.setDefaultGetResponse { ApiResult.Success(successJson) }
        repository.getOrders(OrderQuery(customerId = "C-100"))
        assertTrue(fakeClient.lastGetPath!!.contains("customer/C-100/orders"))
    }

    @Test
    fun `empty orders list returns Success with empty list`() = runTest {
        fakeClient.setDefaultGetResponse {
            ApiResult.Success(TestFixtures.ORDER_SUCCESS_EMPTY_JSON)
        }
        val result = callRepository()
        val data = result.assertSuccess()
        assertTrue(data.orders.isEmpty())
    }
}
```

**Result:** 4 overrides → **13 inherited + 2 custom = 15 tests** in ~40 lines.

### Step 3 - Create the integration test

```kotlin
// features/orders/OrderIntegrationTest.kt

class OrderIntegrationTest : BaseIntegrationTest<OrderListModel>() {

    private lateinit var repo: OrderRepositoryImpl

    override fun createRepository(client: FakeApiClient) {
        repo = OrderRepositoryImpl(apiClient = client)
    }

    override suspend fun callRepository() =
        repo.getOrders(OrderQuery(customerId = "C-100"))

    override val successJson get() = TestFixtures.ORDER_SUCCESS_JSON
    override val errorJson get() = TestFixtures.ORDER_ERROR_JSON

    override fun validateSuccessData(data: OrderListModel) {
        assertEquals("SUCCESS", data.status)
        assertEquals(1, data.orders.size)
        assertEquals("Shipped", data.orders[0].status)
    }
}
```

**Result:** 5 overrides → **8 more tests** - zero boilerplate.

### Step 4 - Add model, mapper, and endpoint tests

```kotlin
// models/OrderResponseTest.kt - JSON deserialization
class OrderResponseTest {
    @Test
    fun `deserializes full success response`() {
        val response = Json.decodeFromString<OrderResponse>(TestFixtures.ORDER_SUCCESS_JSON)
        assertEquals("SUCCESS", response.status)
        assertEquals(1, response.data?.orders?.size)
    }

    @Test
    fun `ignores unknown fields`() {
        val json = CommonFixtures.wrapSuccessWithExtraFields(
            """
            "orderId": "ORD-001", "unknownField": "ignored"
        """
        )
        assertDoesNotThrow {
            Json.decodeFromString<OrderResponse>(json)
        }
    }
}

// models/OrderMapperTest.kt - DTO → UI model
class OrderMapperTest {
    @Test
    fun `maps OrderDetail to Order correctly`() {
        val detail = OrderDetail(orderId = "ORD-001", status = "Shipped", total = "149.99")
        val model = detail.toOrder()
        assertEquals("ORD-001", model.orderId)
        assertEquals("Shipped", model.status)
        assertEquals("149.99", model.total)
    }
}

// endpoints/OrderEndpointsTest.kt - URL construction
class OrderEndpointsTest {
    @Test
    fun `builds path with customer ID`() {
        val path = OrderEndpoints.list(customerId = "C-100")
        assertEquals("ecomm/nxtgen/core/orders/v1/customer/C-100/orders", path)
    }

    @Test
    fun `appends order query parameter`() {
        val path = OrderEndpoints.list(customerId = "C-100", order = "orderId")
        assertTrue(path.contains("order=orderId"))
    }
}
```

### Step 5 - Run and validate

```bash
# Run only this feature's tests
./gradlew :shared:allTests --tests "*.features.orders.*"

# Run all tests
./gradlew :shared:allTests
```

---

## 7. Complete Example - Hypothetical "Orders" Module

Full test file tree you would create:

```
shared/src/commonTest/kotlin/features/orders/
├── OrderIntegrationTest.kt               ← 8 tests (inherited)
├── repository/
│   └── OrderRepositoryImplTest.kt        ← 13+ tests (inherited + custom)
├── models/
│   ├── OrderResponseTest.kt              ← ~5 tests (JSON parsing)
│   ├── OrderMapperTest.kt                ← ~5 tests (DTO → model mapping)
│   └── OrderModelTest.kt                 ← ~3 tests (defaults, equality)
└── endpoints/
    └── OrderEndpointsTest.kt             ← ~4 tests (path building)
```

**Total: ~38+ tests** with the base classes doing all the heavy lifting.

---

## 8. Cheat Sheet

### Base classes summary

| Base Class               | Inherited Tests                                                | You Provide        |
|--------------------------|----------------------------------------------------------------|--------------------|
| `BaseRepositoryTest<T>`  | **13** (error propagation, SDKState, sdkStateFlow)             | 4 abstract members |
| `BaseIntegrationTest<T>` | **8** (full pipeline, SDKState, sdkStateFlow, error edge case) | 5 abstract members |

### Assertion helpers summary

| Helper                                 | Purpose                                 |
|----------------------------------------|-----------------------------------------|
| `result.assertSuccess()`               | Unwrap Success data or fail test        |
| `result.assertFailure(code)`           | Assert Failure with specific code       |
| `result.assertNetworkError()`          | Assert NetworkError variant             |
| `result.assertCancelled()`             | Assert Cancelled variant                |
| `state.assertLoading()`                | Assert SDKState.Loading                 |
| `state.assertSDKSuccess()`             | Assert SDKState.Success and unwrap data |
| `state.assertErrorBody(code)`          | Assert SDKState.ErrorBody with code     |
| `state.assertSDKError(isNetworkError)` | Assert SDKState.Error                   |
| `emissions.assertLoadingThenResult()`  | Validate Loading→Result flow pattern    |

### Fixture helpers summary

| Helper                                            | Returns                     |
|---------------------------------------------------|-----------------------------|
| `CommonFixtures.errorJson(code, message)`         | Error JSON string           |
| `CommonFixtures.wrapSuccess(dataBlock)`           | Full success envelope JSON  |
| `CommonFixtures.wrapSuccessWithExtraFields(data)` | Success with unknown fields |
| `CommonFixtures.MALFORMED_JSON`                   | Invalid JSON string         |
| `TestFixtures.INVENTORY_SUCCESS_JSON`             | Full inventory success JSON |

---

## 9. Best Practices & Common Mistakes

### ✅ Do

- **Extend `BaseRepositoryTest`** for every repository - never duplicate error-propagation logic.
- **Add all JSON fixtures to `TestFixtures.kt`** - when the API contract changes, update in one place.
- **Use `runTest { }`** for all coroutine tests - it provides `TestCoroutineScheduler` automatically.
- **Test each layer independently** - Response (JSON), Mapper (DTO→model), Repository (orchestration), Integration (pipeline).
- **Name tests descriptively** with backticks: `` `empty list returns Success with empty list` ``.
- **Use `CommonFixtures.wrapSuccess()`** to build fixtures from data blocks - keeps JSON DRY.
- **Use `assertSuccess()` / `assertSDKSuccess()`** instead of manual `assertIs<>` + cast chains.

### ❌ Don't

- Don't copy-paste error propagation tests between features - they belong in the base class.
- Don't hardcode JSON strings inline in test methods - use `TestFixtures` or `CommonFixtures`.
- Don't use real network calls - always use `FakeApiClient`.
- Don't forget to call `fakeClient.setDefaultGetResponse { ... }` before `callRepository()`.
- Don't test private implementation details - test through the public repository interface.

### 🔍 Debugging tips

- **`fakeClient.allGetPaths`** - inspect every GET path called during a test
- **`fakeClient.allPostCalls`** - inspect POST paths + request bodies
- **`testLogger.entries`** - see all log messages emitted during a test
- If a test fails with a serialization error, check `TestFixtures` - the JSON may not match the updated response model

---

## 10. Running Tests

```bash
# All platforms (Android JVM + iOS Simulator)
./gradlew :shared:allTests

# Android JVM only (faster)
./gradlew :shared:testDebugUnitTest

# iOS Simulator only
./gradlew :shared:iosSimulatorArm64Test

# Specific feature
./gradlew :shared:allTests --tests "*.features.yourfeature.*"

# Specific test class
./gradlew :shared:allTests --tests "*.YourFeatureRepositoryImplTest"

# With code coverage report (Kover)
./gradlew :shared:koverHtmlReport
# Report → shared/build/reports/kover/html/index.html
```

---

> **Summary:** Implement 4–5 abstract members in the base test classes → get 21+ tests automatically covering error handling, state mapping, and flow emissions. Your manual effort focuses solely on feature-specific logic - path construction, field mapping, edge cases, and business rules.