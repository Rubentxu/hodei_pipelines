package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.domain.job.*
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

/**
 * Use Case: Create and Execute Job
 * Handles the complete flow from job creation to execution
 */
class CreateAndExecuteJobUseCase(
    private val jobRepository: JobRepository,
    private val workerRepository: WorkerRepository,
    private val jobExecutionService: JobExecutionService,
    private val eventPublisher: EventPublisher
) {
    
    suspend fun execute(request: CreateAndExecuteJobRequest): Flow<JobExecutionResult> = flow {
        try {
            // 1. Create job
            val job = Job(
                id = JobId(UUID.randomUUID().toString()),
                definition = request.jobDefinition
            )
            
            val savedJob = jobRepository.save(job)
            eventPublisher.publishJobEvent(JobDomainEvent.JobCreated(savedJob))
            emit(JobExecutionResult.JobCreated(savedJob.id))
            
            // 2. Find available worker
            val availableWorkers = workerRepository.findAvailableWorkers()
            if (availableWorkers.isEmpty()) {
                val failedJob = savedJob.fail("No available workers found", -1)
                jobRepository.save(failedJob)
                eventPublisher.publishJobEvent(JobDomainEvent.JobFailed(failedJob))
                emit(JobExecutionResult.JobFailed(savedJob.id, "No available workers found"))
                return@flow
            }
            
            // 3. Select first available worker (simple strategy for MVP)
            val selectedWorker = availableWorkers.first()
            val busyWorker = selectedWorker.assignJob()
            workerRepository.save(busyWorker)
            emit(JobExecutionResult.JobAssigned(savedJob.id, selectedWorker.id))
            
            // 4. Execute job
            val runningJob = savedJob.start()
            jobRepository.save(runningJob)
            eventPublisher.publishJobEvent(JobDomainEvent.JobStarted(runningJob))
            emit(JobExecutionResult.JobStarted(savedJob.id))
            
            // 5. Monitor execution
            jobExecutionService.executeJob(runningJob, selectedWorker.id).collect { event ->
                when (event) {
                    is JobExecutionEvent.Completed -> {
                        val completedJob = runningJob.complete(event.exitCode, event.output)
                        jobRepository.save(completedJob)
                        
                        val idleWorker = busyWorker.completeJob()
                        workerRepository.save(idleWorker)
                        
                        eventPublisher.publishJobEvent(JobDomainEvent.JobCompleted(completedJob))
                        emit(JobExecutionResult.JobCompleted(savedJob.id, event.exitCode))
                    }
                    is JobExecutionEvent.Failed -> {
                        val failedJob = runningJob.fail(event.error, event.exitCode ?: -1)
                        jobRepository.save(failedJob)
                        
                        val idleWorker = busyWorker.completeJob()
                        workerRepository.save(idleWorker)
                        
                        eventPublisher.publishJobEvent(JobDomainEvent.JobFailed(failedJob))
                        emit(JobExecutionResult.JobFailed(savedJob.id, event.error))
                    }
                    is JobExecutionEvent.OutputReceived -> {
                        emit(JobExecutionResult.JobOutput(savedJob.id, String(event.chunk.data)))
                    }
                    else -> {
                        // Handle other events if needed
                    }
                }
            }
            
        } catch (e: Exception) {
            emit(JobExecutionResult.JobFailed(JobId("unknown"), "Execution failed: ${e.message}"))
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