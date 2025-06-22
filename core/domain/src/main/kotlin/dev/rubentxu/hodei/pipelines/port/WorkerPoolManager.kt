package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import kotlinx.coroutines.flow.Flow

/**
 * Port for Worker Pool Management - Abstract interface for managing worker pools
 * This is the high-level orchestration logic that coordinates between scheduler, orchestrator, and resource manager
 */
interface WorkerPoolManager {
    
    /**
     * Create a new worker pool
     * @param pool The worker pool configuration
     * @return Result of pool creation
     */
    suspend fun createPool(pool: WorkerPool): PoolCreationResult
    
    /**
     * Delete a worker pool and all its workers
     * @param poolId The pool to delete
     * @return Result of pool deletion
     */
    suspend fun deletePool(poolId: WorkerPoolId): PoolDeletionResult
    
    /**
     * Get worker pool by ID
     * @param poolId The pool ID
     * @return The worker pool if found
     */
    suspend fun getPool(poolId: WorkerPoolId): WorkerPool?
    
    /**
     * List all worker pools
     * @return List of all worker pools
     */
    suspend fun listPools(): List<WorkerPool>
    
    /**
     * Update worker pool configuration
     * @param pool Updated pool configuration
     * @return Result of pool update
     */
    suspend fun updatePool(pool: WorkerPool): PoolUpdateResult
    
    /**
     * Scale a worker pool to desired size
     * @param poolId The pool to scale
     * @param desiredSize Target number of workers
     * @param reason Reason for scaling
     * @return Result of scaling operation
     */
    suspend fun scalePool(poolId: WorkerPoolId, desiredSize: Int, reason: String): PoolScalingResult
    
    /**
     * Get available workers across all pools
     * @return List of available workers
     */
    suspend fun getAvailableWorkers(): List<Worker>
    
    /**
     * Get available workers from a specific pool
     * @param poolId The pool to get workers from
     * @return List of available workers in the pool
     */
    suspend fun getAvailableWorkers(poolId: WorkerPoolId): List<Worker>
    
    /**
     * Find best pool for job requirements
     * @param requirements Job requirements
     * @return Best matching pool or null if none suitable
     */
    suspend fun findBestPoolForJob(requirements: WorkerRequirements): WorkerPool?
    
    /**
     * Stream pool events (scaling, worker changes, etc.)
     * @return Flow of pool events
     */
    fun streamPoolEvents(): Flow<PoolEvent>
    
    /**
     * Get pool metrics and statistics
     * @param poolId The pool to get metrics for
     * @return Pool metrics
     */
    suspend fun getPoolMetrics(poolId: WorkerPoolId): PoolMetrics
    
    /**
     * Get overall pool management metrics
     * @return Overall metrics across all pools
     */
    suspend fun getOverallMetrics(): OverallPoolMetrics
    
    /**
     * Trigger auto-scaling evaluation for all pools
     * @return Results of auto-scaling evaluation
     */
    suspend fun evaluateAutoScaling(): List<AutoScalingEvaluation>
    
    /**
     * Manually trigger scaling for a specific pool
     * @param poolId The pool to evaluate
     * @param queueStats Current queue statistics for decision making
     * @return Auto-scaling evaluation result
     */
    suspend fun evaluatePoolScaling(poolId: WorkerPoolId, queueStats: QueueStats): AutoScalingEvaluation
}

/**
 * Pool creation result
 */
sealed class PoolCreationResult {
    data class Success(val pool: WorkerPool) : PoolCreationResult()
    data class Failed(val error: String, val cause: Throwable? = null) : PoolCreationResult()
    data class InvalidConfiguration(val errors: List<String>) : PoolCreationResult()
    data class ResourceConstraints(val constraints: List<ResourceConstraint>) : PoolCreationResult()
}

/**
 * Pool deletion result
 */
sealed class PoolDeletionResult {
    data class Success(val deletedWorkers: Int) : PoolDeletionResult()
    data class Failed(val error: String, val cause: Throwable? = null) : PoolDeletionResult()
    data class NotFound(val poolId: WorkerPoolId) : PoolDeletionResult()
}

/**
 * Pool update result
 */
