package dev.rubentxu.hodei.pipelines.domain.worker.ports

import dev.rubentxu.hodei.pipelines.domain.job.JobType

/**
 * Manager for execution strategies
 */
interface ExecutionStrategyManager {
    fun getStrategy(jobType: JobType): JobExecutionStrategy
    fun registerStrategy(jobType: JobType, strategy: JobExecutionStrategy)
    fun unregisterStrategy(jobType: JobType)
    fun getSupportedJobTypes(): Set<JobType>
}