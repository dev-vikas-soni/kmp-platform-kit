package com.droidunplugged.kmp_platform_kit.core

import com.droidunplugged.kmp_platform_kit.shared.utils.StaticHeaders
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.get
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.Foundation.NSURLAuthenticationChallenge
import platform.Foundation.NSURLAuthenticationMethodServerTrust
import platform.Foundation.NSURLCredential
import platform.Foundation.NSURLSessionAuthChallengeCancelAuthenticationChallenge
import platform.Foundation.NSURLSessionAuthChallengePerformDefaultHandling
import platform.Foundation.NSURLSessionAuthChallengeUseCredential
import platform.Foundation.credentialForTrust
import platform.Foundation.serverTrust
import platform.Security.SecCertificateCopyData
import platform.Security.SecTrustEvaluateWithError
import platform.Security.SecTrustGetCertificateAtIndex
import platform.Security.SecTrustGetCertificateCount
import platform.Security.SecTrustRef
import platform.UIKit.UIDevice

private const val IOS_TAG = "HttpClientFactory.ios"

/** Request-level timeout (seconds). */
private const val REQUEST_TIMEOUT_SECONDS = 30.0

/** Session-level resource timeout (seconds). */
private const val RESOURCE_TIMEOUT_SECONDS = 300.0

/** Per-request Ktor timeout (milliseconds). */
private const val KTOR_TIMEOUT_MS = 30_000L

/**
 * iOS actual implementation of [createClientImpl].
 *
 * Uses Darwin engine with:
 * - Request/session timeouts
 * - Ktor [HttpTimeout] plugin for per-request timeout override capability
 * - Static headers via [DefaultRequest]
 * - Dynamic headers via [installDynamicHeaders] (shared with Android)
 * - **SSL certificate pinning** via [SDKConfig.sslPins] when configured - enforced through
 *   Darwin's handleChallenge hook validating SHA-256 certificate fingerprints, mirroring
 *   Android's CertificatePinner behaviour.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun createClientImpl(): HttpClient {
    val client = HttpClient(Darwin) {
        engine {
            configureRequest {
                setTimeoutInterval(REQUEST_TIMEOUT_SECONDS)
            }
            configureSession {
                setTimeoutIntervalForRequest(REQUEST_TIMEOUT_SECONDS)
                setTimeoutIntervalForResource(RESOURCE_TIMEOUT_SECONDS)
            }

            // SSL Certificate Pinning: enforced only when SDKConfig.sslPins is set
            SDKConfig.sslPins?.let { pinConfig ->
                handleChallenge { _, _, challenge, completionHandler ->
                    applySslPinning(challenge, pinConfig, completionHandler)
                }
            }
        }

        install(HttpTimeout) {
            requestTimeoutMillis = KTOR_TIMEOUT_MS
            connectTimeoutMillis = KTOR_TIMEOUT_MS
            socketTimeoutMillis = KTOR_TIMEOUT_MS
        }

        install(DefaultRequest) {
            header(StaticHeaders.PLATFORM, "ios")
            val osVersion = UIDevice.currentDevice.systemVersion
            val model = UIDevice.currentDevice.model
            header(
                StaticHeaders.USER_AGENT,
                "${SDKInfo.fullName} (iOS; $osVersion; $model)"
            )
            header(StaticHeaders.EXTERNAL_SOURCE, StaticHeaders.EXTERNAL_SOURCE_VALUE)
            header(StaticHeaders.ACCEPT, StaticHeaders.ACCEPT_VALUE)
        }
    }
    installDynamicHeaders(client)
    return client
}

// ── SSL Pinning ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalForeignApi::class)
@Suppress("UNCHECKED_CAST", "CyclomaticComplexMethod", "LoopWithTooManyJumpStatements")
private fun applySslPinning(
    challenge: NSURLAuthenticationChallenge,
    pinConfig: SslPinConfig,
    completionHandler: (Long, NSURLCredential?) -> Unit
) {
    val method = challenge.protectionSpace.authenticationMethod
    if (method != NSURLAuthenticationMethodServerTrust) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling.toLong(), null)
        return
    }
    val host = challenge.protectionSpace.host
    if (host != pinConfig.hostname) {
        completionHandler(NSURLSessionAuthChallengePerformDefaultHandling.toLong(), null)
        return
    }
    val rawTrust = challenge.protectionSpace.serverTrust
    if (rawTrust == null) {
        PlatformLogger.get().e(IOS_TAG, "SSL pinning: null serverTrust for $host")
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge.toLong(), null)
        return
    }
    val trustRef = rawTrust as? SecTrustRef
    if (trustRef != null && !SecTrustEvaluateWithError(trustRef, null)) {
        PlatformLogger.get().e(IOS_TAG, "SSL pinning: trust evaluation failed for $host")
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge.toLong(), null)
        return
    }
    var pinMatched = false
    if (trustRef != null) {
        val certCount = SecTrustGetCertificateCount(trustRef)
        for (i in 0 until certCount) {
            val cert = SecTrustGetCertificateAtIndex(trustRef, i) ?: continue
            val certData = SecCertificateCopyData(cert) ?: continue
            val length = CFDataGetLength(certData).toInt()
            val bytePtr = CFDataGetBytePtr(certData) ?: continue
            val derBytes = ByteArray(length) { idx -> bytePtr[idx].toByte() }
            val b64pin = "sha256/${base64Encode(sha256(derBytes))}"
            if (pinConfig.pins.contains(b64pin)) {
                pinMatched = true; break
            }
        }
    }
    if (pinMatched) {
        PlatformLogger.get().d(IOS_TAG, "SSL pinning: pin matched for $host")
        completionHandler(
            NSURLSessionAuthChallengeUseCredential.toLong(),
            NSURLCredential.credentialForTrust(rawTrust)
        )
    } else {
        PlatformLogger.get().e(IOS_TAG, "SSL pinning: NO matching pin for $host")
        completionHandler(NSURLSessionAuthChallengeCancelAuthenticationChallenge.toLong(), null)
    }
}

// ── Crypto helpers (CommonCrypto via cinterop - zero external deps) ───────────

/**
 * Computes the raw SHA-256 digest of [input] using Apple's CommonCrypto framework.
 */
