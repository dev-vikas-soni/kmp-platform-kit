package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger

abstract class BaseFacade {
    protected abstract val tag: String

    protected fun requireInitialized() {
        SDKInitializer.requireInitialized()
    }

    protected fun logCall(methodName: String) {
        PlatformLogger.d(tag, "Calling $methodName")
    }
}
