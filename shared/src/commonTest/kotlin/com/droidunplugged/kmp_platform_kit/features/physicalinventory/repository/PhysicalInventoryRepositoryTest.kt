package com.droidunplugged.kmp_platform_kit.features.physicalinventory.repository

import com.droidunplugged.kmp_platform_kit.core.ApiClient
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.core.CachePolicy
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryItem
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.InventoryListPayload
import com.droidunplugged.kmp_platform_kit.features.physicalinventory.models.PhysicalInventoryFilter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class PhysicalInventoryRepositoryTest {

    // ─── Fake ApiClient ───────────────────────────────────────────────────────

    private class FakeApiClient(
        private val getResponse: ApiResult<Any> = ApiResult.NetworkError
    ) : ApiClient {
        var lastPath: String? = null
        var lastQueryParams: Map<String, String>? = null
        var lastCachePolicy: CachePolicy? = null

        @Suppress("UNCHECKED_CAST")
        override suspend fun <T : Any> get(
            path: String,
            queryParams: Map<String, String>,
            cachePolicy: CachePolicy
        ): ApiResult<T> {
            lastPath = path
            lastQueryParams = queryParams
            lastCachePolicy = cachePolicy
            return getResponse as ApiResult<T>
        }

        override suspend fun <T : Any, R : Any> post(
            path: String,
            body: T,
            queryParams: Map<String, String>
        ): ApiResult<R> = ApiResult.NetworkError
    }

    private fun fakeItem() = InventoryItem(
        itemNumber     = "ITM-001",
        itemDescription = "Test Item",
        facilityCode   = "FAC-01",
        quantityOnHand = 10.0,
        unitOfMeasure  = "EA"
    )

    private fun fakeList() = InventoryListPayload(items = listOf(fakeItem()))

    // ─── getInventoryList ─────────────────────────────────────────────────────

    @Test
    fun `getInventoryList calls correct endpoint`() = runTest {
        val client = FakeApiClient(ApiResult.Success(fakeList()))
        val repo = PhysicalInventoryRepositoryImpl(client)

        repo.getInventoryList(PhysicalInventoryFilter(facilityCode = "FAC-01"))

        assertEquals("/api/v1/physicalinventory/items", client.lastPath)
    }

    @Test
    fun `getInventoryList passes filter as query params`() = runTest {
        val client = FakeApiClient(ApiResult.Success(fakeList()))
        val repo = PhysicalInventoryRepositoryImpl(client)

        repo.getInventoryList(PhysicalInventoryFilter(facilityCode = "FAC-01", pageSize = 10))

        assertEquals("FAC-01", client.lastQueryParams?.get("facilityCode"))
        assertEquals("10", client.lastQueryParams?.get("pageSize"))
    }

    @Test
    fun `getInventoryList returns Success when client succeeds`() = runTest {
        val payload = fakeList()
        val client = FakeApiClient(ApiResult.Success(payload))
        val repo = PhysicalInventoryRepositoryImpl(client)

        val result = repo.getInventoryList(PhysicalInventoryFilter())

        assertIs<ApiResult.Success<InventoryListPayload>>(result)
        assertEquals(1, result.data.items.size)
    }

    @Test
    fun `getInventoryList propagates NetworkError`() = runTest {
        val client = FakeApiClient(ApiResult.NetworkError)
        val repo = PhysicalInventoryRepositoryImpl(client)

        val result = repo.getInventoryList(PhysicalInventoryFilter())

        assertIs<ApiResult.NetworkError>(result)
    }

    // ─── getInventoryItem ─────────────────────────────────────────────────────

    @Test
    fun `getInventoryItem calls correct endpoint with item number`() = runTest {
        val client = FakeApiClient(ApiResult.Success(fakeItem()))
        val repo = PhysicalInventoryRepositoryImpl(client)

        repo.getInventoryItem("ITM-001")

        assertEquals("/api/v1/physicalinventory/items/ITM-001", client.lastPath)
    }

    @Test
    fun `getInventoryItem uses CACHE_FIRST_5MIN policy`() = runTest {
        val client = FakeApiClient(ApiResult.Success(fakeItem()))
        val repo = PhysicalInventoryRepositoryImpl(client)

        repo.getInventoryItem("ITM-001")

        assertEquals(CachePolicy.CACHE_FIRST_5MIN, client.lastCachePolicy)
    }

    @Test
    fun `getInventoryItem returns Success with correct item`() = runTest {
        val item = fakeItem()
        val client = FakeApiClient(ApiResult.Success(item))
        val repo = PhysicalInventoryRepositoryImpl(client)

        val result = repo.getInventoryItem("ITM-001")

        assertIs<ApiResult.Success<InventoryItem>>(result)
        assertEquals("ITM-001", result.data.itemNumber)
    }
}
