package dev.rubentxu.hodei.shared.domain.primitives

import kotlinx.serialization.Serializable

@Serializable
data class RetryPolicy(
    val maxRetries: Int = 0,
    val retryDelay: String = "5m",
    val retryOnFailure: Boolean = true,
    val backoffMultiplier: Double = 2.0
)