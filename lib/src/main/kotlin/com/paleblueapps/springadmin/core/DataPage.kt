package com.paleblueapps.springadmin.core

import kotlin.math.max

data class DataPage<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
) {
    val totalPages: Int = if (size <= 0) 1 else max(1, ((totalElements + size - 1) / size).toInt())
}
