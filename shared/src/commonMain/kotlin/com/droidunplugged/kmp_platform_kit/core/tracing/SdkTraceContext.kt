package com.droidunplugged.kmp_platform_kit.core.tracing

import kotlin.random.Random

/**
 * Lightweight distributed trace context injected into every SDK HTTP request.
 *
 * Propagates W3C Trace Context headers (`traceparent`) so that mobile-initiated
 * requests can be correlated with backend logs end-to-end.
 *
 * When a bug is reported, the `traceId` in SDK logs lets backend engineers find
 * the exact server-side span in Datadog/Jaeger/CloudWatch instantly.
 *
 * ## Headers injected automatically
 * | Header         | Format                                    | Standard       |
 * |----------------|-------------------------------------------|----------------|
 * | `traceparent`  | `00-{traceId}-{spanId}-01`                | W3C Trace Context |
 * | `x-b3-traceid` | `{traceId}`                               | B3 (legacy)    |
 * | `x-b3-spanid`  | `{spanId}`                                | B3 (legacy)    |
 *
 * ## Usage
 * ```kotlin
 * // Automatically injected - no host app action needed.
 * // Access the current trace ID for logging:
 * val traceId = SdkTraceContext.current().traceId
 * log.i(TAG, "Starting request [traceId=$traceId]")
 * ```
 */
data class SdkTraceContext(
    /** 128-bit trace ID (hex string, 32 chars). Groups all spans for one user action. */
    val traceId: String,
    /** 64-bit span ID (hex string, 16 chars). Identifies this specific SDK call. */
    val spanId: String,
    /** Parent span ID - set when this request is part of a larger trace. */
    val parentSpanId: String? = null
) {
    /**
     * W3C `traceparent` header value.
     * Format: `version-traceId-spanId-flags`
     * Example: `00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01`
     */
    val w3cTraceparent: String get() = "00-$traceId-$spanId-01"

    /**
     * Generate a child context for a sub-operation (e.g. a cache lookup within a request).
     *
     * The returned context keeps the same [traceId] but generates a new [spanId],
     * setting the current [spanId] as [parentSpanId].
     */
    fun child(): SdkTraceContext = SdkTraceContext(
        traceId = traceId,
        spanId = generateId(bits = 64),
        parentSpanId = spanId
    )

    companion object {
        /** Bits per byte - used when converting bit-width to byte count. */
        private const val BITS_PER_BYTE = 8

        /** Bit width of a single random byte for hex formatting. */
        private const val BYTE_BIT_WIDTH = 8

        /** Bitmask to extract the low byte from an Int. */
        private const val BYTE_MASK = 0xFF

        /** Width of each hex byte representation (e.g. "0a" = 2 chars). */
        private const val HEX_PAD_WIDTH = 2

        /** Hex radix for toString conversion. */
        private const val HEX_RADIX = 16

        /**
         * Generate a new root trace context (no parent).
         * Called at the start of each facade method invocation.
         *
         * Use this when beginning a new trace tree for a user-driven action such as
         * opening a screen, tapping refresh, or submitting an update request.
         */
        fun new(): SdkTraceContext = SdkTraceContext(
            traceId = generateId(bits = 128),
            spanId = generateId(bits = 64)
        )

        private fun generateId(bits: Int): String {
            val bytes = bits / BITS_PER_BYTE
            return buildString {
                repeat(bytes) {
                    val byte = Random.nextBits(BYTE_BIT_WIDTH).and(BYTE_MASK)
                    append(byte.toString(HEX_RADIX).padStart(HEX_PAD_WIDTH, '0'))
                }
            }
        }
    }
}

/**
 * Trace context headers injected into HTTP requests.
 * These are added to every outgoing request by the SDK's header interceptor.
 */
internal object TraceHeaders {
    const val TRACEPARENT = "traceparent"
    const val B3_TRACE_ID = "x-b3-traceid"
    const val B3_SPAN_ID = "x-b3-spanid"
    const val B3_PARENT_SPAN_ID = "x-b3-parentspanid"
    const val REQUEST_ID = "x-request-id"  // convenience alias for older backends
}