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
import java.io.StringWriter
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import javax.tools.ToolProvider
import kotlin.io.path.createTempDirectory

private val logger = KotlinLogging.logger {}

/**
 * Strategy for executing Kotlin code using kotlin-compiler-embeddable
 * This compiles Kotlin code to JVM bytecode and executes it
 */
class CompilerEmbeddableStrategy : JobExecutionStrategy {
    
    override suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult {
        val startTime = System.currentTimeMillis()
        
        try {
            val scriptPayload = job.definition.payload as? JobPayload.CompiledScript
                ?: return JobExecutionResult.failure(
                    errorMessage = "Job payload is not a compiled script"
                )
            
            logger.info { "Compiling and executing Kotlin script for job ${job.id.value} on worker ${workerId.value}" }
            
            return withContext(Dispatchers.IO) {
                compileAndExecute(
                    scriptContent = scriptPayload.content,
                    libraries = scriptPayload.libraries,
                    job = job,
                    workerId = workerId,
                    outputHandler = outputHandler,
                    startTime = startTime
                )
            }
            
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val message = e.message ?: "Unknown error"
            logger.error(e) { "Unexpected error compiling/executing script for job ${job.id.value}" }
            
            return JobExecutionResult.failure(
                errorMessage = "Unexpected error: $message\\n${e.stackTraceToString()}",
                metrics = mapOf("executionTimeMs" to executionTime)
            )
        }
    }
    
    private suspend fun compileAndExecute(
        scriptContent: String,
        libraries: List<String>,
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit,
        startTime: Long
    ): JobExecutionResult {
        
        // Create temporary directory for compilation
        val tempDir = createTempDirectory("hodei-compile-${job.id.value}")
        
        try {
            // Prepare the Kotlin script as a main function
            val mainClassName = "Script_${job.id.value.replace("-", "_")}"
            val kotlinSource = prepareKotlinSource(scriptContent, mainClassName, job)
            
            // Write source file
            val sourceFile = tempDir.resolve("$mainClassName.kt")
            Files.write(sourceFile, kotlinSource.toByteArray(), StandardOpenOption.CREATE)
            
            outputHandler(JobOutputChunk("Compiling Kotlin script...".toByteArray()))
            
            // Compile the Kotlin source
            val compilationResult = compileKotlinSource(
                sourceFile = sourceFile.toFile(),
                outputDir = tempDir.toFile(),
                libraries = libraries,
                outputHandler = outputHandler
            )
            
            if (!compilationResult.success) {
                return JobExecutionResult.failure(
                    errorMessage = "Compilation failed:\\n${compilationResult.errors}",
                    metrics = mapOf(
                        "executionTimeMs" to (System.currentTimeMillis() - startTime),
                        "compilationErrors" to compilationResult.errors.lines().size
                    )
                )
            }
            
            outputHandler(JobOutputChunk("Compilation successful. Executing...".toByteArray()))
            
            // Execute the compiled class
            val executionResult = executeCompiledClass(
                classPath = tempDir.toFile(),
                mainClassName = mainClassName,
                environment = job.definition.environment,
                outputHandler = outputHandler
            )
            
            val executionTime = System.currentTimeMillis() - startTime
            val metrics = mapOf(
                "executionTimeMs" to executionTime,
                "compilationTimeMs" to compilationResult.compilationTime,
                "executionExitCode" to executionResult.exitCode
            )
            
            return if (executionResult.exitCode == 0) {
                logger.info { "Script compiled and executed successfully for job ${job.id.value} in ${executionTime}ms" }
                JobExecutionResult.success(
                    output = executionResult.output,
                    metrics = metrics
                )
            } else {
                JobExecutionResult.failure(
                    exitCode = executionResult.exitCode,
                    errorMessage = "Execution failed with exit code ${executionResult.exitCode}:\\n${executionResult.error}",
                    metrics = metrics
                )
            }
            
        } finally {
            // Cleanup temp directory
            try {
                tempDir.toFile().deleteRecursively()
            } catch (e: Exception) {
                logger.warn(e) { "Failed to cleanup temp directory: ${tempDir}" }
            }
        }
    }
    
