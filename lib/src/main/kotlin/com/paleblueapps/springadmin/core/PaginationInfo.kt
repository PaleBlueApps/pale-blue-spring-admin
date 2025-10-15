package com.paleblueapps.springadmin.core

data class PaginationInfo(
    val currentPage: Int,
    val totalPages: Int,
    val totalElements: Long,
    val pageSize: Int,
    val startRecord: Long,
    val endRecord: Long,
    val firstBlockPages: List<Int>,
    val middleWindowPages: List<Int>,
    val lastBlockPages: List<Int>,
    val showEllipsisBeforeMiddle: Boolean,
    val showEllipsisAfterMiddle: Boolean,
) {
    companion object {
        fun <T> from(dataPage: DataPage<T>): PaginationInfo {
            val currentPage = dataPage.page
            val totalPages = dataPage.totalPages
            val size = dataPage.size
            val total = dataPage.totalElements

            // Calculate record range
            val start = if (total > 0) currentPage * size + 1L else 0L
            val end = if (total > 0) ((currentPage + 1) * size).toLong() else 0L
            val endCap = if (end > total) total else end

            // First block: pages 0, 1, 2 (up to index 2, bounded by tp-1)
            val firstBlockEnd = if (2 < totalPages - 1) 2 else totalPages - 1
            val firstBlock = if (firstBlockEnd >= 0) (0..firstBlockEnd).toList() else emptyList()

            // Middle window: current page ± 3, bounded to [0..tp-1], excluding first and last blocks
            val startWindow = maxOf(0, currentPage - 3)
            val endWindow = minOf(totalPages - 1, currentPage + 3)
            val middleWindow = (startWindow..endWindow).filter { it >= 3 && it <= totalPages - 4 }

            // Last block: pages tp-3, tp-2, tp-1 (last 3 pages, bounded by 0)
            val lastBlockStart = if (totalPages - 3 > 0) totalPages - 3 else 0
            val lastBlock = if (totalPages > 0) (lastBlockStart until totalPages).toList() else emptyList()

            // Show ellipsis if there's a gap between blocks
            val showEllipsisBefore = currentPage - 3 > 3
            val showEllipsisAfter = currentPage + 3 < totalPages - 4

            return PaginationInfo(
                currentPage = currentPage,
                totalPages = totalPages,
                totalElements = total,
                pageSize = size,
                startRecord = start,
                endRecord = endCap,
                firstBlockPages = firstBlock,
                middleWindowPages = middleWindow,
                lastBlockPages = lastBlock,
                showEllipsisBeforeMiddle = showEllipsisBefore,
                showEllipsisAfterMiddle = showEllipsisAfter,
            )
        }
    }
}
