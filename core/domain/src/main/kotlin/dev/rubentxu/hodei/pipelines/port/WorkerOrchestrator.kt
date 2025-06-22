package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import kotlinx.coroutines.flow.Flow

/**
 * Port for Worker Orchestration - Hexagonal Architecture Port
 * 
 * This is the PRIMARY PORT (driving port) for worker orchestration operations.
 * It defines the contract that any orchestrator implementation must fulfill.
 * 
 * Following Dependency Inversion Principle:
 * - High-level modules (application layer) depend on this abstraction
 * - Low-level modules (infrastructure layer) implement this abstraction
 * 
 * Implementations could be: Kubernetes, Docker Swarm, AWS ECS, Azure Container Instances, etc.
 */
interface WorkerOrchestrator {
    
    /**
     * Create a new worker based on template
     * 
     * This operation should be idempotent and handle retries gracefully.
     * Resource allocation and scheduling are orchestrator-specific concerns.
     * 
     * @param template The worker template containing all configuration
     * @param poolId The pool this worker belongs to for organization
     * @return Functional result avoiding exceptions for expected failures
     */
    suspend fun createWorker(template: WorkerTemplate, poolId: WorkerPoolId): WorkerCreationResult
    
    /**
     * Delete/terminate a worker gracefully
     * 
     * Should attempt graceful shutdown first, then force termination if needed.
     * Must handle cases where worker is already deleted or not found.
     * 
     * @param workerId The worker to terminate
     * @return Result indicating success or specific failure reasons
     */
    suspend fun deleteWorker(workerId: WorkerId): WorkerDeletionResult
    
    /**
     * Get current status of a specific worker
     * 
     * Provides real-time status from the orchestration platform.
     * Should include resource usage and health information.
     * 
     * @param workerId The worker to check
     * @return Current worker status or failure reasons
     */
    suspend fun getWorkerStatus(workerId: WorkerId): WorkerStatusResult
    
    /**
     * List all workers in a specific pool
     * 
     * Used for pool-specific operations and monitoring.
     * Should return empty list rather than failure for non-existent pools.
     * 
     * @param poolId The pool to list workers from
     * @return List of workers in the pool (empty if pool doesn't exist)
     */
    suspend fun listWorkers(poolId: WorkerPoolId): List<Worker>
    
    /**
     * List all workers across all pools
     * 
     * Used for global monitoring and resource management.
     * May be expensive for large clusters - use with caution.
     * 
     * @return List of all workers managed by this orchestrator
     */
    suspend fun listAllWorkers(): List<Worker>
    
    /**
     * Get current resource availability in the cluster
     * 
     * Critical for scheduling decisions and capacity planning.
     * Should provide real-time resource information.
     * 
     * @return Current resource availability and constraints
     */
    suspend fun getResourceAvailability(): ResourceAvailability
    
    /**
     * Stream worker lifecycle events in real-time
     * 
     * Provides observability into worker state changes.
     * Used for monitoring, alerting, and reactive operations.
     * 
     * @return Flow of worker events (creation, status changes, deletion, etc.)
     */
    fun watchWorkerEvents(): Flow<WorkerOrchestrationEvent>
    
    /**
     * Validate worker template before creation
     * 
     * Performs orchestrator-specific validation of template configuration.
     * Should check resource limits, security policies, and platform constraints.
     * 
     * @param template The template to validate
     * @return Validation result with specific error details if invalid
     */
    suspend fun validateTemplate(template: WorkerTemplate): TemplateValidationResult
    
    /**
     * Get orchestrator implementation information
     * 
     * Provides metadata about capabilities, limits, and configuration.
     * Used for feature detection and compatibility checks.
     * 
     * @return Information about the orchestrator implementation
     */
    suspend fun getOrchestratorInfo(): OrchestratorInfo
    
    /**
     * Health check for the orchestrator connection
     * 
     * Verifies that the orchestrator is reachable and functioning.
     * Should perform lightweight connectivity and permission checks.
     * 
     * @return Health status with diagnostic information
     */
    suspend fun healthCheck(): OrchestratorHealth
}

