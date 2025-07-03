package dev.rubentxu.hodei.application.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.entities.*
import dev.rubentxu.hodei.jobmanagement.domain.repositories.*
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.scheduling.application.services.SchedulerService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Orchestrator service responsible for managing job queues and coordinating
 * between the scheduler and execution engine.
 * 
 * Key responsibilities:
 * - Accept jobs from REST API
 * - Manage job queues
 * - Request placement decisions from scheduler
 * - Delegate execution to ExecutionEngine
 * - Forget about jobs once delegated
 */
class JobOrchestratorService(
    private val jobRepository: JobRepository,
    private val jobQueueRepository: JobQueueRepository,
    private val queuedJobRepository: QueuedJobRepository,
    private val schedulerService: SchedulerService,
    private val executionEngineService: ExecutionEngineService
) {
    private val logger = LoggerFactory.getLogger(JobOrchestratorService::class.java)
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val isProcessing = AtomicBoolean(false)
    
    /**
     * Submit a job to the orchestrator for queuing and eventual execution
     */
    suspend fun submitJob(
        job: Job,
        queueId: DomainId,
        priority: JobPriority = JobPriority.NORMAL,
        maxAttempts: Int = 3
    ): Either<String, Job> {
        logger.info("Submitting job ${job.id} to queue $queueId")
        
        // Validate queue exists and is active
        val queue = jobQueueRepository.findById(queueId).fold(
            { return "Failed to find queue: $it".left() },
            { q -> 
                if (q == null) return "Queue not found".left()
                if (!q.isActive) return "Queue is not active".left()
                q
            }
        )
        
        // Check queue capacity
        if (queue.maxQueuedJobs != null) {
            val queuedCount = queuedJobRepository.countByQueueAndStatus(queueId, QueuedJobStatus.QUEUED).fold(
                { return "Failed to check queue capacity".left() },
                { it }
            )
            if (queuedCount >= queue.maxQueuedJobs) {
                return "Queue is at maximum capacity".left()
            }
        }
        
        // Update job status to queued
        val queuedJob = job.queue()
        jobRepository.update(queuedJob).fold(
            { return "Failed to update job status: $it".left() },
            { }
        )
        
        // Create queued job entry
        val queuedJobEntry = QueuedJob(
            id = DomainId.generate(),
            jobId = job.id,
            queueId = queueId,
            job = queuedJob,
            priority = priority,
            maxAttempts = maxAttempts,
            queuedAt = Clock.System.now()
        )
        
        queuedJobRepository.save(queuedJobEntry).fold(
            { 
                // Revert job status on failure
                jobRepository.update(job).fold({}, {})
                return "Failed to queue job: $it".left() 
            },
            { 
                logger.info("Job ${job.id} successfully queued in ${queue.name}")
            }
        )
        
        // Trigger processing if not already running
        if (!isProcessing.get()) {
            scope.launch { startProcessingLoop() }
        }
        
        return queuedJob.right()
    }
    
    /**
     * Process queued jobs continuously
     */
    private suspend fun startProcessingLoop() {
        if (!isProcessing.compareAndSet(false, true)) {
            return // Already processing
        }
        
        logger.info("Starting job processing loop")
        
        try {
            while (isProcessing.get()) {
                processNextBatch()
                delay(1000) // Process every second
            }
        } catch (e: CancellationException) {
            logger.info("Processing loop cancelled")
        } catch (e: Exception) {
            logger.error("Error in processing loop", e)
        } finally {
            isProcessing.set(false)
            logger.info("Processing loop stopped")
        }
    }
    
    /**
     * Stop the processing loop
     */
    fun stopProcessing() {
        logger.info("Stopping job processing")
        isProcessing.set(false)
    }
    
    private suspend fun processNextBatch() {
        // Get ready jobs from all queues
        val readyJobs = queuedJobRepository.findReadyJobs().fold(
            { 
                logger.error("Failed to find ready jobs: $it")
                return 
            },
            { it }
        )
        
        if (readyJobs.isEmpty()) {
            return
        }
        
        logger.debug("Processing batch of ${readyJobs.size} ready jobs")
        
        // Group by queue and process according to queue rules
        val jobsByQueue = readyJobs.groupBy { it.queueId }
        
        coroutineScope {
            jobsByQueue.forEach { (queueId, jobs) ->
                launch {
                    processQueueJobs(queueId, jobs)
                }
            }
        }
    }
    
    private suspend fun processQueueJobs(queueId: DomainId, jobs: List<QueuedJob>) {
        val queue = jobQueueRepository.findById(queueId).fold(
            { 
                logger.error("Failed to find queue $queueId")
                return 
            },
            { it ?: return }
        )
        
        if (!queue.isActive) {
            logger.debug("Queue ${queue.name} is not active, skipping")
            return
        }
        
        // Check concurrent job limit
        val runningCount = queuedJobRepository.countByQueueAndStatus(queueId, QueuedJobStatus.RUNNING).fold(
            { 
                logger.error("Failed to count running jobs for queue $queueId")
                return 
            },
            { it }
        )
        
        val availableSlots = queue.maxConcurrentJobs?.let { it - runningCount } ?: Int.MAX_VALUE
        if (availableSlots <= 0) {
            logger.debug("Queue ${queue.name} at concurrent job limit ($runningCount/${queue.maxConcurrentJobs})")
            return
        }
        
        // Sort jobs based on queue type
        val sortedJobs = sortJobsForQueue(jobs, queue.queueType)
        val jobsToProcess = sortedJobs.take(availableSlots)
        
        // Process each job
        jobsToProcess.forEach { queuedJob ->
            processJob(queuedJob)
        }
    }
    
    private suspend fun processJob(queuedJob: QueuedJob) {
        logger.info("Processing job ${queuedJob.jobId} from queue ${queuedJob.queueId}")
        
        // Mark as scheduled (attempting to run)
        val scheduledJob = queuedJob.schedule()
        queuedJobRepository.save(scheduledJob).fold(
            { 
                logger.error("Failed to update queued job status: $it")
                return 
            },
            { }
        )
        
        // Request placement from scheduler
        val placementResult = schedulerService.findPlacement(queuedJob.job)
        
        placementResult.fold(
            { error ->
                logger.warn("Failed to find placement for job ${queuedJob.jobId}: $error")
                handlePlacementFailure(queuedJob, error)
            },
            { resourcePool ->
                logger.info("Scheduler selected pool ${resourcePool.name} for job ${queuedJob.jobId}")
                delegateToExecutionEngine(queuedJob, resourcePool)
            }
        )
    }
    
    private suspend fun delegateToExecutionEngine(queuedJob: QueuedJob, resourcePool: ResourcePool) {
        logger.info("Delegating job ${queuedJob.jobId} to ExecutionEngine on pool ${resourcePool.name}")
        
        // Start execution with orchestrator token
        val orchestratorToken = executionEngineService.getOrchestratorToken()
        val executionResult = executionEngineService.startExecution(
            queuedJob.job, 
            resourcePool,
            orchestratorToken
        )
        
        executionResult.fold(
            { error ->
                logger.error("Failed to start execution for job ${queuedJob.jobId}: $error")
                handleExecutionStartFailure(queuedJob, error)
            },
            { execution ->
                logger.info("Successfully delegated job ${queuedJob.jobId} to ExecutionEngine (execution: ${execution.id})")
                
                // Mark queued job as running
                val runningJob = queuedJob.start()
                queuedJobRepository.save(runningJob).fold(
                    { logger.error("Failed to update queued job to running: $it") },
                    { }
                )
                
                // Job is now ExecutionEngine's responsibility - orchestrator forgets about it
                logger.debug("Orchestrator releasing responsibility for job ${queuedJob.jobId}")
            }
        )
    }
    
    private suspend fun handlePlacementFailure(queuedJob: QueuedJob, error: String) {
        if (queuedJob.canRetry()) {
            logger.info("Retrying job ${queuedJob.jobId} after placement failure (attempt ${queuedJob.attempts + 1}/${queuedJob.maxAttempts})")
            
            val retriedJob = queuedJob.retry()
            queuedJobRepository.save(retriedJob).fold(
                { logger.error("Failed to save retried job: $it") },
                { }
            )
        } else {
            logger.error("Job ${queuedJob.jobId} failed permanently after ${queuedJob.attempts} attempts")
            
            // Mark job as failed
            val failedQueuedJob = queuedJob.fail(error)
            queuedJobRepository.save(failedQueuedJob).fold(
                { logger.error("Failed to update queued job status: $it") },
                { }
            )
            
            // Update job status
            val failedJob = queuedJob.job.fail(error)
            jobRepository.update(failedJob).fold(
                { logger.error("Failed to update job status: $it") },
                { }
            )
        }
    }
    
    private suspend fun handleExecutionStartFailure(queuedJob: QueuedJob, error: String) {
        // Similar to placement failure, but job made it further
        handlePlacementFailure(queuedJob, "Execution start failed: $error")
    }
    
    private fun sortJobsForQueue(jobs: List<QueuedJob>, queueType: QueueType): List<QueuedJob> {
        return when (queueType) {
            QueueType.FIFO -> jobs.sortedBy { it.queuedAt }
            QueueType.LIFO -> jobs.sortedByDescending { it.queuedAt }
            QueueType.PRIORITY -> jobs.sortedWith(
                compareByDescending<QueuedJob> { it.priority.value }
                    .thenBy { it.queuedAt }
            )
        }
    }
    
    /**
     * Get queue statistics
     */
    suspend fun getQueueStatistics(queueId: DomainId): Either<String, QueueStatistics> {
        val queue = jobQueueRepository.findById(queueId).fold(
            { return "Failed to find queue: $it".left() },
            { it ?: return "Queue not found".left() }
        )
        
        val queuedCount = queuedJobRepository.countByQueueAndStatus(queueId, QueuedJobStatus.QUEUED).fold(
            { return "Failed to get statistics: $it".left() },
            { it }
        )
        
        val runningCount = queuedJobRepository.countByQueueAndStatus(queueId, QueuedJobStatus.RUNNING).fold(
            { return "Failed to get statistics: $it".left() },
            { it }
        )
        
        val completedCount = queuedJobRepository.countByQueueAndStatus(queueId, QueuedJobStatus.COMPLETED).fold(
            { return "Failed to get statistics: $it".left() },
            { it }
        )
        
        val failedCount = queuedJobRepository.countByQueueAndStatus(queueId, QueuedJobStatus.FAILED).fold(
            { return "Failed to get statistics: $it".left() },
            { it }
        )
        
        return QueueStatistics(
            queueId = queueId,
            queueName = queue.name,
            queuedJobs = queuedCount,
            runningJobs = runningCount,
            completedJobs = completedCount,
            failedJobs = failedCount,
            isActive = queue.isActive,
            maxConcurrentJobs = queue.maxConcurrentJobs,
            maxQueuedJobs = queue.maxQueuedJobs
        ).right()
    }
}

data class QueueStatistics(
    val queueId: DomainId,
    val queueName: String,
    val queuedJobs: Int,
    val runningJobs: Int,
    val completedJobs: Int,
    val failedJobs: Int,
    val isActive: Boolean,
    val maxConcurrentJobs: Int?,
    val maxQueuedJobs: Int?
)