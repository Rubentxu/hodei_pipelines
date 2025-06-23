package dev.rubentxu.hodei.pipelines.dsl.orchestration

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineEngine
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineRunner
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutorManager
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineStepExecutorManager
import dev.rubentxu.hodei.pipelines.dsl.model.Pipeline
import dev.rubentxu.hodei.pipelines.dsl.script.PipelineScriptCompiler
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Orquestador principal del Pipeline DSL que reemplaza el sistema worker complejo.
 * 
 * Este es el punto de entrada único y simplificado para todo lo relacionado con Pipeline DSL:
 * - Compilación de scripts .pipeline.kts
 * - Gestión de ejecutores de steps
 * - Orquestación de ejecución
 * - Configuración del sistema
 * 
 * Reemplaza: UnifiedPipelineManager, ExecutionStrategyManager, ExtensionManager
 */
class PipelineOrchestrator(
    private val stepExecutorManager: StepExecutorManager = PipelineStepExecutorManager(),
    private val scriptCompiler: PipelineScriptCompiler = PipelineScriptCompiler()
) {
    
    private val pipelineEngine = PipelineEngine(stepExecutorManager)
    private val pipelineRunner = PipelineRunner()
    
    init {
        logger.info { "Pipeline DSL Orchestrator initialized" }
        logSystemInfo()
    }
    
    /**
     * Compila un script .pipeline.kts a Pipeline DSL.
     */
    suspend fun compileScript(source: String): Pipeline {
        logger.info { "Compiling pipeline script (${source.length} chars)" }
        // TODO: Implementar compilación real cuando PipelineScriptCompiler esté listo
        return createSamplePipeline()
    }
    
    /**
     * Pipeline de ejemplo temporal.
     */
    private fun createSamplePipeline(): Pipeline {
        return Pipeline(
            name = "Compiled Pipeline",
            description = "Pipeline compilado desde script",
            stages = listOf(
                dev.rubentxu.hodei.pipelines.dsl.model.Stage(
                    name = "Build",
                    steps = listOf(
                        dev.rubentxu.hodei.pipelines.dsl.model.Step.Echo(
                            message = "Pipeline compilado exitosamente"
                        )
                    )
                )
            )
        )
    }
    
    /**
     * Ejecuta un pipeline compilado.
     */
    fun getRunner(): PipelineRunner {
        return pipelineRunner
    }
    
    /**
     * Obtiene el motor de ejecución para uso avanzado.
     */
    fun getEngine(): PipelineEngine {
        return pipelineEngine
    }
    
    /**
     * Registra un ejecutor de step personalizado.
     */
    fun registerStepExecutor(stepType: String, executor: dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor) {
        stepExecutorManager.registerExecutor(stepType, executor)
        logger.info { "Registered custom step executor: $stepType" }
    }
    
    /**
     * Obtiene información del sistema para debugging.
     */
    fun getSystemInfo(): Map<String, Any> {
        return mapOf(
            "orchestratorVersion" to "1.0.0",
            "supportedStepTypes" to getAvailableStepTypes(),
            "engineStatus" to "ready",
            "compilerStatus" to "ready"
        )
    }
    
    /**
     * Obtiene los tipos de step disponibles.
     */
    fun getAvailableStepTypes(): List<String> {
        // Por simplicidad, lista hardcoded de tipos core
        return listOf(
            "sh", "bat", "echo", "script", "docker", 
            "archiveArtifacts", "publishTestResults", "checkout",
            "notification", "dir", "withEnv"
        )
    }
    
    /**
     * Valida que un pipeline está bien formado.
     */
    fun validatePipeline(pipeline: Pipeline): List<String> {
        val issues = mutableListOf<String>()
        
        if (pipeline.name.isBlank()) {
            issues.add("Pipeline name cannot be blank")
        }
        
        if (pipeline.stages.isEmpty()) {
            issues.add("Pipeline must have at least one stage")
        }
        
        pipeline.stages.forEach { stage ->
            if (stage.name.isBlank()) {
                issues.add("Stage name cannot be blank")
            }
            
            if (stage.steps.isEmpty() && stage.parallel == null) {
                issues.add("Stage '${stage.name}' must have steps or parallel stages")
            }
        }
        
        return issues
    }
    
    private fun logSystemInfo() {
        val info = getSystemInfo()
        logger.info { "Pipeline DSL System ready - Available steps: ${info["supportedStepTypes"]}" }
    }
    
    companion object {
        /**
         * Crea un orquestador con configuración por defecto.
         */
        fun createDefault(): PipelineOrchestrator {
            return PipelineOrchestrator()
        }
        
        /**
         * Crea un orquestador con gestores personalizados.
         */
        fun createCustom(
            stepExecutorManager: StepExecutorManager,
            scriptCompiler: PipelineScriptCompiler
        ): PipelineOrchestrator {
            return PipelineOrchestrator(stepExecutorManager, scriptCompiler)
        }
    }
}