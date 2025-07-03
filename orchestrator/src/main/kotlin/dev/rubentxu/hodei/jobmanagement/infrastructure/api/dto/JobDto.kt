package dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto

import dev.rubentxu.hodei.jobmanagement.domain.entities.JobForAPI
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobContent
import dev.rubentxu.hodei.shared.domain.primitives.RetryPolicy
import dev.rubentxu.hodei.shared.infrastructure.api.dto.PaginationMetaDto
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class JobDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val status: JobStatusDto,
    val templateId: String? = null,
    val templateVersion: String? = null,
    val content: JobContentDto,
    val parameters: Map<String, String> = emptyMap(),
    val poolId: String? = null,
    val priority: Int = 50,
    val retryPolicy: RetryPolicyDto? = null,
    val labels: Map<String, String> = emptyMap(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String,
    val scheduledAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val currentExecutionId: String? = null
)

@Serializable
enum class JobStatusDto {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class JobContentDto(
    val type: JobContentTypeDto,
    val kotlinScript: String? = null,
    val shellCommands: List<String>? = null,
    val timeout: String? = null
)

@Serializable
enum class JobContentTypeDto {
    KOTLIN_SCRIPT,
    SHELL_COMMANDS
}

@Serializable
data class RetryPolicyDto(
    val maxRetries: Int = 0,
    val retryDelay: String = "5m",
    val retryOnFailure: Boolean = true,
    val backoffMultiplier: Double = 2.0
)

// Request DTOs
@Serializable
data class CreateAdHocJobRequest(
    val name: String,
    val description: String? = null,
    val content: JobContentDto,
    val parameters: Map<String, String> = emptyMap(),
    val poolId: String? = null,
    val priority: Int = 50,
    val retryPolicy: RetryPolicyDto? = null,
    val labels: Map<String, String> = emptyMap(),
    val scheduledAt: Instant? = null
)

@Serializable
data class CreateJobFromTemplateRequest(
    val templateId: String,
    val templateVersion: String? = null,
    val name: String,
    val description: String? = null,
    val parameters: Map<String, String> = emptyMap(),
    val poolId: String? = null,
    val priority: Int = 50,
    val retryPolicy: RetryPolicyDto? = null,
    val labels: Map<String, String> = emptyMap(),
    val scheduledAt: Instant? = null
)

@Serializable
data class CancelJobRequest(
    val reason: String? = null,
    val force: Boolean = false
)

// Response DTOs
@Serializable
data class JobListResponse(
    val data: List<JobDto>,
    val meta: PaginationMetaDto
)

// Mappers for JobForAPI
fun JobForAPI.toDto(): JobDto = JobDto(
    id = id.value,
    name = name,
    description = description,
    status = status.toDto(),
    templateId = templateId?.value,
    templateVersion = templateVersion?.value,
    content = content.toDto(),
    parameters = parameters,
    poolId = poolId?.value,
    priority = priority,
    retryPolicy = retryPolicy?.toDto(),
    labels = labels,
    createdAt = metadata.createdAt,
    updatedAt = metadata.updatedAt,
    createdBy = metadata.createdBy,
    scheduledAt = scheduledAt,
    startedAt = startedAt,
    completedAt = completedAt,
    currentExecutionId = currentExecutionId?.value
)

fun JobStatus.toDto(): JobStatusDto = when (this) {
    JobStatus.PENDING -> JobStatusDto.PENDING
    JobStatus.QUEUED -> JobStatusDto.QUEUED
    JobStatus.RUNNING -> JobStatusDto.RUNNING
    JobStatus.COMPLETED -> JobStatusDto.COMPLETED
    JobStatus.FAILED -> JobStatusDto.FAILED
    JobStatus.CANCELLED -> JobStatusDto.CANCELLED
}

fun JobContent.toDto(): JobContentDto = when (this) {
    is JobContent.KotlinScript -> JobContentDto(
        type = JobContentTypeDto.KOTLIN_SCRIPT,
        kotlinScript = scriptContent,
        timeout = timeout
    )
    is JobContent.ShellCommands -> JobContentDto(
        type = JobContentTypeDto.SHELL_COMMANDS,
        shellCommands = commands,
        timeout = timeout
    )
}

fun RetryPolicy.toDto(): RetryPolicyDto = RetryPolicyDto(
    maxRetries = maxRetries,
    retryDelay = retryDelay,
    retryOnFailure = retryOnFailure,
    backoffMultiplier = backoffMultiplier
)

// Domain mappers
fun JobContentDto.toDomain(): JobContent = when (type) {
    JobContentTypeDto.KOTLIN_SCRIPT -> JobContent.KotlinScript(
        scriptContent = kotlinScript ?: error("Kotlin script content is required"),
        timeout = timeout
    )
    JobContentTypeDto.SHELL_COMMANDS -> JobContent.ShellCommands(
        commands = shellCommands ?: error("Shell commands are required"),
        timeout = timeout
    )
}

fun RetryPolicyDto.toDomain(): RetryPolicy = RetryPolicy(
    maxRetries = maxRetries,
    retryDelay = retryDelay,
    retryOnFailure = retryOnFailure,
    backoffMultiplier = backoffMultiplier
)