package dev.rubentxu.hodei.execution.infrastructure.api.dto

import dev.rubentxu.hodei.shared.infrastructure.api.dto.PaginationMetaDto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionDto(
    val id: String,
    val jobId: String,
    val workerId: String? = null,
    val status: String,
    val createdAt: Instant,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val exitCode: Int? = null,
    val failureReason: String? = null
)

@Serializable
data class ExecutionListResponse(
    val data: List<ExecutionDto>,
    val meta: PaginationMetaDto
)

@Serializable
data class CancelExecutionRequest(
    val reason: String? = null,
    val force: Boolean = false
)

@Serializable
data class ExecutionLogEntry(
    val timestamp: Instant,
    val level: String,
    val message: String,
    val stream: String? = null // STDOUT, STDERR
)

@Serializable
data class ExecutionLogsResponse(
    val logs: List<ExecutionLogEntry>,
    val message: String? = null
)

@Serializable
data class ExecutionEvent(
    val timestamp: Instant,
    val type: String,
    val message: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ExecutionEventsResponse(
    val events: List<ExecutionEvent>,
    val message: String? = null
)

@Serializable
data class ExecutionReplayResponse(
    val executionId: String,
    val jobId: String,
    val status: String,
    val events: List<ExecutionEvent>,
    val logs: List<ExecutionLogEntry>,
    val message: String? = null
)