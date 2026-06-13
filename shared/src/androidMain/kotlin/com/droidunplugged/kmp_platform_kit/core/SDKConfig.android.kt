package com.droidunplugged.kmp_platform_kit.core

/**
 * Android actual for [isDebugBuild].
 *
 * Reads the `debuggable` flag from the application's [android.content.pm.ApplicationInfo].
 * This flag is set by the Android build system - cannot be spoofed in a release build.
 *
 * Returns `false` if no application context is available (e.g. unit test environments).
 */
actual fun isDebugBuild(): Boolean = try {
    val context = SdkApplicationContext.get() ?: return false
    (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
} catch (_: Exception) {
    false
}