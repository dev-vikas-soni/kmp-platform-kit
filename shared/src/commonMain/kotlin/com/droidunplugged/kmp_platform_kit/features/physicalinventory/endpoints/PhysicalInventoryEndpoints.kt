package com.droidunplugged.kmp_platform_kit.features.physicalinventory.endpoints

/**
 * Centralizes all physical inventory API endpoint paths.
 *
 * Using a sealed class instead of loose string constants ensures
 * compile-time safety and makes it trivial to discover every endpoint
 * the feature touches.
 */
sealed class PhysicalInventoryEndpoints {

    /** Base path prefix shared by all endpoints in this feature. */
    private val basePath = "/api/v1/physicalinventory"

    /** List / search physical inventory (paged). */
    object List : PhysicalInventoryEndpoints() {
        val path = "/api/v1/physicalinventory/items"
    }

    /** Retrieve a single inventory item by its [itemNumber]. */
    data class ItemDetail(val itemNumber: String) : PhysicalInventoryEndpoints() {
        val path = "/api/v1/physicalinventory/items/$itemNumber"
    }

    /** Submit an inventory count / adjustment for an item. */
    object SubmitCount : PhysicalInventoryEndpoints() {
        val path = "/api/v1/physicalinventory/counts"
    }
}