sealed class PoolUpdateResult {
    data class Success(val pool: WorkerPool) : PoolUpdateResult()
    data class Failed(val error: String, val cause: Throwable? = null) : PoolUpdateResult()
    data class NotFound(val poolId: WorkerPoolId) : PoolUpdateResult()
    data class InvalidConfiguration(val errors: List<String>) : PoolUpdateResult()
}

/**
 * Pool scaling result
 */
sealed class PoolScalingResult {
    data class Success(val fromSize: Int, val toSize: Int, val workersAffected: List<WorkerId>) : PoolScalingResult()
    data class Partial(val fromSize: Int, val actualSize: Int, val targetSize: Int, val reason: String) : PoolScalingResult()
    data class Failed(val error: String, val cause: Throwable? = null) : PoolScalingResult()
    data class NoActionNeeded(val currentSize: Int, val reason: String) : PoolScalingResult()
    data class ResourceConstraints(val constraints: List<ResourceConstraint>) : PoolScalingResult()
}

/**
 * Pool events for monitoring
 */
sealed class PoolEvent {
    abstract val poolId: WorkerPoolId
    abstract val timestamp: java.time.Instant
    
    data class PoolCreated(
        override val poolId: WorkerPoolId,
        val pool: WorkerPool,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : PoolEvent()
    
    data class PoolDeleted(
        override val poolId: WorkerPoolId,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : PoolEvent()
    
    data class PoolScaled(
        override val poolId: WorkerPoolId,
        val fromSize: Int,
        val toSize: Int,
        val reason: String,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : PoolEvent()
    
    data class WorkerAdded(
        override val poolId: WorkerPoolId,
        val workerId: WorkerId,
        val currentPoolSize: Int,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : PoolEvent()
    
    data class WorkerRemoved(
        override val poolId: WorkerPoolId,
        val workerId: WorkerId,
        val reason: String,
        val currentPoolSize: Int,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : PoolEvent()
    
    data class PoolError(
        override val poolId: WorkerPoolId,
        val error: String,
        val cause: Throwable? = null,
        override val timestamp: java.time.Instant = java.time.Instant.now()
    ) : PoolEvent()
}

/**
 * Pool metrics
 */
data class PoolMetrics(
    val poolId: WorkerPoolId,
    val currentSize: Int,
    val desiredSize: Int,
    val availableWorkers: Int,
    val busyWorkers: Int,
    val failedWorkers: Int,
    val averageJobDuration: java.time.Duration,
    val jobsCompleted: Int,
    val jobsFailed: Int,
    val resourceUtilization: ResourceUtilization,
    val scalingHistory: List<ScaleAction>,
    val uptime: java.time.Duration,
    val lastScaleAction: java.time.Instant?
)

/**
 * Overall pool management metrics
 */
data class OverallPoolMetrics(
    val totalPools: Int,
    val totalWorkers: Int,
    val availableWorkers: Int,
    val busyWorkers: Int,
    val totalJobsCompleted: Int,
    val totalJobsFailed: Int,
    val averagePoolUtilization: Double,
    val resourceUtilization: ResourceUtilization,
    val costMetrics: CostMetrics? = null
)

/**
 * Cost metrics for optimization
 */
data class CostMetrics(
    val totalCost: Double,
    val costPerHour: Double,
    val costPerJob: Double,
    val savingsFromScaling: Double,
    val currency: String = "USD"
)

/**
 * Auto-scaling evaluation result
 */
data class AutoScalingEvaluation(
    val poolId: WorkerPoolId,
    val currentSize: Int,
    val recommendedSize: Int,
    val action: ScalingAction,
    val reason: String,
    val confidence: Double, // 0.0 - 1.0
    val metrics: ScalingMetrics,
    val constraints: List<ResourceConstraint> = emptyList()
)

/**
 * Scaling action recommendation
 */
enum class ScalingAction {
    SCALE_UP,
    SCALE_DOWN,
    MAINTAIN,
    INSUFFICIENT_DATA
}

/**
 * Metrics used for scaling decisions
 */
data class ScalingMetrics(
    val queueLength: Int,
    val averageWaitTime: java.time.Duration,
    val workerUtilization: Double,
    val resourceUtilization: ResourceUtilization,
    val jobCompletionRate: Double,
    val errorRate: Double,
    val trends: ResourceTrends? = null
)