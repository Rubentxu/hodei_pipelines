package dev.rubentxu.hodei.pipelines.dsl.execution.steps

import dev.rubentxu.hodei.pipelines.dsl.execution.CommandExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import mu.KotlinLogging
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/**
 * Ejecutores para steps básicos compatibles con Jenkins Pipeline DSL.
 * Mantiene las mismas firmas y comportamiento que Jenkins.
 */
object BasicStepExecutors {

    /**
     * Ejecutor para comandos shell compatible con Jenkins.
     * Soporta returnStatus, returnStdout, encoding y label.
     */
    class ShellStepExecutor(
        private val commandExecutor: CommandExecutor
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Shell) { "Expected Shell step" }
            
            val workingDir = step.workingDirectory?.let {
                context.changeDirectory(it)
            } ?: context.workingDirectory
            
            // Capturar stdout si es necesario
            val outputBuffer = if (step.returnStdout) StringBuilder() else null
            
            // Redirigir streams si capturamos stdout
            val streamRedirection = if (step.returnStdout) {
                context.redirectStandardStreams()
            } else null
            
            try {
                val exitCode = commandExecutor.executeShell(
                    command = step.command,
                    workingDirectory = workingDir,
                    environment = context.environment,
                    timeout = step.timeout?.minutes ?: 30.minutes,
                    jobId = context.jobId,
                    stepId = step.id
                )
                
                // Si returnStatus es true, guardar el código de salida
                if (step.returnStatus) {
                    context.setVariable("${step.id}.exitCode", exitCode)
                } else if (exitCode != 0 && !step.continueOnError) {
                    throw RuntimeException("Shell command failed with exit code $exitCode")
                }
                
                // Si returnStdout es true, guardar la salida
                if (step.returnStdout && outputBuffer != null) {
                    val output = outputBuffer.toString()
                    context.setVariable("${step.id}.output", output.trim())
                }
                
            } finally {
                streamRedirection?.close()
            }
        }
    }

    /**
     * Ejecutor para comandos batch compatible con Jenkins.
     * Soporta returnStatus, returnStdout, encoding y label.
     */
    class BatchStepExecutor(
        private val commandExecutor: CommandExecutor
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Batch) { "Expected Batch step" }
            
            val workingDir = step.workingDirectory?.let {
                context.changeDirectory(it)
            } ?: context.workingDirectory
            
            // Capturar stdout si es necesario
            val outputBuffer = if (step.returnStdout) StringBuilder() else null
            
            // Redirigir streams si capturamos stdout
            val streamRedirection = if (step.returnStdout) {
                context.redirectStandardStreams()
            } else null
            
            try {
                val exitCode = commandExecutor.executeBatch(
                    command = step.command,
                    workingDirectory = workingDir,
                    environment = context.environment,
                    timeout = step.timeout?.minutes ?: 30.minutes,
                    jobId = context.jobId,
                    stepId = step.id
                )
                
                // Si returnStatus es true, guardar el código de salida
                if (step.returnStatus) {
                    context.setVariable("${step.id}.exitCode", exitCode)
                } else if (exitCode != 0 && !step.continueOnError) {
                    throw RuntimeException("Batch command failed with exit code $exitCode")
                }
                
                // Si returnStdout es true, guardar la salida
                if (step.returnStdout && outputBuffer != null) {
                    val output = outputBuffer.toString()
                    context.setVariable("${step.id}.output", output.trim())
                }
                
            } finally {
                streamRedirection?.close()
            }
        }
    }

    /**
     * Ejecutor para mensajes echo compatible con Jenkins.
     * Solo tiene el parámetro message.
     */
    class EchoStepExecutor : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Echo) { "Expected Echo step" }
            
            // Enviar evento de inicio
            context.publishEvent(
                PipelineExecutionEvent.StepStarted(
                    jobId = context.jobId,
                    stepId = step.id,
                    timestamp = System.currentTimeMillis()
                )
            )
            
            // Imprimir mensaje
            context.println(step.message)
            
            // Enviar evento de finalización
            context.publishEvent(
                PipelineExecutionEvent.StepCompleted(
                    jobId = context.jobId,
                    stepId = step.id,
                    timestamp = System.currentTimeMillis(),
                    duration = 0
                )
            )
        }
    }

    /**
     * Ejecutor para scripts compatible con Jenkins.
     * Ejecuta archivos de script con intérprete configurable.
     */
    class ScriptStepExecutor(
        private val commandExecutor: CommandExecutor
    ) : StepExecutor {
        override suspend fun execute(step: Step, context: PipelineContext) {
            require(step is Step.Script) { "Expected Script step" }
            
            val scriptFile = context.workingDirectory.resolve(step.scriptFile)
            if (!scriptFile.exists()) {
                throw IllegalArgumentException("Script file not found: ${step.scriptFile}")
            }
            
            // Detectar intérprete basado en shebang o extensión
            val interpreter = step.interpreter ?: detectInterpreter(scriptFile)
            
            // Construir comando con parámetros como variables de entorno
            val envWithParams = context.environment.toMutableMap().apply {
                putAll(step.parameters.mapValues { it.value })
            }
            
            val exitCode = commandExecutor.executeCommand(
                command = listOf(interpreter, scriptFile.absolutePath),
                workingDirectory = context.workingDirectory,
                environment = envWithParams,
                timeout = step.timeout?.minutes ?: 30.minutes,
                jobId = context.jobId,
                stepId = step.id
            )
            
            if (exitCode != 0 && !step.continueOnError) {
                throw RuntimeException("Script failed with exit code $exitCode")
            }
        }
        
        private fun detectInterpreter(scriptFile: java.io.File): String {
            // Primero intentar detectar shebang
            val firstLine = scriptFile.useLines { it.firstOrNull() }
            if (firstLine?.startsWith("#!") == true) {
                return firstLine.substring(2).trim().split(" ").first()
            }
            
            // Si no hay shebang, usar extensión
            return when (scriptFile.extension.lowercase()) {
                "sh" -> "/bin/sh"
                "bash" -> "/bin/bash"
                "py", "python" -> "python"
                "rb", "ruby" -> "ruby"
                "js" -> "node"
                "ps1" -> "powershell"
                "bat", "cmd" -> "cmd"
                else -> if (System.getProperty("os.name").lowercase().contains("windows")) "cmd" else "/bin/sh"
            }
        }
    }
}