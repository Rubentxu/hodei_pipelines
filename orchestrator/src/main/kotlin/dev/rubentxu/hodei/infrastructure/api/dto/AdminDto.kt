package dev.rubentxu.hodei.infrastructure.api.dto

import kotlinx.serialization.Serializable

@Serializable
data class ConfigDto(
    val version: String,
    val environment: String,
    val features: Map<String, Boolean>,
    val limits: Map<String, Int>,
    val endpoints: Map<String, String>
)

@Serializable
data class MaintenanceResultDto(
    val operation: String,
    val status: String,
    val details: Map<String, String>,
    val timestamp: Long
)

@Serializable
data class AuditLogDto(
    val id: String,
    val timestamp: Long,
    val userId: String,
    val action: String,
    val resource: String,
    val resourceId: String?,
    val result: String,
    val details: Map<String, String>?
)

@Serializable
data class BroadcastMessageRequest(
    val type: String,
    val message: String,
    val targets: List<String>? = null
)

@Serializable
data class BroadcastResultDto(
    val messageId: String,
    val type: String,
    val targetCount: Int,
    val deliveredCount: Int,
    val timestamp: Long
)