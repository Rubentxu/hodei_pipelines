package dev.rubentxu.hodei.pipelines.infrastructure.script

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.execution.DefaultExecutionStrategyManager
import dev.rubentxu.hodei.pipelines.domain.worker.ports.ExecutionStrategyManager
import dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies.KotlinScriptingStrategy
import dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies.SystemCommandStrategy
import dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies.CompilerEmbeddableStrategy
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import dev.rubentxu.hodei.pipelines.port.ScriptExecutor
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging


private val logger = KotlinLogging.logger {}

/**
 * Enhanced PipelineScriptExecutor that uses the Strategy pattern
 * to support multiple execution strategies
 */
class PipelineScriptExecutor(
    private val strategyManager: ExecutionStrategyManager = createDefaultStrategyManager()
) : ScriptExecutor {
    
    override fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent> = channelFlow {
        send(JobExecutionEvent.Started(job.id, workerId))
        
        // Canal para recibir la salida en tiempo real
        val outputChannel = Channel<JobOutputChunk>(Channel.UNLIMITED)
        
        coroutineScope {
            // Lanzamos un procesador para la salida en tiempo real
            launch {
                for (chunk in outputChannel) {
                    send(JobExecutionEvent.OutputReceived(job.id, chunk))
                }
            }
            
            try {
                // Determinar el tipo de job basado en el payload
                val jobType = determineJobType(job.definition.payload)
                logger.info { "Executing job ${job.id.value} of type $jobType with strategy manager" }
                
                // Obtener la estrategia apropiada
                val strategy = strategyManager.getStrategy(jobType)
                
                // Ejecutar usando la estrategia
                val result = strategy.execute(
                    job = job,
                    workerId = workerId,
                    outputHandler = { chunk -> 
                        runBlocking { outputChannel.send(chunk) }
                    }
                )
                
                // Convertir resultado a eventos
                when (result.status) {
                    dev.rubentxu.hodei.pipelines.domain.job.JobStatus.COMPLETED -> {
                        send(JobExecutionEvent.Completed(job.id, result.exitCode, result.output))
                    }
                    dev.rubentxu.hodei.pipelines.domain.job.JobStatus.FAILED -> {
                        send(JobExecutionEvent.Failed(job.id, result.errorMessage ?: "Unknown error", result.exitCode))
                    }
                    else -> {
                        send(JobExecutionEvent.Failed(job.id, "Unexpected job status: ${result.status}", result.exitCode))
                    }
                }
                
            } catch (e: IllegalArgumentException) {
                logger.error(e) { "No strategy available for job ${job.id.value}" }
                send(JobExecutionEvent.Failed(job.id, "No execution strategy available: ${e.message}", 1))
            } catch (e: Exception) {
                val message = e.message ?: "Unknown error"
                val stackTrace = e.stackTraceToString()
                logger.error(e) { "Unexpected error executing job ${job.id.value}" }
                send(JobExecutionEvent.Failed(job.id, "An unexpected error occurred: $message\n$stackTrace", 1))
            } finally {
                outputChannel.close()
            }
        }
    }
    
    /**
     * Determine job type based on payload
     */
    private fun determineJobType(payload: JobPayload): JobType {
        return when (payload) {
            is JobPayload.Script -> JobType.SCRIPT
            is JobPayload.Command -> JobType.COMMAND
            is JobPayload.CompiledScript -> JobType.COMPILED_SCRIPT
        }
    }
    
    companion object {
        /**
         * Create default strategy manager with all built-in strategies
         */
        fun createDefaultStrategyManager(): ExecutionStrategyManager {
            val manager = DefaultExecutionStrategyManager()
            
            // Register built-in strategies
            manager.registerStrategy(JobType.SCRIPT, KotlinScriptingStrategy())
            manager.registerStrategy(JobType.COMMAND, SystemCommandStrategy())
            manager.registerStrategy(JobType.COMPILED_SCRIPT, CompilerEmbeddableStrategy())
            
            logger.info { "Registered strategies for: ${manager.getSupportedJobTypes()}" }
            
            return manager
        }
    }
}