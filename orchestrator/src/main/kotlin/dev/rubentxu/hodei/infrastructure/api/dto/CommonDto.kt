package dev.rubentxu.hodei.infrastructure.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val message: String
)

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalItems: Long
)