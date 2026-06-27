package com.droidunplugged.kmp_platform_kit.features.catalog.di

import com.droidunplugged.kmp_platform_kit.features.catalog.remote.CatalogApi
import org.koin.dsl.module

val catalogModule = module {
    single { CatalogApi(get()) }
}
