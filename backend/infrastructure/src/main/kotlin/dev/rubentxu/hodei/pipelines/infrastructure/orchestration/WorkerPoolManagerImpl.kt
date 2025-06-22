package dev.rubentxu.hodei.pipelines.infrastructure.orchestration

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of WorkerPoolManager that coordinates between orchestrator and resource manager
 * Provides high-level pool management with auto-scaling capabilities
 */
class WorkerPoolManagerImpl(
    private val workerOrchestrator: WorkerOrchestrator,
    private val resourceManager: ResourceManager
) : WorkerPoolManager {
    
    private val logger = KotlinLogging.logger {}
    private val pools = ConcurrentHashMap<WorkerPoolId, WorkerPool>()
    private val poolEvents = MutableSharedFlow<PoolEvent>()
    
    override suspend fun createPool(pool: WorkerPool): PoolCreationResult {
        return try {
            logger.info { "Creating worker pool: ${pool.name} (${pool.id.value})" }
            
            // Validate pool configuration
            val validationResult = validatePoolConfiguration(pool)
            if (validationResult.isNotEmpty()) {
                return PoolCreationResult.InvalidConfiguration(validationResult)
            }
            
            // Check resource constraints
            val resourceCheck = resourceManager.checkResourceAvailability(
                pool.template.resources, 
                pool.scalingPolicy.minWorkers
            )
            
            when (resourceCheck) {
                is ResourceAvailabilityCheck.Unavailable -> {
                    return PoolCreationResult.ResourceConstraints(resourceCheck.limitingFactors)
                }
                is ResourceAvailabilityCheck.PartiallyAvailable -> {
                    if (resourceCheck.canAccommodate < pool.scalingPolicy.minWorkers) {
                        return PoolCreationResult.ResourceConstraints(listOf(resourceCheck.limitingFactor))
                    }
                }
                else -> { /* Continue */ }
            }
            
            // Validate template with orchestrator
            val templateValidation = workerOrchestrator.validateTemplate(pool.template)
            if (templateValidation is TemplateValidationResult.Invalid) {
                return PoolCreationResult.InvalidConfiguration(templateValidation.errors)
            }
            
            // Store pool
            val activePool = pool.copy(
                status = WorkerPoolStatus.ACTIVE,
                desiredSize = pool.scalingPolicy.minWorkers
            )
            pools[pool.id] = activePool
            
            // Create initial workers if minimum required
            if (pool.scalingPolicy.minWorkers > 0) {
                scalePool(pool.id, pool.scalingPolicy.minWorkers, "Initial pool creation")
            }
            
            // Emit pool created event
            poolEvents.emit(PoolEvent.PoolCreated(pool.id, activePool))
            
            logger.info { "Successfully created pool: ${pool.name}" }
            PoolCreationResult.Success(activePool)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to create pool: ${pool.name}" }
            PoolCreationResult.Failed("Error creating pool: ${e.message}", e)
        }
    }
    
    override suspend fun deletePool(poolId: WorkerPoolId): PoolDeletionResult {
        return try {
            logger.info { "Deleting worker pool: ${poolId.value}" }
            
            val pool = pools[poolId] ?: return PoolDeletionResult.NotFound(poolId)
            
            // Mark pool as scaling down
            pools[poolId] = pool.copy(status = WorkerPoolStatus.SCALING_DOWN)
            
            // Delete all workers in the pool
            val workers = pool.workers
            var deletedCount = 0
            
            workers.forEach { worker ->
                try {
                    val result = workerOrchestrator.deleteWorker(worker.id)
                    if (result is WorkerDeletionResult.Success) {
                        deletedCount++
                    }
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to delete worker ${worker.id.value}" }
                }
            }
            
            // Remove pool from registry
            pools.remove(poolId)
            
            // Emit pool deleted event
            poolEvents.emit(PoolEvent.PoolDeleted(poolId))
            
            logger.info { "Successfully deleted pool: ${poolId.value}, deleted $deletedCount workers" }
            PoolDeletionResult.Success(deletedCount)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete pool: ${poolId.value}" }
            PoolDeletionResult.Failed("Error deleting pool: ${e.message}", e)
        }
    }
    
    override suspend fun getPool(poolId: WorkerPoolId): WorkerPool? {
        return pools[poolId]?.let { pool ->
            // Refresh worker list from orchestrator
            val currentWorkers = workerOrchestrator.listWorkers(poolId)
            pool.copy(
                workers = currentWorkers,
                currentSize = currentWorkers.size,
                lastModified = Instant.now()
            ).also { updatedPool ->
                pools[poolId] = updatedPool
            }
        }
    }
    
    override suspend fun listPools(): List<WorkerPool> {
        return pools.values.map { pool ->
            // Refresh each pool's worker list
            val currentWorkers = workerOrchestrator.listWorkers(pool.id)
            pool.copy(
                workers = currentWorkers,
                currentSize = currentWorkers.size,
                lastModified = Instant.now()
            ).also { updatedPool ->
                pools[pool.id] = updatedPool
            }
        }
    }
    
    override suspend fun updatePool(pool: WorkerPool): PoolUpdateResult {
        return try {
            val existingPool = pools[pool.id] ?: return PoolUpdateResult.NotFound(pool.id)
            
            // Validate updated configuration
            val validationResult = validatePoolConfiguration(pool)
            if (validationResult.isNotEmpty()) {
                return PoolUpdateResult.InvalidConfiguration(validationResult)
            }
            
            // Update pool
            val updatedPool = pool.copy(lastModified = Instant.now())
            pools[pool.id] = updatedPool
            
            logger.info { "Updated pool: ${pool.name}" }
            PoolUpdateResult.Success(updatedPool)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to update pool: ${pool.id.value}" }
            PoolUpdateResult.Failed("Error updating pool: ${e.message}", e)
        }
    }
    
    override suspend fun scalePool(poolId: WorkerPoolId, desiredSize: Int, reason: String): PoolScalingResult {
        return try {
            val pool = pools[poolId] ?: return PoolScalingResult.Failed("Pool not found: ${poolId.value}")
            
            val currentSize = pool.currentSize
            
            if (currentSize == desiredSize) {
                return PoolScalingResult.NoActionNeeded(currentSize, "Already at desired size")
            }
            
            logger.info { "Scaling pool ${poolId.value} from $currentSize to $desiredSize workers. Reason: $reason" }
            
            // Update pool status
            val scalingPool = pool.copy(
                status = if (desiredSize > currentSize) WorkerPoolStatus.SCALING_UP else WorkerPoolStatus.SCALING_DOWN,
                desiredSize = desiredSize,
                lastModified = Instant.now()
            )
            pools[poolId] = scalingPool
            
            val workersAffected = mutableListOf<WorkerId>()
            var actualSize = currentSize
            
            if (desiredSize > currentSize) {
                // Scale up - create new workers
                val workersToCreate = desiredSize - currentSize
                
                // Check resource availability
                val resourceCheck = resourceManager.checkResourceAvailability(pool.template.resources, workersToCreate)
                
                when (resourceCheck) {
                    is ResourceAvailabilityCheck.Available -> {
                        // Create all requested workers
                        repeat(workersToCreate) { i ->
                            try {
                                val result = workerOrchestrator.createWorker(pool.template, poolId)
                                if (result is WorkerCreationResult.Success) {
                                    workersAffected.add(result.worker.id)
                                    actualSize++
                                    
                                    // Update pool with new worker
                                    val updatedPool = pools[poolId]?.addWorker(result.worker)
                                    if (updatedPool != null) {
                                        pools[poolId] = updatedPool
                                    }
                                    
                                    // Emit worker added event
                                    poolEvents.emit(PoolEvent.WorkerAdded(poolId, result.worker.id, actualSize))
                                    
                                } else {
                                    logger.warn { "Failed to create worker ${i + 1}/$workersToCreate: $result" }
                                }
                            } catch (e: Exception) {
                                logger.error(e) { "Error creating worker ${i + 1}/$workersToCreate" }
                            }
                        }
                    }
                    
                    is ResourceAvailabilityCheck.PartiallyAvailable -> {
                        // Create only what we can
                        repeat(resourceCheck.canAccommodate) { i ->
                            try {
                                val result = workerOrchestrator.createWorker(pool.template, poolId)
                                if (result is WorkerCreationResult.Success) {
                                    workersAffected.add(result.worker.id)
                                    actualSize++
                                    
                                    val updatedPool = pools[poolId]?.addWorker(result.worker)
                                    if (updatedPool != null) {
                                        pools[poolId] = updatedPool
                                    }
                                    
                                    poolEvents.emit(PoolEvent.WorkerAdded(poolId, result.worker.id, actualSize))
                                }
                            } catch (e: Exception) {
                                logger.error(e) { "Error creating worker ${i + 1}/${resourceCheck.canAccommodate}" }
                            }
                        }
                        
                        // Update desired size to what we actually achieved
                        pools[poolId] = pools[poolId]!!.copy(desiredSize = actualSize, status = WorkerPoolStatus.ACTIVE)
                        
                        return PoolScalingResult.Partial(
                            fromSize = currentSize,
                            actualSize = actualSize,
                            targetSize = desiredSize,
                            reason = "Resource constraints: ${resourceCheck.limitingFactor.description}"
                        )
                    }
                    
                    is ResourceAvailabilityCheck.Unavailable -> {
                        return PoolScalingResult.ResourceConstraints(resourceCheck.limitingFactors)
                    }
                }
                
            } else {
                // Scale down - remove workers
                val workersToRemove = currentSize - desiredSize
                val currentWorkers = pool.workers.toMutableList()
                
                // Remove workers (prefer idle workers first)
                val workersToDelete = currentWorkers
                    .sortedBy { if (it.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY) 0 else 1 }
                    .take(workersToRemove)
                
                workersToDelete.forEach { worker ->
                    try {
                        val result = workerOrchestrator.deleteWorker(worker.id)
                        if (result is WorkerDeletionResult.Success) {
                            workersAffected.add(worker.id)
                            actualSize--
                            
                            // Update pool
                            val updatedPool = pools[poolId]?.removeWorker(worker.id)
                            if (updatedPool != null) {
                                pools[poolId] = updatedPool
                            }
                            
                            // Emit worker removed event
                            poolEvents.emit(PoolEvent.WorkerRemoved(poolId, worker.id, "Scale down", actualSize))
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error deleting worker ${worker.id.value}" }
                    }
                }
            }
            
            // Update pool status back to active
            pools[poolId] = pools[poolId]!!.copy(
                status = WorkerPoolStatus.ACTIVE,
                desiredSize = actualSize,
                lastModified = Instant.now()
            )
            
            // Emit scaling event
            poolEvents.emit(PoolEvent.PoolScaled(poolId, currentSize, actualSize, reason))
            
            if (actualSize == desiredSize) {
                PoolScalingResult.Success(currentSize, actualSize, workersAffected)
            } else {
                PoolScalingResult.Partial(currentSize, actualSize, desiredSize, "Partial scaling completed")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to scale pool: ${poolId.value}" }
            PoolScalingResult.Failed("Error scaling pool: ${e.message}", e)
        }
    }
    
    override suspend fun getAvailableWorkers(): List<Worker> {
        return pools.values.flatMap { pool ->
            pool.getAvailableWorkers()
        }
    }
    
    override suspend fun getAvailableWorkers(poolId: WorkerPoolId): List<Worker> {
        return pools[poolId]?.getAvailableWorkers() ?: emptyList()
    }
    
    override suspend fun findBestPoolForJob(requirements: WorkerRequirements): WorkerPool? {
        return pools.values
            .filter { pool -> 
                pool.template.matches(requirements) && 
                pool.status == WorkerPoolStatus.ACTIVE 
            }
            .maxByOrNull { pool ->
                // Score pools based on available workers and template match quality
                val availableWorkers = pool.getAvailableWorkers().size
                val canScale = pool.canScaleUp()
                
                when {
                    availableWorkers > 0 -> 100 + availableWorkers * 10 // Prefer pools with available workers
                    canScale -> 50 // Pools that can scale
                    else -> 0 // Pools at capacity
                }
            }
    }
    
    override fun streamPoolEvents(): Flow<PoolEvent> = poolEvents.asSharedFlow()
    
    override suspend fun getPoolMetrics(poolId: WorkerPoolId): PoolMetrics {
        val pool = pools[poolId] ?: throw IllegalArgumentException("Pool not found: ${poolId.value}")
        val poolUsage = resourceManager.getPoolResourceUsage(poolId)
        
        val workers = pool.workers
        val availableWorkers = workers.count { it.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY }
        val busyWorkers = workers.count { it.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.BUSY }
        val failedWorkers = workers.count { it.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.FAILED }
        
        return PoolMetrics(
            poolId = poolId,
            currentSize = pool.currentSize,
            desiredSize = pool.desiredSize,
            availableWorkers = availableWorkers,
            busyWorkers = busyWorkers,
            failedWorkers = failedWorkers,
            averageJobDuration = Duration.ofMinutes(10), // TODO: Calculate from actual job history
            jobsCompleted = 0, // TODO: Track from job execution
            jobsFailed = 0, // TODO: Track from job execution
            resourceUtilization = ResourceUtilization(
                cpu = poolUsage.averageCpuUtilization,
                memory = poolUsage.averageMemoryUtilization,
                nodes = 0.5 // TODO: Calculate actual node utilization
            ),
            scalingHistory = listOf(), // TODO: Track scaling history
            uptime = Duration.between(pool.createdAt, Instant.now()),
            lastScaleAction = pool.scalingPolicy.lastScaleAction?.timestamp
        )
    }
    
    override suspend fun getOverallMetrics(): OverallPoolMetrics {
        val allPools = pools.values
        val totalWorkers = allPools.sumOf { it.workers.size }
        val availableWorkers = allPools.sumOf { it.getAvailableWorkers().size }
        val busyWorkers = allPools.sumOf { it.getBusyWorkers().size }
        
        val avgUtilization = if (allPools.isNotEmpty()) {
            allPools.map { pool ->
                val available = pool.getAvailableWorkers().size
                val total = pool.workers.size
                if (total > 0) (total - available).toDouble() / total else 0.0
            }.average()
        } else 0.0
        
        val resourceAvailability = resourceManager.getResourceAvailability()
        
        return OverallPoolMetrics(
            totalPools = allPools.size,
            totalWorkers = totalWorkers,
            availableWorkers = availableWorkers,
            busyWorkers = busyWorkers,
            totalJobsCompleted = 0, // TODO: Aggregate from all pools
            totalJobsFailed = 0, // TODO: Aggregate from all pools
            averagePoolUtilization = avgUtilization,
            resourceUtilization = resourceAvailability.getResourceUtilization()
        )
    }
    
    override suspend fun evaluateAutoScaling(): List<AutoScalingEvaluation> {
        return pools.values.map { pool ->
            // Simple queue stats - in real implementation would come from job queue
            val queueStats = QueueStats(
                totalJobs = 0,
                priorityBreakdown = emptyMap(),
                averageWaitTime = Duration.ZERO,
                oldestJob = null,
                criticalJobsCount = 0,
                expiredJobsCount = 0
            )
            
            evaluatePoolScaling(pool.id, queueStats)
        }
    }
    
    override suspend fun evaluatePoolScaling(poolId: WorkerPoolId, queueStats: QueueStats): AutoScalingEvaluation {
        val pool = pools[poolId] ?: throw IllegalArgumentException("Pool not found: ${poolId.value}")
        val resourceAvailability = resourceManager.getResourceAvailability()
        
        val currentSize = pool.currentSize
        val desiredSize = pool.calculateDesiredSize(queueStats.totalJobs, queueStats.averageWaitTime)
        
        val action = when {
            desiredSize > currentSize && pool.canScaleUp() -> ScalingAction.SCALE_UP
            desiredSize < currentSize && pool.canScaleDown() -> ScalingAction.SCALE_DOWN
            desiredSize == currentSize -> ScalingAction.MAINTAIN
            else -> ScalingAction.INSUFFICIENT_DATA
        }
        
        val confidence = when {
            queueStats.totalJobs == 0 && currentSize == pool.scalingPolicy.minWorkers -> 0.9
            queueStats.totalJobs > 0 -> 0.8
            else -> 0.5
        }
        
        val reason = when (action) {
            ScalingAction.SCALE_UP -> "Queue length (${queueStats.totalJobs}) exceeds threshold"
            ScalingAction.SCALE_DOWN -> "Low queue demand, can reduce workers"
            ScalingAction.MAINTAIN -> "Current size optimal for demand"
            ScalingAction.INSUFFICIENT_DATA -> "Not enough data for scaling decision"
        }
        
        val metrics = ScalingMetrics(
            queueLength = queueStats.totalJobs,
            averageWaitTime = queueStats.averageWaitTime,
            workerUtilization = pool.getBusyWorkers().size.toDouble() / maxOf(1, pool.workers.size),
            resourceUtilization = resourceAvailability.getResourceUtilization(),
            jobCompletionRate = 0.95, // TODO: Calculate from actual metrics
            errorRate = 0.05 // TODO: Calculate from actual metrics
        )
        
        return AutoScalingEvaluation(
            poolId = poolId,
            currentSize = currentSize,
            recommendedSize = desiredSize,
            action = action,
            reason = reason,
            confidence = confidence,
            metrics = metrics
        )
    }
    
    private fun validatePoolConfiguration(pool: WorkerPool): List<String> {
        val errors = mutableListOf<String>()
        
        if (pool.name.isBlank()) {
            errors.add("Pool name cannot be blank")
        }
        
        if (pool.scalingPolicy.minWorkers < 0) {
            errors.add("Minimum workers cannot be negative")
        }
        
        if (pool.scalingPolicy.maxWorkers < pool.scalingPolicy.minWorkers) {
            errors.add("Maximum workers must be greater than or equal to minimum workers")
        }
        
        if (pool.maxSize < pool.scalingPolicy.maxWorkers) {
            errors.add("Pool max size must be greater than or equal to scaling policy max workers")
        }
        
        return errors
    }
}