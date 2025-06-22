package dev.rubentxu.hodei.pipelines.domain.orchestration

import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import java.time.Instant

/**
 * Domain events for orchestration operations
 * These events represent things that happened in the orchestration layer
 */
sealed class WorkerOrchestrationEvent {
    abstract val timestamp: Instant
    abstract val workerId: WorkerId
    
    data class WorkerCreationStarted(
        override val workerId: WorkerId,
        val template: WorkerTemplate,
        val poolId: WorkerPoolId,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerCreationCompleted(
        override val workerId: WorkerId,
        val template: WorkerTemplate,
        val actualResources: ResourceRequirements,
        val nodeName: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerCreationFailed(
        override val workerId: WorkerId,
        val template: WorkerTemplate,
        val reason: String,
        val retryable: Boolean = false,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerStatusChanged(
        override val workerId: WorkerId,
        val previousStatus: WorkerPodStatus,
        val newStatus: WorkerPodStatus,
        val reason: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerDeletionStarted(
        override val workerId: WorkerId,
        val reason: String,
        val gracePeriodSeconds: Int? = null,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerDeletionCompleted(
        override val workerId: WorkerId,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerResourcesExhausted(
        override val workerId: WorkerId,
        val resourceType: String, // "cpu", "memory", "storage"
        val current: String,
        val limit: String,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerEvicted(
        override val workerId: WorkerId,
        val reason: String,
        val nodeName: String? = null,
        override val timestamp: Instant = Instant.now()
    ) : WorkerOrchestrationEvent()
}

/**
 * Worker pod status in orchestration layer
 */
enum class WorkerPodStatus {
    PENDING,        // Pod is waiting to be scheduled
    SCHEDULING,     // Pod is being scheduled
    SCHEDULED,      // Pod has been scheduled to a node
    INITIALIZING,   // Pod containers are being created
    RUNNING,        // Pod is running
    SUCCEEDED,      // Pod has completed successfully
    FAILED,         // Pod has failed
    TERMINATING,    // Pod is being terminated
    TERMINATED,     // Pod has been terminated
    EVICTED,        // Pod has been evicted
    UNKNOWN         // Status cannot be determined
}

/**
 * Cluster-level orchestration events
 */
sealed class ClusterOrchestrationEvent {
    abstract val timestamp: Instant
    
    data class NodeAdded(
        val nodeName: String,
        val capacity: Map<String, String>,
        override val timestamp: Instant = Instant.now()
    ) : ClusterOrchestrationEvent()
    
    data class NodeRemoved(
        val nodeName: String,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : ClusterOrchestrationEvent()
    
    data class NodeUnavailable(
        val nodeName: String,
        val reason: String,
        val affectedWorkers: List<WorkerId>,
        override val timestamp: Instant = Instant.now()
    ) : ClusterOrchestrationEvent()
    
    data class ResourceQuotaExceeded(
        val namespace: String,
        val resourceType: String,
        val current: String,
        val limit: String,
        override val timestamp: Instant = Instant.now()
    ) : ClusterOrchestrationEvent()
    
    data class AutoScalingTriggered(
        val poolId: WorkerPoolId,
        val currentSize: Int,
        val targetSize: Int,
        val reason: String,
        override val timestamp: Instant = Instant.now()
    ) : ClusterOrchestrationEvent()
}