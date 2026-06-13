package com.droidunplugged.kmp_platform_kit.shared.models

/**
 * Generic pagination metadata returned by paginated API endpoints.
 *
 * Reusable across all features - not specific to Physical Inventory.
 * Each feature's response DTO can embed or derive from this.
 *
 * @property currentPage  Current page number (0-based).
 * @property totalPages   Total number of pages available.
 * @property totalElements Total number of items across all pages.
 * @property pageSize     Number of items per page.
 * @property hasNext      `true` if a next page is available.
 * @property hasPrevious  `true` if a previous page is available.
 */
data class PaginationInfo(
    val currentPage: Int = 0,
    val totalPages: Int = 0,
    val totalElements: Int = 0,
    val pageSize: Int = 0,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false
) {
    /** `true` when on the last page. */
    val isLastPage: Boolean get() = !hasNext

    /** `true` when on the first page. */
    val isFirstPage: Boolean get() = !hasPrevious

    /** `true` when there is more than one page of results. */
    val isPaginated: Boolean get() = totalPages > 1
}