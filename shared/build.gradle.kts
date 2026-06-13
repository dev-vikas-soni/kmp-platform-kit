import org.jetbrains.dokka.DokkaConfiguration.Visibility
import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.kover)
    alias(libs.plugins.dokka)
    // SKIE: automatically generates idiomatic Swift wrappers for Kotlin coroutines and Flow.
    // Eliminates hand-rolled iOS callback bridges (PhysicalInventoryIOSFacade pattern).
    // suspend fun → async throws   |   Flow<T> → AsyncStream<T>   |   sealed class → enum
    id("co.touchlab.skie") version "0.10.10"
    // Binary-compatibility validator: prevents accidental public API breaks.
    // Declared at root, applied here (where the library code lives).
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

// Maven publishing only when explicitly requested (avoids fail-fast on local dev)
if (gradle.startParameter.taskNames.any { it.contains("publish", ignoreCase = true) }) {
    apply(from = "${rootDir}/publishing/android-publish.gradle.kts")
}

// ---------------------------------------------------------------------------
// Feature-flag mechanism
//
// Pass -Psdk.features=physicalinventory to pick which features get compiled.
// Omit the property (or pass "all") → every feature folder is included.
//
// Examples:
//   ./gradlew :shared:assembleRelease -Psdk.features=physicalinventory
//   ./gradlew :shared:assembleRelease -Psdk.features=physicalinventory,orders
//   ./gradlew :shared:assembleRelease                                 # all
//   ./gradlew :shared:assembleSharedReleaseXCFramework -Psdk.features=physicalinventory
// ---------------------------------------------------------------------------

// Discover every feature directory that exists under commonMain/kotlin/features/
val allFeatures: List<String> = file("src/commonMain/kotlin/features")
    .listFiles { f -> f.isDirectory }
    ?.map { it.name }
    ?: emptyList()

// Resolve requested features from the Gradle property
val requestedRaw = (findProperty("sdk.features") as? String) ?: "all"
val enabledFeatures: List<String> = if (requestedRaw.trim().lowercase() == "all") {
    allFeatures
} else {
    val requested = requestedRaw.split(",").map { it.trim().lowercase() }
    requested.forEach { f ->
        require(f in allFeatures) {
            "Unknown feature '$f'. Available: $allFeatures"
        }
    }
    requested
}

logger.lifecycle("🧩 SDK features enabled: $enabledFeatures  (available: $allFeatures)")

