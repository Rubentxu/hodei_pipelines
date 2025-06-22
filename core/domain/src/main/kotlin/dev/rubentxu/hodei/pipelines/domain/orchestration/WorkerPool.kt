package dev.rubentxu.hodei.pipelines.domain.orchestration

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import java.time.Instant

/**
 * Worker Pool ID
 */
@JvmInline
value class WorkerPoolId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkerPoolId cannot be blank" }
    }
}

/**
 * Worker Pool - Manages a group of workers with shared characteristics
 */
data class WorkerPool(
    val id: WorkerPoolId,
    val name: String,
    val template: WorkerTemplate,
    val currentSize: Int = 0,
    val desiredSize: Int = 0,
    val maxSize: Int = 10,
    val scalingPolicy: ScalingPolicy,
    val workers: List<Worker> = emptyList(),
    val status: WorkerPoolStatus = WorkerPoolStatus.INACTIVE,
    val createdAt: Instant = Instant.now(),
    val lastModified: Instant = Instant.now()
) {
    
    /**
     * Get available (ready) workers in the pool
     */
    fun getAvailableWorkers(): List<Worker> = 
        workers.filter { it.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY }
    
    /**
     * Get busy workers in the pool
     */
    fun getBusyWorkers(): List<Worker> = 
        workers.filter { it.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.BUSY }
    
    /**
     * Check if pool can scale up
     */
    fun canScaleUp(): Boolean = currentSize < maxSize
    
    /**
     * Check if pool can scale down
     */
    fun canScaleDown(): Boolean = currentSize > scalingPolicy.minWorkers
    
    /**
     * Calculate desired workers based on scaling policy
     */
    fun calculateDesiredSize(queueLength: Int, avgWaitTime: java.time.Duration): Int {
        val scaleUpNeeded = scalingPolicy.shouldScaleUp(queueLength, avgWaitTime, getAvailableWorkers().size)
        val scaleDownNeeded = scalingPolicy.shouldScaleDown(queueLength, avgWaitTime, getAvailableWorkers().size)
        
        return when {
            scaleUpNeeded && canScaleUp() -> minOf(currentSize + 1, maxSize)
            scaleDownNeeded && canScaleDown() -> maxOf(currentSize - 1, scalingPolicy.minWorkers)
            else -> currentSize
        }
    }
    
    /**
     * Add worker to pool
     */
    fun addWorker(worker: Worker): WorkerPool = 
        copy(
            workers = workers + worker,
            currentSize = workers.size + 1,
            lastModified = Instant.now()
        )
    
    /**
     * Remove worker from pool
     */
    fun removeWorker(workerId: WorkerId): WorkerPool {
        val updatedWorkers = workers.filterNot { it.id == workerId }
        return copy(
            workers = updatedWorkers,
            currentSize = updatedWorkers.size,
            lastModified = Instant.now()
        )
    }
    
    /**
     * Update worker status in pool
     */
    fun updateWorkerStatus(workerId: WorkerId, status: dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus): WorkerPool {
        val updatedWorkers = workers.map { worker ->
            if (worker.id == workerId) {
                worker.copy(status = status)
            } else {
                worker
            }
        }
        return copy(workers = updatedWorkers, lastModified = Instant.now())
    }
}

/**
 * Worker Pool Status
 */
enum class WorkerPoolStatus {
    INACTIVE,     // Pool not active
    ACTIVE,       // Pool actively managing workers
    SCALING_UP,   // Pool scaling up workers
    SCALING_DOWN, // Pool scaling down workers
    ERROR         // Pool in error state
}