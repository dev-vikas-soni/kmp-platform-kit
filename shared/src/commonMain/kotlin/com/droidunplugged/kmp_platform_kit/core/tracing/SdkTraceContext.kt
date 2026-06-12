package com.droidunplugged.kmp_platform_kit.core.tracing

import com.benasher44.uuid.uuid4
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header

object SdkTraceContext {
    fun injectTracingHeaders(request: HttpRequestBuilder) {
        val traceId = uuid4().toString().replace("-", "")
        val spanId = uuid4().toString().replace("-", "").take(16)
        
        request.header("traceparent", "00-$traceId-$spanId-01")
        request.header("x-b3-traceid", traceId)
        request.header("x-b3-spanid", spanId)
        request.header("x-request-id", spanId)
    }
}
