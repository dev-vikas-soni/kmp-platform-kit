package com.droidunplugged.kmp_platform_kit.core


/**
 * Thread-safe runtime header store, split by lifecycle:
 *
 * **Dynamic headers** - change frequently (token refresh, new login):
 *   `authorization`, `x-cah-api-guid`
 *
 * **Env-specific headers** - fixed for the session:
 *   `clientid`, `x-api-key`
 *
 * [getHeader] checks dynamic first, then env-specific.
 * HttpClient DefaultRequest calls this on every request.
 *
 * Implement actuals in androidMain/iosMain with @Volatile maps.
 */
expect object PlatformConfig {

    /** Store dynamic headers (authorization, x-cah-api-guid). */
    fun setDynamicHeaders(map: Map<String, String>)

    /** Store env-specific headers (clientid, x-api-key). Set once at init. */
    fun setEnvHeaders(map: Map<String, String>)

    /**
     * Update only the auth token - does not touch guid or env headers.
     * Call from the host app's token-refresh callback (~every 15 min).
     */
    fun updateAuthToken(token: String)

    /** Look up a header: dynamic first, then env-specific. */
    fun getHeader(key: String): String?
}