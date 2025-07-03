package dev.rubentxu.hodei.resourcemanagement.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceQuotas
import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class ResourcePoolService(
    private val repository: ResourcePoolRepository,
    private val workerManager: WorkerManagerService
) {

    suspend fun createPool(
        name: String,
        displayName: String? = null,
        description: String? = null,
        resourceQuotas: ResourceQuotas = ResourceQuotas.basic(),
        labels: Map<String, String> = emptyMap(),
        annotations: Map<String, String> = emptyMap(),
        createdBy: String
    ): Either<String, ResourcePool> {
        logger.info { "Creating resource pool: $name" }

        // Validate name doesn't exist
        if (repository.exists(name)) {
            return "Resource pool with name '$name' already exists".left()
        }

        val now = kotlinx.datetime.Clock.System.now()
        val pool = ResourcePool(
            id = DomainId.generate(),
            name = name,
            displayName = displayName ?: name,
            description = description,
            resourceQuotas = resourceQuotas,
            labels = labels,
            annotations = annotations,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )

        return repository.save(pool).fold(
            { error -> "Failed to create resource pool: ${error.message}".left() },
            { savedPool ->
                logger.info { "Created resource pool: ${savedPool.name} (${savedPool.id})" }
                savedPool.right()
            }
        )
    }

    suspend fun getPool(poolId: DomainId): Either<String, ResourcePool> {
        return repository.findById(poolId).fold(
            { error -> "Failed to get resource pool: ${error.message}".left() },
            { pool -> 
                if (pool != null) {
                    pool.right()
                } else {
                    "Resource pool not found: $poolId".left()
                }
            }
        )
    }

    suspend fun getPoolByName(name: String): Either<String, ResourcePool> {
        val pool = repository.findByName(name)
        return if (pool != null) {
            pool.right()
        } else {
            "Resource pool not found: $name".left()
        }
    }

    suspend fun listPools(): List<ResourcePool> {
        return repository.findAll()
    }

    suspend fun listActivePools(): List<ResourcePool> {
        return repository.findByStatus(PoolStatus.ACTIVE)
    }

    suspend fun updateQuotas(
        poolId: DomainId,
        newQuotas: ResourceQuotas
    ): Either<String, ResourcePool> {
        val pool = repository.findById(poolId).fold(
            { return "Failed to find pool: ${it.message}".left() },
            { it ?: return "Resource pool not found: $poolId".left() }
        )

        val updatedPool = pool.updateQuotas(newQuotas)
        return repository.update(updatedPool).fold(
            { error -> "Failed to update resource pool quotas: ${error.message}".left() },
            { savedPool ->
                logger.info { "Updated quotas for resource pool: ${savedPool.name}" }
                savedPool.right()
            }
        )
    }

    suspend fun addLabel(
        poolId: DomainId,
        key: String,
        value: String
    ): Either<String, ResourcePool> {
        val pool = repository.findById(poolId).fold(
            { return "Failed to find pool: ${it.message}".left() },
            { it ?: return "Resource pool not found: $poolId".left() }
        )

        val updatedPool = pool.addLabel(key, value)
        return repository.update(updatedPool).fold(
            { error -> "Failed to add label to resource pool: ${error.message}".left() },
            { savedPool -> savedPool.right() }
        )
    }

    suspend fun removeLabel(
        poolId: DomainId,
        key: String
    ): Either<String, ResourcePool> {
        val pool = repository.findById(poolId).fold(
            { return "Failed to find pool: ${it.message}".left() },
            { it ?: return "Resource pool not found: $poolId".left() }
        )

        val updatedPool = pool.removeLabel(key)
        return repository.update(updatedPool).fold(
            { error -> "Failed to remove label from resource pool: ${error.message}".left() },
            { savedPool -> savedPool.right() }
        )
    }

    suspend fun deletePool(poolId: DomainId): Either<String, Unit> {
        val pool = repository.findById(poolId).fold(
            { return "Failed to find pool: ${it.message}".left() },
            { it ?: return "Resource pool not found: $poolId".left() }
        )

        // Check if pool has active workers
        val workersInPool = workerManager.getWorkersByPool(pool.name)
        if (workersInPool.isNotEmpty()) {
            return "Cannot delete resource pool with active workers. Found ${workersInPool.size} workers in pool '${pool.name}'".left()
        }

        // Mark pool as terminating first
        val terminatingPool = pool.updateStatus(PoolStatus.TERMINATING)
        repository.update(terminatingPool).fold(
            { error -> return "Failed to mark pool as terminating: ${error.message}".left() },
            { /* continue */ }
        )

        // Now delete the pool
        return repository.delete(poolId).fold(
            { error -> "Failed to delete resource pool: ${error.message}".left() },
            {
                logger.info { "Deleted resource pool: ${pool.name} (${pool.id})" }
                Unit.right()
            }
        )
    }

    suspend fun getPoolResourceUsage(poolName: String): Either<String, ResourcePoolUsage> {
        val pool = repository.findByName(poolName)
        if (pool == null) {
            return "Resource pool not found: $poolName".left()
        }

        val workers = workerManager.getWorkersByPool(poolName)
        val activeWorkers = workers.count { it.isAvailable || it.status == dev.rubentxu.hodei.domain.worker.WorkerStatus.BUSY }

        // Calculate resource usage from workers
        var totalCpuRequests = 0.0
        var totalMemoryRequests = 0.0
        var totalStorageRequests = 0.0

        workers.forEach { worker ->
            // Parse CPU (assume "2" means 2 cores)
            totalCpuRequests += worker.capabilities.cpu.toDoubleOrNull() ?: 0.0
            
            // Parse memory (assume "4Gi" format)
            val memoryValue = worker.capabilities.memory.replace("Gi", "").toDoubleOrNull() ?: 0.0
            totalMemoryRequests += memoryValue
            
            // Parse storage (assume "20Gi" format) 
            val storageValue = worker.capabilities.storage.replace("Gi", "").toDoubleOrNull() ?: 0.0
            totalStorageRequests += storageValue
        }

        val usage = ResourcePoolUsage(
            poolName = poolName,
            activeWorkers = activeWorkers,
            totalWorkers = workers.size,
            cpuRequests = "${totalCpuRequests.toInt()}",
            memoryRequests = "${totalMemoryRequests.toInt()}Gi",
            storageRequests = "${totalStorageRequests.toInt()}Gi",
            quotas = pool.resourceQuotas
        )

        return usage.right()
    }

    suspend fun checkQuotaViolations(poolName: String): List<String> {
        val pool = repository.findByName(poolName) ?: return listOf("Pool not found: $poolName")
        val workers = workerManager.getWorkersByPool(poolName)
        
        val violations = mutableListOf<String>()
        
        pool.resourceQuotas.maxWorkers?.let { maxWorkers ->
            if (workers.size > maxWorkers) {
                violations.add("Worker count (${workers.size}) exceeds limit ($maxWorkers)")
            }
        }
        
        // Add more quota checks as needed
        
        return violations
    }
}

data class ResourcePoolUsage(
    val poolName: String,
    val activeWorkers: Int,
    val totalWorkers: Int,
    val cpuRequests: String,
    val memoryRequests: String,
    val storageRequests: String,
    val quotas: ResourceQuotas
)