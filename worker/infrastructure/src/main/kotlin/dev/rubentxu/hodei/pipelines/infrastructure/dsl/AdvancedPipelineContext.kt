package dev.rubentxu.hodei.pipelines.infrastructure.dsl

import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.security.PipelineSecurityManager
import dev.rubentxu.hodei.pipelines.infrastructure.security.SecurityCheckResult
import dev.rubentxu.hodei.pipelines.infrastructure.dsl.LibraryManager
import dev.rubentxu.hodei.pipelines.infrastructure.dsl.LibraryReference
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import mu.KotlinLogging
import java.io.File
import java.time.Instant

private val logger = KotlinLogging.logger {}

/**
 * Advanced Pipeline Context with enhanced DSL capabilities
 */
class AdvancedPipelineContext(
    val jobId: JobId,
    val workerId: WorkerId,
    private val environment: Map<String, String>,
    val outputChannel: Channel<JobOutputChunk>,
    val eventChannel: Channel<JobExecutionEvent>,
    private val securityManager: PipelineSecurityManager? = null,
    private val libraryManager: LibraryManager? = null
) {
    private var currentStage: String? = null
    private val stageStartTimes = mutableMapOf<String, Long>()
    private val executedStages = mutableListOf<String>()
    private val artifacts = mutableMapOf<String, String>()
    
    // DSL Properties
    val env = EnvironmentContext(environment)
    val agent = AgentContext(this)
    val tools = ToolsContext(this)
    val params = ParametersContext(environment)
    val scm = SCMContext(this)
    
    // Build information
    val currentBuild = BuildContext()
    
    /**
     * Enhanced stage execution with events
     */
    suspend fun stage(name: String, type: StageType = StageType.CUSTOM, block: suspend StageContext.() -> Unit) {
        try {
            startStage(name, type)
            val stageContext = StageContext(name, this)
            stageContext.block()
            completeStage(name, StageStatus.SUCCESS)
        } catch (e: Exception) {
            failStage(name, e.message ?: "Unknown error", e)
            throw e
        }
    }
    
    /**
     * Parallel stage execution
     */
    suspend fun parallel(vararg stages: Pair<String, suspend () -> Unit>) {
        val groupName = "parallel-${System.currentTimeMillis()}"
        val stageNames = stages.map { it.first }
        
        eventChannel.send(PipelineEvent.ParallelGroupStarted(jobId, groupName, stageNames))
        
        val startTime = System.currentTimeMillis()
        val successful = mutableListOf<String>()
        val failed = mutableListOf<String>()
        
        try {
            coroutineScope {
                stages.map { (name, block) ->
                    async {
                        try {
                            stage(name) { block() }
                            successful.add(name)
                        } catch (e: Exception) {
                            failed.add(name)
                            throw e
                        }
                    }
                }.forEach { it.await() }
            }
        } finally {
            val duration = System.currentTimeMillis() - startTime
            eventChannel.send(PipelineEvent.ParallelGroupCompleted(jobId, groupName, duration, successful, failed))
        }
    }
    
    /**
     * Enhanced script execution with security
     */
    suspend fun script(scriptContent: String): String {
        securityManager?.checkScriptAccess(scriptContent)?.let { result ->
            if (result is SecurityCheckResult.Denied) {
                throw SecurityException("Script execution denied: ${result.violations}")
            }
        }
        
        return executeSecureScript(scriptContent)
    }
    
    /**
     * Progress reporting
     */
    suspend fun updateProgress(current: Int, total: Int, message: String) {
        eventChannel.send(PipelineEvent.ProgressUpdate(jobId, current, total, message))
        println("Progress: [$current/$total] $message")
    }
    
    /**
     * Custom event emission
     */
    suspend fun emitEvent(eventType: String, eventName: String, data: Map<String, Any>) {
        eventChannel.send(PipelineEvent.CustomEvent(jobId, eventType, eventName, data))
    }
    
    /**
     * Archive artifacts
     */
    suspend fun archiveArtifacts(pattern: String, allowEmptyArchive: Boolean = false) {
        val matchedFiles = findFilesByPattern(pattern)
        
        if (matchedFiles.isEmpty() && !allowEmptyArchive) {
            throw IllegalArgumentException("No artifacts found matching pattern: $pattern")
        }
        
        matchedFiles.forEach { file ->
            artifacts[file.name] = file.absolutePath
            eventChannel.send(PipelineEvent.ArtifactGenerated(
                jobId = jobId,
                artifactName = file.name,
                artifactPath = file.absolutePath,
                artifactSize = file.length(),
                artifactType = file.extension
            ))
        }
        
        println("Archived ${matchedFiles.size} artifacts")
    }
    
    /**
     * Library loading with security checks
     */
    suspend fun library(identifier: String): LibraryReference {
        securityManager?.checkLibraryAccess(identifier)?.let { result ->
            if (result is SecurityCheckResult.Denied) {
                throw SecurityException("Library access denied: ${result.violations}")
            }
        }
        
        return libraryManager?.loadLibrary(identifier) 
            ?: throw IllegalStateException("Library manager not available")
    }
    
    // Internal methods
    private suspend fun startStage(name: String, type: StageType) {
        currentStage = name
        stageStartTimes[name] = System.currentTimeMillis()
        eventChannel.send(PipelineEvent.StageStarted(jobId, name, type))
        println("🚀 Starting stage: $name")
    }
    
    private suspend fun completeStage(name: String, status: StageStatus) {
        val startTime = stageStartTimes[name] ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        eventChannel.send(PipelineEvent.StageCompleted(jobId, name, duration, status))
        executedStages.add(name)
        println("✅ Completed stage: $name (${duration}ms)")
        currentStage = null
    }
    
    private suspend fun failStage(name: String, error: String, cause: Throwable?) {
        val startTime = stageStartTimes[name] ?: System.currentTimeMillis()
        val duration = System.currentTimeMillis() - startTime
        eventChannel.send(PipelineEvent.StageFailed(jobId, name, error, cause, duration))
        println("❌ Failed stage: $name - $error")
        currentStage = null
    }
    
    private suspend fun executeSecureScript(scriptContent: String): String {
        // This would integrate with the secure script executor
        // For now, we'll use a simple implementation
        return try {
            val process = ProcessBuilder("/bin/sh", "-c", scriptContent)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                throw RuntimeException("Script failed with exit code $exitCode: $output")
            }
            
            output
        } catch (e: Exception) {
            logger.error(e) { "Script execution failed" }
            throw e
        }
    }
    
    private fun findFilesByPattern(pattern: String): List<File> {
        val workingDir = File(System.getProperty("user.dir"))
        return workingDir.walkTopDown()
            .filter { it.isFile }
            .filter { matchesPattern(it.relativeTo(workingDir).path, pattern) }
            .toList()
    }
    
    private fun matchesPattern(path: String, pattern: String): Boolean {
        // Simple glob pattern matching - in production, use a proper glob library
        val regex = pattern
            .replace(".", "\\\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return path.matches(Regex(regex))
    }
    
    // Standard pipeline functions
    fun println(message: Any?) {
        val text = message.toString()
        runBlocking {
            outputChannel.send(JobOutputChunk(text.toByteArray(), isError = false))
        }
        kotlin.io.println(text)
    }
    
    suspend fun sh(command: String): String {
        return executeShellCommand(command, false)
    }
    
    suspend fun bat(command: String): String {
        return executeShellCommand(command, true)
    }
    
    private suspend fun executeShellCommand(command: String, isWindows: Boolean): String {
        try {
            val cmd = if (isWindows) listOf("cmd", "/c", command) else listOf("/bin/sh", "-c", command)
            val process = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()
            
            val output = StringBuilder()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    outputChannel.send(JobOutputChunk(line.toByteArray(), isError = false))
                    output.appendLine(line)
                    kotlin.io.println(line)
                }
            }
            
            val exitCode = process.waitFor()
            
            if (exitCode != 0) {
                val errorMsg = "Command '$command' failed with exit code $exitCode"
                outputChannel.send(JobOutputChunk(errorMsg.toByteArray(), isError = true))
                throw RuntimeException("$errorMsg\\nOutput: $output")
            }
            
            return output.toString()
        } catch (e: Exception) {
            val errorMsg = "Failed to execute command '$command': ${e.message}"
            outputChannel.send(JobOutputChunk(errorMsg.toByteArray(), isError = true))
            throw RuntimeException(errorMsg, e)
        }
    }
}

