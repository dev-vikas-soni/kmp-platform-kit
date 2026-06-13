package com.droidunplugged.kmp_platform_kit.core

import android.content.Context
import kotlin.concurrent.Volatile

/**
 * Lightweight holder for the Android [Context] inside the SDK.
 *
 * The host app must call [initialize] once (typically from `Application.onCreate`)
 * before using the SDK on Android. [SDKInitializer.init] calls this automatically.
 *
 * Uses `applicationContext` to avoid Activity leaks.
 */
internal object SdkApplicationContext {

    @Volatile
    private var context: Context? = null

    /**
     * Initialize with the application context.
     * Safe to call multiple times - subsequent calls are no-ops.
     */
    fun initialize(ctx: Context) {
        if (context == null) {
            context = ctx.applicationContext
        }
    }

    /** Returns the stored application context, or `null` if not yet initialized. */
    fun get(): Context? = context

    /** Clears the stored context (called from [SDKInitializer.reset]). */
    fun clear() {
        context = null
    }
}