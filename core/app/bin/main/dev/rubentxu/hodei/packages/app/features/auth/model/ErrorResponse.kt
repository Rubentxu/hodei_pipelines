package dev.rubentxu.hodei.packages.app.features.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class ErrorResponse(
    val error: String,
    val details: List<String>? = null
)