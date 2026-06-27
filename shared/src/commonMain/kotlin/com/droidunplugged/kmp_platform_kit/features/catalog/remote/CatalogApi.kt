package com.droidunplugged.kmp_platform_kit.features.catalog.remote

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.features.catalog.models.Product
import com.droidunplugged.kmp_platform_kit.shared.utils.JsonProvider
import kotlinx.serialization.builtins.ListSerializer

open class CatalogApi(private val apiClient: ApiClient) {
    open suspend fun getProducts(): ApiResult<List<Product>> {
        return apiClient.get(
            path = "products",
            responseParser = { json ->
                JsonProvider.json.decodeFromString(ListSerializer(Product.serializer()), json)
            }
        )
    }

    open suspend fun getProductDetails(id: String): ApiResult<Product> {
        return apiClient.get(
            path = "products/$id",
            responseParser = { json ->
                JsonProvider.json.decodeFromString(Product.serializer(), json)
            }
        )
    }
}
