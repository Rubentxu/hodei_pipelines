package dev.rubentxu.hodei.jobmanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Metadata
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.shared.domain.primitives.Version
import dev.rubentxu.hodei.shared.domain.primitives.RetryPolicy
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// Extended Job model for API layer that includes content and other fields
@Serializable
data class JobForAPI(
    val id: DomainId,
    val name: String,
    val description: String? = null,
    val status: JobStatus,
    val templateId: DomainId? = null,
    val templateVersion: Version? = null,
    val content: JobContent,
    val parameters: Map<String, String> = emptyMap(),
    val poolId: DomainId? = null,
    val priority: Int = 50,
    val retryPolicy: RetryPolicy? = null,
    val labels: Map<String, String> = emptyMap(),
    val metadata: Metadata,
    val scheduledAt: Instant? = null,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val currentExecutionId: DomainId? = null
) {
    fun start(executionId: DomainId): JobForAPI {
        return copy(
            status = JobStatus.RUNNING,
            startedAt = kotlinx.datetime.Clock.System.now(),
            currentExecutionId = executionId,
            metadata = metadata.copy(updatedAt = kotlinx.datetime.Clock.System.now())
        )
    }
    
    fun complete(): JobForAPI {
        return copy(
            status = JobStatus.COMPLETED,
            completedAt = kotlinx.datetime.Clock.System.now(),
            metadata = metadata.copy(updatedAt = kotlinx.datetime.Clock.System.now())
        )
    }
    
    fun fail(reason: String): JobForAPI {
        return copy(
            status = JobStatus.FAILED,
            completedAt = kotlinx.datetime.Clock.System.now(),
            metadata = metadata.copy(updatedAt = kotlinx.datetime.Clock.System.now())
        )
    }
    
    fun cancel(reason: String): JobForAPI {
        return copy(
            status = JobStatus.CANCELLED,
            completedAt = kotlinx.datetime.Clock.System.now(),
            metadata = metadata.copy(updatedAt = kotlinx.datetime.Clock.System.now())
        )
    }
    
    // Convert JobForAPI to Job for compatibility with ExecutionEngine
    fun toJob(): Job {
        val spec = when (content) {
            is JobContent.KotlinScript -> buildJsonObject {
                put("type", "kotlin_script")
                put("script", content.scriptContent)
                content.timeout?.let { put("timeout", it) }
            }
            is JobContent.ShellCommands -> buildJsonObject {
                put("type", "shell_commands")
                put("commands", content.commands.toString())
                content.timeout?.let { put("timeout", it) }
            }
        }
        
        val parametersJson = buildJsonObject {
            parameters.forEach { (key, value) ->
                put(key, value)
            }
        }
        
        return Job(
            id = id,
            name = name,
            templateId = templateId,
            templateVersion = templateVersion,
            status = status,
            priority = Priority.fromValue(priority),
            parameters = parametersJson,
            spec = spec,
            maxRetries = retryPolicy?.maxRetries ?: 0,
            createdAt = metadata.createdAt,
            updatedAt = metadata.updatedAt,
            createdBy = metadata.createdBy,
            scheduledAt = scheduledAt,
            completedAt = completedAt,
            latestExecutionId = currentExecutionId
        )
    }
}