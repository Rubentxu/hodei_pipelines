package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.execution.JobExecutionResult
import dev.rubentxu.hodei.pipelines.domain.worker.ports.JobExecutionStrategy
import dev.rubentxu.hodei.pipelines.dsl.model.Pipeline
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Estrategia de ejecución principal para Pipeline DSL que reemplaza las implementaciones worker.
 * 
 * Esta es la nueva implementación limpia que:
 * - Reemplaza completamente las estrategias worker antiguas
 * - Se enfoca únicamente en Pipeline DSL
 * - Mantiene la interfaz del sistema para compatibilidad
 * - Ofrece ejecución optimizada y directa
 */
class PipelineRunner : JobExecutionStrategy {

    private val pipelineEngine = PipelineEngine()

    override suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult = coroutineScope {
        
        val startTime = System.currentTimeMillis()
        
        try {
            logger.info { "Executing Pipeline DSL job ${job.id.value} on worker ${workerId.value}" }
            
            // Crear canales para comunicación
            val outputChannel = Channel<JobOutputChunk>(Channel.UNLIMITED)
            val eventChannel = Channel<JobExecutionEvent>(Channel.UNLIMITED)
            
            // Crear contexto limpio para ejecución
            val pipelineContext = PipelineContext(
                jobId = job.id,
                workerId = workerId,
                environment = job.definition.environment,
                outputChannel = outputChannel,
                eventChannel = eventChannel
            )
            
            // Por ahora, pipeline de ejemplo (TODO: obtener del job payload)
            val pipeline = createSamplePipeline()
            
            // Lanzar recolector de output
            val outputCollector = mutableListOf<JobOutputChunk>()
            val outputJob = launch {
                for (chunk in outputChannel) {
                    outputCollector.add(chunk)
                    outputHandler(chunk)
                }
            }
            
            // Ejecutar usando el motor renovado
            val result = pipelineEngine.execute(
                pipeline = pipeline,
                jobId = job.id,
                workerId = workerId,
                outputChannel = outputChannel,
                eventChannel = eventChannel,
                runtimeEnvironment = job.definition.environment
            )
            
            // Cerrar canales
            outputChannel.close()
            eventChannel.close()
            outputJob.join()
            
            val executionTime = System.currentTimeMillis() - startTime
            val metrics = mapOf(
                "executionTimeMs" to executionTime,
                "stagesExecuted" to pipeline.stages.size,
                "outputChunksCount" to outputCollector.size,
                "totalSteps" to pipeline.getTotalStepCount()
            )
            
            if (result.success) {
                logger.info { "Pipeline executed successfully in ${executionTime}ms" }
                JobExecutionResult.success(
                    output = outputCollector.joinToString("\n") { String(it.data) },
                    metrics = metrics
                )
            } else {
                logger.error { "Pipeline execution failed: ${result.error}" }
                JobExecutionResult.failure(
                    errorMessage = result.error ?: "Pipeline execution failed",
                    metrics = metrics
                )
            }
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val message = e.message ?: "Unknown error"
            val stackTrace = e.stackTraceToString()
            logger.error(e) { "Unexpected error executing Pipeline DSL" }

            JobExecutionResult.failure(
                errorMessage = "Unexpected error: $message\n$stackTrace",
                metrics = mapOf("executionTimeMs" to executionTime)
            )
        }
    }
    
    /**
     * Pipeline de ejemplo para testing inicial.
     */
    private fun createSamplePipeline(): Pipeline {
        return Pipeline(
            name = "Sample Pipeline DSL",
            description = "Generated sample for testing",
            stages = listOf(
                dev.rubentxu.hodei.pipelines.dsl.model.Stage(
                    name = "Sample Stage",
                    description = "Sample stage execution",
                    steps = listOf(
                        dev.rubentxu.hodei.pipelines.dsl.model.Step.Echo(
                            message = "🚀 Pipeline DSL ejecutándose correctamente!"
                        ),
                        dev.rubentxu.hodei.pipelines.dsl.model.Step.Shell(
                            command = "echo 'Build completado'"
                        )
                    )
                )
            )
        )
    }

    override fun canHandle(jobType: JobType): Boolean {
        return jobType == JobType.SCRIPT // Temporalmente usando SCRIPT
    }

    override fun getSupportedJobTypes(): Set<JobType> {
        return setOf(JobType.SCRIPT)
    }
}

/**
 * Factory para crear instancias del runner de pipelines.
 */
object PipelineRunnerFactory {
    
    /**
     * Crea un runner por defecto con configuración estándar.
     */
    fun createDefault(): PipelineRunner {
        return PipelineRunner()
    }
    
    /**
     * Crea un runner con motor personalizado.
     */
    fun createWithCustomEngine(engine: PipelineEngine): PipelineRunner {
        // Por ahora retorna el default, se puede extender después
        return PipelineRunner()
    }
}