kotlin {
    androidTarget {
        publishLibraryVariants("release", "debug")
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
        }
    }

    // Suppress beta warning for expect/actual classes (objects, enums, etc.)
    targets.configureEach {
        compilations.configureEach {
            compileTaskProvider.configure {
                compilerOptions {
                    freeCompilerArgs.add("-Xexpect-actual-classes")
                }
            }
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    // ✅ Proper XCFramework setup (modern KMP way)
    val xcFramework = XCFramework("Shared")

    targets.withType<org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget>().configureEach {
        binaries.framework {
            baseName = "Shared"
            isStatic = true
            xcFramework.add(this)
        }
    }

    sourceSets {
        commonMain.dependencies {
            // Use `api` so transitive deps are visible to host apps via Maven POM.
            // For local-AAR consumers, these must be added manually - see docs/integration-android.md.
            api(libs.ktor.client.core)
            api(libs.ktor.client.websockets)  // real-time WebSocket / SSE support
            api(libs.coroutines.core)
            api(libs.serialization.json)
            api(libs.koin.core)
        }

        androidMain.dependencies {
            // `api` so OkHttp engine classes are on the host app's classpath
            api(libs.ktor.client.okhttp)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.koin.test)
            implementation(libs.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}

android {
    namespace = "com.cardinalhealth.vantus.sdk"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
    }
}

// ── Binary API surface validation ──────────────────────────────────────────
// Generates api/shared.api on `apiDump`. CI fails on `apiCheck` if the public
// API changes without a deliberate dump. Excludes internal DI wiring and
// generated code from the tracked surface.
apiValidation {
    ignoredPackages.addAll(
        listOf(
            "com.cardinalhealth.vantus.sdk.core.di",
            "com.cardinalhealth.vantus.sdk.features.physicalinventory.di",
        )
    )
    ignoredClasses.addAll(
        listOf(
            "com.cardinalhealth.vantus.sdk.core.SDKInfo",
        )
    )
    klib {
        enabled = true
    }
}

// ---------------------------------------------------------------------------
// Exclude disabled feature source files from compilation
// ---------------------------------------------------------------------------
val disabledFeatures = allFeatures - enabledFeatures.toSet()

kotlin.sourceSets.named("commonMain") {
    kotlin.exclude(disabledFeatures.map { "features/$it/**" })
}

// commonTest - exclude tests for disabled features (if any exist)
kotlin.sourceSets.named("commonTest") {
    kotlin.exclude(disabledFeatures.map { "features/$it/**" })
}

// ---------------------------------------------------------------------------
// Generate FeatureModules.kt
//
// Produces a single file that imports + collects only the enabled features'
// Koin modules. SDKInitializer calls `FeatureModules.all` - zero reflection,
// fully compile-time safe.
//
// Convention: feature "inventory" → import ...features.inventory.di.inventoryModule
// ---------------------------------------------------------------------------
val generatedDir = layout.buildDirectory.dir("generated/features/kotlin")

// ---------------------------------------------------------------------------
// Generate SDKInfo.kt
//
// Keeps SDKInfo.VERSION in sync with the actual project version resolved by
// versioning.gradle.kts.  Never hard-code the version in the source tree again.
// ---------------------------------------------------------------------------
val generateSDKInfo by tasks.registering {
    description = "Generates SDKInfo.kt with the current SDK version"
    // Resolve at task execution time to guarantee versioning.gradle.kts has run
    val sdkVersion = (rootProject.extra.properties["sdkVersion"] as? String)
        ?: project.version.toString().takeIf { it != "unspecified" }
        ?: "0.0.1-SNAPSHOT"
    inputs.property("sdkVersion", sdkVersion)
    outputs.dir(generatedDir)

    doLast {
        val resolvedVersion = (rootProject.extra.properties["sdkVersion"] as? String)
            ?: project.version.toString().takeIf { it != "unspecified" }
            ?: "0.0.1-SNAPSHOT"
        val code = """
            |// AUTO-GENERATED - do not edit. Version is controlled by versioning.gradle.kts.
            |package com.cardinalhealth.vantus.sdk.core
            |
            |/**
            | * Runtime-accessible SDK metadata.
            | *
            | * Host apps and crash reporters can query:
            | * ```kotlin
            | * val version = SDKInfo.VERSION      // e.g. "1.0.0"
            | * val name    = SDKInfo.NAME         // "VantusSDK"
            | * val full    = SDKInfo.fullName     // "VantusSDK/1.0.0"
            | * ```
            | *
            | * Version is injected at build time - always matches the published artifact.
            | */
            |object SDKInfo {
            |    /** SDK display name. */
            |    const val NAME = "VantusSDK"
            |
            |    /** SDK version string (SemVer) - auto-synced with build version. */
            |    const val VERSION = "$resolvedVersion"
            |
            |    /** Combined name/version, e.g. `"VantusSDK/1.0.0"`. */
            |    val fullName: String get() = "${'$'}NAME/${'$'}VERSION"
            |}
        """.trimMargin()

        val outFile = generatedDir.get().asFile.resolve(
            "com/cardinalhealth/vantus/sdk/core/SDKInfo.kt"
        )
        outFile.parentFile.mkdirs()
        outFile.writeText(code)
    }
}

val generateFeatureModules by tasks.registering {
    description = "Generates FeatureModules.kt based on enabled sdk.features"
    inputs.property("enabledFeatures", enabledFeatures)
    outputs.dir(generatedDir)

    doLast {
        val imports = enabledFeatures.joinToString("\n") { feat ->
            "import com.cardinalhealth.vantus.sdk.features.$feat.di.${feat}Module"
        }
        val listEntries = enabledFeatures.joinToString(",\n        ") { feat ->
            "${feat}Module"
        }

        val code = """
            |// AUTO-GENERATED - do not edit. Controlled by sdk.features property.
            |package com.cardinalhealth.vantus.sdk.core.di
            |
            |$imports
            |import org.koin.core.module.Module
            |
            |/**
            | * Collects Koin modules for all features enabled at build time.
            | *
            | * Enabled features: $enabledFeatures
            | *
            | * To change, rebuild with: -Psdk.features=inventory,orders,...
            | */
            |object FeatureModules {
            |    val all: List<Module> = listOf(
            |        $listEntries
            |    )
            |}
        """.trimMargin()

        val outFile = generatedDir.get().asFile.resolve(
            "com/cardinalhealth/vantus/sdk/core/di/FeatureModules.kt"
        )
        outFile.parentFile.mkdirs()
        outFile.writeText(code)
    }
}

// Wire generated source into commonMain
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(generatedDir)
}

// Ensure code-gen runs before any Kotlin compilation
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(generateFeatureModules, generateSDKInfo)
}