@OptIn(ExperimentalForeignApi::class)
private fun sha256(input: ByteArray): ByteArray {
    val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
    input.usePinned { pinnedInput ->
        digest.usePinned { pinnedDigest ->
            CC_SHA256(pinnedInput.addressOf(0), input.size.toUInt(), pinnedDigest.addressOf(0))
        }
    }
    return ByteArray(CC_SHA256_DIGEST_LENGTH) { i -> digest[i].toByte() }
}

private const val BASE64_CHARS =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"

/**
 * Standard Base64 encoder (RFC 4648) - used to format pin strings as
 * `sha256/<base64>` matching the Android CertificatePinner format.
 */
@Suppress("MagicNumber") // Bit-shift constants inherent to RFC 4648 Base64 encoding
private fun base64Encode(data: ByteArray): String {
    val sb = StringBuilder()
    var i = 0
    while (i < data.size) {
        val b0 = data[i].toInt() and 0xFF
        val b1 = if (i + 1 < data.size) data[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < data.size) data[i + 2].toInt() and 0xFF else 0
        sb.append(BASE64_CHARS[b0 shr 2])
        sb.append(BASE64_CHARS[(b0 and 0x3) shl 4 or (b1 shr 4)])
        sb.append(if (i + 1 < data.size) BASE64_CHARS[(b1 and 0xF) shl 2 or (b2 shr 6)] else '=')
        sb.append(if (i + 2 < data.size) BASE64_CHARS[b2 and 0x3F] else '=')
        i += 3
    }
    return sb.toString()
}