/**
 * Context classes for different DSL areas
 */
class StageContext(
    val name: String,
    private val pipelineContext: AdvancedPipelineContext
) {
    suspend fun steps(block: suspend StepsContext.() -> Unit) {
        val stepsContext = StepsContext(pipelineContext)
        stepsContext.block()
    }
    
    suspend fun parallel(block: suspend ParallelContext.() -> Unit) {
        val parallelContext = ParallelContext(pipelineContext)
        parallelContext.block()
    }
    
    suspend fun when_(condition: Boolean, block: suspend StageContext.() -> Unit) {
        if (condition) {
            this.block()
        }
    }
}

class StepsContext(
    private val pipelineContext: AdvancedPipelineContext
) {
    suspend fun script(content: String) = pipelineContext.script(content)
    suspend fun sh(command: String) = pipelineContext.sh(command)
    suspend fun bat(command: String) = pipelineContext.bat(command)
    fun echo(message: String) = pipelineContext.println(message)
    suspend fun archiveArtifacts(pattern: String) = pipelineContext.archiveArtifacts(pattern)
}

class ParallelContext(
    private val pipelineContext: AdvancedPipelineContext
) {
    private val parallelStages = mutableListOf<Pair<String, suspend () -> Unit>>()
    
    fun stage(name: String, block: suspend StageContext.() -> Unit) {
        parallelStages.add(name to {
            val stageContext = StageContext(name, pipelineContext)
            stageContext.block()
        })
    }
    
    suspend fun execute() {
        pipelineContext.parallel(*parallelStages.toTypedArray())
    }
}

