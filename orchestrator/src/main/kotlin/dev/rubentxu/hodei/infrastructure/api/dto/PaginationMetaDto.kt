package dev.rubentxu.hodei.infrastructure.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class PaginationMetaDto(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)