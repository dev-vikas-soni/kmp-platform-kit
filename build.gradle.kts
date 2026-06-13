plugins {
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.detekt)
    id("org.owasp.dependencycheck") version "12.2.0"
    // Binary-compatibility validator: applied to :shared where the library code lives.
    // Declared here so Gradle resolves the plugin JAR for the whole build.
    // Run: ./gradlew :shared:apiDump   (update .api files after intentional API changes)
    //      ./gradlew :shared:apiCheck  (validate - wired into CI)
    id("org.jetbrains.kotlinx.binary-compatibility-validator") version "0.18.1" apply false
}

// Apply detekt to root project for project-wide analysis
detekt {
    config.setFrom(files("${rootDir}/config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
    parallel = true
    source.setFrom(
        files(
            "shared/src/commonMain/kotlin",
            "shared/src/androidMain/kotlin",
            "shared/src/iosMain/kotlin"
        )
    )
}

// ── OWASP Dependency-Check ────────────────────────────────────────────────────
// Scans all Gradle dependencies for known CVEs via the NVD database.
// Fails the build on any finding with CVSS score >= 7.0 (HIGH or CRITICAL).
// Suppress false positives in config/owasp/suppression.xml with documented justification.
//
// Run manually: ./gradlew dependencyCheckAnalyze
// CI gate:      .github/workflows/security.yml
dependencyCheck {
    failBuildOnCVSS = 7f
    formats = listOf("HTML", "JSON", "SARIF")
    suppressionFile = "${rootDir}/config/owasp/suppression.xml"
    nvd {
        apiKey = System.getenv("NVD_API_KEY") ?: ""
    }
    skipConfigurations = listOf("testImplementation", "androidTestImplementation")
}