class EnvironmentContext(
    private val environment: Map<String, String>
) {
    operator fun get(key: String): String? = environment[key]
    fun get(key: String, defaultValue: String): String = environment[key] ?: defaultValue
    fun getRequired(key: String): String = environment[key] 
        ?: throw IllegalArgumentException("Required environment variable '$key' not found")
}

class ParametersContext(
    private val parameters: Map<String, String>
) {
    operator fun get(key: String): String? = parameters[key]
    fun get(key: String, defaultValue: String): String = parameters[key] ?: defaultValue
    fun getRequired(key: String): String = parameters[key] 
        ?: throw IllegalArgumentException("Required parameter '$key' not found")
}

class AgentContext(
    private val pipelineContext: AdvancedPipelineContext
) {
    fun label(label: String) {
        // Agent labeling logic
        pipelineContext.println("Running on agent with label: $label")
    }
}

class ToolsContext(
    private val pipelineContext: AdvancedPipelineContext
) {
    suspend fun maven(version: String = "3.8.6", block: suspend () -> Unit) {
        pipelineContext.println("Using Maven $version")
        block()
    }
    
    suspend fun gradle(version: String = "7.6", block: suspend () -> Unit) {
        pipelineContext.println("Using Gradle $version")
        block()
    }
    
    suspend fun docker(block: suspend DockerContext.() -> Unit) {
        val dockerContext = DockerContext(pipelineContext)
        dockerContext.block()
    }
}

class DockerContext(
    private val pipelineContext: AdvancedPipelineContext
) {
    suspend fun build(tag: String, dockerfile: String = "Dockerfile") {
        pipelineContext.sh("docker build -t $tag -f $dockerfile .")
    }
    
    suspend fun push(tag: String) {
        pipelineContext.sh("docker push $tag")
    }
    
    suspend fun run(image: String, command: String = "") {
        pipelineContext.sh("docker run $image $command")
    }
}

class SCMContext(
    private val pipelineContext: AdvancedPipelineContext
) {
    suspend fun checkout(url: String, branch: String = "main", credentials: String? = null) {
        val credentialsFlag = credentials?.let { "-c credential.helper= " } ?: ""
        pipelineContext.sh("git $credentialsFlag clone -b $branch $url .")
    }
}

class BuildContext {
    val number: Int = (System.currentTimeMillis() % 10000).toInt()
    val timestamp: Instant = Instant.now()
    var result: String = "SUCCESS"
    var description: String = ""
    val changeSets: MutableList<String> = mutableListOf()
}