package dev.rubentxu.hodei.infrastructure.api.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponseDto(
    val error: String,
    val message: String,
    val timestamp: Instant,
    val traceId: String? = null
)