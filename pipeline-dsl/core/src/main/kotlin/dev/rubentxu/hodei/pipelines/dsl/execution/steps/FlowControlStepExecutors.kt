package dev.rubentxu.hodei.pipelines.dsl.execution.steps

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutorManager
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import mu.KotlinLogging
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * Ejecutores para steps de control de flujo compatibles con Jenkins Pipeline DSL.
 */
object FlowControlStepExecutors {

    /**
     * Ejecutor para cambio de directorio compatible con Jenkins.
     * Ejecuta steps anidados en un directorio específico.
     */
    class DirStepExecutor(
        private val stepExecutorManager: StepExecutorManager
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Dir) { "Expected Dir step" }
            
            val originalDir = context.workingDirectory
            val targetPath = Paths.get(step.path)
            val absolutePath = if (targetPath.isAbsolute) {
                targetPath.toFile()
            } else {
                originalDir.resolve(step.path)
            }
            
            // Verificar que el directorio existe
            if (!absolutePath.exists() || !absolutePath.isDirectory) {
                throw IllegalArgumentException("Directory does not exist: ${absolutePath.absolutePath}")
            }
            
            // Crear nuevo contexto con el directorio cambiado
            val newContext = PipelineContext(
                jobId = context.jobId,
                workerId = context.workerId,
                workingDirectory = absolutePath,
                environment = context.environment,
                outputChannel = context.outputContext.outputChannel,
                eventChannel = context.eventChannel,
                libraryManager = context.libraryManager,
                securityManager = context.securityManager,
                commandExecutor = context.commandExecutor
            )
            
            // Copiar variables del contexto original
            context.getAllVariables().forEach { (key, value) ->
                newContext.setVariable(key, value)
            }
            
            try {
                context.println("📁 Changed directory to: ${absolutePath.absolutePath}")
                
                // Ejecutar steps anidados con el nuevo contexto
                for (nestedStep in step.steps) {
                    val executor = stepExecutorManager.getExecutor(nestedStep.stepType)
                        ?: throw IllegalStateException("No executor found for step type: ${nestedStep.stepType}")
                    
                    executor.execute(nestedStep, newContext)
                }
                
            } finally {
                context.println("📁 Restored directory to: ${originalDir.absolutePath}")
                
                // Copiar variables de vuelta al contexto original
                newContext.getAllVariables().forEach { (key, value) ->
                    context.setVariable(key, value)
                }
            }
        }
    }

    /**
     * Ejecutor para variables de entorno compatible con Jenkins.
     * Ejecuta steps con variables de entorno adicionales.
     */
    class WithEnvStepExecutor(
        private val stepExecutorManager: StepExecutorManager
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.WithEnv) { "Expected WithEnv step" }
            
            // Guardar variables originales
            val originalEnv = context.environment.toMap()
            
            try {
                // Agregar nuevas variables de entorno
                context.environment.putAll(step.environment)
                
                context.println("🌍 Setting environment variables: ${step.environment.keys}")
                
                // Ejecutar steps anidados
                for (nestedStep in step.steps) {
                    val executor = stepExecutorManager.getExecutor(nestedStep.stepType)
                        ?: throw IllegalArgumentException("No executor found for step type: ${nestedStep.stepType}")
                    
                    executor.execute(nestedStep, context)
                }
                
            } finally {
                // Restaurar variables originales
                context.environment.clear()
                context.environment.putAll(originalEnv)
                
                context.println("🌍 Environment variables restored")
            }
        }
    }

    /**
     * Ejecutor para timeout compatible con Jenkins.
     * Ejecuta steps con un límite de tiempo.
     */
    class TimeoutStepExecutor(
        private val stepExecutorManager: StepExecutorManager
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Timeout) { "Expected Timeout step" }
            
            // Implementar timeout usando coroutines
            kotlinx.coroutines.withTimeout(step.time.toLong() * 60 * 1000) {
                for (nestedStep in step.steps) {
                    val executor = stepExecutorManager.getExecutor(nestedStep.stepType)
                        ?: throw IllegalArgumentException("No executor found for step type: ${nestedStep.stepType}")
                    
                    executor.execute(nestedStep, context)
                }
            }
        }
    }

    /**
     * Ejecutor para retry compatible con Jenkins.
     * Reintenta la ejecución de steps en caso de fallo.
     */
    class RetryStepExecutor(
        private val stepExecutorManager: StepExecutorManager
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Retry) { "Expected Retry step" }
            
            var lastException: Exception? = null
            
            for (attempt in 1..step.count) {
                try {
                    context.println("🔄 Retry attempt $attempt of ${step.count}")
                    
                    // Ejecutar steps anidados
                    for (nestedStep in step.steps) {
                        val executor = stepExecutorManager.getExecutor(nestedStep.stepType)
                            ?: throw IllegalArgumentException("No executor found for step type: ${nestedStep.stepType}")
                        
                        executor.execute(nestedStep, context)
                    }
                    
                    // Si llegamos aquí, fue exitoso
                    context.println("✅ Retry successful on attempt $attempt")
                    return
                    
                } catch (e: Exception) {
                    lastException = e
                    if (attempt < step.count) {
                        context.printError("❌ Attempt $attempt failed: ${e.message}")
                        context.println("⏳ Waiting before retry...")
                        kotlinx.coroutines.delay(5000) // 5 segundos entre reintentos
                    }
                }
            }
            
            // Si llegamos aquí, todos los intentos fallaron
            throw lastException ?: RuntimeException("All retry attempts failed")
        }
    }

    /**
     * Ejecutor para steps paralelos compatible con Jenkins.
     * Ejecuta múltiples branches en paralelo.
     */
    class ParallelStepExecutor(
        private val stepExecutorManager: StepExecutorManager
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Parallel) { "Expected Parallel step" }
            
            context.println("🚀 Starting parallel execution of ${step.branches.size} branches")
            
            // Ejecutar branches en paralelo usando coroutines
            val jobs = step.branches.map { (branchName, branchSteps) ->
                kotlinx.coroutines.async {
                    try {
                        context.println("🌿 Branch '$branchName' started")
                        
                        for (branchStep in branchSteps) {
                            val executor = stepExecutorManager.getExecutor(branchStep.stepType)
                                ?: throw IllegalArgumentException("No executor found for step type: ${branchStep.stepType}")
                            
                            executor.execute(branchStep, context)
                        }
                        
                        context.println("✅ Branch '$branchName' completed")
                        true
                    } catch (e: Exception) {
                        context.printError("❌ Branch '$branchName' failed: ${e.message}")
                        if (step.failFast) {
                            throw e
                        }
                        false
                    }
                }
            }
            
            // Esperar a que todos terminen
            val results = kotlinx.coroutines.awaitAll(*jobs.toTypedArray())
            
            // Verificar si alguno falló
            if (results.any { !it } && !step.failFast) {
                throw RuntimeException("One or more parallel branches failed")
            }
            
            context.println("🏁 Parallel execution completed")
        }
    }
}