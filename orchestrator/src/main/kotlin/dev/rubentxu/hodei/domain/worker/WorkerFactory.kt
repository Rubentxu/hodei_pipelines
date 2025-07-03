package dev.rubentxu.hodei.domain.worker

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.errors.DomainError
import dev.rubentxu.hodei.shared.domain.errors.WorkerCreationError
import dev.rubentxu.hodei.shared.domain.errors.WorkerDeletionError
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool

/**
 * Factory interface for creating and managing worker instances.
 * This abstracts the complexity of provisioning workers across different
 * infrastructure types (Kubernetes, Docker, Cloud VMs, etc.)
 */
interface WorkerFactory {
    
    /**
     * Creates a new worker instance for executing a job in the specified resource pool.
     * 
     * @param job The job that will be executed by the worker
     * @param resourcePool The resource pool where the worker should be provisioned
     * @return Either an error or the created worker instance
     */
    suspend fun createWorker(
        job: Job,
        resourcePool: ResourcePool
    ): Either<WorkerCreationError, WorkerInstance>
    
    /**
     * Destroys a worker instance and releases its resources.
     * 
     * @param workerId The ID of the worker to destroy
     * @return Either an error or Unit on success
     */
    suspend fun destroyWorker(
        workerId: String
    ): Either<WorkerDeletionError, Unit>
    
    /**
     * Checks if the factory supports creating workers for the given resource pool type.
     * 
     * @param poolType The type of resource pool (e.g., "kubernetes", "docker", "vm")
     * @return true if the factory can create workers for this pool type
     */
    fun supportsPoolType(poolType: String): Boolean
}

/**
 * Represents a created worker instance with its metadata
 */
data class WorkerInstance(
    val workerId: String,
    val poolId: String,
    val poolType: String,
    val instanceType: String,
    val metadata: Map<String, String> = emptyMap()
)

