package dev.rubentxu.hodei.shared.infrastructure.api.dto

import dev.rubentxu.hodei.shared.domain.errors.DomainError
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val error: String,
    val message: String,
    val timestamp: Instant,
    val traceId: String? = null
)

@Serializable
data class PaginationMetaDto(
    val page: Int,
    val size: Int,
    val totalElements: Int,
    val totalPages: Int,
    val hasNext: Boolean,
    val hasPrevious: Boolean
)

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val totalPages: Int,
    val totalItems: Long
)

@Serializable
data class ErrorResponse(
    val code: String,
    val message: String,
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
)

// Extension function to convert DomainError to ErrorResponseDto
fun DomainError.toErrorResponse(): ErrorResponseDto = ErrorResponseDto(
    error = this::class.simpleName ?: "DomainError",
    message = message,
    timestamp = kotlinx.datetime.Clock.System.now()
)