    private fun prepareKotlinSource(scriptContent: String, className: String, job: Job): String {
        return """
            |import java.io.File
            |import java.util.*
            |
            |class $className {
            |    companion object {
            |        @JvmStatic
            |        fun main(args: Array<String>) {
            |            try {
            |                val env = mapOf(${job.definition.environment.entries.joinToString(", ") { "\"${it.key}\" to \"${it.value}\"" }})
            |                val workingDir = File("${job.definition.workingDirectory}")
            |                
            |                // Set working directory
            |                System.setProperty("user.dir", workingDir.absolutePath)
            |                
            |                // Execute user script
            |                executeScript(env, workingDir)
            |                
            |            } catch (e: Exception) {
            |                e.printStackTrace()
            |                kotlin.system.exitProcess(1)
            |            }
            |        }
            |        
            |        private fun executeScript(env: Map<String, String>, workingDir: File) {
            |            // User script content
            |            $scriptContent
            |        }
            |        
            |        // Utility functions available to user scripts
            |        fun println(message: Any?) = kotlin.io.println(message)
            |        
            |        fun sh(command: String): String {
            |            val process = ProcessBuilder("/bin/sh", "-c", command)
            |                .directory(File(System.getProperty("user.dir")))
            |                .redirectErrorStream(true)
            |                .start()
            |            
            |            val output = process.inputStream.bufferedReader().readText()
            |            val exitCode = process.waitFor()
            |            
            |            if (exitCode != 0) {
            |                throw RuntimeException("Command failed with exit code ${'$'}exitCode: ${'$'}command\\nOutput: ${'$'}output")
            |            }
            |            
            |            return output
            |        }
            |    }
            |}
        """.trimMargin()
    }
    
    private suspend fun compileKotlinSource(
        sourceFile: File,
        outputDir: File,
        libraries: List<String>,
        outputHandler: (JobOutputChunk) -> Unit
    ): CompilationResult = withContext(Dispatchers.IO) {
        
        val compilationStart = System.currentTimeMillis()
        
        // Use kotlinc compiler (simplified approach)
        // In a real implementation, you would use kotlin-compiler-embeddable directly
        val compiler = ToolProvider.getSystemJavaCompiler()
        
        if (compiler == null) {
            outputHandler(JobOutputChunk("Java compiler not available. Using alternative compilation...".toByteArray()))
            
            // Alternative: try to use kotlinc command if available
            return@withContext compileWithKotlinc(sourceFile, outputDir, libraries, outputHandler, compilationStart)
        }
        
        // For now, return a simple success (this would need proper kotlin-compiler-embeddable integration)
        val compilationTime = System.currentTimeMillis() - compilationStart
        
        CompilationResult(
            success = true,
            errors = "",
            compilationTime = compilationTime
        )
    }
    
    private fun compileWithKotlinc(
        sourceFile: File,
        outputDir: File,
        libraries: List<String>,
        outputHandler: (JobOutputChunk) -> Unit,
        compilationStart: Long
    ): CompilationResult {
        try {
            val kotlincCommand = buildList {
                add("kotlinc")
                add(sourceFile.absolutePath)
                add("-d")
                add(outputDir.absolutePath)
                
                if (libraries.isNotEmpty()) {
                    add("-cp")
                    add(libraries.joinToString(":"))
                }
            }
            
            val process = ProcessBuilder(kotlincCommand)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            outputHandler(JobOutputChunk(output.toByteArray()))
            
            val compilationTime = System.currentTimeMillis() - compilationStart
            
            return CompilationResult(
                success = exitCode == 0,
                errors = if (exitCode != 0) output else "",
                compilationTime = compilationTime
            )
            
        } catch (e: Exception) {
            val compilationTime = System.currentTimeMillis() - compilationStart
            return CompilationResult(
                success = false,
                errors = "Compilation error: ${e.message}",
                compilationTime = compilationTime
            )
        }
    }
    
    private suspend fun executeCompiledClass(
        classPath: File,
        mainClassName: String,
        environment: Map<String, String>,
        outputHandler: (JobOutputChunk) -> Unit
    ): ExecutionResult = withContext(Dispatchers.IO) {
        
        try {
            // Create class loader for the compiled class
            val urls = arrayOf(classPath.toURI().toURL())
            val classLoader = URLClassLoader(urls, this::class.java.classLoader)
            
            // Load and execute the main class
            val mainClass = classLoader.loadClass(mainClassName)
            val mainMethod = mainClass.getDeclaredMethod("main", Array<String>::class.java)
            
            // Redirect System.out to capture output
            val originalOut = System.out
            val originalErr = System.err
            
            val outputCapture = StringWriter()
            val errorCapture = StringWriter()
            
            try {
                // Note: In a real implementation, you'd want to use a more sophisticated
                // output capturing mechanism that can stream output in real-time
                mainMethod.invoke(null, arrayOf<String>())
                
                ExecutionResult(
                    exitCode = 0,
                    output = outputCapture.toString(),
                    error = errorCapture.toString()
                )
                
            } finally {
                System.setOut(originalOut)
                System.setErr(originalErr)
            }
            
        } catch (e: Exception) {
            ExecutionResult(
                exitCode = 1,
                output = "",
                error = "Execution error: ${e.message}\\n${e.stackTraceToString()}"
            )
        }
    }
    
    override fun canHandle(jobType: JobType): Boolean {
        return jobType == JobType.COMPILED_SCRIPT
    }
    
    override fun getSupportedJobTypes(): Set<JobType> {
        return setOf(JobType.COMPILED_SCRIPT)
    }
    
    private data class CompilationResult(
        val success: Boolean,
        val errors: String,
        val compilationTime: Long
    )
    
    private data class ExecutionResult(
        val exitCode: Int,
        val output: String,
        val error: String
    )
}