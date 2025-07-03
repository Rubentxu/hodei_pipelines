package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Execution(
    val id: DomainId,
    val jobId: DomainId,
    val poolId: DomainId,
    val workerId: DomainId? = null,
    val status: ExecutionStatus,
    val startedAt: Instant? = null,
    val completedAt: Instant? = null,
    val resourceUsage: ResourceUsage? = null,
    val exitCode: Int? = null,
    val errorMessage: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun updateStatus(newStatus: ExecutionStatus): Execution {
        require(status.canTransitionTo(newStatus)) { 
            "Cannot transition from $status to $newStatus" 
        }
        val now = kotlinx.datetime.Clock.System.now()
        return copy(
            status = newStatus,
            updatedAt = now,
            startedAt = if (newStatus == ExecutionStatus.RUNNING && startedAt == null) now else startedAt,
            completedAt = if (newStatus.isTerminal) now else completedAt
        )
    }
    
    fun assignWorker(workerId: DomainId): Execution =
        copy(
            workerId = workerId,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun updateResourceUsage(usage: ResourceUsage): Execution =
        copy(
            resourceUsage = usage,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun fail(errorMessage: String, exitCode: Int? = null): Execution =
        updateStatus(ExecutionStatus.FAILED).copy(
            errorMessage = errorMessage,
            exitCode = exitCode
        )
    
    val duration: Long?
        get() = if (startedAt != null && completedAt != null) {
            completedAt.epochSeconds - startedAt.epochSeconds
        } else null
    
    val isRunning: Boolean
        get() = status == ExecutionStatus.RUNNING
    
    val hasWorker: Boolean
        get() = workerId != null
        
    companion object {
        fun create(
            jobId: DomainId,
            workerId: DomainId,
            spec: Map<String, Any>,
            poolId: DomainId = DomainId("default-pool")
        ): Execution {
            val now = kotlinx.datetime.Clock.System.now()
            return Execution(
                id = DomainId.generate(),
                jobId = jobId,
                poolId = poolId,
                workerId = workerId,
                status = ExecutionStatus.PENDING,
                createdAt = now,
                updatedAt = now
            )
        }
    }
}

@Serializable
data class ResourceUsage(
    val cpuSeconds: Double,
    val memoryMaxBytes: Long,
    val networkBytesIn: Long = 0,
    val networkBytesOut: Long = 0,
    val diskBytesRead: Long = 0,
    val diskBytesWritten: Long = 0
)