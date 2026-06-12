package com.droidunplugged.kmp_platform_kit.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
) {
    val isLastPage: Boolean get() = !hasNext
    val isFirstPage: Boolean get() = !hasPrevious
    val isPaginated: Boolean get() = totalPages > 1
}
