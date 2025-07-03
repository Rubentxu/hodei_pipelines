package dev.rubentxu.hodei.shared.domain.primitives

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class Metadata(
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String
)