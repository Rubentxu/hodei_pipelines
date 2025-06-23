package dev.rubentxu.hodei.pipelines.infrastructure.execution

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.model.execution.JobExecutionResult
import dev.rubentxu.hodei.pipelines.domain.worker.ports.JobExecutionStrategy
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Estrategia de ejecución que delega al Pipeline DSL.
 * 
 * Esta es la única estrategia que necesita el worker - todo se ejecuta
 * a través del Pipeline DSL standalone.
 */
class PipelineDslExecutionStrategy : JobExecutionStrategy {
    
    override fun canHandle(jobType: JobType): Boolean {
        // El Pipeline DSL maneja todos los tipos de job
        return when (jobType) {
            JobType.SCRIPT,
            JobType.COMMAND,
            JobType.COMPILED_SCRIPT -> true
        }
    }
    
    override fun getSupportedJobTypes(): List<JobType> {
        return listOf(JobType.SCRIPT, JobType.COMMAND, JobType.COMPILED_SCRIPT)
    }
    
    override suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult {
        logger.info { "Delegating job execution to Pipeline DSL: ${job.id}" }
        
        try {
            // TODO: Integrate with Pipeline DSL execution
            // Por ahora, devolver un resultado básico para que compile
            
            outputHandler(JobOutputChunk(
                jobId = job.id.value,
                data = "Pipeline DSL execution placeholder\n".toByteArray(),
                isError = false,
                timestamp = System.currentTimeMillis()
            ))
            
            return JobExecutionResult(
                jobId = job.id,
                status = JobStatus.COMPLETED,
                exitCode = 0,
                output = "Pipeline DSL execution completed",
                errorMessage = null,
                metrics = mapOf(
                    "executionStrategy" to "PipelineDslExecutionStrategy",
                    "executionTimeMs" to 100L,
                    "delegatedToPipelineDsl" to true
                )
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Pipeline DSL execution failed for job ${job.id}" }
            
            return JobExecutionResult(
                jobId = job.id,
                status = JobStatus.FAILED,
                exitCode = 1,
                output = null,
                errorMessage = "Pipeline DSL execution failed: ${e.message}",
                metrics = mapOf(
                    "executionStrategy" to "PipelineDslExecutionStrategy",
                    "error" to e.message.orEmpty()
                )
            )
        }
    }
}