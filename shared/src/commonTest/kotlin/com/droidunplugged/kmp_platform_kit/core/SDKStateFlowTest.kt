package com.droidunplugged.kmp_platform_kit.core

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class SDKStateFlowTest {

    @Test
    fun `toSDKState maps Success correctly`() {
        val result = ApiResult.Success("data")
        val state = result.toSDKState()
        assertIs<SDKState.Success<String>>(state)
        assertEquals("data", state.data)
    }

    @Test
    fun `toSDKState maps Failure correctly`() {
        val result = ApiResult.Failure(404, "Not Found")
        val state = result.toSDKState()
        assertIs<SDKState.ErrorBody>(state)
        assertEquals(404, state.code)
        assertEquals("Not Found", state.message)
    }

    @Test
    fun `toSDKState maps NetworkError correctly`() {
        val result = ApiResult.NetworkError
        val state = result.toSDKState()
        assertIs<SDKState.Error>(state)
        assertEquals(SDKErrorCode.NETWORK_ERROR, state.message)
        assertEquals(true, state.isNetworkError)
    }

    @Test
    fun `toSDKState maps Cancelled correctly`() {
        val result = ApiResult.Cancelled
        val state = result.toSDKState()
        assertIs<SDKState.Error>(state)
        assertEquals(SDKErrorCode.REQUEST_CANCELLED, state.message)
    }

    @Test
    fun `sdkStateFlow emits Loading then Success`() = runTest {
        val flow = sdkStateFlow { ApiResult.Success("ok") }
        val states = flow.toList()

        assertEquals(2, states.size)
        assertIs<SDKState.Loading>(states[0])
        assertIs<SDKState.Success<String>>(states[1])
        assertEquals("ok", (states[1] as SDKState.Success).data)
    }

    @Test
    fun `sdkStateFlow emits Loading then Error on exception`() = runTest {
        val flow = sdkStateFlow<String> { throw RuntimeException("boom") }
        val states = flow.toList()

        assertEquals(2, states.size)
        assertIs<SDKState.Loading>(states[0])
        assertIs<SDKState.Error>(states[1])
        assertEquals(SDKErrorCode.UNEXPECTED_ERROR, (states[1] as SDKState.Error).message)
    }
}
