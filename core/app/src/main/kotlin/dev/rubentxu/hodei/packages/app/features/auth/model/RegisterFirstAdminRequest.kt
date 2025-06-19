package dev.rubentxu.hodei.packages.app.features.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class RegisterFirstAdminRequest(
    val username: String,
    val email: String,
    val password: String
)