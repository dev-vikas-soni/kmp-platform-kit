# 📖 Documentation Index - KMP Platform Kit

> **Last Updated:** May 2026  
> **SDK Version:** `1.0.0`  
> **Source of Truth:** [`gradle/libs.versions.toml`](../gradle/libs.versions.toml)

---

## Documents

| Document                                              | Audience                             | Description                                                                                    |
|-------------------------------------------------------|--------------------------------------|------------------------------------------------------------------------------------------------|
| [Architecture](./architecture.md)                     | SDK contributors, platform engineers | Layer design, DI, feature selection, thread safety, security, all advanced subsystems          |
| [Technical Concepts](./technical-concepts.md)         | Engineers new to KMP / the SDK       | Deep-dive into every concept: KMP, coroutines, expect/actual, Koin, sealed classes, SKIE, etc. |
| [Android Integration Guide](./integration-android.md) | Android developers                   | Complete guide from building AAR to production usage                                           |
| [iOS Integration Guide](./integration-ios.md)         | iOS developers                       | Complete guide from building XCFramework to production usage                                   |
| [Unit Testing Guide](./unit-testing-guide.md)         | SDK contributors                     | Test infrastructure, base classes, fixtures, running tests                                     |
| [API Endpoints Reference](./endpoints/ENDPOINTS.md)   | All developers                       | Endpoint paths, headers, request/response schemas per feature                                  |
| Generated API Reference (Dokka)                       | SDK consumers, contributors          | Generated API docs from KDoc - available via CI artifacts, local HTML output, and Maven IDE docs |

---

## Start Here

**I want to integrate the SDK into an Android app →** [Android Integration Guide](./integration-android.md)

**I want to integrate the SDK into an iOS app →** [iOS Integration Guide](./integration-ios.md)

