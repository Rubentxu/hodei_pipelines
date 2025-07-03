package dev.rubentxu.hodei.infrastructure.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class WorkerDto(
    val id: String,
    val poolId: String,
    val status: String,
    val capabilities: List<String>,
    val currentExecutionId: String? = null,
    val lastHeartbeat: String,
    val registeredAt: String,
    val stats: WorkerStatsDto
)

@Serializable
data class WorkerStatsDto(
    val totalExecutions: Int,
    val successfulExecutions: Int,
    val failedExecutions: Int,
    val averageExecutionTime: Double
)