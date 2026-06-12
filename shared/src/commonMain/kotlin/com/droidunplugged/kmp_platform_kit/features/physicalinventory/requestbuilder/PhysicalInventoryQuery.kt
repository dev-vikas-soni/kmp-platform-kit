package com.droidunplugged.kmp_platform_kit.features.physicalinventory.requestbuilder

import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.PhysicalInventoryFilter

/**
 * Converts a [PhysicalInventoryFilter] into the query-parameter map expected
 * by [com.droidunplugged.kmp_platform_kit.core.ApiClient.get].
 *
 * Only non-null / non-default values are included, keeping the HTTP URL
 * clean and avoiding server-side validation errors from empty parameters.
 */
object PhysicalInventoryQuery {

    /**
     * Builds the query-param map for a list/search call.
     */
    fun fromFilter(filter: PhysicalInventoryFilter): Map<String, String> =
        buildMap {
            filter.facilityCode?.let { put("facilityCode", it) }
            filter.itemNumber?.let   { put("itemNumber", it) }
            filter.lotNumber?.let    { put("lotNumber", it) }
            filter.locationCode?.let { put("locationCode", it) }
            put("pageNumber", filter.pageNumber.toString())
            put("pageSize",   filter.pageSize.toString())
        }

    /**
     * Minimal map for fetching a single item — just the item number path
     * param is embedded in the URL, so no query params are required here.
     */
    fun forItemDetail(): Map<String, String> = emptyMap()
}