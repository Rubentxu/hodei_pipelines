package dev.rubentxu.hodei.pipelines.domain.worker.ports

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.model.execution.JobExecutionResult
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk

/**
 * Strategy interface for job execution
 */
interface JobExecutionStrategy {
    suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult
    
    fun canHandle(jobType: JobType): Boolean
    fun getSupportedJobTypes(): Set<JobType>
}