// ---------------------------------------------------------------------------
// Dokka - Public SDK Documentation
//
// Run:   ./gradlew :shared:dokkaHtml         → browsable HTML docs
//        ./gradlew :shared:dokkaGfm          → GitHub-flavoured Markdown docs
//        ./gradlew :shared:dokkaJar          → javadoc-style JAR for Maven publish
//
// Output: shared/build/docs/html/index.html
//         shared/build/docs/gfm/
// ---------------------------------------------------------------------------
val docsRepoBaseUrl = "https://github.com/cardinal-health/digital-mobile-lab-vantus-kmp/blob/main"
val sdkVersionForDocs = providers.provider {
    (rootProject.extra.properties["sdkVersion"] as? String)
        ?: project.version.toString().takeIf { it != "unspecified" }
        ?: "0.0.1-SNAPSHOT"
}

tasks.withType<DokkaTask>().configureEach {
    dependsOn(generateFeatureModules, generateSDKInfo)

    moduleName.set("VantusSDK")
    moduleVersion.set(sdkVersionForDocs)
    failOnWarning.set(false)
    suppressObviousFunctions.set(true)
    suppressInheritedMembers.set(false)

    dokkaSourceSets.configureEach {
        includes.from(rootProject.file("docs/dokka/module.md"))
        jdkVersion.set(17)
        reportUndocumented.set(false)
        skipDeprecated.set(false)
        documentedVisibilities.set(
            setOf(
                Visibility.PUBLIC,
                Visibility.PROTECTED,
            )
        )

        sourceLink {
            localDirectory.set(file("src/commonMain/kotlin"))
            remoteUrl.set(uri("$docsRepoBaseUrl/shared/src/commonMain/kotlin").toURL())
            remoteLineSuffix.set("#L")
        }
        sourceLink {
            localDirectory.set(file("src/androidMain/kotlin"))
            remoteUrl.set(uri("$docsRepoBaseUrl/shared/src/androidMain/kotlin").toURL())
            remoteLineSuffix.set("#L")
        }
        sourceLink {
            localDirectory.set(file("src/iosMain/kotlin"))
            remoteUrl.set(uri("$docsRepoBaseUrl/shared/src/iosMain/kotlin").toURL())
            remoteLineSuffix.set("#L")
        }

        perPackageOption {
            matchingRegex.set("com\\.cardinalhealth\\.vantus\\.sdk\\..*\\.di(\\..*)?")
            suppress.set(true)
        }
        perPackageOption {
            matchingRegex.set("com\\.cardinalhealth\\.vantus\\.sdk\\.core\\.di(\\..*)?")
            suppress.set(true)
        }
    }
}

tasks.named<DokkaTask>("dokkaHtml") {
    outputDirectory.set(layout.buildDirectory.dir("docs/html"))
}

tasks.named<DokkaTask>("dokkaGfm") {
    outputDirectory.set(layout.buildDirectory.dir("docs/gfm"))
}

val dokkaJar by tasks.registering(Jar::class) {
    group = "publishing"
    description = "Packages Dokka HTML output into a javadoc-style jar"
    dependsOn(tasks.named("dokkaHtml"))
    archiveClassifier.set("javadoc")
    from(tasks.named<DokkaTask>("dokkaHtml").flatMap { it.outputDirectory })
}

