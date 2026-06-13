package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.ALL_REQUEST_HEADER_KEYS
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpSend
import io.ktor.client.plugins.plugin

/**
 * Installs the dynamic-header injection interceptor on [client].
 *
 * Called by **both** platform [createClientImpl] actuals - single source of truth
 * for the header-injection logic that previously was copy-pasted verbatim.
 *
 * Reads [PlatformConfig] on **every** outgoing request so headers are always
 * current (token refresh, GUID rotation). The `HttpSend` interceptor runs
 * **after** `DefaultRequest`, so it can overwrite static defaults with live values.
 */
internal fun installDynamicHeaders(client: HttpClient) {
    client.plugin(HttpSend).intercept { request ->
        ALL_REQUEST_HEADER_KEYS.forEach { key ->
            PlatformConfig.getHeader(key)?.let { value ->
                request.headers.remove(key)
                request.headers.append(key, value)
            }
        }
        execute(request)
    }
}