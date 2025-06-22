package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import kotlinx.coroutines.flow.Flow

/**
 * Port for Worker Orchestration - Abstract interface for different container orchestrators
 * Implementations could be: Kubernetes, Docker Swarm, AWS ECS, Azure Container Instances, etc.
 */
interface WorkerOrchestrator {
    
    /**
     * Create a new worker based on template
     * @param template The worker template to use
     * @param poolId The pool this worker belongs to
     * @return Result of worker creation
     */
    suspend fun createWorker(template: WorkerTemplate, poolId: WorkerPoolId): WorkerCreationResult
    
    /**
     * Delete/terminate a worker
     * @param workerId The worker to terminate
     * @return Result of worker deletion
     */
    suspend fun deleteWorker(workerId: WorkerId): WorkerDeletionResult
    
    /**
     * Get current status of a worker
     * @param workerId The worker to check
     * @return Current worker status
     */
    suspend fun getWorkerStatus(workerId: WorkerId): WorkerStatusResult
    
    /**
     * List all workers in a pool
     * @param poolId The pool to list workers from
     * @return List of workers in the pool
     */
    suspend fun listWorkers(poolId: WorkerPoolId): List<Worker>
    
    /**
     * List all workers across all pools
     * @return List of all workers
     */
    suspend fun listAllWorkers(): List<Worker>
    
    /**
     * Get resource availability in the orchestration platform
     * @return Current resource availability
     */
    suspend fun getResourceAvailability(): ResourceAvailability
    
    /**
     * Stream worker lifecycle events
     * @return Flow of worker events (created, started, stopped, failed, etc.)
     */
    fun watchWorkerEvents(): Flow<WorkerOrchestrationEvent>
    
    /**
     * Validate if a worker template is valid for this orchestrator
     * @param template The template to validate
     * @return Validation result
     */
    suspend fun validateTemplate(template: WorkerTemplate): TemplateValidationResult
    
    /**
     * Get orchestrator-specific information
     * @return Information about the orchestrator implementation
     */
    suspend fun getOrchestratorInfo(): OrchestratorInfo
    
    /**
     * Health check for the orchestrator
     * @return Health status of the orchestrator
     */
    suspend fun healthCheck(): OrchestratorHealth
}

/**
 * Result of worker creation
 */
sealed class WorkerCreationResult {
    data class Success(val worker: Worker) : WorkerCreationResult()
    data class Failed(val error: String, val cause: Throwable? = null) : WorkerCreationResult()
    data class InsufficientResources(val required: ResourceRequirements, val available: ResourceAvailability) : WorkerCreationResult()
    data class InvalidTemplate(val errors: List<String>) : WorkerCreationResult()
}

/**
 * Result of worker deletion
 */
sealed class WorkerDeletionResult {
    object Success : WorkerDeletionResult()
    data class Failed(val error: String, val cause: Throwable? = null) : WorkerDeletionResult()
    data class NotFound(val workerId: WorkerId) : WorkerDeletionResult()
}

/**
 * Result of worker status check
 */
sealed class WorkerStatusResult {
    data class Success(val worker: Worker) : WorkerStatusResult()
    data class NotFound(val workerId: WorkerId) : WorkerStatusResult()
    data class Failed(val error: String, val cause: Throwable? = null) : WorkerStatusResult()
}

/**
 * Template validation result
 */
sealed class TemplateValidationResult {
    object Valid : TemplateValidationResult()
    data class Invalid(val errors: List<String>) : TemplateValidationResult()
}

/**
 * Worker orchestration lifecycle events
 */
sealed class WorkerOrchestrationEvent {
    abstract val workerId: WorkerId
    abstract val poolId: WorkerPoolId
    abstract val timestamp: java.time.Instant
    
    data class WorkerCreated(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        val worker: Worker,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerStarted(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerReady(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerBusy(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        val jobId: String,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerStopped(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        val reason: String,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerFailed(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        val error: String,
        val cause: Throwable? = null,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
    
    data class WorkerDeleted(
        override val workerId: WorkerId,
        override val poolId: WorkerPoolId,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : WorkerOrchestrationEvent()
}

/**
 * Information about the orchestrator implementation
 */
data class OrchestratorInfo(
    val type: OrchestratorType,
    val version: String,
    val capabilities: Set<OrchestratorCapability>,
    val limits: OrchestratorLimits,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Type of orchestrator
 */
enum class OrchestratorType {
    KUBERNETES,
    DOCKER_SWARM,
    AWS_ECS,
    AWS_FARGATE,
    AZURE_CONTAINER_INSTANCES,
    GOOGLE_CLOUD_RUN,
    NOMAD,
    MESOS,
    LOCAL_DOCKER,
    PROCESS_BASED // For testing/development
}

/**
 * Capabilities supported by the orchestrator
 */
enum class OrchestratorCapability {
    AUTO_SCALING,
    PERSISTENT_STORAGE,
    LOAD_BALANCING,
    SERVICE_DISCOVERY,
    SECRETS_MANAGEMENT,
    NETWORK_POLICIES,
    RESOURCE_QUOTAS,
    NODE_AFFINITY,
    TOLERATIONS,
    VOLUME_MOUNTS,
    SECURITY_CONTEXTS,
    HEALTH_CHECKS,
    ROLLING_UPDATES,
    BATCH_JOBS
}

/**
 * Limits imposed by the orchestrator
 */
data class OrchestratorLimits(
    val maxWorkersPerPool: Int? = null,
    val maxTotalWorkers: Int? = null,
    val maxCpuPerWorker: String? = null,
    val maxMemoryPerWorker: String? = null,
    val maxVolumesPerWorker: Int? = null,
    val supportedVolumeTypes: Set<String> = emptySet(),
    val maxConcurrentCreations: Int? = null
)

/**
 * Health status of the orchestrator
 */
data class OrchestratorHealth(
    val status: HealthStatus,
    val message: String? = null,
    val details: Map<String, Any> = emptyMap(),
    val lastChecked: java.time.Instant = java.time.Instant.now()
)

enum class HealthStatus {
    HEALTHY,
    DEGRADED,
    UNHEALTHY,
    UNKNOWN
}