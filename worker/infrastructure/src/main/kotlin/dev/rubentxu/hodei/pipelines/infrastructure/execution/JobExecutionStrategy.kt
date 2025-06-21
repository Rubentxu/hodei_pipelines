package dev.rubentxu.hodei.pipelines.infrastructure.execution

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
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

/**
 * Result of job execution
 */
data class JobExecutionResult(
    val exitCode: Int,
    val status: JobStatus,
    val metrics: Map<String, Any> = emptyMap(),
    val output: String = "",
    val errorMessage: String? = null
) {
    companion object {
        fun success(exitCode: Int = 0, output: String = "", metrics: Map<String, Any> = emptyMap()) =
            JobExecutionResult(exitCode, JobStatus.COMPLETED, metrics, output)
            
        fun failure(exitCode: Int = 1, errorMessage: String, metrics: Map<String, Any> = emptyMap()) =
            JobExecutionResult(exitCode, JobStatus.FAILED, metrics, "", errorMessage)
    }
}

/**
 * Manager for execution strategies
 */
interface ExecutionStrategyManager {
    fun getStrategy(jobType: JobType): JobExecutionStrategy
    fun registerStrategy(jobType: JobType, strategy: JobExecutionStrategy)
    fun unregisterStrategy(jobType: JobType)
    fun getSupportedJobTypes(): Set<JobType>
}

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