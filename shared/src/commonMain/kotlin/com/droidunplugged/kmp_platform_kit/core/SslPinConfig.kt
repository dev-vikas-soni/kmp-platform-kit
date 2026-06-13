package com.droidunplugged.kmp_platform_kit.core

/**
 * SSL Certificate Pinning configuration.
 *
 * Pass to [SDKInitializer.init] via [SDKConfig.sslPins] to enable certificate
 * pinning on Android (OkHttp CertificatePinner) and iOS (TLS challenge validation).
 *
 * When enabled, SDK requests fail fast if the server presents a certificate chain
 * whose public key does not match one of the configured [pins]. This helps protect
 * against mis-issued certificates and man-in-the-middle attacks.
 *
 * **How to get SHA-256 pins:**
 * ```bash
 * # Using openssl against your API server
 * openssl s_client -connect api.cardinalhealth.com:443 | \
 *   openssl x509 -pubkey -noout | openssl pkey -pubin -outform der | \
 *   openssl dgst -sha256 -binary | openssl enc -base64
 * ```
 *
 * Always include **at least two pins** (primary + backup) to prevent lockout
 * during certificate rotation.
 *
 * ```kotlin
 * SDKConfig.sslPins = SslPinConfig(
 *     hostname = "api.cardinalhealth.com",
 *     pins = listOf(
 *         "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",  // primary
 *         "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB="   // backup
 *     )
 * )
 * ```
 *
 * @property hostname The hostname to pin (e.g. `"api.cardinalhealth.com"`).
 * @property pins     List of `sha256/<base64>` formatted public-key pins.
 * @throws IllegalArgumentException if [hostname] is blank or any pin does not start with `sha256/`.
 */
data class SslPinConfig(
    val hostname: String,
    val pins: List<String>
) {
    init {
        require(hostname.isNotBlank()) { "hostname must not be blank" }
        require(pins.isNotEmpty()) { "at least one pin is required" }
        pins.forEach { pin ->
            require(pin.startsWith("sha256/")) {
                "Each pin must start with 'sha256/'. Got: $pin"
            }
        }
    }
}