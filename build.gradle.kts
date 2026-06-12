// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.skie) apply false
    alias(libs.plugins.detekt)
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.bcv)
    alias(libs.plugins.owasp)
    alias(libs.plugins.dokka) apply false
}

// Global configuration for Detekt and OWASP as per docs
detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(file("$rootDir/config/detekt/detekt.yml"))
}

dependencyCheck {
    failBuildOnCVSS = 7.0f
    suppressionFile = "$rootDir/config/owasp/suppression.xml"
    scanConfigurations = listOf("releaseRuntimeClasspath")
}