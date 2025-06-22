package dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.model.execution.JobExecutionResult
import dev.rubentxu.hodei.pipelines.domain.worker.ports.JobExecutionStrategy
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Strategy for executing system commands
 */
class SystemCommandStrategy : JobExecutionStrategy {
    
    override suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            val commandPayload = job.definition.payload as? JobPayload.Command
                ?: return JobExecutionResult.failure(
                    errorMessage = "Job payload is not a command"
                )
            
            logger.info { "Executing system command for job ${job.id.value} on worker ${workerId.value}" }
            
            val commandLines = commandPayload.commandLine
            if (commandLines.isEmpty()) {
                return JobExecutionResult.failure(
                    errorMessage = "Command line is empty"
                )
            }
            
            // Execute command(s)
            var totalOutput = StringBuilder()
            var finalExitCode = 0
            
            for ((index, commandLine) in commandLines.withIndex()) {
                logger.debug { "Executing command ${index + 1}/${commandLines.size}: $commandLine" }
                
                val result = executeCommand(
                    command = commandLine,
                    workingDirectory = File(job.definition.workingDirectory),
                    environment = job.definition.environment,
                    outputHandler = outputHandler,
                    timeoutSeconds = if (job.definition.timeoutSeconds > 0) job.definition.timeoutSeconds.toLong() else 300L
                )
                
                totalOutput.append(result.output)
                
                if (result.exitCode != 0) {
                    finalExitCode = result.exitCode
                    logger.warn { "Command failed with exit code ${result.exitCode}: $commandLine" }
                    
                    val executionTime = System.currentTimeMillis() - startTime
                    return JobExecutionResult.failure(
                        exitCode = result.exitCode,
                        errorMessage = "Command failed: $commandLine\\nOutput: ${result.output}\\nError: ${result.error}",
                        metrics = mapOf(
                            "executionTimeMs" to executionTime,
                            "commandsExecuted" to index + 1,
                            "totalCommands" to commandLines.size
                        )
                    )
                }
            }
            
            val executionTime = System.currentTimeMillis() - startTime
            logger.info { "All commands executed successfully for job ${job.id.value} in ${executionTime}ms" }
            
            return JobExecutionResult.success(
                exitCode = finalExitCode,
                output = totalOutput.toString(),
                metrics = mapOf(
                    "executionTimeMs" to executionTime,
                    "commandsExecuted" to commandLines.size,
                    "totalCommands" to commandLines.size
                )
            )
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val message = e.message ?: "Unknown error"
            logger.error(e) { "Unexpected error executing command for job ${job.id.value}" }
            
            return JobExecutionResult.failure(
                errorMessage = "Unexpected error: $message",
                metrics = mapOf("executionTimeMs" to executionTime)
            )
        }
    }
    
    private suspend fun executeCommand(
        command: String,
        workingDirectory: File,
        environment: Map<String, String>,
        outputHandler: (JobOutputChunk) -> Unit,
        timeoutSeconds: Long
    ): CommandResult = withContext(Dispatchers.IO) {
        
        val processBuilder = ProcessBuilder()
            .command(parseCommand(command))
            .directory(workingDirectory)
            .redirectErrorStream(false)
        
        // Set environment variables
        val processEnv = processBuilder.environment()
        environment.forEach { (key, value) ->
            processEnv[key] = value
        }
        
        val process = processBuilder.start()
        
        val outputCollector = StringBuilder()
        val errorCollector = StringBuilder()
        
        // Read stdout
        val stdoutThread = Thread {
            process.inputStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    outputCollector.appendLine(line)
                    outputHandler(JobOutputChunk(line.toByteArray(), isError = false))
                }
            }
        }
        
        // Read stderr
        val stderrThread = Thread {
            process.errorStream.bufferedReader().use { reader ->
                reader.lineSequence().forEach { line ->
                    errorCollector.appendLine(line)
                    outputHandler(JobOutputChunk(line.toByteArray(), isError = true))
                }
            }
        }
        
        stdoutThread.start()
        stderrThread.start()
        
        // Wait for process completion with timeout
        val completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        
        if (!completed) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out after ${timeoutSeconds}s: $command")
        }
        
        // Wait for output threads to complete
        stdoutThread.join(1000)
        stderrThread.join(1000)
        
        CommandResult(
            exitCode = process.exitValue(),
            output = outputCollector.toString(),
            error = errorCollector.toString()
        )
    }
    
    private fun parseCommand(command: String): List<String> {
        // Simple command parsing - in production, you might want more sophisticated parsing
        return if (System.getProperty("os.name").lowercase().contains("windows")) {
            listOf("cmd", "/c", command)
        } else {
            listOf("/bin/sh", "-c", command)
        }
    }
    
    override fun canHandle(jobType: JobType): Boolean {
        return jobType == JobType.COMMAND
    }
    
    override fun getSupportedJobTypes(): Set<JobType> {
        return setOf(JobType.COMMAND)
    }
    
    private data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String
    )
}