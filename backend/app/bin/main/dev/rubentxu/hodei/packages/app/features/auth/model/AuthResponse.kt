package dev.rubentxu.hodei.packages.app.features.auth.model

import kotlinx.serialization.Serializable

@Serializable
data class AuthResponse(
    val message: String, // Example: "Login successful", "Admin registered"
    val token: String? = null, // Placeholder for JWT
    val email: String? = null,
    val username: String? = null
)