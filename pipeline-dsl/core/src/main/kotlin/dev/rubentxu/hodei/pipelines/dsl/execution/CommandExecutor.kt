package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import dev.rubentxu.hodei.pipelines.dsl.security.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withTimeout
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

private val logger = KotlinLogging.logger {}

/**
 * Ejecutor de comandos con responsabilidad única.
 * Separa la lógica de ejecución de comandos del contexto del pipeline.
 */
class CommandExecutor(
    private val outputChannel: SendChannel<PipelineOutputChunk>,
    private val eventChannel: SendChannel<PipelineExecutionEvent>,
    private val securityManager: PipelineSecurityManager,
    private val outputHandler: OutputStreamHandler = OutputStreamHandler(outputChannel)
) {
    companion object {
        private val DEFAULT_TIMEOUT = 30.minutes
        private const val PROCESS_CHECK_INTERVAL_MS = 100L
    }

    /**
     * Ejecuta un comando con verificación de seguridad y streaming de salida.
     */
    suspend fun executeCommand(
        command: List<String>,
        workingDirectory: File,
        environment: Map<String, String>,
        timeout: Duration = DEFAULT_TIMEOUT,
        jobId: String,
        stepId: String
    ): Int {
        // Verificar seguridad del comando
        val commandString = command.joinToString(" ")
        checkCommandSecurity(commandString)
        
        // Configurar proceso
        val processBuilder = ProcessBuilder(command).apply {
            directory(workingDirectory)
            environment().putAll(environment)
            redirectErrorStream(false) // Mantener streams separados para mejor control
        }
        
        return withTimeout(timeout) {
            executeProcess(processBuilder, jobId, stepId, timeout)
        }
    }

    /**
     * Ejecuta un comando shell con verificación de seguridad.
     */
    suspend fun executeShell(
        command: String,
        workingDirectory: File,
        environment: Map<String, String>,
        timeout: Duration = DEFAULT_TIMEOUT,
        jobId: String,
        stepId: String
    ): Int {
        checkCommandSecurity(command)
        
        val shellCommand = if (isWindows()) {
            listOf("cmd", "/c", command)
        } else {
            listOf("sh", "-c", command)
        }
        
        return executeCommand(
            command = shellCommand,
            workingDirectory = workingDirectory,
            environment = environment,
            timeout = timeout,
            jobId = jobId,
            stepId = stepId
        )
    }

    /**
     * Ejecuta un comando batch (Windows) con verificación de seguridad.
     */
    suspend fun executeBatch(
        command: String,
        workingDirectory: File,
        environment: Map<String, String>,
        timeout: Duration = DEFAULT_TIMEOUT,
        jobId: String,
        stepId: String
    ): Int {
        if (!isWindows()) {
            throw UnsupportedOperationException("Batch commands are only supported on Windows")
        }
        
        checkCommandSecurity(command)
        
        return executeCommand(
            command = listOf("cmd", "/c", command),
            workingDirectory = workingDirectory,
            environment = environment,
            timeout = timeout,
            jobId = jobId,
            stepId = stepId
        )
    }

    /**
     * Ejecuta el proceso y maneja el streaming de salida.
     */
    private suspend fun executeProcess(
        processBuilder: ProcessBuilder,
        jobId: String,
        stepId: String,
        timeout: Duration
    ): Int {
        val startTime = System.currentTimeMillis()
        
        // Enviar evento de inicio
        eventChannel.send(
            PipelineExecutionEvent.StepStarted(
                jobId = jobId,
                stepId = stepId,
                timestamp = startTime
            )
        )
        
        val process = processBuilder.start()
        
        try {
            // Stream de salida de forma concurrente
            outputHandler.streamProcessOutput(process, jobId, stepId)
            
            // Esperar finalización con timeout
            val completed = process.waitFor(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
            
            if (!completed) {
                process.destroyForcibly()
                throw ProcessTimeoutException(
                    "Process timed out after $timeout",
                    timeout
                )
            }
            
            val exitCode = process.exitValue()
            val duration = System.currentTimeMillis() - startTime
            
            // Enviar evento de finalización
            eventChannel.send(
                if (exitCode == 0) {
                    PipelineExecutionEvent.StepCompleted(
                        jobId = jobId,
                        stepId = stepId,
                        timestamp = System.currentTimeMillis(),
                        duration = duration
                    )
                } else {
                    PipelineExecutionEvent.StepFailed(
                        jobId = jobId,
                        stepId = stepId,
                        timestamp = System.currentTimeMillis(),
                        error = "Process exited with code $exitCode",
                        duration = duration
                    )
                }
            )
            
            return exitCode
            
        } catch (e: Exception) {
            process.destroyForcibly()
            
            // Enviar evento de error
            eventChannel.send(
                PipelineExecutionEvent.StepFailed(
                    jobId = jobId,
                    stepId = stepId,
                    timestamp = System.currentTimeMillis(),
                    error = e.message ?: "Unknown error",
                    duration = System.currentTimeMillis() - startTime
                )
            )
            
            throw e
        }
    }

    /**
     * Verifica la seguridad de un comando antes de ejecutarlo.
     */
    private fun checkCommandSecurity(command: String) {
        val securityCheck = securityManager.checkScriptAccess(command)
        if (securityCheck is SecurityCheckResult.Denied) {
            val violations = securityCheck.violations.joinToString(", ") { it.message }
            throw SecurityException("Command blocked by security policy: $violations")
        }
    }

    /**
     * Detecta si el sistema es Windows.
     */
    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("windows")
}

/**
 * Excepción para timeout de procesos.
 */
class ProcessTimeoutException(
    message: String,
    val timeout: Duration
) : RuntimeException(message)