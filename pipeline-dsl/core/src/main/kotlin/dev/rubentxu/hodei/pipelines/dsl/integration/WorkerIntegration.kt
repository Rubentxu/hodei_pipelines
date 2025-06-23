package dev.rubentxu.hodei.pipelines.dsl.integration

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.model.execution.JobExecutionResult
import dev.rubentxu.hodei.pipelines.domain.worker.ports.JobExecutionStrategy
import dev.rubentxu.hodei.pipelines.dsl.orchestration.PipelineOrchestrator
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineRunner
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Estrategia principal que reemplaza TODAS las estrategias worker antiguas.
 * 
 * Los workers ahora usan exclusivamente el Pipeline DSL para ejecutar cualquier tipo de job:
 * - Scripts (.pipeline.kts) se compilan a Pipeline DSL
 * - Comandos simples se envuelven en Pipeline DSL
 * - Jobs compilados ya son Pipeline DSL
 * 
 * Esto simplifica enormemente el sistema worker eliminando múltiples estrategias.
 */
class PipelineDslStrategy(
    private val orchestrator: PipelineOrchestrator = PipelineOrchestrator.createDefault()
) : JobExecutionStrategy {

    override suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult {
        
        logger.info { "Executing job ${job.id.value} using Pipeline DSL strategy" }
        
        try {
            // Convertir cualquier tipo de job a Pipeline DSL
            val pipeline = when (val payload = job.definition.payload) {
                is JobPayload.Script -> {
                    logger.debug { "Converting script to Pipeline DSL" }
                    convertScriptToPipeline(payload.content)
                }
                is JobPayload.Command -> {
                    logger.debug { "Converting command to Pipeline DSL" }
                    convertCommandToPipeline(payload.commandLine)
                }
                is JobPayload.CompiledScript -> {
                    logger.debug { "Using pre-compiled Pipeline DSL" }
                    orchestrator.compileScript(payload.content)
                }
                else -> {
                    logger.warn { "Unknown payload type, creating default pipeline" }
                    createDefaultPipeline(job)
                }
            }
            
            // Validar pipeline
            val validationIssues = orchestrator.validatePipeline(pipeline)
            if (validationIssues.isNotEmpty()) {
                logger.warn { "Pipeline validation issues: $validationIssues" }
                // Continuar con warnings, no fallar
            }
            
            // Ejecutar usando el runner del Pipeline DSL
            val runner = orchestrator.getRunner()
            return runner.execute(job, workerId, outputHandler)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute job ${job.id.value} with Pipeline DSL" }
            return JobExecutionResult.failure(
                errorMessage = "Pipeline DSL execution failed: ${e.message}",
                metrics = mapOf("error" to e::class.simpleName!!)
            )
        }
    }
    
    /**
     * Convierte un script Kotlin a Pipeline DSL.
     */
    private suspend fun convertScriptToPipeline(scriptContent: String): dev.rubentxu.hodei.pipelines.dsl.model.Pipeline {
        // Si el script ya es .pipeline.kts, compilarlo directamente
        if (scriptContent.contains("pipeline(")) {
            return orchestrator.compileScript(scriptContent)
        }
        
        // Si es script Kotlin regular, envolverlo en Pipeline DSL
        return dev.rubentxu.hodei.pipelines.dsl.model.Pipeline(
            name = "Script Execution",
            description = "Kotlin script converted to Pipeline DSL",
            stages = listOf(
                dev.rubentxu.hodei.pipelines.dsl.model.Stage(
                    name = "Execute Script",
                    description = "Execute Kotlin script content",
                    steps = listOf(
                        dev.rubentxu.hodei.pipelines.dsl.model.Step.Script(
                            scriptFile = "script.kts", // Se creará temporalmente
                            parameters = mapOf("content" to scriptContent)
                        )
                    )
                )
            )
        )
    }
    
    /**
     * Convierte un comando de sistema a Pipeline DSL.
     */
    private fun convertCommandToPipeline(
        commandLine: List<String>
    ): dev.rubentxu.hodei.pipelines.dsl.model.Pipeline {
        
        val command = commandLine.joinToString(" ")
        
        return dev.rubentxu.hodei.pipelines.dsl.model.Pipeline(
            name = "Command Execution",
            description = "System command converted to Pipeline DSL",
            stages = listOf(
                dev.rubentxu.hodei.pipelines.dsl.model.Stage(
                    name = "Execute Command",
                    description = "Execute system command: $command",
                    steps = listOf(
                        dev.rubentxu.hodei.pipelines.dsl.model.Step.Shell(
                            command = command
                        )
                    )
                )
            )
        )
    }
    
    /**
     * Crea un pipeline por defecto para jobs desconocidos.
     */
    private fun createDefaultPipeline(job: Job): dev.rubentxu.hodei.pipelines.dsl.model.Pipeline {
        return dev.rubentxu.hodei.pipelines.dsl.model.Pipeline(
            name = "Default Pipeline",
            description = "Default pipeline for job ${job.id.value}",
            environment = job.definition.environment,
            stages = listOf(
                dev.rubentxu.hodei.pipelines.dsl.model.Stage(
                    name = "Default Stage",
                    description = "Default stage execution",
                    steps = listOf(
                        dev.rubentxu.hodei.pipelines.dsl.model.Step.Echo(
                            message = "Executing job ${job.id.value} with default pipeline"
                        )
                    )
                )
            )
        )
    }

    override fun canHandle(jobType: JobType): Boolean {
        // El Pipeline DSL puede manejar TODOS los tipos de job
        return true
    }

    override fun getSupportedJobTypes(): Set<JobType> {
        // Soporta todos los tipos
        return setOf(JobType.SCRIPT, JobType.COMMAND, JobType.COMPILED_SCRIPT)
    }
}

/**
 * Factory para reemplazar el sistema de estrategias worker complejo.
 * 
 * Este factory reemplaza:
 * - DefaultExecutionStrategyManager
 * - KotlinScriptingStrategy
 * - SystemCommandStrategy  
 * - CompilerEmbeddableStrategy
 * 
 * Todo se unifica en una sola estrategia Pipeline DSL.
 */
object WorkerStrategyFactory {
    
    /**
     * Crea la estrategia unificada que reemplaza todas las estrategias worker.
     */
    fun createUnifiedStrategy(): PipelineDslStrategy {
        logger.info { "Creating unified Pipeline DSL strategy to replace worker strategies" }
        return PipelineDslStrategy()
    }
    
    /**
     * Obtiene información sobre la migración.
     */
    fun getMigrationInfo(): Map<String, Any> {
        return mapOf(
            "replacedStrategies" to listOf(
                "KotlinScriptingStrategy",
                "SystemCommandStrategy", 
                "CompilerEmbeddableStrategy"
            ),
            "replacedManagers" to listOf(
                "DefaultExecutionStrategyManager",
                "DefaultExtensionManager",
                "DefaultLibraryManager"
            ),
            "newUnifiedStrategy" to "PipelineDslStrategy",
            "benefits" to listOf(
                "Single execution path",
                "Consistent DSL syntax",
                "Simplified worker architecture",
                "Better error handling",
                "Unified logging and metrics"
            )
        )
    }
}