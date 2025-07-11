package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Motor de ejecución principal del Pipeline DSL.
 * 
 * Características:
 * - Ejecución limpia y tipada de pipelines
 * - Integración directa con PipelineContext  
 * - Sistema de eventos y output streaming
 * - Gestión de stages y steps
 * - Paralelización de stages
 */
class PipelineEngine(
    private val stepExecutorManager: StepExecutorManager = PipelineStepExecutorManager()
) {
    
    /**
     * Ejecuta un pipeline completo.
     */
    suspend fun execute(
        pipeline: Pipeline,
        jobId: String,
        workerId: String,
        outputChannel: Channel<PipelineOutputChunk>,
        eventChannel: Channel<PipelineExecutionEvent>,
        runtimeEnvironment: Map<String, String> = emptyMap()
    ): PipelineExecutionResult = coroutineScope {
        
        // Crear contexto de ejecución standalone del Pipeline DSL
        val context = PipelineContext(
            jobId = jobId,
            workerId = workerId,
            workingDirectory = java.io.File(System.getProperty("user.dir")),
            environment = (pipeline.environment + runtimeEnvironment).toMutableMap(),
            outputChannel = outputChannel,
            eventChannel = eventChannel
        )
        
        val startTime = System.currentTimeMillis()
        var currentStage: String? = null
        
        try {
            logger.info { "Starting pipeline execution: ${pipeline.name}" }
            
            // Enviar evento de inicio
            eventChannel.send(PipelineExecutionEvent.StatusUpdate(
                jobId = jobId,
                status = "STARTED",
                message = "Pipeline execution started: ${pipeline.name}"
            ))
            
            // Preparar entorno
            eventChannel.send(PipelineExecutionEvent.EnvironmentPrepared(
                jobId = jobId,
                environment = pipeline.environment + runtimeEnvironment,
                workingDirectory = System.getProperty("user.dir")
            ))
            
            // Validar dependencias de artifacts
            val unsatisfiedDeps = pipeline.validateArtifactDependencies()
            if (unsatisfiedDeps.isNotEmpty()) {
                logger.warn { "Unsatisfied artifact dependencies: $unsatisfiedDeps" }
            }
            
            // Ejecutar stages
            val stageResults = mutableListOf<StageExecutionResult>()
            
            for (stage in pipeline.stages) {
                if (!stage.shouldExecute(context)) {
                    logger.info { "Skipping stage '${stage.name}' due to when condition" }
                    eventChannel.send(PipelineExecutionEvent.StageSkipped(
                        jobId = jobId,
                        stageName = stage.name,
                        reason = "When condition not met"
                    ))
                    continue
                }
                
                currentStage = stage.name
                val stageResult = executeStage(stage, context)
                stageResults.add(stageResult)
                
                if (!stageResult.success && stage.options?.retry?.count?.let { it > 0 } != true) {
                    // Stage failed and no retry - execute post failure
                    executePostActions(pipeline.post?.failure ?: emptyList(), context)
                    
                    return@coroutineScope PipelineExecutionResult(
                        success = false,
                        duration = System.currentTimeMillis() - startTime,
                        failedStage = stage.name,
                        error = stageResult.error,
                        stageResults = stageResults
                    )
                }
            }
            
            // Ejecutar post success
            executePostActions(pipeline.post?.success ?: emptyList(), context)
            
            val duration = System.currentTimeMillis() - startTime
            logger.info { "Pipeline execution completed successfully in ${duration}ms" }
            
            eventChannel.send(PipelineExecutionEvent.StatusUpdate(
                jobId = jobId,
                status = "SUCCESS",
                message = "Pipeline execution completed successfully"
            ))
            
            PipelineExecutionResult(
                success = true,
                duration = duration,
                stageResults = stageResults
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Pipeline execution failed" }
            
            // Ejecutar post failure
            executePostActions(pipeline.post?.failure ?: emptyList(), context)
            
            eventChannel.send(PipelineExecutionEvent.StatusUpdate(
                jobId = jobId,
                status = "FAILED",
                message = "Pipeline execution failed: ${e.message}"
            ))
            
            PipelineExecutionResult(
                success = false,
                duration = System.currentTimeMillis() - startTime,
                failedStage = currentStage,
                error = e.message ?: "Unknown error"
            )
        } finally {
            // Ejecutar post always
            try {
                executePostActions(pipeline.post?.always ?: emptyList(), context)
            } catch (e: Exception) {
                logger.error(e) { "Failed to execute post always actions" }
            }
        }
    }
    
    /**
     * Ejecuta un stage individual.
     */
    private suspend fun executeStage(
        stage: Stage,
        context: PipelineContext
    ): StageExecutionResult = coroutineScope {
        
        logger.info { "Executing stage: ${stage.name}" }
        val startTime = System.currentTimeMillis()
        
        try {
            // Enviar evento de inicio de stage
            context.publishEvent(PipelineExecutionEvent.StageStarted(
                pipelineName = "pipeline", // TODO: get from context
                stageName = stage.name
            ))
            
            // Ejecutar steps secuenciales
            for (step in stage.steps) {
                executeStep(step, context)
            }
            
            // Ejecutar stages paralelos si existen
            stage.parallel?.let { parallelStages ->
                val parallelJobs = parallelStages.stages.map { parallelStage ->
                    async {
                        // Ejecutar steps del stage paralelo
                        for (step in parallelStage.steps) {
                            executeStep(step, context)
                        }
                    }
                }
                
                // Esperar a que todos los stages paralelos terminen
                parallelJobs.awaitAll()
            }
            
            // Enviar evento de finalización de stage
            context.publishEvent(PipelineExecutionEvent.StageCompleted(
                pipelineName = "pipeline", // TODO: get from context
                stageName = stage.name,
                success = true
            ))
            
            val duration = System.currentTimeMillis() - startTime
            logger.info { "Stage '${stage.name}' completed successfully in ${duration}ms" }
            
            StageExecutionResult(
                stageName = stage.name,
                success = true,
                duration = duration
            )
            
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            logger.error(e) { "Stage '${stage.name}' failed after ${duration}ms" }
            
            StageExecutionResult(
                stageName = stage.name,
                success = false,
                duration = duration,
                error = e.message ?: "Unknown error"
            )
        }
    }
    
    /**
     * Ejecuta un step individual.
     */
    private suspend fun executeStep(step: Step, context: PipelineContext) {
        logger.debug { "Executing step: ${step.stepType}" }
        
        val executor = stepExecutorManager.getExecutor(step.stepType)
            ?: throw IllegalArgumentException("No executor found for step type: ${step.stepType}")
        
        try {
            executor.execute(step, context)
        } catch (e: Exception) {
            if (step.continueOnError) {
                logger.warn(e) { "Step ${step.stepType} failed but continuing due to continueOnError=true" }
                context.println("⚠️ Step failed but continuing: ${e.message}")
            } else {
                throw e
            }
        }
    }
    
    /**
     * Ejecuta acciones post-ejecución.
     */
    private suspend fun executePostActions(
        steps: List<Step>,
        context: PipelineContext
    ) {
        if (steps.isEmpty()) return
        
        logger.debug { "Executing ${steps.size} post actions" }
        
        for (step in steps) {
            try {
                executeStep(step, context)
            } catch (e: Exception) {
                logger.error(e) { "Post action failed: ${step.stepType}" }
                // No propagamos el error para no interrumpir otras post actions
            }
        }
    }
    
    /**
     * Construye contexto de ejecución para evaluación de condiciones.
     */
    private fun buildExecutionContext(
        context: PipelineContext,
        runtimeEnvironment: Map<String, String>
    ): Map<String, Any> {
        // Usar la API pública del contexto worker para acceso a entorno
        return runtimeEnvironment + mapOf(
            "JOB_ID" to context.jobId,
            "WORKER_ID" to context.workerId
        )
    }
}

/**
 * Resultado de ejecución del pipeline.
 */
data class PipelineExecutionResult(
    val success: Boolean,
    val duration: Long,
    val failedStage: String? = null,
    val error: String? = null,
    val stageResults: List<StageExecutionResult> = emptyList()
)

/**
 * Resultado de ejecución de un stage.
 */
data class StageExecutionResult(
    val stageName: String,
    val success: Boolean,
    val duration: Long,
    val error: String? = null
)