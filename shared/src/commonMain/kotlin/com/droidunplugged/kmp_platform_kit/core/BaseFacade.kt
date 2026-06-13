package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.core.di.SdkKoinComponent
import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger

/**
 * Base class for all SDK feature facades.
 *
 * Centralises the SDK-initialised guard that previously lived in every facade's
 * suspend methods - removing copy-paste and ensuring a consistent error message.
 *
 * ## Usage
 * ```kotlin
 * object AppFacadePhysicalInventory : BaseFacade() {
 *
 *     suspend fun getInventories(...): ApiResult<InventoryListModel> {
 *         requireInitialized()               // ← one-liner replaces the check() call
 *         require(customerNo.isNotBlank()) { "customerNo must not be blank" }
 *         ...
 *     }
 * }
 * ```
 *
 * Also exposes a shared [log] for consistent tagging.
 */
abstract class BaseFacade : SdkKoinComponent {

    /** Subclasses override to supply their log tag (e.g. `"PhysicalInventoryFacade"`). */
    protected abstract val tag: String

    protected val log get() = PlatformLogger.get()

    /**
     * Throws [IllegalStateException] if the SDK is not yet initialized.
     *
     * Call this at the start of every suspend facade method **before** any
     * business logic or Koin resolution.
     *
     * The message intentionally tells the developer exactly what to do.
     */
    protected fun requireInitialized() {
        check(SDKInitializer.isInitialized) {
            "SDK not initialized. Call SDKInitializer.init(...) or " +
                    "SDKInitializer.ensureInitialized() before using any facade."
        }
    }
}