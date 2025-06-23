package dev.rubentxu.hodei.pipelines.application.worker.execution

import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.ports.ExecutionStrategyManager
import dev.rubentxu.hodei.pipelines.domain.worker.ports.JobExecutionStrategy

/**
 * Default implementation of ExecutionStrategyManager
 */
class DefaultExecutionStrategyManager : ExecutionStrategyManager {
    private val strategies = mutableMapOf<JobType, JobExecutionStrategy>()

    override fun getStrategy(jobType: JobType): JobExecutionStrategy {
        return strategies[jobType]
            ?: throw IllegalArgumentException("No strategy registered for job type: $jobType")
    }

    override fun registerStrategy(jobType: JobType, strategy: JobExecutionStrategy) {
        strategies[jobType] = strategy
    }

    override fun unregisterStrategy(jobType: JobType) {
        strategies.remove(jobType)
    }

    override fun getSupportedJobTypes(): Set<JobType> {
        return strategies.keys.toSet()
    }
}