// ---------------------------------------------------------------------------
// Kover - Code Coverage
//
// Run:   ./gradlew :shared:koverHtmlReport        → HTML report
//        ./gradlew :shared:koverXmlReport         → XML report (CI)
//        ./gradlew :shared:koverVerify            → Enforce minimums
//        ./gradlew :shared:koverLog               → Print to console
//
// Output: shared/build/reports/kover/html/index.html
// ---------------------------------------------------------------------------
kover {
    reports {
        filters {
            excludes {
                // Exclude auto-generated code and DI wiring
                classes(
                    "*.di.*Module*",
                    "*.di.FeatureModules",
                    "*.core.SDKInfo",
                    "*.BuildConfig",
                )
                // Exclude platform-specific actual implementations (tested indirectly
                // via iosSimulatorArm64Test / Android instrumented tests, not commonTest)
                packages(
                    "com.cardinalhealth.vantus.sdk.android",
                    "com.cardinalhealth.vantus.sdk.ios",
                )
                // Exclude platform `actual` functions compiled into the core package.
                // These require a real HTTP engine or Android Context - not testable in
                // JVM unit tests (commonTest). They are validated by platform integration tests.
                classes(
                    "*.core.HttpClientFactory_androidKt",  // actual fun createClientImpl() - creates OkHttp engine
                    "*.core.PlatformConfig",               // actual object - synchronized/AtomicReference header store
                    "*.core.SDKConfig_androidKt",          // actual fun isDebugBuild() - reads ApplicationInfo
                    "*.core.SdkApplicationContext",         // Android Context holder - no Context in JVM tests
                    "*.core.DebugLoggingInterceptorKt*",   // installDebugLogging - requires live HttpSend plugin
                    "*.core.HttpClientHeadersKt*",         // installDynamicHeaders - requires live HttpSend plugin
                )
                // Exclude Kotlin compiler-generated inner classes for lambdas inside
                // SDKInitializer.initUnderLock that run on a CoroutineScope and require
                // a live Koin graph / HTTP client to exercise (background remote config,
                // token-refresh callbacks). These are tested via integration tests.
                classes(
                    "*.core.SDKInitializer\$initUnderLock*",
                    "*.core.SDKInitializer\$reset*",  // reset() try/catch blocks for HttpClient close/KtorApiClient reset
                )
            }
        }

        verify {
            // Note: Kover instruments Android (JVM) tests only. Platform-specific
            // actual implementations (iOS) are tested via iosSimulatorArm64Test
            // but not reflected in Kover metrics. Set threshold accordingly.
            rule("SDK minimum coverage") {
                minBound(80)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// AAR Binary Size Budget
//
// Prevents the release AAR from silently growing beyond the approved size limit.
// Run:   ./gradlew :shared:checkAarSize
// Also wired into assembleRelease so CI catches regressions automatically.
//
// Budget: 6 MB (fat AAR with all bundled dependencies)
// ---------------------------------------------------------------------------
val AAR_SIZE_BUDGET_BYTES = 6 * 1024 * 1024L   // 6 MB

val checkAarSize by tasks.registering {
    description = "Fails the build if the release AAR exceeds ${"%.1f".format(AAR_SIZE_BUDGET_BYTES / 1_048_576.0)} MB"

    doLast {
        // KMP library produces the AAR via the android variant build pipeline
        val aarDir = layout.buildDirectory.dir("outputs/aar").get().asFile
        val aarFile = aarDir.listFiles()?.firstOrNull { it.extension == "aar" && it.name.contains("release") }

        if (aarFile == null || !aarFile.exists()) {
            logger.warn("⚠ Release AAR not found under ${aarDir.path} - build the project first")
            return@doLast
        }

        val sizeMb = aarFile.length() / 1_048_576.0
        val budgetMb = AAR_SIZE_BUDGET_BYTES / 1_048_576.0
        logger.lifecycle("📦 AAR size: ${"%.2f".format(sizeMb)} MB  |  budget: ${"%.1f".format(budgetMb)} MB  |  file: ${aarFile.name}")

        check(aarFile.length() <= AAR_SIZE_BUDGET_BYTES) {
            "❌ AAR size (${"%.2f".format(sizeMb)} MB) exceeds budget (${"%.1f".format(budgetMb)} MB). " +
                    "Check for new transitive dependencies or bloated resources."
        }
        logger.lifecycle("✅ AAR size within budget")
    }
}

// Wire size check after any task that produces a release AAR
afterEvaluate {
    tasks.matching { it.name.startsWith("bundle") && it.name.contains("ReleaseAar") }
        .configureEach { finalizedBy(checkAarSize) }
}