package com.droidunplugged.kmp_platform_kit.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BaseApiResponseTest {

    @Test
    fun `isSuccess is true for SUCCESS status`() {
        val response = BaseApiResponse(status = "SUCCESS")
        assertTrue(response.isSuccess)
    }

    @Test
    fun `isSuccess is true for lowercase success status`() {
        val response = BaseApiResponse(status = "success")
        assertTrue(response.isSuccess)
    }

    @Test
    fun `isSuccess is false for ERROR status`() {
        val response = BaseApiResponse(status = "ERROR")
        assertFalse(response.isSuccess)
    }

    @Test
    fun `errorMessage returns first error detail message`() {
        val response = BaseApiResponse(
            status = "ERROR",
            error = ErrorInfo(
                errorDetails = listOf(
                    ErrorDetail("1", "First error"),
                    ErrorDetail("2", "Second error")
                )
            )
        )
        assertEquals("First error", response.errorMessage)
    }

    @Test
    fun `errorMessage returns null if no error info`() {
        val response = BaseApiResponse(status = "SUCCESS")
        assertEquals(null, response.errorMessage)
    }
}
