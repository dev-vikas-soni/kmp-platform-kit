package com.droidunplugged.kmp_platform_kit.core

import kotlin.experimental.ExperimentalNativeApi

/**
 * iOS actual for [isDebugBuild].
 *
 * Uses [Platform.isDebugBinary] which is set by the Kotlin/Native compiler
 * when building in Debug configuration. Returns `false` for Release builds
 * with zero overhead.
 */
@OptIn(ExperimentalNativeApi::class)
actual fun isDebugBuild(): Boolean = Platform.isDebugBinary