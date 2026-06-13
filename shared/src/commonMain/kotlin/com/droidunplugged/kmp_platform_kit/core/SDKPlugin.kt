package com.droidunplugged.kmp_platform_kit.core

import org.koin.core.module.Module

/**
 * Extension point for adding optional features to the SDK at init time.
 *
 * As the SDK grows beyond 10+ features across multiple teams, using plugins
 * allows each feature to self-register its DI module, respond to lifecycle
 * events, and be discovered without modifying [SDKInitializer].
 *
 * ## How to implement a plugin
 * ```kotlin
 * class PhysicalInventoryPlugin : SDKPlugin {
 *     override val id = "physical_inventory"
 *     override val koinModule = physicalinventoryModule
 *
 *     override fun onSDKInitialized() {
 *         // Warm up any caches, pre-fetch config, etc.
 *     }
 *
 *     override fun onSDKReset() {
 *         // Clear feature-specific state on logout
 *     }
 * }
 * ```
 *
 * ## How to register
 * ```kotlin
 * SDKInitializer.registerPlugin(PhysicalInventoryPlugin())
 * SDKInitializer.init(baseUrl, authToken, ...)
 * ```
 *
 * Plugins are registered **before** calling [SDKInitializer.init].
 * Their [koinModule] is automatically added to the SDK's isolated Koin graph.
 */
interface SDKPlugin {

    /**
     * Unique identifier for this plugin.
     * Used for logging and to prevent duplicate registration.
     */
    val id: String

    /**
     * The Koin module providing this plugin's dependency bindings.
     * Injected into the SDK's isolated Koin instance automatically.
     */
    val koinModule: Module

    /**
     * Called after [SDKInitializer.init] completes successfully.
     * Use for warm-up tasks (prefetch, cache init, analytics registration, etc.).
     */
    fun onSDKInitialized() {}

    /**
     * Called after [SDKInitializer.reset] completes.
     * Use for cleanup tasks (clear caches, cancel subscriptions, etc.).
     */
    fun onSDKReset() {}
}

/**
 * Registry that holds all registered [SDKPlugin] instances.
 *
 * Internal - managed exclusively by [SDKInitializer].
 */
internal object SDKPluginRegistry {

    private val plugins = mutableMapOf<String, SDKPlugin>()

    /**
     * Registers a plugin. Silently replaces any previously registered plugin
     * with the same [SDKPlugin.id].
     */
    fun register(plugin: SDKPlugin) {
        plugins[plugin.id] = plugin
    }

    /** All registered plugins' Koin modules. */
    val koinModules: List<Module> get() = plugins.values.map { it.koinModule }

    /** Notify all plugins that the SDK was initialized. */
    fun dispatchInitialized() = plugins.values.forEach { it.onSDKInitialized() }

    /** Notify all plugins that the SDK was reset. */
    fun dispatchReset() = plugins.values.forEach { it.onSDKReset() }

    /** Clears all registered plugins - called on [SDKInitializer.reset]. */
    fun clear() = plugins.clear()
}