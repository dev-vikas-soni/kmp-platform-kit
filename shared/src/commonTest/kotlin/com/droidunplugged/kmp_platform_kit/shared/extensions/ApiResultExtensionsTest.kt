package com.droidunplugged.kmp_platform_kit.shared.extensions

import com.droidunplugged.kmp_platform_kit.core.ApiResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ApiResultExtensionsTest {

    @Test
    fun `map transforms Success data`() {
        val result = ApiResult.Success(10)
        val mapped = result.map { it * 2 }
        assertIs<ApiResult.Success<Int>>(mapped)
        assertEquals(20, mapped.data)
    }

    @Test
    fun `map preserves Failure`() {
        val result = ApiResult.Failure(404, "Not Found")
        val mapped = result.map { it.toString() }
        assertIs<ApiResult.Failure>(mapped)
        assertEquals(404, mapped.code)
    }

    @Test
    fun `flatMap transforms Success to another ApiResult`() {
        val result = ApiResult.Success(10)
        val flattened = result.flatMap { ApiResult.Success(it.toString()) }
        assertIs<ApiResult.Success<String>>(flattened)
        assertEquals("10", flattened.data)
    }

    @Test
    fun `flatMap can transform Success to Failure`() {
        val result = ApiResult.Success(10)
        val flattened = result.flatMap { ApiResult.Failure(500, "Error") }
        assertIs<ApiResult.Failure>(flattened)
        assertEquals(500, flattened.code)
    }
}
