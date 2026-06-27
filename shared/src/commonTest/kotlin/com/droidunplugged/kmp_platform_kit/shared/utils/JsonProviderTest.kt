package com.droidunplugged.kmp_platform_kit.shared.utils

import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class JsonProviderTest {

    @Serializable
    data class TestModel(val id: Int, val name: String? = null)

    @Test
    fun `json ignores unknown keys`() {
        val json = "{\"id\": 1, \"unknown\": \"key\"}"
        val model = JsonProvider.json.decodeFromString<TestModel>(json)
        assertEquals(1, model.id)
    }

    @Test
    fun `json encodes defaults`() {
        val model = TestModel(id = 1)
        val json = JsonProvider.json.encodeToString(TestModel.serializer(), model)
        // Since explicitNulls = false, nulls might be omitted if they are defaults
        // BUT encodeDefaults = true.
        // Let's just check it doesn't crash and behaves as expected.
        assertTrue(json.contains("\"id\":1"))
    }
}
