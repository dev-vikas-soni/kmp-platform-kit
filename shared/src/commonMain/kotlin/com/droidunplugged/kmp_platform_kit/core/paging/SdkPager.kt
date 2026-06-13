package com.droidunplugged.kmp_platform_kit.core.paging
import com.droidunplugged.kmp_platform_kit.core.ApiResult
import com.droidunplugged.kmp_platform_kit.shared.models.PaginationInfo
import com.droidunplugged.kmp_platform_kit.shared.utils.PlatformLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// ── Page model ────────────────────────────────────────────────────────────────

/**
 * A single page of results with attached [PaginationInfo].
 *
 * @param T     The item type in this page.
 * @property items      Items for this page.
 * @property pagination Metadata about the full result set.
 */
data class Page<T>(
    val items: List<T>,
    val pagination: PaginationInfo
)

// ── Pager state ───────────────────────────────────────────────────────────────

/**
 * State emitted by [SdkPager].
 *
 * @param T The item type.
 */
sealed class PagerState<out T> {
    /** No load has been triggered yet. */
    data object Idle : PagerState<Nothing>()

    /**
     * Initial page load in progress (no data yet).
     * Show a full-screen loader.
     */
    data object LoadingInitial : PagerState<Nothing>()

    /**
     * A subsequent page is loading.
     * Show a footer spinner while [items] remains visible.
     *
     * @property items All items accumulated so far (do not clear these).
     */
    data class LoadingMore<T>(val items: List<T>) : PagerState<T>()

    /**
     * Data is available. No load is in progress.
     *
     * @property items      All accumulated items (all pages loaded so far).
     * @property pagination Pagination metadata for the latest loaded page.
     */
    data class Success<T>(
        val items: List<T>,
        val pagination: PaginationInfo
    ) : PagerState<T>()

    /**
     * All pages have been loaded - [items] is the complete list.
     *
     * @property items All items across all pages.
     */
    data class Complete<T>(val items: List<T>) : PagerState<T>()

    /**
     * A page load failed.
     *
     * @property error      Human-readable error message.
     * @property items      Items loaded so far (if any) - retain so the user can retry.
     * @property isNetworkError `true` to show "check your connection" UI.
     */
    data class Error<T>(
        val error: String,
        val items: List<T> = emptyList(),
        val isNetworkError: Boolean = false
    ) : PagerState<T>()
}

// ── Pager ─────────────────────────────────────────────────────────────────────

/**
 * Multiplatform, coroutine-based pagination engine for the KMP Platform Kit.
 *
 * Supports:
 * - **Offset pagination** - `loadNextPage()` increments `currentPage` automatically.
 * - **Infinite scroll** - call `loadNextPage()` when the user reaches the list end.
 * - **Refresh** - `refresh()` resets to page 0 and reloads.
 * - **State as Flow** - `state` is a [StateFlow] that drives UI reactively.
 *
 * ## Android (Compose) Usage
 * ```kotlin
 * val pager = AppFacadePhysicalInventory.createInventoryPager(customerNo = "2057192797")
 *
 * val state by pager.state.collectAsStateWithLifecycle()
 * when (state) {
 *     is PagerState.LoadingInitial -> FullScreenLoader()
 *     is PagerState.Success        -> InventoryList(state.items, onEndReached = pager::loadNextPage)
 *     is PagerState.LoadingMore    -> InventoryList(state.items) + FooterSpinner()
 *     is PagerState.Complete       -> InventoryList(state.items) + EndOfListBanner()
 *     is PagerState.Error          -> ErrorScreen(state.error, onRetry = pager::loadNextPage)
 *     else                         -> {}
 * }
 * ```
 *
 * ## iOS (Swift) Usage via SKIE
 * ```swift
 * for await state in pager.state {
 *     switch state {
 *     case let success as PagerState.Success<InventoryListModel>:
 *         render(success.items)
 *     ...
 *     }
 * }
 * ```
 *
 * @param T         Item type.
 * @param pageSize  Number of items per page.
 * @param loader    Suspend lambda that fetches a single page. Receives `(page: Int, pageSize: Int)`.
 */
class SdkPager<T>(
    private val pageSize: Int = 20,
    private val loader: suspend (page: Int, pageSize: Int) -> ApiResult<Page<T>>
) {
    private val tag = "SdkPager"
    private val log get() = PlatformLogger.get()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val _state = MutableStateFlow<PagerState<T>>(PagerState.Idle)

    /** Observable pager state. Collect in your ViewModel or Composable. */
    val state: StateFlow<PagerState<T>> = _state.asStateFlow()

    private val allItems = mutableListOf<T>()
    private var nextPage = 0
    private var isComplete = false
    private var isLoading = false

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Load the first page (or the next page if more are available).
     *
     * - Safe to call multiple times - ignored if a load is already in progress.
     * - Does nothing if all pages have been loaded ([PagerState.Complete]).
     */
    fun loadNextPage() {
        scope.launch {
            mutex.withLock {
                if (isLoading || isComplete) return@withLock
                isLoading = true
                val page = nextPage
                val isInitialLoad = page == 0

                _state.value = if (isInitialLoad) {
                    PagerState.LoadingInitial
                } else {
                    PagerState.LoadingMore(allItems.toList())
                }

                when (val result = loader(page, pageSize)) {
                    is ApiResult.Success -> handleSuccess(result.data)
                    is ApiResult.Failure -> {
                        log.w(tag, "Page $page failed: ${result.message}")
                        _state.value = PagerState.Error(
                            error = result.message ?: "Unknown error",
                            items = allItems.toList()
                        )
                    }

                    ApiResult.NetworkError -> {
                        log.w(tag, "Page $page network error")
                        _state.value = PagerState.Error(
                            error = "No internet connection",
                            items = allItems.toList(),
                            isNetworkError = true
                        )
                    }

                    ApiResult.Cancelled -> {
                        log.d(tag, "Page $page load cancelled")
                        _state.value = if (allItems.isEmpty()) PagerState.Idle
                        else PagerState.Success(allItems.toList(), EMPTY_PAGINATION)
                    }
                }

                isLoading = false
            }
        }
    }

    /**
     * Reset to the first page and reload.
     * Clears all accumulated items and resets pagination state.
     */
    fun refresh() {
        scope.launch {
            mutex.withLock {
                allItems.clear()
                nextPage = 0
                isComplete = false
                isLoading = false
                _state.value = PagerState.Idle
            }
            loadNextPage()
        }
    }

    /**
     * Cancel all in-flight operations and release resources.
     * Call this from `onCleared()` in Android ViewModel, or `deinit` in iOS.
     */
    fun cancel() {
        scope.cancel()
        log.d(tag, "Pager cancelled")
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private fun handleSuccess(page: Page<T>) {
        allItems.addAll(page.items)
        nextPage++

        if (!page.pagination.hasNext || page.items.isEmpty()) {
            isComplete = true
            log.i(tag, "Pager complete - ${allItems.size} total items")
            _state.value = PagerState.Complete(allItems.toList())
        } else {
            log.d(tag, "Page ${nextPage - 1} loaded - ${allItems.size} total items, more available")
            _state.value = PagerState.Success(allItems.toList(), page.pagination)
        }
    }

    private companion object {
        val EMPTY_PAGINATION = PaginationInfo()
    }
}