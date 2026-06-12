package com.droidunplugged.kmp_platform_kit.core

import org.koin.core.module.Module
import org.koin.dsl.module

interface SDKPlugin {
    val id: String
    val koinModule: Module? get() = null
    fun onSDKInitialized() {}
    fun onSDKReset() {}
}
