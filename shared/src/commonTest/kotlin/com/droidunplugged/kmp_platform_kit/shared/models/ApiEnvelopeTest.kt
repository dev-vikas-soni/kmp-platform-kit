package com.droidunplugged.kmp_platform_kit.shared.models

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ApiEnvelopeTest {

    @Test
    fun `BaseApiResponse isSuccess returns true for SUCCESS status`() {
        val response = BaseApiResponse(status = "SUCCESS")
        assertTrue(response.isSuccess)
    }

    @Test
    fun `BaseApiResponse isSuccess returns false for other status`() {
        val response = BaseApiResponse(status = "ERROR")
        assertFalse(response.isSuccess)
    }

    @Test
    fun `errorMessage returns first detail message`() {
        val errorInfo = ErrorInfo(
            errorDetails = listOf(
                ErrorDetail(message = "First error"),
                ErrorDetail(message = "Second error")
            )
        )
        val response = BaseApiResponse(status = "ERROR", error = errorInfo)
        assertEquals("First error", response.errorMessage)
    }
}
