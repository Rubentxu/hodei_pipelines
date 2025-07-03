package dev.rubentxu.hodei.worker.execution

import dev.rubentxu.hodei.pipelines.v1.LogChunk
import dev.rubentxu.hodei.pipelines.v1.LogStream
import dev.rubentxu.hodei.pipelines.v1.ShellTask
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Result of a shell execution
 */
data class ExecutionResult(
    val success: Boolean,
    val exitCode: Int,
    val details: String
)

/**
 * Executes shell commands for worker jobs
 */
class ShellExecutor(private val baseWorkDir: String) {
    
    suspend fun execute(
        shellTask: ShellTask,
        executionId: String,
        logCallback: suspend (LogChunk) -> Unit
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                logger.info { "Executing shell task for execution $executionId with ${shellTask.commandsCount} commands" }
                
                // Create execution-specific work directory
                val workDir = File(baseWorkDir, "execution-$executionId")
                if (!workDir.exists()) {
                    workDir.mkdirs()
                }
                
                // Log the working directory
                logCallback(createLogChunk(LogStream.STDOUT, "Working directory: ${workDir.absolutePath}\n"))
                
                // Execute each command
                for ((index, command) in shellTask.commandsList.withIndex()) {
                    logger.debug { "Executing command ${index + 1}/${shellTask.commandsCount}: $command" }
                    
                    logCallback(createLogChunk(LogStream.STDOUT, "$ $command\n"))
                    
                    val result = executeCommand(command, workDir, logCallback)
                    
                    if (!result.success) {
                        return@withContext ExecutionResult(
                            success = false,
                            exitCode = result.exitCode,
                            details = "Command ${index + 1} failed: $command"
                        )
                    }
                }
                
                logCallback(createLogChunk(LogStream.STDOUT, "All commands completed successfully\n"))
                
                ExecutionResult(
                    success = true,
                    exitCode = 0,
                    details = "Shell execution completed successfully"
                )
                
            } catch (e: Exception) {
                logger.error(e) { "Shell execution failed for $executionId" }
                
                logCallback(createLogChunk(LogStream.STDERR, "Execution failed: ${e.message}\n"))
                
                ExecutionResult(
                    success = false,
                    exitCode = -1,
                    details = "Shell execution failed: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun executeCommand(
        command: String,
        workDir: File,
        logCallback: suspend (LogChunk) -> Unit
    ): ExecutionResult {
        
        val processBuilder = ProcessBuilder()
            .command("sh", "-c", command)
            .directory(workDir)
            .redirectErrorStream(false)
        
        val process = processBuilder.start()
        
        // Read stdout and stderr in separate threads
        val stdoutReader = Thread {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    kotlinx.coroutines.runBlocking {
                        logCallback(createLogChunk(LogStream.STDOUT, "$line\n"))
                    }
                }
            }
        }
        
        val stderrReader = Thread {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    kotlinx.coroutines.runBlocking {
                        logCallback(createLogChunk(LogStream.STDERR, "$line\n"))
                    }
                }
            }
        }
        
        stdoutReader.start()
        stderrReader.start()
        
        // Wait for process to complete
        val exitCode = process.waitFor()
        
        // Wait for output readers to finish
        stdoutReader.join()
        stderrReader.join()
        
        return ExecutionResult(
            success = exitCode == 0,
            exitCode = exitCode,
            details = if (exitCode == 0) "Command succeeded" else "Command failed with exit code $exitCode"
        )
    }
    
    private fun createLogChunk(stream: LogStream, content: String): LogChunk {
        return LogChunk.newBuilder()
            .setStream(stream)
            .setContent(com.google.protobuf.ByteString.copyFromUtf8(content))
            .build()
    }
    
    fun cleanup() {
        logger.info { "Shell executor cleanup completed" }
        // Could clean up old execution directories here if needed
    }
}