package dev.rubentxu.hodei.pipelines.infrastructure.service

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import dev.rubentxu.hodei.pipelines.port.ExecutionSignal
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * In-Memory implementation of JobExecutor for MVP
 * Simulates job execution without actually running commands
 */
class InMemoryJobExecutor : JobExecutor {
    
    override suspend fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent> = flow {
        // Emit job started event
        emit(JobExecutionEvent.Started(job.id, workerId))
        
        // Simulate job execution time (1-3 seconds)
        val executionTime = (1000..3000).random()
        delay(executionTime.toLong())
        
        // Simulate job completion (90% success rate for demo)
        val success = (1..10).random() > 1
        
        if (success) {
            val output = "Job '${job.definition.name}' executed successfully.\nCommand: ${job.definition.command.joinToString(" ")}\nSimulated output: Hello from worker ${workerId.value}"
            emit(JobExecutionEvent.Completed(job.id, 0, output))
        } else {
            val errorMessage = "Simulated job failure for demonstration"
            emit(JobExecutionEvent.Failed(job.id, errorMessage, 1))
        }
    }
    
    override suspend fun sendSignal(jobId: JobId, signal: ExecutionSignal): Boolean {
        // In a real implementation, this would signal the worker to stop execution
        // For MVP, we just return true to indicate signal was sent
        return true
    }
    
    override suspend fun getJobOutput(jobId: JobId): Flow<JobOutputChunk> = flow {
        // Simulate streaming output
        val sampleOutput = "Sample job output for ${jobId.value}"
        emit(JobOutputChunk(sampleOutput.toByteArray()))
    }
}