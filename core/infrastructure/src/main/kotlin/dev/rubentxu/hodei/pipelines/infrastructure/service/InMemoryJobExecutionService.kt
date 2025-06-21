package dev.rubentxu.hodei.pipelines.infrastructure.service

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import dev.rubentxu.hodei.pipelines.port.ExecutionSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging

/**
 * In-Memory implementation of JobExecutor for MVP
 * Simulates job execution without actually running commands
 */
class InMemoryJobExecutor : JobExecutor {

    private val logger = KotlinLogging.logger {}

    override suspend fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent> = flow {
        logger.info { "Executing job ${job.id.value} on worker ${workerId.value}" }
        // Emit job started event
        emit(JobExecutionEvent.Started(job.id, workerId))
        logger.debug { "Job ${job.id.value}: Emitted JobExecutionEvent.Started" }

        // Simulate job execution time (1-3 seconds)
        val executionTime = (1000..3000).random()
        logger.debug { "Job ${job.id.value}: Simulating execution for ${executionTime}ms" }
        delay(executionTime.toLong())
        
        // Simulate job completion (90% success rate for demo)
        val success = (1..10).random() > 1
        
        if (success) {
            val payloadDescription = when (val payload = job.definition.payload) {
                is JobPayload.Command -> "Command: ${payload.commandLine.joinToString(" ")}"
                is JobPayload.Script -> "Script"
            is JobPayload.CompiledScript -> "Compiled Script: ${payload.content.take(50)}..."
                else -> "Unknown payload type"
            }
            val output = "Job '${job.definition.name}' executed successfully.\n$payloadDescription\nSimulated output: Hello from worker ${workerId.value}"
            logger.info { "Job ${job.id.value} completed successfully" }
            emit(JobExecutionEvent.Completed(job.id, 0, output))
            logger.debug { "Job ${job.id.value}: Emitted JobExecutionEvent.Completed" }
        } else {
            val errorMessage = "Simulated job failure for demonstration"
            logger.error { "Job ${job.id.value} failed: $errorMessage" }
            emit(JobExecutionEvent.Failed(job.id, errorMessage, 1))
            logger.debug { "Job ${job.id.value}: Emitted JobExecutionEvent.Failed" }
        }
    }
    
    override suspend fun sendSignal(jobId: JobId, signal: ExecutionSignal): Boolean {
        logger.info { "Sending signal ${signal.name} to job ${jobId.value}" }
        // In a real implementation, this would signal the worker to stop execution
        // For MVP, we just return true to indicate signal was sent
        return true
    }
    
    override suspend fun getJobOutput(jobId: JobId): Flow<JobOutputChunk> = flow {
        logger.info { "Getting output for job ${jobId.value}" }
        // Simulate streaming output
        val sampleOutput = "Sample job output for ${jobId.value}"
        emit(JobOutputChunk(sampleOutput.toByteArray()))
    }
}