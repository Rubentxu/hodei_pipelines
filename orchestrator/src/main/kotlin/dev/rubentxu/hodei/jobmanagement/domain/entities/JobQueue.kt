package dev.rubentxu.hodei.jobmanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Job Queue domain model - manages job scheduling and prioritization
 * Similar to Kubernetes Job scheduling with priority classes
 */
@Serializable
data class JobQueue(
    val id: DomainId,
    val name: String,
    val resourcePoolId: DomainId,
    val queueType: QueueType = QueueType.FIFO,
    val priority: QueuePriority = QueuePriority.NORMAL,
    val maxConcurrentJobs: Int? = null,
    val maxQueuedJobs: Int? = null,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    init {
        require(name.isNotBlank()) { "Queue name cannot be blank" }
        require(maxConcurrentJobs == null || maxConcurrentJobs > 0) { 
            "Max concurrent jobs must be positive" 
        }
        require(maxQueuedJobs == null || maxQueuedJobs > 0) { 
            "Max queued jobs must be positive" 
        }
    }

    fun updatePriority(newPriority: QueuePriority): JobQueue {
        return copy(
            priority = newPriority,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun updateLimits(maxConcurrent: Int?, maxQueued: Int?): JobQueue {
        return copy(
            maxConcurrentJobs = maxConcurrent,
            maxQueuedJobs = maxQueued,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun activate(): JobQueue = copy(isActive = true, updatedAt = kotlinx.datetime.Clock.System.now())
    fun deactivate(): JobQueue = copy(isActive = false, updatedAt = kotlinx.datetime.Clock.System.now())
}

@Serializable
enum class QueueType {
    FIFO,      // First In, First Out
    LIFO,      // Last In, First Out  
    PRIORITY   // Priority-based scheduling
}

@Serializable
enum class QueuePriority(val value: Int) {
    CRITICAL(100),
    HIGH(75),
    NORMAL(50),
    LOW(25),
    BACKGROUND(0)
}

/**
 * Queued Job - represents a job waiting to be scheduled
 */
@Serializable
data class QueuedJob(
    val id: DomainId,
    val jobId: DomainId,
    val queueId: DomainId,
    val job: Job,
    val priority: JobPriority = JobPriority.NORMAL,
    val scheduledAfter: Instant? = null, // For delayed scheduling
    val attempts: Int = 0,
    val maxAttempts: Int = 3,
    val status: QueuedJobStatus = QueuedJobStatus.QUEUED,
    val queuedAt: Instant,
    val scheduledAt: Instant? = null,
    val completedAt: Instant? = null
) {
    fun isReady(): Boolean {
        return status == QueuedJobStatus.QUEUED && 
               (scheduledAfter == null || scheduledAfter <= kotlinx.datetime.Clock.System.now())
    }

    fun canRetry(): Boolean {
        return attempts < maxAttempts && status == QueuedJobStatus.FAILED
    }

    fun schedule(): QueuedJob {
        return copy(
            status = QueuedJobStatus.SCHEDULED,
            scheduledAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun complete(): QueuedJob {
        return copy(
            status = QueuedJobStatus.COMPLETED,
            completedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun start(): QueuedJob {
        return copy(
            status = QueuedJobStatus.RUNNING
        )
    }

    fun fail(): QueuedJob {
        return copy(
            status = QueuedJobStatus.FAILED,
            attempts = attempts + 1
        )
    }
    
    fun fail(reason: String): QueuedJob {
        return copy(
            status = QueuedJobStatus.FAILED,
            attempts = attempts + 1
        )
    }

    fun retry(): QueuedJob {
        return copy(
            status = QueuedJobStatus.QUEUED,
            attempts = attempts + 1
        )
    }
}

@Serializable
enum class JobPriority(val value: Int) {
    URGENT(100),
    HIGH(75),
    NORMAL(50),
    LOW(25),
    DEFERRED(0);
    
    companion object {
        fun fromInt(value: Int): JobPriority {
            return when {
                value >= 90 -> URGENT
                value >= 70 -> HIGH
                value >= 40 -> NORMAL
                value >= 20 -> LOW
                else -> DEFERRED
            }
        }
    }
}

@Serializable
enum class QueuedJobStatus {
    QUEUED,
    SCHEDULED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}