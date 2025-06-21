package dev.rubentxu.hodei.pipelines.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.application.CreateAndExecuteJobUseCase
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.mappers.JobMappers
import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.proto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import mu.KotlinLogging

/**
 * gRPC implementation of JobExecutorService
 * Handles job execution, monitoring, and control
 */
class JobExecutorServiceImpl(
    private val createAndExecuteJobUseCase: CreateAndExecuteJobUseCase,
    private val jobExecutor: JobExecutor
) : JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}
    private val activeWorkers = mutableMapOf<String, WorkerSession>()
    private val jobQueue = mutableListOf<ExecuteJobRequest>()

    /**
     * Establishes a bidirectional channel for job execution.
     * The server receives messages from workers (like heartbeats or job status)
     * and sends messages to workers (like job requests or control signals).
     */
    override fun jobExecutionChannel(requests: Flow<WorkerToServer>): Flow<ServerToWorker> = flow {
        logger.info { "Job execution channel opened for worker" }
        
        var workerId: String? = null
        
        try {
            requests.collect { workerMessage ->
                when (workerMessage.messageCase) {
                    WorkerToServer.MessageCase.HEARTBEAT -> {
                        val heartbeat = workerMessage.heartbeat
                        workerId = heartbeat.workerId.value
                        
                        logger.debug { "Received heartbeat from worker: $workerId" }
                        
                        // Update worker session
                        activeWorkers[workerId!!] = WorkerSession(
                            workerId = workerId!!,
                            status = heartbeat.status,
                            activeJobsCount = heartbeat.activeJobsCount,
                            lastHeartbeat = java.time.Instant.now(),
                            channel = this@flow
                        )
                        
                        // Check if worker can take more jobs
                        if (heartbeat.status == WorkerStatus.WORKER_STATUS_READY && 
                            heartbeat.activeJobsCount == 0 && 
                            jobQueue.isNotEmpty()) {
                            
                            val nextJob = jobQueue.removeFirst()
                            logger.info { "Assigning job ${nextJob.jobDefinition.name} to worker $workerId" }
                            
                            emit(ServerToWorker.newBuilder()
                                .setJobRequest(nextJob)
                                .build())
                        }
                    }
                    
                    WorkerToServer.MessageCase.JOB_OUTPUT_AND_STATUS -> {
                        val jobOutput = workerMessage.jobOutputAndStatus
                        
                        when (jobOutput.contentCase) {
                            JobOutputAndStatus.ContentCase.OUTPUT_CHUNK -> {
                                val chunk = jobOutput.outputChunk
                                logger.debug { "Received output chunk from job: ${chunk.jobId.value}" }
                                // Here you would typically store or forward the output
                            }
                            
                            JobOutputAndStatus.ContentCase.STATUS_UPDATE -> {
                                val statusUpdate = jobOutput.statusUpdate
                                logger.info { "Job ${statusUpdate.jobId.value} status: ${statusUpdate.status}" }
                                
                                // Update job status and handle completion
                                if (statusUpdate.status == JobStatus.JOB_STATUS_SUCCESS ||
                                    statusUpdate.status == JobStatus.JOB_STATUS_FAILED ||
                                    statusUpdate.status == JobStatus.JOB_STATUS_CANCELLED) {
                                    
                                    logger.info { "Job ${statusUpdate.jobId.value} completed with status: ${statusUpdate.status}" }
                                    
                                    // Worker is now available for more jobs
                                    if (jobQueue.isNotEmpty() && workerId != null) {
                                        val nextJob = jobQueue.removeFirst()
                                        logger.info { "Assigning next job ${nextJob.jobDefinition.name} to worker $workerId" }
                                        
                                        emit(ServerToWorker.newBuilder()
                                            .setJobRequest(nextJob)
                                            .build())
                                    }
                                }
                            }
                            
                            else -> {
                                logger.warn { "Received unknown job output content type: ${jobOutput.contentCase}" }
                            }
                        }
                    }
                    
                    else -> {
                        logger.warn { "Received unknown message type from worker: ${workerMessage.messageCase}" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in job execution channel for worker: $workerId" }
        } finally {
            // Clean up worker session when channel closes
            workerId?.let { id ->
                activeWorkers.remove(id)
                logger.info { "Worker $id disconnected, removed from active workers" }
            }
        }
    }
    
    /**
     * Queue a job for execution
     */
    fun queueJob(jobRequest: ExecuteJobRequest) {
        logger.info { "Queuing job: ${jobRequest.jobDefinition.name}" }
        
        // Try to assign to an available worker immediately
        val availableWorker = activeWorkers.values.find { worker ->
            worker.status == WorkerStatus.WORKER_STATUS_READY && worker.activeJobsCount == 0
        }
        
        if (availableWorker != null) {
            logger.info { "Assigning job ${jobRequest.jobDefinition.name} immediately to worker ${availableWorker.workerId}" }
            
            // This would require a way to send messages to specific workers
            // For now, we'll add to queue and let the next heartbeat handle it
            jobQueue.add(jobRequest)
        } else {
            logger.info { "No available workers, adding job to queue: ${jobRequest.jobDefinition.name}" }
            jobQueue.add(jobRequest)
        }
    }
    
    /**
     * Get current system statistics
     */
    fun getSystemStats(): SystemStats {
        return SystemStats(
            activeWorkers = activeWorkers.size,
            queuedJobs = jobQueue.size,
            totalJobs = jobQueue.size + activeWorkers.values.sumOf { it.activeJobsCount }
        )
    }
}

/**
 * Represents an active worker session
 */
data class WorkerSession(
    val workerId: String,
    val status: WorkerStatus,
    val activeJobsCount: Int,
    val lastHeartbeat: java.time.Instant,
    val channel: kotlinx.coroutines.flow.FlowCollector<ServerToWorker>
)

/**
 * System statistics
 */
data class SystemStats(
    val activeWorkers: Int,
    val queuedJobs: Int,
    val totalJobs: Int
)