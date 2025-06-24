package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import mu.KotlinLogging
import java.io.File
import java.nio.file.Paths

private val logger = KotlinLogging.logger {}

/**
 * Registry para ejecutores de steps del Pipeline DSL.
 */
interface StepExecutorManager {
    fun getExecutor(stepType: String): StepExecutor?
    fun registerExecutor(stepType: String, executor: StepExecutor)
}

/**
 * Implementación del gestor de ejecutores de steps.
 */
class PipelineStepExecutorManager : StepExecutorManager {
    private val executors = mutableMapOf<String, StepExecutor>()
    
    init {
        // Registrar ejecutores core del Pipeline DSL
        registerExecutor("sh", ShellStepExecutor())
        registerExecutor("bat", BatchStepExecutor())
        registerExecutor("echo", EchoStepExecutor())
        registerExecutor("script", ScriptStepExecutor())
        // NOTE: archiveArtifacts, publishTestResults, checkout, docker, notification executors
        // moved to dedicated extension modules for better modularity
        // NOTE: dir, withEnv, timeout, retry, parallel executors provided by pipeline-steps-library extension
    }
    
    override fun getExecutor(stepType: String): StepExecutor? = executors[stepType]
    
    override fun registerExecutor(stepType: String, executor: StepExecutor) {
        executors[stepType] = executor
    }
}

/**
 * Interfaz base para ejecutores de steps.
 */
interface StepExecutor {
    suspend fun execute(step: Step, context: PipelineContext)
}

/**
 * Ejecutor para comandos shell.
 */
class ShellStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Shell) { "Expected Shell step" }
        
        try {
            val result = if (step.workingDirectory != null) {
                val currentDir = System.getProperty("user.dir")
                System.setProperty("user.dir", step.workingDirectory)
                try {
                    context.sh(step.command)
                } finally {
                    System.setProperty("user.dir", currentDir)
                }
            } else {
                context.sh(step.command)
            }
            
            if (step.returnStdout) {
                context.println("Command output: $result")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Shell command failed: ${step.command}" }
            throw e
        }
    }
}

/**
 * Ejecutor para comandos batch.
 */
class BatchStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Batch) { "Expected Batch step" }
        
        try {
            val result = if (step.workingDirectory != null) {
                val currentDir = System.getProperty("user.dir")
                System.setProperty("user.dir", step.workingDirectory)
                try {
                    context.bat(step.command)
                } finally {
                    System.setProperty("user.dir", currentDir)
                }
            } else {
                context.bat(step.command)
            }
            
            if (step.returnStdout) {
                context.println("Command output: $result")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Batch command failed: ${step.command}" }
            throw e
        }
    }
}

/**
 * Ejecutor para mensajes echo.
 */
class EchoStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Echo) { "Expected Echo step" }
        context.println(step.message)
    }
}

// NOTE: ArchiveArtifactsStepExecutor, PublishTestResultsStepExecutor, and CheckoutStepExecutor
// moved to dedicated extension modules for better modularity

/**
 * Ejecutor para scripts.
 */
class ScriptStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Script) { "Expected Script step" }
        
        val scriptFile = File(step.scriptFile)
        if (!scriptFile.exists()) {
            throw IllegalArgumentException("Script file not found: ${step.scriptFile}")
        }
        
        fun detectInterpreter(scriptFile: File): String {
            return when (scriptFile.extension.lowercase()) {
                "sh" -> "/bin/sh"
                "bash" -> "/bin/bash"
                "py" -> "python"
                "rb" -> "ruby"
                "js" -> "node"
                else -> "/bin/sh"
            }
        }
        
        val interpreter = step.interpreter ?: detectInterpreter(scriptFile)
        val command = "$interpreter ${step.scriptFile}"
        
        // Set script parameters as environment variables
        val envVars = step.parameters.map { "${it.key}=${it.value}" }.joinToString(" ")
        val fullCommand = if (envVars.isNotEmpty()) "$envVars $command" else command
        
        context.sh(fullCommand)
        context.println("✅ Script executed: ${step.scriptFile}")
    }
}

// NOTE: DockerStepExecutor and NotificationStepExecutor removed
// These are now provided by dedicated extension modules (docker-steps, notification-steps)

// NOTE: DirStepExecutor and WithEnvStepExecutor removed
// These are now provided by the pipeline-steps-library extension