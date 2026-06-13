package com.droidunplugged.kmp_platform_kit.core.di

import com.droidunplugged.kmp_platform_kit.core.SDKInitializer
import org.koin.core.Koin
import org.koin.core.component.KoinComponent

/**
 * KoinComponent that resolves dependencies from the SDK's **isolated** Koin instance.
 *
 * Facades implement this interface instead of the global [KoinComponent] so that
 * all SDK-internal dependency resolution stays within the SDK's own DI graph -
 * no conflict with a host app that also uses Koin.
 *
 * ## Usage in facades
 * ```kotlin
 * object AppFacadePhysicalInventory : SdkKoinComponent {
 *     private val repository: PhysicalInventoryRepository by inject()
 * }
 * ```
 *
 * Internal - host apps never use this directly.
 */
interface SdkKoinComponent : KoinComponent {

    /**
     * Returns the SDK's isolated [Koin] instance.
     *
     * @throws IllegalStateException if the SDK has not been initialized yet.
     */
    override fun getKoin(): Koin =
        SDKInitializer.koinApp?.koin
            ?: error(
                "SDK Koin instance is not available. " +
                        "Ensure SDKInitializer.init(...) or SDKInitializer.ensureInitialized() " +
                        "has been called before accessing any facade."
            )
}