**I want to add a new feature to the SDK →** [Architecture → Adding a New Feature](./architecture.md#19-adding-a-new-feature) + [Unit Testing Guide](./unit-testing-guide.md)

**I want to understand how KMP/coroutines/Koin work →** [Technical Concepts](./technical-concepts.md)

**I want to see all available API endpoints →** [API Endpoints Reference](./endpoints/ENDPOINTS.md)

**I want to browse generated API reference docs →** Run `bundle exec fastlane generate_docs` or `./gradlew :shared:dokkaHtml :shared:dokkaGfm`, then open `shared/build/docs/html/index.html`

**I want to understand the overall design →** [Architecture](./architecture.md)

**I want to understand token refresh / circuit breaker / caching →** [Advanced Subsystems](#advanced-subsystems--complete-developer-guide) (in this document)

**I want to understand Detekt / OWASP / Kover / BCV →** [Code Quality & Security Tools](#code-quality--security-tools) (in this document)

**I want to run all quality checks locally →** [All-Tools Quick Reference](#all-tools-quick-reference) (in this document)

---

## Generated API Reference (Dokka)

The project generates SDK API documentation from KDoc using Dokka.

### Generate locally

```bash
bundle exec fastlane generate_docs

# or directly with Gradle
./gradlew :shared:dokkaHtml :shared:dokkaGfm
```

### Output

- `shared/build/docs/html/` - browsable HTML site
- `shared/build/docs/gfm/` - GitHub-flavoured Markdown output
- `shared/build/libs/shared-<version>-javadoc.jar` - attached to Maven publications for Android IDE docs

### Consumer access model

- **Android Maven consumers** see Quick Documentation in Android Studio / IntelliJ via the attached `javadoc.jar`
- **AAR and XCFramework consumers** should use the generated HTML/Markdown docs or the CI `sdk-docs` artifact
- **GitHub Pages** deploys the HTML site on pushes to `main` via `.github/workflows/docs-pages.yml` once Pages is enabled for the repository
- CI uploads the generated docs as the `sdk-docs` artifact on every run

> Generated docs under `shared/build/docs/` are build artifacts and should not be committed to git.

---

## Quick SDK Facts

| Property        | Value                                                                                                    |
|-----------------|----------------------------------------------------------------------------------------------------------|
| Language        | Kotlin 2.1.21 (Multiplatform)                                                                            |
| Android         | minSdk 24 · compileSdk 36 · Fat AAR ~3.3 MB                                                              |
| iOS             | iOS 15+ · Static XCFramework (self-contained)                                                            |
| Networking      | Ktor 3.1.3 (OkHttp on Android, Darwin on iOS) + WebSocket support                                        |
| Serialization   | kotlinx.serialization 1.8.1                                                                              |
| DI              | Koin 4.1.0 (internal, host app never touches it)                                                         |
| Coroutines      | kotlinx.coroutines 1.10.2                                                                                |
| Swift Interop   | SKIE 0.10.10 (sealed class → enum, Flow → AsyncSequence)                                                 |
| Build           | Gradle 8.x · AGP 8.9.1 · Kotlin DSL · Version Catalog                                                    |
| Code Quality    | Detekt 1.23.8 · Kover 0.9.7 (80% min) · OWASP Dependency-Check (CVSS ≥ 7 fail) · Binary Compat Validator |
| AAR Size Budget | 6 MB max (enforced by CI)                                                                                |

---

## Code Quality & Security Tools

The SDK enforces code quality and security at build time via **4 automated tools**. These tools run locally and in CI - you don't need to set them up manually, but you should understand what they do and how to run them.

> **For Android & iOS developers:** These tools protect **you** as a consumer. Detekt ensures the Kotlin code you depend on is clean. OWASP ensures no vulnerable libraries are bundled into your AAR/XCFramework. Kover ensures the SDK's business logic is tested. BCV ensures no one accidentally breaks the API you compile against.

### All-Tools Quick Reference

| Tool       | What It Does             | Command                            | Pass Criteria              | Build Time |
|------------|--------------------------|------------------------------------|----------------------------|------------|
| **Detekt** | Static code analysis     | `./gradlew detekt`                 | ≤ 10 weighted issue points | +2-5 sec   |
| **OWASP**  | CVE vulnerability scan   | `./gradlew dependencyCheckAnalyze` | No CVSS ≥ 7.0 findings     | 30-60 sec* |
| **Kover**  | Code coverage            | `./gradlew :shared:koverVerify`    | ≥ 80% line coverage        | +10-20%    |
| **BCV**    | Public API compatibility | `./gradlew :shared:apiCheck`       | API matches `.api` file    | < 1 sec    |

_*First OWASP run takes 5-15 min to download the NVD database._

```bash
# Run ALL quality checks at once (what CI does):
./gradlew detekt :shared:koverVerify :shared:apiCheck

# Run ALL quality checks + OWASP security scan:
./gradlew detekt :shared:koverVerify :shared:apiCheck dependencyCheckAnalyze
```

### Latest Tool Run Results (May 26, 2026)

| Tool               | Status                | Details                                                                               |
|--------------------|-----------------------|---------------------------------------------------------------------------------------|
| **Detekt**         | ✅ **PASSED**          | 0 issues found across `commonMain`, `androidMain`, `iosMain`                          |
| **Kover**          | ⚠️ **52.9% coverage** | 349 tests passing; below 80% threshold - new subsystems need additional test coverage |
| **BCV (apiCheck)** | ✅ **PASSED**          | Public API surface matches committed `.api` files - no accidental breaks              |
| **OWASP**          | ℹ️ Not run locally    | Requires NVD database download; run via `./gradlew dependencyCheckAnalyze` or in CI   |

### 1. Detekt - Static Code Analysis

<details>
<summary><strong>📖 What · Why · How · Impact (click to expand)</strong></summary>

#### What is it?

[Detekt](https://detekt.dev/) is a **static analysis tool for Kotlin**. It reads your source files (without compiling) and flags code smells, complexity issues, style violations, potential bugs, and performance anti-patterns. Think of it as a Kotlin-specific linter, similar to ESLint for JavaScript or SwiftLint for Swift.

#### Why do we use it?

Without static analysis, code quality degrades over time - functions grow too long, magic numbers creep in, exceptions get swallowed silently, and naming inconsistencies make the codebase harder to navigate. Detekt catches these **at build time**, before they reach code review.

#### How does it work?

**Plugin** (`io.gitlab.arturbosch.detekt` v1.23.8) is declared in `gradle/libs.versions.toml` and applied at the root `build.gradle.kts`.

**Config file:** [`config/detekt/detekt.yml`](../config/detekt/detekt.yml)

**Source scanned:** `commonMain`, `androidMain`, `iosMain` - all production Kotlin code. **Test code is not scanned.**

**How it fails:** Every issue has a weighted score. If the total exceeds **10 points**, the build fails.

| Issue Category                              | Weight (per issue) |
|---------------------------------------------|--------------------|
| Complexity (e.g. `CyclomaticComplexMethod`) | 2 points           |
| `LongParameterList`                         | 2 points           |
| Style (e.g. `MagicNumber`, `MaxLineLength`) | 1 point            |
| Comments                                    | 1 point            |

**Key rules and thresholds:**

| Rule                        | Threshold                       | What It Catches                                             |
|-----------------------------|---------------------------------|-------------------------------------------------------------|
| `CyclomaticComplexMethod`   | 15 branches max                 | Functions with too many `if`/`when`/`try`/`&&`/`\|\|` paths |
| `LongMethod`                | 60 lines max                    | Functions that should be broken into smaller pieces         |
| `LongParameterList`         | 6 params max                    | Too many arguments (data classes & default params excluded) |
| `TooManyFunctions`          | 20/class, 15/file               | Classes/files that violate Single Responsibility            |
| `MagicNumber`               | Only `-1, 0, 1, 2, 100` allowed | Numeric literals without a named constant                   |
| `MaxLineLength`             | 120 chars                       | Long lines (imports, packages, comments excluded)           |
| `ReturnCount`               | 4 returns max                   | Too many exit points (guard clauses & lambdas excluded)     |
| `WildcardImport`            | Banned                          | `import foo.*` - forces explicit imports                    |
| `TooGenericExceptionCaught` | Active                          | Catching `Exception` / `Throwable` broadly                  |
| `SwallowedException`        | Active                          | `catch (e: ...) { }` without handling                       |
| `UnsafeCast`                | Active                          | `as String` without a null/type check                       |
| `SpreadOperator`            | Active                          | `foo(*array)` - copies the array                            |

**KMP-specific rules (intentionally disabled):**

| Rule                        | Status | Why Disabled                                                                                                                                                                        |
|-----------------------------|--------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `InvalidPackageDeclaration` | ❌ Off  | KMP source roots (`commonMain/kotlin/`, `iosMain/kotlin/`) don't follow standard Java directory-to-package mapping. Every file triggers a false positive.                           |
| `MatchingDeclarationName`   | ❌ Off  | KMP `expect`/`actual` files use platform-suffixed names (e.g., `Logger.android.kt` → `actual class PlatformLogger`). The file name intentionally differs from the declaration name. |

**How to run:**

```bash
./gradlew detekt                           # run analysis
open build/reports/detekt/detekt.html      # view results
```

**How to fix a failing check:**

```kotlin
// ❌ BAD - Detekt flags "MagicNumber"
delay(500)

// ✅ GOOD - Use a named constant
private const val DEFAULT_BACKOFF_MS = 500L
delay(DEFAULT_BACKOFF_MS)

// If you MUST suppress a rule (with justification):
@Suppress("MagicNumber")
val timeoutMs = 5000L
```

#### What's the impact on the system?

| Dimension               | Impact                                                                      |
|-------------------------|-----------------------------------------------------------------------------|
| Build time              | +2-5 seconds (runs in parallel)                                             |
| Runtime / artifact size | **Zero** - Detekt only analyzes source files, adds nothing to the binary    |
| Developer experience    | Catches issues early, reduces code review cycles, enforces consistent style |

</details>

---

### 2. OWASP Dependency-Check - Vulnerability Scanning

<details>
<summary><strong>📖 What · Why · How · Impact (click to expand)</strong></summary>

#### What is it?

[OWASP Dependency-Check](https://owasp.org/www-project-dependency-check/) scans every production dependency (direct and transitive) against the [National Vulnerability Database (NVD)](https://nvd.nist.gov/) for known CVEs.

#### Why do we use it?

Third-party libraries can have security vulnerabilities discovered after release. Without automated scanning, the SDK could ship a vulnerable library to every host app. OWASP catches these before production.

#### How does it work?

**Plugin** (`org.owasp.dependencycheck` v12.2.0) declared in root `build.gradle.kts`.

**Flow:**

```
1. Gradle resolves ALL production dependencies + transitive deps
2. OWASP downloads/updates the local NVD CVE database
3. Each JAR/AAR is matched against known CVEs
4. Suppressions in config/owasp/suppression.xml are excluded
5. Test-only dependencies are skipped
6. CVSS ≥ 7.0 (HIGH/CRITICAL) → BUILD FAILS ❌
   Otherwise → BUILD PASSES ✅
```

**What gets scanned:**

| ✅ Scanned (production)                                   | ❌ Skipped (test-only)             |
|----------------------------------------------------------|-----------------------------------|
| ktor-client-core, ktor-client-okhttp, ktor-client-darwin | kotlin-test, koin-test            |
| kotlinx-coroutines-core, kotlinx-serialization-json      | coroutines-test, ktor-client-mock |
| koin-core + all transitive deps                          | androidTestImplementation deps    |

**How to run:**

```bash
./gradlew dependencyCheckAnalyze                           # basic scan
NVD_API_KEY=your-key ./gradlew dependencyCheckAnalyze      # faster with API key
open build/reports/dependency-check-report.html             # view results
```

**Handling failures - Option A (upgrade the dep):**

```toml
# gradle/libs.versions.toml
ktor = "3.1.4"  # was 3.1.3, fixes CVE-2024-XXXXX
```

**Handling failures - Option B (suppress false positive):**

```xml
<!-- config/owasp/suppression.xml -->
<suppress>
    <notes>CVE-2024-XXXXX: Affects server-side Ktor, not client. Verified 2026-03-23.</notes>
    <packageUrl regex="true">^pkg:maven/io\.ktor/ktor\-server\-core@.*$</packageUrl>
    <cve>CVE-2024-XXXXX</cve>
</suppress>
```

> ⚠️ Every suppression MUST have a `<notes>` element. Suppressions without notes are removed during quarterly security reviews.

#### What's the impact on the system?

| Dimension               | Impact                                                                   |
|-------------------------|--------------------------------------------------------------------------|
| Build time              | First run 5–15 min (NVD download), subsequent 30–60 sec                  |
| Runtime / artifact size | **Zero** - build-time check only                                         |
| For Android devs        | Fat AAR bundles all deps; OWASP ensures none have HIGH/CRITICAL CVEs     |
| For iOS devs            | XCFramework statically links all deps; OWASP gate protects the framework |

</details>

---

### 3. Kover - Code Coverage

<details>
<summary><strong>📖 What · Why · How · Impact (click to expand)</strong></summary>

#### What is it?

[Kover](https://github.com/Kotlin/kotlinx-kover) is JetBrains' **Kotlin-native code coverage** tool (v0.9.7). It instruments your compiled bytecode during test execution and measures what percentage of lines, branches, and methods are exercised by unit tests. Unlike JaCoCo (Java-only), Kover is designed specifically for Kotlin - it understands coroutines, inline functions, and multiplatform source sets.

#### Why do we use it?

The SDK enforces a **minimum 80% line coverage** threshold. Without coverage enforcement:

- Critical paths (retry logic, error mapping, circuit breaker transitions) could ship untested
- Refactoring becomes risky - you don't know what's covered
- New features might skip tests and erode quality over time

The 80% threshold is calibrated to account for the fact that Kover only instruments **JVM (Android) tests** - iOS `actual` implementations are tested via `iosSimulatorArm64Test` but those metrics are **not reflected** in Kover reports.

#### How does it work?

**Plugin** (`org.jetbrains.kotlinx.kover` v0.9.7) is declared in `gradle/libs.versions.toml` and applied in `shared/build.gradle.kts`.

**What gets measured vs. excluded:**

| ✅ Measured (production code)                          | ❌ Excluded from coverage                                          |
|-------------------------------------------------------|-------------------------------------------------------------------|
| All `commonMain` business logic                       | `*.di.*Module*`, `*.di.FeatureModules` (auto-generated DI wiring) |
| Core subsystems (retry, cache, circuit breaker, etc.) | `*.core.SDKInfo` (auto-generated version constant)                |
| Repositories, mappers, facades                        | `*.BuildConfig` (build-time generated)                            |
| Error handling, state management                      | Platform packages (`*.android`, `*.ios`) - tested indirectly      |

**Verification rule:**

```
Rule: "SDK minimum coverage"
Metric: Line coverage
Minimum: 80%
Scope: All non-excluded production classes
```

If coverage drops below 80%, `koverVerify` **fails the build**.

#### How to run:

```bash
# Generate visual HTML report (open in browser)
./gradlew :shared:koverHtmlReport
open shared/build/reports/kover/html/index.html

# Generate XML report (for CI integration - SonarQube, Codecov, etc.)
./gradlew :shared:koverXmlReport
# Output: shared/build/reports/kover/report.xml

# Enforce 80% minimum (CI gate - fails build if below threshold)
./gradlew :shared:koverVerify

# Print coverage summary to console (quick check)
./gradlew :shared:koverLog
# Output: "application line coverage: 85.2%"
```

#### How to read the HTML report

Open `shared/build/reports/kover/html/index.html` after running `koverHtmlReport`:

```
Package View
├── com.droidunplugged.kmp_platform_kit.core        ← 88% (networking, state, retry)
├── com.droidunplugged.kmp_platform_kit.core.auth    ← 92% (token manager)
├── com.droidunplugged.kmp_platform_kit.core.circuit ← 95% (circuit breaker)
├── com.droidunplugged.kmp_platform_kit.features.*   ← 85% (feature repos + mappers)
└── com.droidunplugged.kmp_platform_kit.shared       ← 90% (utilities, extensions)
```

Click any class to see **line-by-line highlighting**: green = covered, red = not covered, yellow = partially covered (branch).

#### How to fix a failing coverage check

```kotlin
// 1. Identify uncovered lines in the HTML report
// 2. Write targeted tests for those paths

// Example: if the "timeout" branch in KtorApiClient is uncovered:
@Test
fun `timeout returns NetworkError`() = runTest {
    val client = FakeApiClient()
    client.setDefaultGetResponse { ApiResult.NetworkError }
    val result = repository.getInventories(query)
    assertIs<ApiResult.NetworkError>(result)
}
```

#### What's the impact on the system?

| Dimension               | Impact                                                                   |
|-------------------------|--------------------------------------------------------------------------|
| Build time              | +10-20% on test task (bytecode instrumentation overhead)                 |
| Runtime / artifact size | **Zero** - instrumentation is test-only, never shipped                   |
| Developer experience    | Clear visibility into what's tested; prevents untested code from merging |
| CI gate                 | `koverVerify` blocks PRs that drop coverage below 80%                    |

</details>

---

### 4. Binary Compatibility Validator

<details>
<summary><strong>📖 What · Why · How · Impact (click to expand)</strong></summary>

#### What is it?

[Binary Compatibility Validator](https://github.com/Kotlin/binary-compatibility-validator) (BCV, v0.18.1) prevents **accidental public API breaks**. It dumps every public class, function, and property signature into a human-readable `.api` file. CI fails if the actual public API differs from the committed `.api` file - meaning someone changed the API without an intentional update.

#### Why do we use it?

Host apps (Android and iOS) compile against the SDK's public API. If a public method signature changes, is removed, or is renamed without notice:

- **Android apps** get compile errors when upgrading the AAR
- **iOS apps** get linker errors when updating the XCFramework
- **Both** get runtime crashes if the change is binary-incompatible (e.g., parameter type change)

BCV catches these **at PR time** - before they reach any consumer.

#### How does it work?

**Plugin** (`org.jetbrains.kotlinx.binary-compatibility-validator` v0.18.1) is declared in root `build.gradle.kts` and applied in `shared/build.gradle.kts`.

**What it tracks:**

| ✅ Tracked (public API)                    | ❌ Excluded from tracking                                            |
|-------------------------------------------|---------------------------------------------------------------------|
| All `public` classes, objects, interfaces | `*.core.di` package (internal DI wiring)                            |
| All `public` functions and properties     | `*.features.*.di` packages (feature DI modules)                     |
| Constructor signatures                    | `*.core.SDKInfo` (auto-generated, version changes on every release) |
| Return types, parameter types             | All `internal` / `private` declarations                             |

**API dump files:**

| File                         | Platform       | Contains                            |
|------------------------------|----------------|-------------------------------------|
| `shared/api/shared.api`      | Android (JVM)  | JVM public API signatures           |
| `shared/api/shared.klib.api` | iOS (K/N klib) | Kotlin/Native public API signatures |

**Example `.api` file content:**

```
public final class com.droidunplugged.kmp_platform_kit/core/SDKInitializer {
    public static final field INSTANCE Lcom/.../SDKInitializer;
    public final fun configure (Lkotlin/jvm/functions/Function0;)V
    public final fun init (Ljava/lang/String;Ljava/lang/String;...)V
    public final fun reset (Lkotlin/coroutines/Continuation;)Ljava/lang/Object;
    public final fun updateAuthToken (Ljava/lang/String;)V
}
```

#### How to use:

```bash
# Validate - CI uses this (fails if public API changed without apiDump)
./gradlew :shared:apiCheck

# Update .api files after INTENTIONAL public API changes
./gradlew :shared:apiDump

# Typical workflow:
# 1. Make your code changes
# 2. Run apiCheck → it fails (expected, you changed the API)
# 3. Review the diff to confirm the change is intentional
# 4. Run apiDump to update the .api files
# 5. Commit the updated .api files with your PR
```

**Common scenarios:**

| Scenario                       | What Happens                                                 | Action                                                              |
|--------------------------------|--------------------------------------------------------------|---------------------------------------------------------------------|
| Added a new `public fun`       | `apiCheck` fails - new method not in `.api` file             | Run `apiDump`, commit updated `.api` files                          |
| Renamed a `public fun`         | `apiCheck` fails - old method missing, new one unknown       | Run `apiDump`, document breaking change in CHANGELOG                |
| Changed an `internal` function | `apiCheck` passes - internal changes don't affect public API | No action needed                                                    |
| Removed a `public fun`         | `apiCheck` fails - method missing                            | **⚠️ Breaking change!** Discuss with consumers, update MIGRATION.md |

#### What's the impact on the system?

| Dimension               | Impact                                                                            |
|-------------------------|-----------------------------------------------------------------------------------|
| Build time              | < 1 second (reads compiled metadata, no recompilation)                            |
| Runtime / artifact size | **Zero** - `.api` files are dev-only, never shipped                               |
| Developer experience    | Prevents accidental API breaks; makes intentional changes explicit and reviewable |
| Consumer safety         | Android & iOS teams can upgrade the SDK with confidence - no surprise breaks      |

</details>

---

## Advanced Subsystems - Complete Developer Guide

The SDK ships production-grade subsystems in `shared/src/commonMain/kotlin/core/`. Each subsystem is **fully implemented**, **thread-safe**, and **ready for use**. Below is a detailed guide for each - covering **what it is**, **why to use it**, **how to use it** (with Android & iOS code), and **what impact it has on the system**.

### Quick Reference Table

| Subsystem                                            | Source File                                 | What It Does                            | You Need To Do Anything?               |
|------------------------------------------------------|---------------------------------------------|-----------------------------------------|----------------------------------------|
| [Token Manager](#1-token-manager)                    | `core/auth/TokenManager.kt`                 | Auto-refreshes auth tokens              | ✅ Implement `TokenRefreshProvider`     |
| [Circuit Breaker](#2-circuit-breaker)                | `core/circuit/CircuitBreaker.kt`            | Stops traffic to failing backends       | ⚙️ Optional config                     |
| [Pagination Engine](#3-pagination-engine)            | `core/paging/SdkPager.kt`                   | Infinite scroll with Flow state         | ✅ Use in UI layer                      |
| [Response Cache](#4-response-cache)                  | `core/CachePolicy.kt`                       | Per-request caching (4 strategies)      | ⚙️ Pass `CachePolicy` to API calls     |
| [Request Deduplication](#5-request-deduplication)    | `core/RequestDeduplicator.kt`               | Coalesces identical concurrent requests | ❌ Automatic                            |
| [Request Interceptors](#6-request-interceptors)      | `core/interceptor/SdkRequestInterceptor.kt` | Custom HTTP middleware                  | ⚙️ Register interceptors               |
| [Distributed Tracing](#7-distributed-tracing)        | `core/tracing/SdkTraceContext.kt`           | W3C + B3 trace headers                  | ❌ Automatic                            |
| [Structured Errors](#8-structured-error-taxonomy)    | `core/error/SdkError.kt`                    | 11 typed error categories               | ✅ Handle in `when` blocks              |
| [SDK Plugins](#9-sdk-plugins)                        | `core/SDKPlugin.kt`                         | Self-registering feature modules        | ⚙️ For feature teams                   |
| [Telemetry](#10-telemetry)                           | `core/SDKTelemetry.kt`                      | Pluggable observability                 | ⚙️ Implement `SDKTelemetry`            |
| [Remote Config](#11-remote-config)                   | `core/config/SdkRemoteConfig.kt`            | Server-pushed kill-switches & tuning    | ⚙️ Implement `SdkRemoteConfigProvider` |
| [SSL Pinning](#12-ssl-certificate-pinning)           | `core/SslPinConfig.kt`                      | Certificate pinning                     | ⚙️ Provide SHA-256 pins                |
| [Environment Config](#13-environment-config)         | `core/config/SdkEnvironment.kt`             | Typed env configs                       | ✅ Define environments                  |
| [Base Facade](#14-base-facade)                       | `core/BaseFacade.kt`                        | Init guard + shared logging             | ❌ SDK-internal                         |
| [SDKState & Error Codes](#15-sdkstate--sdkerrorcode) | `core/SDKState.kt`, `core/SDKStateFlow.kt`  | UI-ready state wrapper                  | ✅ Use in ViewModels                    |

---

### 1. Token Manager

> **Source:** `core/auth/TokenManager.kt` · **Audience:** Android & iOS developers who manage authentication

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

An automatic token lifecycle manager that handles **proactive refresh** (before the token expires) and **reactive refresh** (when a 401 is received). Coalesces concurrent 401 responses - only one refresh call is ever made, regardless of how many requests fail simultaneously.

#### Why use it?

Without a Token Manager, every host app must independently:

- Track token expiry and schedule refresh timers
- Handle 401 responses and retry the original request
- Prevent N concurrent 401s from triggering N refresh calls
- Handle "user logged out during refresh" edge cases

#### How to use it

**Android - Implement `TokenRefreshProvider`:**

```kotlin
class MyTokenRefreshProvider(private val authRepo: AuthRepository) : TokenRefreshProvider {
    override suspend fun refreshToken(expiredToken: String): TokenResult {
        return try {
            val response = authRepo.refresh()
            TokenResult.Success(newToken = response.accessToken, expiresIn = response.expiresIn.seconds)
        } catch (e: SessionExpiredException) {
            TokenResult.UserLoggedOut   // → SDK calls reset() automatically
        } catch (e: Exception) {
            TokenResult.RefreshFailed(reason = e.message)  // → SDK retries with backoff
        }
    }
}

// Register before init
SDKInitializer.setTokenRefreshProvider(MyTokenRefreshProvider(authRepo))
SDKInitializer.init(baseUrl, authToken, apiGuid, clientId, apiKey, tokenExpiresIn = 900.seconds)
```

**iOS (Swift via SKIE):**

```swift
class TokenRefreshProviderImpl: TokenRefreshProvider {
    func refreshToken(expiredToken: String) async throws -> TokenResult {
        let response = try await AuthService.shared.refresh()
        return TokenResultSuccess(newToken: response.token, expiresIn: .seconds(Int64(response.expiresIn)))
    }
}

SDKInitializer.shared.setTokenRefreshProvider(provider: TokenRefreshProviderImpl())
```

**Internal flow:**

```
Token acquired → Schedule proactive refresh at (expiresIn - 60s buffer)
                                │
           Proactive refresh fires         HTTP 401 received
                    │                              │
              Acquire Mutex                  Acquire Mutex
              (one call only)                (coalesces N 401s)
                    │                              │
            Call refreshToken()            Call refreshToken()
                    │                              │
              ┌─────┴─────┐                  ┌─────┴─────┐
            Success   Failed               Success   Failed → retry (max 3)
              │         │                    │           │
        Update token  Retry             Replay       UserLoggedOut
        Schedule next  w/backoff        requests     → SDK reset()
```

**Configuration (`TokenConfig`):**

| Property                 | Default    | Description                                                 |
|--------------------------|------------|-------------------------------------------------------------|
| `proactiveRefreshBuffer` | 60 seconds | Refresh this long before expiry. `Duration.ZERO` = disable. |
| `refreshMaxRetries`      | 3          | Max retries per refresh cycle                               |
| `refreshRetryBackoffMs`  | 1,000 ms   | Initial backoff (doubles: 1s → 2s → 4s)                     |

#### What's the impact?

| Dimension | Impact                                                                      |
|-----------|-----------------------------------------------------------------------------|
| Memory    | One `CoroutineScope` + one `Job`. Negligible.                               |
| Network   | At most 1 refresh every ~14 min. Coalescing prevents N calls on burst 401s. |
| Battery   | Uses `delay()` (coroutine suspension), not `AlarmManager`. Zero wake-lock.  |
| Cleanup   | `cancel()` is called automatically on `SDKInitializer.reset()`.             |

</details>

---

### 2. Circuit Breaker

> **Source:** `core/circuit/CircuitBreaker.kt` · **Audience:** All developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A state machine that **prevents hammering a degraded backend**. After too many consecutive failures, the circuit "opens" and immediately rejects requests without making network calls. After a cooldown, it probes to check if the server recovered.

#### Why use it?

When a backend is struggling, continuing to send requests makes things worse for the server (more load) and for your app (wasted battery, slow UIs, error floods). The circuit breaker:

- **Protects the server** - stops sending traffic it can't handle
- **Protects the app** - fails instantly (<1ms) instead of waiting for timeouts
- **Self-heals** - automatically probes and resumes when the server recovers

#### How to use it

```kotlin
// Optional - tune thresholds before init
SDKInitializer.setCircuitBreakerConfig(
    CircuitBreakerConfig(
        failureThreshold = 5,       // 5 failures → open
        successThreshold = 2,       // 2 probe successes → close
        openDuration = 30.seconds   // stay open 30s
    )
)
SDKInitializer.init(...)

// Optional - observe circuit state in your ViewModel
circuitBreaker.state.collect { state ->
    when (state) {
        CircuitBreaker.State.CLOSED -> hideCircuitBanner()
        CircuitBreaker.State.OPEN -> showDegradedModeBanner()
        CircuitBreaker.State.HALF_OPEN -> showRecoveringBanner()
    }
}
```

**State machine:**

```
    ┌──────────┐   5 consecutive     ┌──────────┐
    │  CLOSED  │────failures────────▶│   OPEN   │
    │ (normal) │                     │(rejected)│
    └──────────┘                     └────┬─────┘
         ▲                                │ 30s elapsed
         │   2 probe successes       ┌────▼─────┐
         └◀──────────────────────────│HALF_OPEN │
                                     │  (probe) │
                                     └──────────┘
                                      failure → back to OPEN
```

#### What's the impact?

| Dimension     | Impact                                                           |
|---------------|------------------------------------------------------------------|
| When OPEN     | **Zero network calls** - fails in <1ms                           |
| When CLOSED   | One `Mutex` check per request (~microseconds)                    |
| Memory        | One `StateFlow` + counters. Negligible.                          |
| Observability | `state: StateFlow<State>` - bind to UI for degraded-mode banners |

</details>

---

### 3. Pagination Engine

> **Source:** `core/paging/SdkPager.kt` · **Audience:** Android & iOS developers building list screens

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A multiplatform, coroutine-based pagination engine supporting **infinite scroll**, **refresh**, and **Flow-driven state**. Accumulates items across pages and exposes `StateFlow<PagerState<T>>`.

#### Why use it?

Pagination is hard to get right - race conditions on rapid scrolling, preserving data on page-3 errors, proper cancellation, "all loaded" detection. `SdkPager<T>` handles it all.

#### How to use it

**Android (Compose):**

```kotlin
val pager = AppFacadeYourFeature.createInventoryPager(customerNo = "2057192797")
pager.loadNextPage()

val state by pager.state.collectAsStateWithLifecycle()
when (state) {
    is PagerState.Idle -> { /* nothing yet */
    }
    is PagerState.LoadingInitial -> FullScreenLoader()
    is PagerState.Success -> InventoryList(state.items, onEndReached = pager::loadNextPage)
    is PagerState.LoadingMore -> InventoryList(state.items) + FooterSpinner()
    is PagerState.Complete -> InventoryList(state.items) + EndOfListBanner()
    is PagerState.Error -> ErrorBanner(state.error, onRetry = pager::loadNextPage)
}

// Pull-to-refresh
SwipeRefresh(onRefresh = { pager.refresh() })

// Cleanup
override fun onCleared() {
    pager.cancel()
}
```

**iOS (Swift via SKIE):**

```swift
let pager = AppFacadeYourFeature.shared.createInventoryPager(customerNo: "2057192797")
pager.loadNextPage()

for await state in pager.state {
    switch state {
    case is PagerState.LoadingInitial: showLoader()
    case let s as PagerState.Success:  render(s.items)
    case let s as PagerState.LoadingMore: render(s.items, showFooterSpinner: true)
    case let s as PagerState.Complete: render(s.items, showEndBanner: true)
    case let e as PagerState.Error:    showError(e.error, retryAction: pager.loadNextPage)
    default: break
    }
}
```

**`PagerState<T>` reference:**

| State            | UI Action                       | `items`                    |
|------------------|---------------------------------|----------------------------|
| `Idle`           | Nothing / placeholder           | -                          |
| `LoadingInitial` | Full-screen loader              | -                          |
| `LoadingMore`    | Footer spinner                  | ✅ All items so far         |
| `Success`        | Show list + "load more" trigger | ✅ All items so far         |
| `Complete`       | "End of list" banner            | ✅ All items                |
| `Error`          | Error banner + retry button     | ✅ Previous pages preserved |

#### What's the impact?

| Dimension     | Impact                                                             |
|---------------|--------------------------------------------------------------------|
| Memory        | Accumulates all items in a list. 1,000 typical items ≈ 100-200 KB. |
| Network       | Exactly 1 request per page. No-op if already loading or complete.  |
| Thread safety | All mutations `Mutex`-guarded - safe from any thread.              |
| Cleanup       | Call `cancel()` in `onCleared()` / `deinit`.                       |

</details>

---

### 4. Response Cache

> **Source:** `core/CachePolicy.kt` · **Audience:** All developers making API calls

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

An in-memory, thread-safe response cache with **4 strategies** per API call. Stores raw JSON keyed by URL with configurable TTL.

#### Why use it?

Reduces network calls, enables offline resilience, and makes revisited screens load instantly.

#### The 4 strategies

| Strategy                      | Behaviour                                   | Best For                     |
|-------------------------------|---------------------------------------------|------------------------------|
| **`NETWORK_FIRST`** (default) | Fetch fresh; on failure → serve stale cache | Most API calls               |
| **`CACHE_FIRST`**             | Serve cached if fresh; else network         | Frequently revisited screens |
| **`CACHE_ONLY`**              | Never network; cached or fail               | Offline-first features       |
| **`NETWORK_ONLY`**            | Always network; never cache                 | Mutations, real-time data    |

**Usage:**

```kotlin
val result = apiClient.get(
    path = "/inventories",
    responseParser = { json -> Json.decodeFromString(json) },
    cachePolicy = CachePolicy(strategy = CacheStrategy.CACHE_FIRST, maxAge = 5.minutes)
)

// Convenience shortcuts:
CachePolicy.DEFAULT            // NETWORK_FIRST, 5 min
CachePolicy.CACHE_FIRST_5MIN   // CACHE_FIRST, 5 min
CachePolicy.NO_CACHE           // NETWORK_ONLY
```

#### What's the impact?

| Dimension     | Impact                                                                |
|---------------|-----------------------------------------------------------------------|
| Memory        | ~2-10 KB per cached URL × ~20 URLs = 40-200 KB. Cleared on `reset()`. |
| Latency       | Cache hit ≈ 0ms. Cache miss = normal network latency.                 |
| Thread safety | All ops are `Mutex`-guarded.                                          |
| Security      | In-memory only - nothing persisted to disk.                           |

</details>

---

### 5. Request Deduplication

> **Source:** `core/RequestDeduplicator.kt` · **Audience:** All developers (works transparently)

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

Coalesces simultaneous identical requests into a **single network call**. If two ViewModels call `getInventories("123")` at the same time, only one HTTP request fires.

#### Why use it?

Multiple UI components often request the same data simultaneously - on app launch, on tab switches, during pull-to-refresh. Without deduplication, each triggers a separate network call.

#### How to use it

**You don't need to do anything** - it's automatic for `NETWORK_FIRST` strategy.

**How it works:**

```
ViewModel A: getInventories("123")    ViewModel B: getInventories("123")
        │                                       │
   No in-flight → create Deferred         In-flight found → await
   Execute HTTP request                         │ (no network call)
        │                                       │
   Response received                            │
        │                                       │
   Complete Deferred ──────────────────────────▶│
        │                                       │
   Return result                          Return same result
```

#### What's the impact?

| Dimension | Impact                                                                      |
|-----------|-----------------------------------------------------------------------------|
| Network   | Saves N-1 calls when N callers request the same URL simultaneously          |
| Memory    | One `CompletableDeferred` per unique in-flight URL. Cleaned up immediately. |
| Latency   | Waiting callers add ~0ms (they suspend, not block)                          |

</details>

---

### 6. Request Interceptors

> **Source:** `core/interceptor/SdkRequestInterceptor.kt` · **Audience:** Developers needing custom HTTP headers/logic

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A pluggable middleware pipeline for injecting custom logic into every SDK HTTP request/response.

#### Why use it?

Add app-specific context the SDK doesn't know about: device fingerprints, A/B test variants, request signing, custom analytics.

#### How to use it

```kotlin
class DeviceFingerprintInterceptor(private val deviceId: String) : SdkRequestInterceptor {
    override val id = "device_fingerprint"

    override suspend fun onRequest(request: SdkMutableRequest): SdkMutableRequest {
        return request.withHeader("x-device-id", deviceId)
    }

    override suspend fun onResponse(response: SdkResponse) {
        analytics.trackLatency(response.url, response.durationMs)
    }
}

SDKInitializer.addInterceptor(DeviceFingerprintInterceptor(getDeviceId()))
SDKInitializer.init(...)
```

**Available hooks:**

| Hook           | When                   | Modifiable?             | Use Case                |
|----------------|------------------------|-------------------------|-------------------------|
| `onRequest()`  | Before every HTTP call | ✅ Headers, query params | Custom headers, signing |
| `onResponse()` | After every response   | ❌ Read-only             | Logging, analytics      |
| `onError()`    | On network errors      | ❌ Read-only             | Crash reporting         |

#### What's the impact?

| Dimension | Impact                                          |
|-----------|-------------------------------------------------|
| Latency   | <1ms per interceptor for header-only operations |
| Memory    | One object per interceptor. Negligible.         |
| Cleanup   | All cleared on `SDKInitializer.reset()`         |

</details>

---

### 7. Distributed Tracing

> **Source:** `core/tracing/SdkTraceContext.kt` · **Audience:** All developers & backend teams

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

Automatic injection of **W3C Trace Context** and **B3** headers into every outgoing request. Enables end-to-end request correlation across mobile app → API gateway → backend services.

#### Why use it?

When a user reports "my inventory didn't load", one `traceId` finds the exact request journey in Datadog/Jaeger/CloudWatch instantly.

#### How to use it

**Automatic** - no setup needed. Headers injected on every request:

| Header         | Format                     | Standard |
|----------------|----------------------------|----------|
| `traceparent`  | `00-{traceId}-{spanId}-01` | W3C      |
| `x-b3-traceid` | `{traceId}`                | B3       |
| `x-b3-spanid`  | `{spanId}`                 | B3       |
| `x-request-id` | `{spanId}`                 | Legacy   |

#### What's the impact?

| Dimension | Impact                                    |
|-----------|-------------------------------------------|
| Network   | 4 extra headers (~200 bytes). Negligible. |
| Privacy   | Trace IDs are random - **zero PII**.      |

</details>

---

### 8. Structured Error Taxonomy

> **Source:** `core/error/SdkError.kt` · **Audience:** All developers handling errors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A sealed class hierarchy with **11 strongly-typed error categories**, each with machine-readable properties and recovery hints.

#### Why use it?

Replaces guessing what `code = -2` means with type-safe, actionable errors:

```kotlin
// ✅ Type-safe, actionable, localizable
when (error) {
    is SdkError.Unauthorized -> navigateToLogin()
    is SdkError.NoConnectivity -> showOfflineBanner()
    is SdkError.BusinessError -> showApiError(error.apiCode, error.message)
    is SdkError.RateLimited -> retryAfter(error.retryAfterMs)
    is SdkError.ServerError -> showRetryButton()
    else -> showGenericError()
}
```

#### Complete error reference

| Error Type          | HTTP    | Recovery                              | `isRetryable` |
|---------------------|---------|---------------------------------------|---------------|
| `Unauthorized`      | 401     | `reset()` → re-login                  | ❌             |
| `Forbidden`         | 403     | Show "access denied"                  | ❌             |
| `NoConnectivity`    | -       | Offline banner, auto-retry            | ✅             |
| `Timeout`           | -       | Retry after delay                     | ✅             |
| `NetworkFailure`    | -       | Retry after delay                     | ✅             |
| `ServerError`       | 5xx     | Retry (SDK already does with backoff) | ✅             |
| `BusinessError`     | 200/4xx | Show `apiCode` + `message`            | ❌             |
| `NotFound`          | 404     | "Not found" message                   | ❌             |
| `Conflict`          | 409     | Show conflict message                 | ❌             |
| `RateLimited`       | 429     | Wait `retryAfterMs`                   | ✅             |
| `SdkNotInitialized` | -       | Call `init()`                         | ❌             |
| `ParseError`        | -       | SDK bug or API change                 | ❌             |
| `Unexpected`        | -       | File a bug                            | ❌             |

**Convenience properties:** `error.isRetryable`, `error.requiresReLogin`

#### What's the impact?

Zero runtime overhead. Errors are lightweight data classes created only when errors occur. In Swift via SKIE, maps to a native `enum`.

</details>

---

### 9. SDK Plugins

> **Source:** `core/SDKPlugin.kt` · **Audience:** Feature teams adding new modules

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A self-registration mechanism for features to plug into the SDK's lifecycle (init, reset) and DI graph without modifying `SDKInitializer`.

#### Why use it?

Decouples feature teams - no merge conflicts on `SDKInitializer`, clean lifecycle hooks, discoverable without central wiring.

#### How to use it

```kotlin
class AnalyticsPlugin(private val config: AnalyticsConfig) : SDKPlugin {
    override val id = "analytics"
    override val koinModule = module { single { AnalyticsTracker(config) } }
    override fun onSDKInitialized() { /* warm up */
    }
    override fun onSDKReset() { /* clean up */
    }
}

SDKInitializer.registerPlugin(AnalyticsPlugin(config))
SDKInitializer.init(...)
```

**Lifecycle:** `register` → `koinModule` added to DI → `onSDKInitialized()` → normal operation → `onSDKReset()` on logout.

</details>

---

### 10. Telemetry

> **Source:** `core/SDKTelemetry.kt` · **Audience:** Developers adding observability

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A pluggable interface the SDK calls automatically on every API call, error, and lifecycle event. Host apps provide an implementation (Firebase, Datadog, custom).

#### Why use it?

Measure API latency, error rates, retry effectiveness, and circuit breaker hits - without modifying SDK code.

#### How to use it

```kotlin
class FirebaseTelemetry(private val analytics: FirebaseAnalytics) : SDKTelemetry {
    override fun recordApiCall(endpoint: String, durationMs: Long, statusCode: Int, retries: Int) {
        analytics.logEvent("sdk_api_call") {
            param("endpoint", endpoint); param("duration_ms", durationMs)
        }
    }
    override fun recordError(type: ErrorType, endpoint: String?, message: String) { /* ... */
    }
    override fun recordSdkEvent(event: SDKEvent, detail: String?) { /* ... */
    }
}

SDKInitializer.setTelemetry(FirebaseTelemetry(Firebase.analytics))
```

**Events emitted automatically:**

| Method                | Data                                                        |
|-----------------------|-------------------------------------------------------------|
| `recordApiCall(...)`  | endpoint, duration (ms), HTTP status, retry count           |
| `recordError(...)`    | error type, endpoint, message                               |
| `recordSdkEvent(...)` | `INITIALIZED`, `RESET`, `TOKEN_REFRESHED`, `FEATURE_CALLED` |

**Default: `NoOpTelemetry`** - zero impact when not configured. Privacy: **no tokens, keys, or PII** are ever passed.

</details>

---

### 11. Remote Config

> **Source:** `core/config/SdkRemoteConfig.kt` · **Audience:** Teams needing runtime feature control

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

Server-pushed runtime configuration - feature kill-switches, retry/cache tuning, and maintenance mode - without a host app update.

#### Why use it?

- **Kill-switch** a broken feature instantly across all users
- **Tune** retry attempts or cache TTL in response to server load
- **Maintenance mode** - push a message to the app without a release

#### How to use it

```kotlin
class MyRemoteConfigProvider(private val api: ConfigApi) : SdkRemoteConfigProvider {
    override suspend fun fetchConfig(): SdkRemoteConfig? {
        return try {
            api.getSdkConfig()
        } catch (e: Exception) {
            null
        }
    }
}

SDKInitializer.setRemoteConfigProvider(MyRemoteConfigProvider(api))
SDKInitializer.init(...)
```

**Config fields:**

| Field                            | Effect                                                      |
|----------------------------------|-------------------------------------------------------------|
| `featureFlags["orders"] = false` | "orders" facade returns error immediately - no network call |
| `retryConfig`                    | Overrides default `RetryConfig` at runtime                  |
| `cacheMaxAge`                    | Overrides default cache TTL                                 |
| `maintenanceMessage` (non-null)  | All facades return `SdkError.ServerError` with this message |
| `refreshInterval`                | How often to re-fetch config (default 15 min)               |

**Failure resilience:** If `fetchConfig()` returns `null`, the SDK keeps the last known config.

</details>

---

### 12. SSL Certificate Pinning

> **Source:** `core/SslPinConfig.kt` · **Audience:** Security-conscious teams

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

Validates the server's TLS certificate matches a pre-configured public key hash - preventing MITM attacks even with a compromised CA.

#### How to use it

```bash
# Get your server's SHA-256 pin
openssl s_client -connect api.example.com:443 | \
  openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | \
  openssl dgst -sha256 -binary | openssl enc -base64
```

```kotlin
SDKConfig.sslPins = SslPinConfig(
    hostname = "api.example.com",
    pins = listOf(
        "sha256/AAAA...",  // primary
        "sha256/BBBB..."   // backup - ALWAYS include 2+ pins
    )
)
```

**Platform implementations:** Android → OkHttp `CertificatePinner` · iOS → Darwin TLS challenge validation.

> ⚠️ **Always include ≥2 pins.** If the only pinned cert rotates without an app update → complete lockout.

</details>

---

### 13. Environment Config

> **Source:** `core/config/SdkEnvironment.kt` · **Audience:** All developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A strongly-typed config object bundling `baseUrl`, `clientId`, `apiKey`, and optional `SslPinConfig` - replacing error-prone raw strings.

#### How to use it

```kotlin
object Environments {
    val STAGING = SdkEnvironment(
        id = "staging", baseUrl = "https://api.stage.example.com",
        clientId = BuildConfig.STAGING_CLIENT_ID, apiKey = BuildConfig.STAGING_API_KEY
    )
    val PRODUCTION = SdkEnvironment(
        id = "production", baseUrl = "https://api.example.com",
        clientId = BuildConfig.PROD_CLIENT_ID, apiKey = BuildConfig.PROD_API_KEY,
        sslPins = SslPinConfig(hostname = "api.example.com", pins = listOf("sha256/..."))
    )
}

val env = if (BuildConfig.DEBUG) Environments.STAGING else Environments.PRODUCTION
SDKInitializer.init(environment = env, authToken = token, apiGuid = guid)
```

**Validation at construction:** blank `id`, `baseUrl`, `clientId`, `apiKey` → throws immediately. Trailing slash on `baseUrl` → throws.

</details>

---

### 14. Base Facade

> **Source:** `core/BaseFacade.kt` · **Audience:** SDK contributors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

Abstract base class for all facades. Centralises the "SDK must be initialized" guard and provides a shared logger.

#### How it works

```kotlin
object AppFacadeYourFeature : BaseFacade() {
    override val tag = "YourFeatureFacade"
    suspend fun getInventories(...): ApiResult<...> {
        requireInitialized()   // throws IllegalStateException with clear message if not init'd
        // ... actual logic
    }
}
```

**Impact:** One boolean check per facade call (~nanoseconds). Gives a clear, developer-friendly error instead of a cryptic `NullPointerException`.

</details>

---

### 15. SDKState & SDKErrorCode

> **Source:** `core/SDKState.kt`, `core/SDKStateFlow.kt` · **Audience:** All developers building UI

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

`SDKState<T>` is the **UI-ready state wrapper** with 4 states: `Loading`, `Success`, `ErrorBody`, `Error`. `SDKErrorCode` provides machine-readable constants for localization.

#### How to use it

**Recommended - `sdkStateFlow {}` (auto-emits Loading):**

```kotlin
val state: StateFlow<SDKState<InventoryListModel>> =
    sdkStateFlow { AppFacadeYourFeature.getInventories("2057192797") }
        .stateIn(viewModelScope, SharingStarted.Lazily, SDKState.Loading)
```

**Manual conversion:**

```kotlin
val state = result.toSDKState()
```

**Localized error handling with SDKErrorCode:**

```kotlin
is SDKState.Error -> when (state.message) {
    SDKErrorCode.NETWORK_ERROR     -> showDialog(R.string.no_internet)
    SDKErrorCode.REQUEST_CANCELLED -> { /* no-op */ }
    else                           -> showDialog(R.string.unexpected_error)
}
```

**Convenience:** `state.isLoading`, `state.isSuccess`, `state.isError`, `state.dataOrNull()`, `state.errorMessageOrNull()`

**iOS:** Via SKIE, `SDKState` maps to a Swift `enum` - native `switch` works out of the box.

</details>

---

## Hidden Gems & Crucial Internals

These are the **non-obvious** design decisions, utilities, and patterns that are easy to miss but critical to understand. Discovered by scanning every file in the codebase.

---

### 16. SDKCredentials - Security by Design

> **Source:** `core/SDKInitializer.kt` (bottom) · **Audience:** Security reviewers, all developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

`SDKCredentials` is intentionally **NOT a `data class`**. This is a deliberate security design choice.

#### Why it matters

Kotlin `data class` auto-generates `.copy()`, `.toString()`, and `.componentN()` - all of which could leak secrets:

```kotlin
// ❌ If SDKCredentials were a data class:
val creds = SDKCredentials(baseUrl, authToken, apiGuid, clientId, apiKey)
println(creds)              // → prints all secrets in plaintext
val leaked = creds.copy()   // → creates a new object with all secrets accessible
val (_, token, _, _, _) = creds // → destructuring exposes token
```

Instead, `SDKCredentials` is a regular class with:

- **`toString()` redacts all values** → `"SDKCredentials(baseUrl=https://..., authToken=***, apiGuid=***, clientId=***, apiKey=***)"`
- **No `.copy()`** - cannot accidentally duplicate credentials
- **No destructuring** - cannot accidentally extract secrets
- **Custom `equals()`/`hashCode()`** - manual implementation for correctness without `data class`

#### Impact

Zero runtime overhead. This is a pure compile-time safety measure that prevents credentials from appearing in logs, crash reports, or debugger watches.

</details>

---

### 17. iOS Callback Bridge - `YourFeatureIOSFacade`

> **Source:** `iosMain/kotlin/shared/YourFeatureIOSFacade.kt` · **Audience:** iOS developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A Swift-idiomatic **callback-based wrapper** around the Kotlin coroutine-based facade. While SKIE handles most interop, this class provides an alternative pattern for teams that prefer explicit callbacks over `async/await`.

#### How to use it (Swift)

```swift
let facade = YourFeatureIOSFacade()

facade.getInventories(
    customerNo: "2057192797",
    order: nil,
    onLoading: { self.showLoader() },
    onSuccess: { model in self.render(model) },
    onError: { errorCode, message in self.showError(errorCode, message) }
)

// CRITICAL: Call on deinit / viewDidDisappear to prevent leaked callbacks
facade.cancelAll()
```

#### Why it exists

- Callbacks fire on **main thread** (`Dispatchers.Main`) - safe for UI updates
- Provides `onLoading` / `onSuccess` / `onError` - maps directly to iOS UI patterns
- `cancelAll()` cancels the internal `CoroutineScope` - prevents callbacks firing after the view is deallocated
- Error codes use `SDKErrorCode` constants - same i18n-ready codes as Android

#### Impact

One `CoroutineScope` per facade instance. Must call `cancelAll()` to avoid memory leaks.

</details>

---

### 18. ProGuard / R8 Consumer Rules

> **Source:** `shared/consumer-rules.pro` · **Audience:** Android developers using minification

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

ProGuard rules **bundled inside the AAR** that are automatically applied when the host app enables R8 code shrinking (`minifyEnabled = true`). The host app **does not need to add any SDK-specific ProGuard rules**.

#### What it keeps (and why)

| Rule                                                                                               | Why                                                               |
|----------------------------------------------------------------------------------------------------|-------------------------------------------------------------------|
| `SDKInitializer`, `SDKCredentials`, `SDKState`, `SDKConfig`, `ApiResult`, `SDKInfo`, `RetryConfig` | Public API surface - host apps reference these by name            |
| `features.**.facade.**`                                                                            | All facade objects (entry points per feature)                     |
| `features.**.models.Inventories`, `InventoryListModel`                                             | UI models that host apps deserialize/display                      |
| `models.**` (shared)                                                                               | `BaseApiResponse`, `PaginationInfo`, `ErrorInfo`, `ErrorDetail`   |
| `org.koin.**`                                                                                      | Koin uses reflection for dependency resolution                    |
| `**$$serializer`                                                                                   | kotlinx.serialization generated serializers - R8 would strip them |
| `io.ktor.client.**`                                                                                | Ktor uses reflection for engine discovery and plugin installation |
| `okhttp3.**`, `okio.**`                                                                            | Transitive deps - suppress warnings only                          |

#### Impact

- **Zero work for host apps** - rules are applied automatically
- **Aggressive shrinking** - only the public API and reflection-dependent classes are kept; all `internal` SDK classes are shrinkable
- **No `dontwarn` suppression of SDK classes** - only for transitive deps (okhttp, okio, ktor)

</details>

---

### 19. Build-Time Code Generation

> **Source:** `shared/build.gradle.kts` · **Audience:** SDK contributors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What gets generated?

**Two files are auto-generated at build time** - never edit these manually:

##### 1. `FeatureModules.kt` - Feature Discovery

When you build with `-Psdk.features=yourfeature`:

```kotlin
// AUTO-GENERATED - do not edit. Controlled by sdk.features property.
package com.droidunplugged.kmp_platform_kit.core.di

import com.droidunplugged.kmp_platform_kit.features.yourfeature.di.yourfeatureModule
import org.koin.core.module.Module

object FeatureModules {
    val all: List<Module> = listOf(
        yourfeatureModule
    )
}
```

**Convention:** Feature folder `features/orders/` → expects a val named `ordersModule` in `features/orders/di/OrdersModule.kt`. The build script discovers this by folder name.

**Why auto-generate?** Zero manual wiring. Adding a new feature = create the folder + DI module. No touching `SDKInitializer`, no import lists to maintain, no merge conflicts.

##### 2. `SDKInfo.kt` - Version Sync

```kotlin
// AUTO-GENERATED - do not edit. Version is controlled by versioning.gradle.kts.
object SDKInfo {
    const val NAME = "KmpPlatformKit"
    const val VERSION = "1.0.0"  // ← from -PSDK_VERSION or env var or default
    val fullName: String get() = "$NAME/$VERSION"
}
```

**Why auto-generate?** The version in code **always** matches the published artifact. No manual syncing, no forgotten version bumps. Used in `user-agent` headers on every request.

#### Version Resolution Order

```
1. Gradle property:  -PSDK_VERSION=1.0.0
2. Environment var:  SDK_VERSION=1.0.0
3. Default:          0.0.1-SNAPSHOT
```

</details>

---

### 20. AAR Binary Size Budget (6 MB)

> **Source:** `shared/build.gradle.kts` (bottom) · **Audience:** All contributors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

An automated build check that **fails the build** if the release AAR exceeds **6 MB**. This prevents silent size regressions from new dependencies or bloated resources.

#### How it works

```
assembleRelease → bundleReleaseAar → checkAarSize
                                          │
                                    Is AAR > 6 MB?
                                    YES → ❌ BUILD FAILS
                                    NO  → ✅ "AAR size within budget"
```

**Run manually:**

```bash
./gradlew :shared:checkAarSize
```

**Output:**

```
📦 AAR size: 3.24 MB  |  budget: 6.0 MB  |  file: shared-release-fat.aar
✅ AAR size within budget
```

#### Why it exists

A single careless dependency addition (e.g., pulling in all of OkHttp4 instead of just the engine adapter) can double the AAR size overnight. The budget catches this in CI before it reaches host apps.

#### What to do if it fails

1. Check what new dependencies were added in `libs.versions.toml`
2. Check the Fat AAR build log - it lists every bundled JAR with class counts
3. Consider: is the dependency needed? Can it be `implementation` instead of `api`?
4. If the size increase is intentional, update `AAR_SIZE_BUDGET_BYTES` in `shared/build.gradle.kts`

</details>

---

### 21. Fat AAR - Zero-Dependency Distribution

> **Source:** `publishing/fat-aar.gradle.kts` · **Audience:** Android developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A custom Gradle task that **repackages the release AAR with all runtime dependencies baked in** - so the host app needs **zero additional `implementation` lines**.

#### What gets bundled vs. what doesn't

| ✅ Bundled (host unlikely to have)        | ❌ NOT Bundled (host always has) |
|------------------------------------------|---------------------------------|
| `io.ktor:*` (~1,550 classes)             | `kotlin-stdlib`                 |
| `kotlinx-serialization:*` (~365 classes) | `kotlinx-coroutines`            |
| `io.insert-koin:*` (~233 classes)        | `okhttp3`, `okio`               |
|                                          | `slf4j`                         |

#### Security: META-INF stripping

The fat AAR task strips all `META-INF/*.SF`, `*.DSA`, `*.RSA` signature files from bundled JARs. This is required because mixing signed JARs from different publishers would cause `SecurityException` at runtime. The SDK re-signs the fat AAR as a single, coherent artifact.

#### Class conflict warning

> ⚠️ If your host app **already depends on Ktor, Koin, or kotlinx-serialization**, use the **lean AAR** (`shared-release.aar`) instead. The fat AAR will cause duplicate class errors at compile time.

#### How to build

```bash
./scripts/build-android.sh yourfeature
# Output:
#   shared/build/outputs/aar/shared-release.aar      (lean ~115 KB)
#   shared/build/outputs/aar/shared-release-fat.aar  (fat ~3.3 MB)
```

</details>

---

### 22. Test Infrastructure - Reusable Base Classes

> **Source:** `commonTest/kotlin/testutil/` · **Audience:** SDK contributors writing tests

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A complete test toolkit that eliminates boilerplate across all feature tests. New features get **13+ tests for free** by extending a base class.

#### The toolkit

| Class                    | Purpose                          | What You Get For Free                                                                                                                                                  |
|--------------------------|----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `FakeApiClient`          | In-memory fake HTTP client       | Per-verb response queues, call counting, path recording                                                                                                                |
| `BaseRepositoryTest<T>`  | Abstract base for repo tests     | 13 tests: error propagation, SDKState mapping, sdkStateFlow integration, network error, cancellation                                                                   |
| `BaseIntegrationTest<T>` | Abstract base for pipeline tests | Full JSON → Repository → Domain Model → SDKState tests                                                                                                                 |
| `Assertions.kt`          | Fluent assertion helpers         | `result.assertSuccess()`, `result.assertFailure(401)`, `state.assertLoading()`, `state.assertSDKSuccess()`, `state.assertErrorBody(403)`, `state.assertNetworkError()` |
| `CommonFixtures`         | Cross-feature JSON fixtures      | `errorJson()`, `multiErrorJson()`, `wrapSuccess(dataBlock)`, `wrapSuccessWithExtraFields()`, malformed JSON, minimal JSON                                              |
| `TestFixtures`           | Feature-specific JSON fixtures   | Full success, empty list, no pagination, multi-error, edge cases                                                                                                       |
| `TestLogger`             | In-memory log capture            | `logger.hasErrors()`, `logger.hasWarning("tag")`, `logger.hasMessageContaining("text")`, `logger.entries`                                                              |

#### How to use it (adding tests for a new "orders" feature)

```kotlin
class OrderRepositoryImplTest : BaseRepositoryTest<OrderListModel>() {
    private lateinit var repository: OrderRepositoryImpl

    override fun createRepository(client: FakeApiClient) {
        repository = OrderRepositoryImpl(apiClient = client)
    }

    override suspend fun callRepository(): ApiResult<OrderListModel> =
        repository.getOrders(OrderQuery(customerId = "123"))

    override val successJson: String get() = """{ "status": "SUCCESS", ... }"""

    override fun validateSuccessData(data: OrderListModel) {
        assertEquals("SUCCESS", data.status)
        assertTrue(data.orders.isNotEmpty())
    }

    // ← You automatically get 13 tests: Failure propagation, NetworkError,
    //    Cancelled, SDKState.Success, SDKState.ErrorBody, SDKState.Error,
    //    sdkStateFlow Loading→Success, sdkStateFlow Loading→Error, etc.

    // Add feature-specific tests below:
    @Test
    fun `maps order dates correctly`() = runTest { ... }
}
```

#### FakeApiClient - Key APIs

```kotlin
val client = FakeApiClient()

// Queue a response for a specific path prefix
client.enqueueGet("inventories") { ApiResult.Success(json) }

// Set a default response for all GET calls
client.setDefaultGetResponse { ApiResult.Failure(500, "Server Error") }

// Verify calls
assertEquals(1, client.getCallCount)
assertEquals("/inventories?page=0", client.lastGetPath)
assertTrue(client.allGetPaths.contains("/inventories"))
```

#### Impact

- Writing tests for a new feature takes **minutes instead of hours**
- 13 standard tests ensure consistent error handling across all features
- JSON fixtures are centralized - API contract changes update once, fix everywhere

</details>

---

### 23. JsonProvider - Resilient Serialization Config

> **Source:** `shared/utils/JsonProvider.kt` · **Audience:** SDK contributors, backend teams

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A centralized `kotlinx.serialization.Json` instance used for **all** JSON parsing in the SDK. Every setting is chosen for maximum resilience against backend changes.

#### Configuration and why each setting matters

| Setting             | Value   | Why                                                                                                                                                                 |
|---------------------|---------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `ignoreUnknownKeys` | `true`  | **Backend adds a new field** → SDK doesn't crash. Without this, a single new JSON field breaks all older SDK versions.                                              |
| `encodeDefaults`    | `true`  | Always include default values when serializing → backend receives complete objects, no missing fields.                                                              |
| `explicitNulls`     | `false` | Null fields are omitted from output → smaller payloads, cleaner JSON.                                                                                               |
| `isLenient`         | `true`  | Accept non-standard JSON (unquoted keys, trailing commas) → tolerates backend quirks.                                                                               |
| `coerceInputValues` | `true`  | If the backend sends `null` for a non-nullable field, the SDK uses the default value instead of crashing. **This is the single most important resilience setting.** |

#### Why a single centralized instance?

If different parts of the SDK created their own `Json {}` instances with different settings, one could crash on input that another handles fine. `JsonProvider.json` ensures identical behavior everywhere.

#### Impact

Zero runtime overhead - the `Json` object is created once at class load time. The settings above mean the SDK survives:

- Backend adding new fields (forward compatibility)
- Backend sending `null` for non-nullable types (coercion)
- Backend sending non-standard JSON (leniency)
- Backend removing optional fields (defaults)

</details>

---

### 24. Header Lifecycle Architecture

> **Source:** `shared/utils/HttpHeaders.kt`, `core/PlatformConfig.kt`, `core/HttpClientHeaders.kt` · **Audience:** All developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A three-tier header system with clear lifecycle boundaries - preventing the #1 SDK bug: stale or misrouted authentication headers.

#### The three tiers

```
┌──────────────────┬──────────────────────┬──────────────────────────────┐
│ Category         │ Headers              │ Lifecycle                    │
├──────────────────┼──────────────────────┼──────────────────────────────┤
│ DYNAMIC          │ authorization        │ Token refreshes every ~15min │
│ (PlatformConfig) │ x-cah-api-guid       │ Changes every login          │
├──────────────────┼──────────────────────┼──────────────────────────────┤
│ ENV-SPECIFIC     │ clientid             │ Fixed per env (DEV/STAGE/…)  │
│ (PlatformConfig) │ x-api-key            │ Fixed per env (DEV/STAGE/…)  │
├──────────────────┼──────────────────────┼──────────────────────────────┤
│ STATIC           │ platform             │ SDK auto-sets, never changes │
│ (DefaultRequest) │ user-agent           │                              │
│                  │ x-external-source    │                              │
├──────────────────┼──────────────────────┼──────────────────────────────┤
│ AUTO-MANAGED     │ accept-encoding      │ OkHttp / Darwin engine       │
│ (HTTP engine)    │ connection, host     │ Don't touch                  │
└──────────────────┴──────────────────────┴──────────────────────────────┘
```

#### How dynamic headers stay fresh

```
PlatformConfig (thread-safe store)
       │
       │ ← updateAuthToken(newToken)    every ~15 min
       │ ← setDynamicHeaders(...)       on re-login
       │
       ▼
HttpClientHeaders.installDynamicHeaders()
       │
       │  Runs on EVERY outgoing request via HttpSend interceptor
       │  Reads PlatformConfig → overwrites request headers with latest values
       │
       ▼
Request goes out with current headers
```

#### Platform-specific thread safety

| Platform | Dynamic Headers              | Env Headers             | Why Different                                                                     |
|----------|------------------------------|-------------------------|-----------------------------------------------------------------------------------|
| Android  | `synchronized {}` block      | `synchronized {}` block | JVM monitor lock - standard Java pattern                                          |
| iOS      | `AtomicReference` + CAS loop | `@Volatile`             | Kotlin/Native has no JVM monitors; `AtomicReference.compareAndSet()` is lock-free |

The `updateAuthToken()` on iOS uses a CAS (Compare-And-Swap) loop:

```kotlin
while (true) {
    val current = dynamicRef.value
    val updated = current.toMutableMap().apply { put("authorization", token) }
    if (dynamicRef.compareAndSet(current, updated)) break
}
```

This ensures no lost updates even under concurrent token refreshes - without any locks.

</details>

---

### 25. Isolated Koin - `SdkKoinComponent`

> **Source:** `core/di/SdkKoinComponent.kt` · **Audience:** Android developers using Koin

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A custom `KoinComponent` subtype that resolves dependencies from the SDK's **private Koin instance** - not the global/host app's Koin.

#### Why it matters

Many Android apps use Koin for their own DI. Without isolation, the SDK's `startKoin` would conflict with the app's `startKoin`:

```kotlin
// ❌ WITHOUT isolation - crashes if host app also uses Koin
class AppFacade : KoinComponent {  // resolves from GLOBAL Koin
    val repo: MyRepo by inject()   // → might get host app's binding or crash
}

// ✅ WITH isolation - no conflict possible
class AppFacade : SdkKoinComponent {  // resolves from SDK's PRIVATE Koin
    val repo: MyRepo by inject()      // → always gets SDK's binding
}
```

The SDK uses `koinApplication { }` (not `startKoin { }`) to create an isolated instance. `SdkKoinComponent.getKoin()` returns this instance:

```kotlin
override fun getKoin(): Koin =
    SDKInitializer.koinApp?.koin
        ?: error("SDK Koin instance is not available. Ensure SDKInitializer.init(...) has been called.")
```

#### Impact

- Apps using **Koin**: Zero conflict - SDK has its own graph
- Apps using **Hilt/Dagger**: Zero interaction - Koin runs independently
- Apps using **no DI**: Zero impact - Koin is internal to the SDK

</details>

---

### 26. `ApiResult.map()` / `flatMap()` - Functional Error Handling

> **Source:** `shared/extensions/ApiResultExtensions.kt` · **Audience:** SDK contributors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

Functional combinators for transforming `ApiResult` values in the repository layer - without unwrapping and re-wrapping manually.

#### How they work

**`map` - transform success data, pass errors through unchanged:**

```kotlin
// Transform DTO → domain model (errors pass through automatically)
val result: ApiResult<InventoryListModel> = apiClient.get(path, parser)
    .map { response -> response.data.inventories.map { it.toModel() } }
```

**`flatMap` - transform into another `ApiResult` (when transform can fail):**

```kotlin
// Check API-level errors INSIDE a 200 response
val result = apiClient.get(path, parser).flatMap { response ->
    if (!response.isSuccess) {
        ApiResult.Failure(-1, response.errorMessage)  // convert to Failure
    } else {
        ApiResult.Success(response.data.toModel())
    }
}
```

**Both are `internal`** - only usable within the SDK, not by host apps. This keeps the public API surface clean.

#### Impact

Zero runtime overhead. These are `inline` functions - the compiler inlines the lambda at the call site, no function object allocation.

</details>

---

### 27. Debug Logging Interceptor - Redaction Rules

> **Source:** `core/DebugLoggingInterceptor.kt` · **Audience:** All developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

An HTTP interceptor that logs full request/response details - but **only when `SDKConfig.debugMode = true`**. When debug mode is off (the default), the interceptor is a **complete no-op** - not even a string comparison.

#### Security safeguards (always enforced, even in debug)

| Safeguard             | Implementation                                                        |
|-----------------------|-----------------------------------------------------------------------|
| Auth headers redacted | `authorization` → `***REDACTED***` in all logs                        |
| Body truncation       | Request and response bodies truncated to **2,048 characters**         |
| Release-proof         | `enableDebugMode()` checks `isDebugBuild()` - no-op in release builds |
| Off by default        | `debugMode = false` - must be explicitly opted in                     |

#### Output format

```
┌── GET https://api.stage.example.com/v1/customer/2052008238/inventories
│ authorization: ***REDACTED***
│ clientid: XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj
│ x-api-key: XWlXpMjuLrYfC7kvlVF02YYJcb7iaJSj
├── 200 OK
│ Body: {"status":"SUCCESS","data":{"customer":2057192797,"inventories":[...]}}
└──────────────────────────────────
```

#### How to enable safely

```kotlin
// Android - in Application.onCreate()
if (BuildConfig.DEBUG) SDKConfig.enableDebugMode()

// OR (safe for all builds - enableDebugMode() is a no-op in release)
SDKConfig.enableDebugMode()
```

</details>

---

### 28. Build Scripts - Complete Reference

> **Source:** `scripts/` · **Audience:** All developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

| Script               | What It Does                             | Usage                                          |
|----------------------|------------------------------------------|------------------------------------------------|
| `build-android.sh`   | Clean → assembleRelease → packageFatAar  | `./scripts/build-android.sh yourfeature` |
| `build-ios.sh`       | Clean → assembleSharedReleaseXCFramework | `./scripts/build-ios.sh yourfeature`     |
| `build-ios-debug.sh` | Clean → assembleSharedDebugXCFramework   | `./scripts/build-ios-debug.sh`                 |
| `clean.sh`           | Full Gradle clean                        | `./scripts/clean.sh`                           |
| `release.sh`         | Tag + build + publish                    | `./scripts/release.sh`                         |
| `setup.sh`           | One-time dev environment bootstrap       | `./scripts/setup.sh`                           |

All scripts accept feature selection as the first argument:

```bash
./scripts/build-android.sh yourfeature         # one feature
./scripts/build-android.sh yourfeature,orders   # multiple
./scripts/build-android.sh                            # all (default)
```

</details>

---

### 29. `SdkApplicationContext` - Android Context Holder

> **Source:** `androidMain/kotlin/core/SdkApplicationContext.kt` · **Audience:** Android developers

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

A lightweight holder for the Android `Context` inside the SDK. Uses `applicationContext` to prevent `Activity` leaks.

#### Why it exists

The SDK needs a `Context` to check `isDebugBuild()` (reads `ApplicationInfo.FLAG_DEBUGGABLE`). Storing an `Activity` context would cause a memory leak; storing `applicationContext` is safe.

#### How it works

- `SDKInitializer.init()` calls `SdkApplicationContext.initialize(context)` automatically
- `initialize()` is idempotent - subsequent calls are no-ops
- `SDKInitializer.reset()` calls `clear()` to release the reference
- `get()` returns `null` in unit test environments (no Android context available)

#### Impact

One `@Volatile` `Context?` reference. No memory leak risk (uses `applicationContext`). Cleared on `reset()`.

</details>

---

### 30. Kover Coverage Configuration

> **Source:** `shared/build.gradle.kts` (Kover block) · **Audience:** SDK contributors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What gets excluded from coverage?

| Exclusion                                | Reason                                        |
|------------------------------------------|-----------------------------------------------|
| `*.di.*Module*`, `*.di.FeatureModules`   | Auto-generated DI wiring - no logic to test   |
| `*.core.SDKInfo`                         | Auto-generated version constant               |
| `*.BuildConfig`                          | Build-time generated                          |
| Platform packages (`*.android`, `*.ios`) | Tested indirectly; Kover only instruments JVM |

#### Minimum threshold

**80%** line coverage enforced by `koverVerify`. The threshold accounts for the fact that Kover only instruments JVM (Android) tests - iOS `actual` implementations are tested via `iosSimulatorArm64Test` but don't show up in Kover metrics.

```bash
./gradlew :shared:koverVerify     # enforce 80% minimum
./gradlew :shared:koverHtmlReport # generate visual report
./gradlew :shared:koverLog        # print summary to console
```

</details>

---

### 31. `@JvmSynchronized` - Multiplatform Annotation

> **Source:** `shared/concurrency/Synchronized.kt` · **Audience:** SDK contributors

<details>
<summary><strong>📖 Full details (click to expand)</strong></summary>

#### What is it?

An `expect`/`actual` annotation that maps to `@kotlin.jvm.Synchronized` on Android and is a **no-op on iOS**.

#### Why it exists

`@Synchronized` is a JVM-only annotation. In Kotlin/Native (iOS), there are no JVM monitor locks - the strict memory model and single-threaded coroutine dispatchers provide safety by default. This annotation lets you write `@JvmSynchronized` in `commonMain` without compiler errors on iOS.

#### When to use it

Use `Mutex` instead for new code (suspend-friendly). This annotation exists for rare cases where you need JVM synchronization on a non-suspend function.

</details>

---

## Repository Structure

```
kmp-platform-kit/
├── docs/                              ← You are here
│   ├── INDEX.md                       ← This file
│   ├── architecture.md                ← Layer design, DI, security, all advanced subsystems
│   ├── technical-concepts.md          ← KMP, coroutines, Koin, SKIE deep-dives
│   ├── integration-android.md         ← Android developer guide
│   ├── integration-ios.md             ← iOS developer guide
│   ├── unit-testing-guide.md          ← Test infrastructure guide
│   ├── diagrams/                      ← Mermaid diagrams + presentation
│   └── endpoints/
│       └── ENDPOINTS.md               ← API reference
│
├── shared/src/
│   ├── commonMain/kotlin/
│   │   ├── core/                      ← SDK core: networking, DI, state, auth, circuit, cache
│   │   │   ├── ApiClient.kt          ← HTTP abstraction (GET, POST, PUT, DELETE, PATCH + CachePolicy)
│   │   │   ├── ApiResult.kt          ← Sealed result type (Success, Failure, NetworkError, Cancelled)
│   │   │   ├── BaseFacade.kt         ← Abstract base for all facades (requireInitialized guard)
│   │   │   ├── CachePolicy.kt        ← 4 cache strategies + ResponseCache (thread-safe, Mutex-guarded)
│   │   │   ├── DebugLoggingInterceptor.kt ← Opt-in HTTP body logging (redacts auth headers)
│   │   │   ├── HttpClientFactory.kt   ← Platform-bridged HttpClient creation (expect/actual)
│   │   │   ├── HttpClientHeaders.kt   ← Static header constants
│   │   │   ├── KtorApiClient.kt       ← Ktor impl with retry + jitter, cache, deduplication
│   │   │   ├── PlatformConfig.kt      ← Thread-safe header store (expect/actual)
│   │   │   ├── RequestDeduplicator.kt ← Coalesces concurrent identical requests
│   │   │   ├── RetryConfig.kt         ← Configurable retry (1–10 attempts, 0–30s backoff)
│   │   │   ├── SDKConfig.kt           ← debugMode toggle, SSL pins config, isDebugBuild()
│   │   │   ├── SDKInitializer.kt      ← Entry point: init, configure, ensureInitialized, reset
│   │   │   ├── SDKPlugin.kt           ← Plugin interface + SDKPluginRegistry
│   │   │   ├── SDKState.kt            ← UI-ready sealed class (Loading, Success, ErrorBody, Error)
│   │   │   ├── SDKStateFlow.kt        ← sdkStateFlow{}, toSDKState(), SDKErrorCode constants
│   │   │   ├── SDKTelemetry.kt        ← Pluggable telemetry interface + NoOpTelemetry
│   │   │   ├── SslPinConfig.kt        ← SSL certificate pinning config
│   │   │   ├── auth/
│   │   │   │   └── TokenManager.kt    ← Proactive + reactive token refresh with coalescing
│   │   │   ├── circuit/
│   │   │   │   └── CircuitBreaker.kt  ← CLOSED → OPEN → HALF_OPEN state machine
│   │   │   ├── config/
│   │   │   │   ├── SdkEnvironment.kt  ← Strongly-typed env config (baseUrl, keys, SSL pins)
│   │   │   │   └── SdkRemoteConfig.kt ← Server-pushed config, kill-switches, maintenance mode
│   │   │   ├── di/
│   │   │   │   ├── CoreModule.kt      ← Core Koin module (HttpClient, ApiClient, RetryConfig)
│   │   │   │   ├── SdkKoinComponent.kt ← SDK-scoped KoinComponent base
│   │   │   │   └── FeatureModules.kt  ← AUTO-GENERATED: collects enabled feature modules
│   │   │   ├── error/
│   │   │   │   └── SdkError.kt        ← Sealed error hierarchy (11 types + recovery hints)
│   │   │   ├── interceptor/
│   │   │   │   └── SdkRequestInterceptor.kt ← Pluggable HTTP middleware pipeline
│   │   │   ├── paging/
│   │   │   │   └── SdkPager.kt        ← Pagination engine with Flow-driven state
│   │   │   └── tracing/
│   │   │       └── SdkTraceContext.kt  ← W3C traceparent + B3 header generation
│   │   │
│   │   ├── features/                   ← Feature modules (yourfeature, ...)
│   │   │   └── yourfeature/      ← Compiled only when sdk.features includes it
│   │   │       ├── di/                 ← Koin module
│   │   │       ├── endpoints/          ← API paths & sort order constants
│   │   │       ├── facade/             ← Public API (AppFacadeYourFeature)
│   │   │       ├── models/             ← Request/response/UI models + mapper
│   │   │       ├── repository/         ← Interface + implementation
│   │   │       └── requestbuilder/     ← Query builders
│   │   │
│   │   └── shared/                     ← Cross-feature utilities
│   │       ├── concurrency/            ← JvmSynchronized expect annotation
│   │       ├── extensions/             ← ApiResult.map(), ApiResult.flatMap()
│   │       ├── models/                 ← BaseApiResponse, ErrorInfo, PaginationInfo
│   │       └── utils/                  ← Logger, PlatformLogger, JsonProvider, HttpHeaders
│   │
│   ├── androidMain/kotlin/             ← Android-specific implementations
│   │   ├── core/                       ← OkHttp engine, PlatformConfig (synchronized), SDKConfig
│   │   └── shared/                     ← JvmSynchronized, Android PlatformLogger (Log.x)
│   │
│   ├── iosMain/kotlin/                 ← iOS-specific implementations
│   │   ├── core/                       ← Darwin engine, PlatformConfig (AtomicReference), SDKConfig
│   │   └── shared/                     ← iOS PlatformLogger (platform_log / NSLog)
│   │
│   └── commonTest/kotlin/              ← Shared tests
│       ├── testutil/                   ← FakeApiClient, BaseRepositoryTest, assertions, fixtures
│       ├── core/                       ← Tests for SDK core (ApiResult, KtorApiClient, SDKState, etc.)
│       ├── shared/                     ← Tests for shared utilities
│       └── features/                   ← Tests per feature module
│
├── publishing/                         ← AAR, fat AAR, XCFramework, Maven scripts
│   ├── android-publish.gradle.kts      ← Maven (GitHub Packages) publishing
│   ├── fat-aar.gradle.kts             ← Bundles transitive deps into a single AAR
│   ├── ios-xcframework.gradle.kts     ← XCFramework generation
│   └── versioning.gradle.kts          ← SemVer resolution from Git tags
│
├── scripts/                            ← Build, clean, release shell scripts
│   ├── build-android.sh               ← Build AAR (with feature selection)
│   ├── build-ios.sh                   ← Build XCFramework release
│   ├── build-ios-debug.sh            ← Build XCFramework debug
│   ├── clean.sh                       ← Full clean
│   ├── release.sh                     ← Tag + build + publish
│   └── setup.sh                       ← One-time dev environment bootstrap
│
├── config/
│   ├── detekt/detekt.yml              ← Static analysis config
│   └── owasp/suppression.xml         ← CVE suppression list
│
├── gradle/libs.versions.toml          ← Dependency version catalog (source of truth)
├── CHANGELOG.md                        ← Version history
├── CONTRIBUTING.md                     ← Contribution guidelines
├── MIGRATION.md                        ← Version upgrade guide
├── SECURITY.md                         ← Security policy
└── README.md                           ← Project overview
```