package dev.rubentxu.hodei.worker.execution

import dev.rubentxu.hodei.pipelines.v1.KotlinScriptTask
import dev.rubentxu.hodei.pipelines.v1.LogChunk
import dev.rubentxu.hodei.pipelines.v1.LogStream
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

private val logger = KotlinLogging.logger {}

/**
 * Executes Kotlin scripts for worker jobs
 * Note: This is a basic implementation for MVP. Full Kotlin DSL support will be added later.
 */
class KotlinScriptExecutor(private val baseWorkDir: String) {
    
    suspend fun execute(
        kotlinTask: KotlinScriptTask,
        executionId: String,
        logCallback: suspend (LogChunk) -> Unit
    ): ExecutionResult {
        return withContext(Dispatchers.IO) {
            try {
                logger.info { "Executing Kotlin script for execution $executionId" }
                
                // Create execution-specific work directory
                val workDir = File(baseWorkDir, "execution-$executionId")
                if (!workDir.exists()) {
                    workDir.mkdirs()
                }
                
                logCallback(createLogChunk(LogStream.STDOUT, "Working directory: ${workDir.absolutePath}\n"))
                logCallback(createLogChunk(LogStream.STDOUT, "Executing Kotlin script...\n"))
                
                // For MVP, we'll save the script to a file and execute it with kotlinc
                val scriptFile = File(workDir, "script.kts")
                scriptFile.writeText(kotlinTask.scriptContent)
                
                logCallback(createLogChunk(LogStream.STDOUT, "Script saved to: ${scriptFile.absolutePath}\n"))
                
                // Try to execute with kotlinc if available, otherwise just log the script content
                val result = if (isKotlincAvailable()) {
                    executeWithKotlinc(scriptFile, workDir, logCallback)
                } else {
                    executeBasicScript(kotlinTask, logCallback)
                }
                
                if (result.success) {
                    logCallback(createLogChunk(LogStream.STDOUT, "Kotlin script execution completed successfully\n"))
                } else {
                    logCallback(createLogChunk(LogStream.STDERR, "Kotlin script execution failed\n"))
                }
                
                result
                
            } catch (e: Exception) {
                logger.error(e) { "Kotlin script execution failed for $executionId" }
                
                logCallback(createLogChunk(LogStream.STDERR, "Execution failed: ${e.message}\n"))
                
                ExecutionResult(
                    success = false,
                    exitCode = -1,
                    details = "Kotlin script execution failed: ${e.message}"
                )
            }
        }
    }
    
    private suspend fun executeWithKotlinc(
        scriptFile: File,
        workDir: File,
        logCallback: suspend (LogChunk) -> Unit
    ): ExecutionResult {
        try {
            logCallback(createLogChunk(LogStream.STDOUT, "Executing script with kotlinc...\n"))
            
            val processBuilder = ProcessBuilder()
                .command("kotlinc", "-script", scriptFile.absolutePath)
                .directory(workDir)
                .redirectErrorStream(false)
            
            val process = processBuilder.start()
            
            // Read stdout and stderr
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
            
            val exitCode = process.waitFor()
            
            stdoutReader.join()
            stderrReader.join()
            
            return ExecutionResult(
                success = exitCode == 0,
                exitCode = exitCode,
                details = if (exitCode == 0) "Kotlin script executed successfully" else "Kotlin script failed with exit code $exitCode"
            )
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to execute with kotlinc" }
            return ExecutionResult(
                success = false,
                exitCode = -1,
                details = "Failed to execute with kotlinc: ${e.message}"
            )
        }
    }
    
    private suspend fun executeBasicScript(
        kotlinTask: KotlinScriptTask,
        logCallback: suspend (LogChunk) -> Unit
    ): ExecutionResult {
        // For MVP: Basic script parsing and execution simulation
        logCallback(createLogChunk(LogStream.STDOUT, "kotlinc not available, parsing script content...\n"))
        logCallback(createLogChunk(LogStream.STDOUT, "Script content:\n"))
        logCallback(createLogChunk(LogStream.STDOUT, "${kotlinTask.scriptContent}\n"))
        
        // Parse basic script structure
        val scriptLines = kotlinTask.scriptContent.lines()
        
        // Look for simple shell commands in stage/step blocks
        val shellCommands = extractShellCommands(scriptLines)
        
        if (shellCommands.isNotEmpty()) {
            logCallback(createLogChunk(LogStream.STDOUT, "Found ${shellCommands.size} shell commands to execute:\n"))
            
            for ((index, command) in shellCommands.withIndex()) {
                logCallback(createLogChunk(LogStream.STDOUT, "Executing: $command\n"))
                
                // Execute shell command
                val processBuilder = ProcessBuilder()
                    .command("sh", "-c", command)
                    .redirectErrorStream(false)
                
                val process = processBuilder.start()
                
                // Capture output
                val stdout = process.inputStream.bufferedReader().readText()
                val stderr = process.errorStream.bufferedReader().readText()
                
                val exitCode = process.waitFor()
                
                if (stdout.isNotEmpty()) {
                    logCallback(createLogChunk(LogStream.STDOUT, stdout))
                }
                if (stderr.isNotEmpty()) {
                    logCallback(createLogChunk(LogStream.STDERR, stderr))
                }
                
                if (exitCode != 0) {
                    return ExecutionResult(
                        success = false,
                        exitCode = exitCode,
                        details = "Command ${index + 1} failed: $command"
                    )
                }
            }
        } else {
            logCallback(createLogChunk(LogStream.STDOUT, "No executable commands found in script\n"))
        }
        
        return ExecutionResult(
            success = true,
            exitCode = 0,
            details = "Basic Kotlin script execution completed"
        )
    }
    
    private fun extractShellCommands(scriptLines: List<String>): List<String> {
        val commands = mutableListOf<String>()
        
        for (line in scriptLines) {
            val trimmed = line.trim()
            
            // Look for sh() calls
            if (trimmed.contains("sh(")) {
                val shPattern = Regex("""sh\s*\(\s*["']([^"']+)["']\s*\)""")
                val match = shPattern.find(trimmed)
                if (match != null) {
                    commands.add(match.groupValues[1])
                }
            }
        }
        
        return commands
    }
    
    private fun isKotlincAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("kotlinc", "-version")
                .redirectErrorStream(true)
                .start()
            
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    private fun createLogChunk(stream: LogStream, content: String): LogChunk {
        return LogChunk.newBuilder()
            .setStream(stream)
            .setContent(com.google.protobuf.ByteString.copyFromUtf8(content))
            .build()
    }
    
    fun cleanup() {
        logger.info { "Kotlin script executor cleanup completed" }
        // Could clean up temporary script files here if needed
    }
}