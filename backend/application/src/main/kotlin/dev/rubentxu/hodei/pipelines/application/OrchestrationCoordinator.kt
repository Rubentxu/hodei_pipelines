package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.application.OrchestrationEvent.*
import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.time.Duration
import java.time.Instant

/**
 * Central orchestration coordinator that manages the entire pipeline system
 * Integrates job scheduling, worker pool management, and resource orchestration
 */
class OrchestrationCoordinator(
    private val workerPoolManager: WorkerPoolManager,
    private val workerOrchestrator: WorkerOrchestrator,
    private val resourceManager: ResourceManager,
    private val jobExecutor: JobExecutor
) {
    
    private val logger = KotlinLogging.logger {}
    private val jobQueue = JobQueue(SchedulingStrategy.PRIORITY_BASED, maxQueueSize = 1000)
    private val coordinatorScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private var isRunning = false
    private val orchestrationEvents = MutableSharedFlow<OrchestrationEvent>()
    
    /**
     * Start the orchestration coordinator
     */
    suspend fun start() {
        if (isRunning) {
            logger.warn { "Orchestration coordinator is already running" }
            return
        }
        
        logger.info { "Starting Hodei Pipelines orchestration coordinator..." }
        isRunning = true
        
        // Start background coroutines
        coordinatorScope.launch { processJobQueue() }
        coordinatorScope.launch { monitorAndScale() }
        coordinatorScope.launch { collectMetrics() }
        
        logger.info { "Orchestration coordinator started successfully" }
        orchestrationEvents.emit(OrchestrationEvent.SystemStarted(Instant.now()))
    }
    
    /**
     * Stop the orchestration coordinator
     */
    suspend fun stop() {
        logger.info { "Stopping orchestration coordinator..." }
        isRunning = false
        coordinatorScope.cancel()
        logger.info { "Orchestration coordinator stopped" }
        orchestrationEvents.emit(OrchestrationEvent.SystemStopped(Instant.now()))
    }
    
    /**
     * Submit a job for execution
     */
    suspend fun submitJob(
        job: Job,
        priority: JobPriority = JobPriority.NORMAL,
        requirements: WorkerRequirements = WorkerRequirements(),
        deadline: Instant? = null
    ): JobSubmissionResult {
        return try {
            logger.info { "Submitting job: ${job.definition.name} (${job.id.value})" }
            
            val queueResult = jobQueue.enqueue(job, priority, requirements, deadline)
            
            when (queueResult) {
                is QueueResult.Success -> {
                    orchestrationEvents.emit(OrchestrationEvent.JobQueued(job.id, priority, queueResult.queueSize))
                    
                    // Trigger immediate scaling evaluation
                    evaluateScalingNeeds()
                    
                    JobSubmissionResult.Success(job.id, queueResult.queueSize)
                }
                is QueueResult.QueueFull -> JobSubmissionResult.QueueFull(queueResult.maxSize)
                is QueueResult.AlreadyQueued -> JobSubmissionResult.AlreadyQueued(queueResult.jobId)
                is QueueResult.InvalidJob -> JobSubmissionResult.InvalidJob(queueResult.reason)
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to submit job: ${job.id.value}" }
            JobSubmissionResult.Failed("Error submitting job: ${e.message}")
        }
    }
    
    /**
     * Create a new worker pool
     */
    suspend fun createWorkerPool(
        poolConfig: WorkerPoolConfiguration
    ): PoolCreationResult {
        logger.info { "Creating worker pool: ${poolConfig.name}" }
        
        val pool = WorkerPool(
            id = WorkerPoolId(poolConfig.name.lowercase().replace(" ", "-")),
            name = poolConfig.name,
            template = poolConfig.template,
            maxSize = poolConfig.maxSize,
            scalingPolicy = poolConfig.scalingPolicy
        )
        
        val result = workerPoolManager.createPool(pool)
        
        if (result is PoolCreationResult.Success) {
            orchestrationEvents.emit(OrchestrationEvent.PoolCreated(pool.id, pool.name))
        }
        
        return result
    }
    
    /**
     * Get system status
     */
    suspend fun getSystemStatus(): SystemStatus {
        val pools = workerPoolManager.listPools()
        val overallMetrics = workerPoolManager.getOverallMetrics()
        val queueStats = jobQueue.getQueueStats()
        val resourceAvailability = resourceManager.getResourceAvailability()
        val orchestratorHealth = workerOrchestrator.healthCheck()
        
        return SystemStatus(
            isRunning = isRunning,
            totalPools = pools.size,
            totalWorkers = overallMetrics.totalWorkers,
            availableWorkers = overallMetrics.availableWorkers,
            queuedJobs = queueStats.totalJobs,
            resourceUtilization = resourceAvailability.getResourceUtilization(),
            systemHealth = when (orchestratorHealth.status) {
                HealthStatus.HEALTHY -> SystemHealth.HEALTHY
                HealthStatus.DEGRADED -> SystemHealth.DEGRADED
                HealthStatus.UNHEALTHY -> SystemHealth.UNHEALTHY
                HealthStatus.UNKNOWN -> SystemHealth.UNKNOWN
            },
            uptime = if (isRunning) Duration.ofMillis(System.currentTimeMillis() - startTime) else Duration.ZERO
        )
    }
    
    /**
     * Get detailed system metrics
     */
    suspend fun getSystemMetrics(): SystemMetrics {
        val pools = workerPoolManager.listPools()
        val poolMetrics = pools.associate { pool ->
            pool.id to workerPoolManager.getPoolMetrics(pool.id)
        }
        
        return SystemMetrics(
            overallMetrics = workerPoolManager.getOverallMetrics(),
            poolMetrics = poolMetrics,
            queueStats = jobQueue.getQueueStats(),
            resourceAvailability = resourceManager.getResourceAvailability(),
            orchestratorInfo = workerOrchestrator.getOrchestratorInfo()
        )
    }
    
    /**
     * Stream orchestration events
     */
    fun streamEvents(): Flow<OrchestrationEvent> = orchestrationEvents.asSharedFlow()
    
    /**
     * Process jobs from the queue
     */
    private suspend fun processJobQueue() {
        logger.info { "Starting job queue processor" }
        
        while (isRunning) {
            try {
                val availableWorkers = workerPoolManager.getAvailableWorkers()
                
                if (availableWorkers.isNotEmpty()) {
                    val nextJob = jobQueue.getNextJob(availableWorkers)
                    
                    if (nextJob != null) {
                        logger.info { "Processing job: ${nextJob.job.definition.name}" }
                        
                        // Find best worker for the job
                        val bestWorker = findBestWorkerForJob(nextJob, availableWorkers)
                        
                        if (bestWorker != null) {
                            // Remove job from queue
                            jobQueue.dequeue(nextJob.job.id)
                            
                            // Execute job
                            coordinatorScope.launch {
                                executeJob(nextJob, bestWorker.id)
                            }
                            
                            orchestrationEvents.emit(
                                OrchestrationEvent.JobAssigned(nextJob.job.id, bestWorker.id)
                            )
                        } else {
                            logger.warn { "No suitable worker found for job: ${nextJob.job.id.value}" }
                        }
                    }
                }
                
                delay(1000) // Check every second
                
            } catch (e: Exception) {
                logger.error(e) { "Error in job queue processor" }
                delay(5000) // Wait longer on error
            }
        }
        
        logger.info { "Job queue processor stopped" }
    }
    
    /**
     * Monitor system and trigger auto-scaling
     */
    private suspend fun monitorAndScale() {
        logger.info { "Starting auto-scaling monitor" }
        
        while (isRunning) {
            try {
                evaluateScalingNeeds()
                delay(30_000) // Evaluate every 30 seconds
                
            } catch (e: Exception) {
                logger.error(e) { "Error in scaling monitor" }
                delay(60_000) // Wait longer on error
            }
        }
        
        logger.info { "Auto-scaling monitor stopped" }
    }
    
    /**
     * Collect and emit system metrics
     */
    private suspend fun collectMetrics() {
        logger.info { "Starting metrics collector" }
        
        while (isRunning) {
            try {
                val metrics = getSystemMetrics()
                orchestrationEvents.emit(OrchestrationEvent.MetricsCollected(metrics))
                
                delay(60_000) // Collect metrics every minute
                
            } catch (e: Exception) {
                logger.error(e) { "Error collecting metrics" }
                delay(120_000) // Wait longer on error
            }
        }
        
        logger.info { "Metrics collector stopped" }
    }
    
    /**
     * Evaluate scaling needs for all pools
     */
    private suspend fun evaluateScalingNeeds() {
        val queueStats = jobQueue.getQueueStats()
        val evaluations = workerPoolManager.evaluateAutoScaling()
        
        evaluations.forEach { evaluation ->
            when (evaluation.action) {
                ScalingAction.SCALE_UP -> {
                    logger.info { "Scaling up pool ${evaluation.poolId.value}: ${evaluation.currentSize} -> ${evaluation.recommendedSize}" }
                    
                    val result = workerPoolManager.scalePool(
                        evaluation.poolId,
                        evaluation.recommendedSize,
                        evaluation.reason
                    )
                    
                    orchestrationEvents.emit(OrchestrationEvent.AutoScalingTriggered(
                        poolId = evaluation.poolId,
                        action = evaluation.action,
                        fromSize = evaluation.currentSize,
                        toSize = evaluation.recommendedSize,
                        success = result is PoolScalingResult.Success
                    ))
                }
                
                ScalingAction.SCALE_DOWN -> {
                    // Be more conservative with scale down
                    if (evaluation.confidence > 0.8) {
                        logger.info { "Scaling down pool ${evaluation.poolId.value}: ${evaluation.currentSize} -> ${evaluation.recommendedSize}" }
                        
                        val result = workerPoolManager.scalePool(
                            evaluation.poolId,
                            evaluation.recommendedSize,
                            evaluation.reason
                        )
                        
                        orchestrationEvents.emit(OrchestrationEvent.AutoScalingTriggered(
                            poolId = evaluation.poolId,
                            action = evaluation.action,
                            fromSize = evaluation.currentSize,
                            toSize = evaluation.recommendedSize,
                            success = result is PoolScalingResult.Success
                        ))
                    }
                }
                
                else -> { /* No action needed */ }
            }
        }
    }
    
    /**
     * Find the best worker for a specific job
     */
    private suspend fun findBestWorkerForJob(queuedJob: QueuedJob, availableWorkers: List<dev.rubentxu.hodei.pipelines.domain.worker.Worker>): dev.rubentxu.hodei.pipelines.domain.worker.Worker? {
        // Score workers based on capability match and current load
        return availableWorkers
            .filter { worker ->
                // Check basic capability requirements
                queuedJob.requirements.capabilities.all { (key, value) ->
                    when (key) {
                        "build" -> worker.capabilities.hasLabel("build") && value == "true"
                        "test" -> worker.capabilities.hasLabel("test") && value == "true"
                        "deploy" -> worker.capabilities.hasLabel("deploy") && value == "true"
                        else -> worker.capabilities.toMap()[key] == value
                    }
                }
            }
            .maxByOrNull { worker ->
                // Simple scoring: prefer workers with exact capability matches
                val capabilityScore = queuedJob.requirements.capabilities.count { (key, value) ->
                    worker.capabilities.toMap()[key] == value
                } * 10
                
                // Add small randomness to distribute load
                capabilityScore + (Math.random() * 5).toInt()
            }
    }
    
    /**
     * Execute a job on a specific worker
     */
    private suspend fun executeJob(queuedJob: QueuedJob, workerId: WorkerId) {
        try {
            logger.info { "Executing job ${queuedJob.job.id.value} on worker ${workerId.value}" }
            
            orchestrationEvents.emit(OrchestrationEvent.JobStarted(queuedJob.job.id, workerId))
            
            // Execute the job (this would integrate with the actual job executor)
            jobExecutor.execute(queuedJob.job, workerId).collect { event ->
                when (event) {
                    is JobExecutionEvent.Started -> {
                        logger.debug { "Job ${queuedJob.job.id.value} started execution" }
                    }
                    is JobExecutionEvent.Completed -> {
                        logger.info { "Job ${queuedJob.job.id.value} completed successfully" }
                        orchestrationEvents.emit(JobCompleted(queuedJob.job.id, workerId, true))
                    }
                    is JobExecutionEvent.Failed -> {
                        logger.warn { "Job ${queuedJob.job.id.value} failed: ${event.error}" }
                        orchestrationEvents.emit(JobCompleted(queuedJob.job.id, workerId, false))
                        
                        // Handle job retry if applicable
                        if (queuedJob.canRetry()) {
                            val retryJob = queuedJob.retry()
                            jobQueue.enqueue(retryJob.job, retryJob.priority, retryJob.requirements, retryJob.deadline)
                            orchestrationEvents.emit(JobRetried(queuedJob.job.id, retryJob.retryCount))
                        }
                    }
                    is JobExecutionEvent.Cancelled -> {
                        logger.info { "Job ${queuedJob.job.id.value} was cancelled" }
                        orchestrationEvents.emit(JobCompleted(queuedJob.job.id, workerId, false))
                    }
                    is JobExecutionEvent.OutputReceived -> {
                        logger.trace { "Job ${queuedJob.job.id.value} produced output" }
                    }
                    else -> {
                        logger.debug { "Received other job execution event: ${event::class.simpleName}" }
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error executing job ${queuedJob.job.id.value}" }
            orchestrationEvents.emit(OrchestrationEvent.JobCompleted(queuedJob.job.id, workerId, false))
        }
    }
    
    companion object {
        private val startTime = System.currentTimeMillis()
    }
}

/**
 * Configuration for creating worker pools
 */
data class WorkerPoolConfiguration(
    val name: String,
    val template: WorkerTemplate,
    val maxSize: Int = 10,
    val scalingPolicy: ScalingPolicy
)

/**
 * Job submission results
 */
sealed class JobSubmissionResult {
    data class Success(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, val queuePosition: Int) : JobSubmissionResult()
    data class QueueFull(val maxSize: Int) : JobSubmissionResult()
    data class AlreadyQueued(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId) : JobSubmissionResult()
    data class InvalidJob(val reason: String) : JobSubmissionResult()
    data class Failed(val error: String) : JobSubmissionResult()
}

/**
 * System status information
 */
data class SystemStatus(
    val isRunning: Boolean,
    val totalPools: Int,
    val totalWorkers: Int,
    val availableWorkers: Int,
    val queuedJobs: Int,
    val resourceUtilization: ResourceUtilization,
    val systemHealth: SystemHealth,
    val uptime: Duration
)

enum class SystemHealth {
    HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
}

/**
 * Comprehensive system metrics
 */
data class SystemMetrics(
    val overallMetrics: OverallPoolMetrics,
    val poolMetrics: Map<WorkerPoolId, PoolMetrics>,
    val queueStats: QueueStats,
    val resourceAvailability: ResourceAvailability,
    val orchestratorInfo: OrchestratorInfo
)

/**
 * Orchestration events for monitoring and observability
 */
sealed class OrchestrationEvent {
    abstract val timestamp: Instant
    
    data class SystemStarted(override val timestamp: Instant) : OrchestrationEvent()
    data class SystemStopped(override val timestamp: Instant) : OrchestrationEvent()
    data class JobQueued(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, val priority: JobPriority, val queueSize: Int, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class JobAssigned(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, val workerId: WorkerId, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class JobStarted(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, val workerId: WorkerId, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class JobCompleted(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, val workerId: WorkerId, val success: Boolean, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class JobRetried(val jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, val retryCount: Int, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class PoolCreated(val poolId: WorkerPoolId, val poolName: String, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class AutoScalingTriggered(val poolId: WorkerPoolId, val action: ScalingAction, val fromSize: Int, val toSize: Int, val success: Boolean, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
    data class MetricsCollected(val metrics: SystemMetrics, override val timestamp: Instant = Instant.now()) : OrchestrationEvent()
}