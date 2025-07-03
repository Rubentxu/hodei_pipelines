package dev.rubentxu.hodei.jobmanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.shared.domain.primitives.Version
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Job(
    val id: DomainId,
    val name: String,
    val templateId: DomainId? = null,
    val templateVersion: Version? = null,
    val poolId: DomainId? = null,
    val status: JobStatus,
    val priority: Priority,
    val parameters: JsonObject = JsonObject(emptyMap()),
    val overrides: JsonObject = JsonObject(emptyMap()),
    val spec: JsonObject? = null,
    val definition: Definition? = null,
    val resourceRequirements: Map<String, String> = emptyMap(),
    val namespace: String = "default",
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val latestExecutionId: DomainId? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String,
    val scheduledAt: Instant? = null,
    val completedAt: Instant? = null
) {
    init {
        require(name.isNotBlank()) { "Job name cannot be blank" }
        require(namespace.isNotBlank()) { "Job namespace cannot be blank" }
        require(retryCount >= 0) { "Retry count cannot be negative" }
        require(maxRetries >= 0) { "Max retries cannot be negative" }
        require(templateId != null || spec != null) { 
            "Job must have either a template reference or an inline spec" 
        }
    }
    
    fun updateStatus(newStatus: JobStatus): Job {
        require(status.canTransitionTo(newStatus)) { 
            "Cannot transition from $status to $newStatus" 
        }
        val now = kotlinx.datetime.Clock.System.now()
        return copy(
            status = newStatus,
            updatedAt = now,
            completedAt = if (newStatus.isTerminal) now else completedAt
        )
    }
    
    fun assignExecution(executionId: DomainId): Job =
        copy(
            latestExecutionId = executionId,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun retry(): Job {
        require(canRetry()) { "Job cannot be retried" }
        return copy(
            status = JobStatus.QUEUED,
            retryCount = retryCount + 1,
            updatedAt = kotlinx.datetime.Clock.System.now(),
            completedAt = null
        )
    }
    
    fun canRetry(): Boolean = 
        status == JobStatus.FAILED && retryCount < maxRetries
    
    fun queue(): Job =
        updateStatus(JobStatus.QUEUED)
        
    fun start(executionId: DomainId): Job =
        updateStatus(JobStatus.RUNNING).assignExecution(executionId)
        
    fun complete(): Job =
        updateStatus(JobStatus.COMPLETED)
        
    fun fail(reason: String): Job =
        updateStatus(JobStatus.FAILED)
        
    fun cancel(reason: String): Job =
        updateStatus(JobStatus.CANCELLED)
    
    val isFromTemplate: Boolean
        get() = templateId != null
    
    val isAdHoc: Boolean
        get() = spec != null
    
    @Serializable
    data class Definition(
        val type: JobType,
        val content: String,
        val envVars: Map<String, String> = emptyMap()
    )
}