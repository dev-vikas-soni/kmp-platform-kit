package com.droidunplugged.kmp_platform_kit.features.catalog.facade

import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.core.BaseFacade
import com.droidunplugged.kmp_platform_kit.features.catalog.models.Product
import com.droidunplugged.kmp_platform_kit.features.catalog.remote.CatalogApi

/**
 * Public entry point for the Catalog feature.
 * 
 * Demonstrates:
 * - KMP shared business logic
 * - Resilience (via the underlying ApiClient)
 * - Swift-friendly async/await (via SKIE)
 */
object CatalogFacade : BaseFacade() {
    override val tag: String = "CatalogFacade"

    private val api: CatalogApi get() = getKoin().get()

    /**
     * Fetch all available products.
     * Demonstrates request deduplication if called rapidly from multiple UI components.
     */
    suspend fun getProducts(): ApiResult<List<Product>> {
        requireInitialized()
        log.d(tag, "Fetching products...")
        return api.getProducts()
    }

    /**
     * Fetch details for a specific product.
     */
    suspend fun getProductDetails(productId: String): ApiResult<Product> {
        requireInitialized()
        require(productId.isNotBlank()) { "productId must not be blank" }
        return api.getProductDetails(productId)
    }
}
