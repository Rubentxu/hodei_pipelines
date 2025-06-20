package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.domain.job.*
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.util.UUID

/**
 * Use Case: Create and Execute Job
 * Handles the complete flow from job creation to execution
 */
class CreateAndExecuteJobUseCase(
    private val jobRepository: JobRepository,
    private val workerRepository: WorkerRepository,
    private val jobExecutor: JobExecutor,
    private val eventPublisher: EventPublisher
) {
    
    private val logger = KotlinLogging.logger {}

    suspend fun execute(request: CreateAndExecuteJobRequest): Flow<JobExecutionResult> = flow {
        logger.info { "Executing job creation and execution flow for job: ${request.jobDefinition.name}" }
        try {
            // 1. Create job
            val job = Job(
                id = JobId(UUID.randomUUID().toString()),
                definition = request.jobDefinition
            )
            
            logger.debug { "Creating job with ID: ${job.id.value}" }
            val saveResult = jobRepository.save(job)
            if (saveResult.isFailure) {
                logger.error(saveResult.exceptionOrNull()) { "Failed to save job: ${job.id.value}" }
                emit(JobExecutionResult.JobFailed(job.id, "Failed to save job: ${saveResult.exceptionOrNull()?.message}"))
                return@flow
            }
            
            eventPublisher.publishJobEvent(JobDomainEvent.JobCreated(job))
            logger.info { "Job created and queued: ${job.id.value}" }
            emit(JobExecutionResult.JobCreated(job.id))
            
            // 2. Find available worker
            logger.debug { "Finding available workers for job: ${job.id.value}" }
            val workersResult = workerRepository.findAvailableWorkers()
            if (workersResult.isFailure) {
                logger.error(workersResult.exceptionOrNull()) { "Failed to find workers for job: ${job.id.value}" }
                val failedJob = job.fail("Failed to find workers: ${workersResult.exceptionOrNull()?.message}", -1)
                jobRepository.save(failedJob)
                eventPublisher.publishJobEvent(JobDomainEvent.JobFailed(failedJob))
                emit(JobExecutionResult.JobFailed(job.id, "Failed to find workers"))
                return@flow
            }
            
            val availableWorkers = workersResult.getOrNull() ?: emptyList()
            if (availableWorkers.isEmpty()) {
                logger.warn { "No available workers found for job: ${job.id.value}" }
                val failedJob = job.fail("No available workers found", -1)
                jobRepository.save(failedJob)
                eventPublisher.publishJobEvent(JobDomainEvent.JobFailed(failedJob))
                emit(JobExecutionResult.JobFailed(job.id, "No available workers found"))
                return@flow
            }
            
            // 3. Select first available worker (simple strategy for MVP)
            val selectedWorker = availableWorkers.first()
            logger.info { "Selected worker ${selectedWorker.id.value} for job: ${job.id.value}" }
            val busyWorker = selectedWorker.assignJob()
            workerRepository.save(busyWorker)
            emit(JobExecutionResult.JobAssigned(job.id, selectedWorker.id))
            
            // 4. Execute job
            logger.info { "Starting job execution for job ${job.id.value} on worker ${selectedWorker.id.value}" }
            val runningJob = job.start()
            jobRepository.save(runningJob)
            eventPublisher.publishJobEvent(JobDomainEvent.JobStarted(runningJob))
            emit(JobExecutionResult.JobStarted(job.id))
            
            // 5. Monitor execution
            jobExecutor.execute(runningJob, selectedWorker.id).collect { event ->
                when (event) {
                    is JobExecutionEvent.Completed -> {
                        logger.info { "Job ${job.id.value} completed successfully with exit code: ${event.exitCode}" }
                        val completedJob = runningJob.complete(event.exitCode, event.output)
                        jobRepository.save(completedJob)
                        
                        val idleWorker = busyWorker.completeJob()
                        workerRepository.save(idleWorker)
                        
                        eventPublisher.publishJobEvent(JobDomainEvent.JobCompleted(completedJob))
                        emit(JobExecutionResult.JobCompleted(job.id, event.exitCode))
                    }
                    is JobExecutionEvent.Failed -> {
                        logger.error { "Job ${job.id.value} failed with error: ${event.error}" }
                        val failedJob = runningJob.fail(event.error, event.exitCode ?: -1)
                        jobRepository.save(failedJob)
                        
                        val idleWorker = busyWorker.completeJob()
                        workerRepository.save(idleWorker)
                        
                        eventPublisher.publishJobEvent(JobDomainEvent.JobFailed(failedJob))
                        emit(JobExecutionResult.JobFailed(job.id, event.error))
                    }
                    is JobExecutionEvent.OutputReceived -> {
                        logger.debug { "Received output chunk for job ${job.id.value}: ${event.chunk.data.size} bytes" }
                        emit(JobExecutionResult.JobOutput(job.id, String(event.chunk.data)))
                    }
                    else -> {
                        logger.warn { "Unhandled job execution event: ${event::class.simpleName}" }
                        // Handle other events if needed
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Execution failed for job: ${request.jobDefinition.name}" }
            emit(JobExecutionResult.JobFailed(JobId("unknown"), "Execution failed: ${e.message}"))
        } finally {
            logger.info { "Job execution flow completed for job: ${request.jobDefinition.name}" }
        }
    }
}

/**
 * Request for creating and executing a job
 */
data class CreateAndExecuteJobRequest(
    val jobDefinition: JobDefinition
)

/**
 * Results from job execution flow
 */
sealed class JobExecutionResult {
    data class JobCreated(val jobId: JobId) : JobExecutionResult()
    data class JobAssigned(val jobId: JobId, val workerId: WorkerId) : JobExecutionResult()
    data class JobStarted(val jobId: JobId) : JobExecutionResult()
    data class JobOutput(val jobId: JobId, val output: String) : JobExecutionResult()
    data class JobCompleted(val jobId: JobId, val exitCode: Int) : JobExecutionResult()
    data class JobFailed(val jobId: JobId, val error: String) : JobExecutionResult()
}