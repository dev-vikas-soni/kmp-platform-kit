package com.droidunplugged.kmp_platform_kit.core.paging

import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.shared.models.PaginationInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class SdkPagerTest {

    @Test
    fun `initial state is Idle`() = runTest {
        val pager = SdkPager<String>(coroutineScope = this) { _, _ -> ApiResult.NetworkError }
        assertEquals(PagerState.Idle, pager.state.value)
    }

    @Test
    fun `loadNextPage success updates state to Success then Complete`() = runTest {
        var callCount = 0
        val pager = SdkPager<String>(pageSize = 2, coroutineScope = this) { page, _ ->
            callCount++
            val items = listOf("item${page * 2}", "item${page * 2 + 1}")
            val hasNext = page < 1
            ApiResult.Success(
                Page(
                    items = items,
                    pagination = PaginationInfo(
                        currentPage = page,
                        hasNext = hasNext
                    )
                )
            )
        }

        // Load first page
        pager.loadNextPage()
        advanceUntilIdle()

        val state1 = pager.state.value
        assertIs<PagerState.Success<String>>(state1)
        assertEquals(listOf("item0", "item1"), state1.items)
        assertTrue(state1.pagination.hasNext)

        // Load second page
        pager.loadNextPage()
        advanceUntilIdle()

        val state2 = pager.state.value
        assertIs<PagerState.Complete<String>>(state2)
        assertEquals(listOf("item0", "item1", "item2", "item3"), state2.items)
        assertEquals(2, callCount)
    }

    @Test
    fun `loadNextPage failure updates state to Error`() = runTest {
        val pager = SdkPager<String>(coroutineScope = this) { _, _ ->
            ApiResult.Failure(500, "Server Error")
        }

        pager.loadNextPage()
        advanceUntilIdle()

        val state = pager.state.value
        assertIs<PagerState.Error<String>>(state)
        assertEquals("Server Error", state.error)
    }

    @Test
    fun `refresh resets pager state`() = runTest {
        var callCount = 0
        val pager = SdkPager<String>(coroutineScope = this) { page, _ ->
            callCount++
            ApiResult.Success(
                Page(
                    items = listOf("item$page"),
                    pagination = PaginationInfo(hasNext = true)
                )
            )
        }

        pager.loadNextPage()
        advanceUntilIdle()
        assertEquals(1, callCount)

        pager.refresh()
        advanceUntilIdle()

        // refresh calls loadNextPage after resetting, so callCount should be 2
        // and allItems should only contain the new first page result
        assertEquals(2, callCount)
        val state = pager.state.value
        assertIs<PagerState.Success<String>>(state)
        assertEquals(listOf("item0"), state.items)
    }
}
