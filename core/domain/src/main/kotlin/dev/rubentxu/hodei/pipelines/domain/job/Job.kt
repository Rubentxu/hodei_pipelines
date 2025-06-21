package dev.rubentxu.hodei.pipelines.domain.job

import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class JobId(val value: String)
{
    init {
        require(value.isNotBlank()) { "Job id cannot be empty" }
    }

    companion object {
        fun fromString(value: String): JobId {
            return JobId(value.trim())
        }

        @OptIn(ExperimentalUuidApi::class)
        fun generate(): JobId {
            return JobId(Uuid.random().toString())
        }
    }
}

enum class JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;

    fun isTerminal(): Boolean {
        return this == COMPLETED || this == FAILED || this == CANCELLED
    }
}

data class JobDefinition(
    val name: String,
    val payload: JobPayload,
    val environment: Map<String, String> = emptyMap(),
    val workingDirectory: String,
    val parameters: List<JobParameter> = emptyList(),
    val priority: Int = 0,
    val timeoutSeconds: Int = 0
)

data class JobParameter(
    val name: String,
    val type: ParameterType,
    val value: Any?,
    val required: Boolean = false,
    val description: String = ""
) {
    init {
        require(name.isNotBlank()) { "Parameter name cannot be empty" }
        if (required) {
            require(value != null) { "Required parameter '$name' must have a value" }
        }
    }
}

enum class ParameterType {
    STRING,
    TEXT,
    BOOLEAN,
    CHOICE,
    PASSWORD,
    FILE,
    ENVIRONMENT,
    JSON
}

data class JobExecution(
    val status: JobStatus,
    val startedAt: Instant,
    val finishedAt: Instant? = null,
    val exitCode: Int? = null,
    val output: String? = null,
    val errorMessage: String? = null
) {
    init {
        require(status != JobStatus.QUEUED) { "JobExecution cannot be created with QUEUED status" }
        if (finishedAt != null) {
            require(finishedAt.isAfter(startedAt)) { "Finished time must be after started time" }
        }
    }

    fun duration(): Long {
        return if (finishedAt != null) {
            finishedAt.toEpochMilli() - startedAt.toEpochMilli()
        } else {
            Instant.now().toEpochMilli() - startedAt.toEpochMilli()
        }
    }

    fun isCompleted(): Boolean= status in setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)

    fun isRunning(): Boolean = status == JobStatus.RUNNING
}


data class Job(
    val id: JobId,
    val definition: JobDefinition,
    val status: JobStatus = JobStatus.QUEUED,
    val execution: JobExecution? = null,
    val assignedWorker: WorkerId? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val exitCode: Int? = null,
    val output: String? = null,
    val errorMessage: String? = null
) {
    fun assign(workerId: WorkerId): Job {
        validateTransition(JobStatus.RUNNING)
        return copy(
            assignedWorker = workerId,
            updatedAt = Instant.now()
        )
    }
    fun start(): Job {
        validateTransition(JobStatus.RUNNING)
        return copy(
            status = JobStatus.RUNNING,
            startedAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    fun complete(exitCode: Int, output: String): Job {
        validateTransition(JobStatus.COMPLETED)
        return copy(
            status = JobStatus.COMPLETED,
            exitCode = exitCode,
            output = output,
            completedAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    fun fail(errorMessage: String, exitCode: Int): Job {
        validateTransition(JobStatus.FAILED)
        return copy(
            status = JobStatus.FAILED,
            errorMessage = errorMessage,
            exitCode = exitCode,
            completedAt = Instant.now(),
            updatedAt = Instant.now()
        )
    }
    
    fun cancel(): Job {
        validateTransition(JobStatus.CANCELLED)
        return copy(
            status = JobStatus.CANCELLED,
            updatedAt = Instant.now()
        )
    }
    
    private fun validateTransition(newStatus: JobStatus) {
        val validTransitions = when (status) {
            JobStatus.QUEUED -> setOf(JobStatus.RUNNING, JobStatus.CANCELLED, JobStatus.FAILED)
            JobStatus.RUNNING -> setOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)
            JobStatus.COMPLETED -> emptySet()
            JobStatus.FAILED -> emptySet()
            JobStatus.CANCELLED -> emptySet()
        }
        
        if (newStatus !in validTransitions) {
            throw IllegalStateException("Cannot transition from $status to $newStatus")
        }
    }
}