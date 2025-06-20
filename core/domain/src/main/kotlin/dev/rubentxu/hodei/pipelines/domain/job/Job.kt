package dev.rubentxu.hodei.pipelines.domain.job

import java.time.Instant

@JvmInline
value class JobId(val value: String)

enum class JobStatus {
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

data class JobDefinition(
    val name: String,
    val command: List<String>
)

data class Job(
    val id: JobId,
    val definition: JobDefinition,
    val status: JobStatus = JobStatus.QUEUED,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val exitCode: Int? = null,
    val output: String? = null,
    val errorMessage: String? = null
) {
    
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