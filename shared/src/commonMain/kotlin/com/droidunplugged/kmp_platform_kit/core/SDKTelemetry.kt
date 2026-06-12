package com.droidunplugged.kmp_platform_kit.core

interface SDKTelemetry {
    fun recordApiCall(endpoint: String, durationMs: Long, statusCode: Int, retries: Int)
    fun recordError(type: String, endpoint: String?, message: String)
    fun recordSdkEvent(event: SDKEvent, detail: String? = null)
}

enum class SDKEvent {
    INITIALIZED, RESET, TOKEN_REFRESHED, FEATURE_CALLED
}

object NoOpTelemetry : SDKTelemetry {
    override fun recordApiCall(endpoint: String, durationMs: Long, statusCode: Int, retries: Int) {}
    override fun recordError(type: String, endpoint: String?, message: String) {}
    override fun recordSdkEvent(event: SDKEvent, detail: String?) {}
}
