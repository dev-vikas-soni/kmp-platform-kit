package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin
import io.ktor.client.statement.bodyAsText

/**
 * Installs a debug-level request/response logging interceptor on [httpClient].
 *
 * Only active when [SDKConfig.debugMode] is `true`.
 *
 * **Security:** Request bodies and response bodies are truncated to [MAX_BODY_LOG_CHARS]
 * characters. Authorization headers are redacted.
 *
 * Call this after all other interceptors have been installed.
 */
internal fun installDebugLogging(httpClient: HttpClient) {
    val log = PlatformLogger.get()

    httpClient.plugin(HttpSend).intercept { request ->
        if (!SDKConfig.debugMode) {
            return@intercept execute(request)
        }

        // Log request
        val method = request.method.value
        val url = request.url.buildString()
        log.d(TAG, "┌── $method $url")

        request.headers.entries().forEach { (key, values) ->
            val safeValue = if (key.equals("authorization", ignoreCase = true))
                "***REDACTED***" else values.joinToString()
            log.d(TAG, "│ $key: $safeValue")
        }

        val body = request.body.toString()
        if (body.isNotBlank() && body != "EmptyContent") {
            log.d(TAG, "│ Body: ${body.take(MAX_BODY_LOG_CHARS)}")
        }

        // Execute
        val call = execute(request)
        val response = call.response
        val responseBody = response.bodyAsText()

        // Log response
        log.d(TAG, "├── ${response.status}")
        log.d(TAG, "│ Body: ${responseBody.take(MAX_BODY_LOG_CHARS)}")
        log.d(TAG, "└──────────────────────────────────")

        call
    }
}

private const val TAG = "HTTP"
private const val MAX_BODY_LOG_CHARS = 2048