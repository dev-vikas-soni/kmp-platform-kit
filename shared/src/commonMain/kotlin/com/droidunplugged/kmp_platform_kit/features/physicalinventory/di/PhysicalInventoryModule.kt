package com.droidunplugged.kmp_platform_kit.features.physicalinventory.di

import com.droidunplugged.kmp_platform_kit.features.physicalinventory.facade.AppFacadePhysicalInventory
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.repository.PhysicalInventoryRepository
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.repository.PhysicalInventoryRepositoryImpl
import org.koin.dsl.module

/**
 * Koin module for the Physical Inventory feature.
 *
 * Wire this into your Koin application with:
 * ```kotlin
 * startKoin {
 *     modules(coreModule, physicalInventoryModule)
 * }
 * ```
 *
 * Or register it via [com.droidunplugged.kmp_platform_kit.core.SDKPlugin] to keep
 * bootstrapping decoupled from the app's DI graph.
 */
val physicalInventoryModule = module {

    /**
     * Repository – internal implementation, exposed via the interface so
     * test doubles can be swapped in without touching the facade.
     */
    single<PhysicalInventoryRepository> {
        PhysicalInventoryRepositoryImpl(apiClient = get())
    }

    /**
     * Public facade – the only surface that host apps should reference.
     */
    single {
        AppFacadePhysicalInventory(repository = get())
    }
}