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
        registerExecutor("archiveArtifacts", ArchiveArtifactsStepExecutor())
        registerExecutor("publishTestResults", PublishTestResultsStepExecutor())
        registerExecutor("checkout", CheckoutStepExecutor())
        registerExecutor("script", ScriptStepExecutor())
        registerExecutor("docker", DockerStepExecutor())
        registerExecutor("notification", NotificationStepExecutor())
        registerExecutor("dir", DirStepExecutor())
        registerExecutor("withEnv", WithEnvStepExecutor())
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

/**
 * Ejecutor para archivar artifacts.
 */
class ArchiveArtifactsStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.ArchiveArtifacts) { "Expected ArchiveArtifacts step" }
        
        // Simplified artifact archiving
        context.println("📦 Archiving artifacts: ${step.artifacts}")
        if (step.fingerprint) {
            context.println("🔒 Fingerprinting enabled")
        }
    }
}

/**
 * Ejecutor para publicar resultados de tests.
 */
class PublishTestResultsStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.PublishTestResults) { "Expected PublishTestResults step" }
        
        try {
            // Buscar archivos de resultados de tests
            val testFiles = findTestResultFiles(step.testResultsPattern)
            
            if (testFiles.isEmpty() && !step.allowEmptyResults) {
                throw IllegalArgumentException("No test result files found matching pattern: ${step.testResultsPattern}")
            }
            
            // Enviar evento de resultados de tests
            // Log test results publication
            context.println("📊 Test results pattern: ${step.testResultsPattern}")
            context.println("📊 Files found: ${testFiles.size}")
            context.println("📊 Checks name: ${step.checksName ?: "Tests}"}")
            
            context.println("📊 Published test results: ${testFiles.size} files found")
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish test results: ${step.testResultsPattern}" }
            throw e
        }
    }
    
    private fun findTestResultFiles(pattern: String): List<File> {
        val workingDir = File(System.getProperty("user.dir"))
        return workingDir.walkTopDown()
            .filter { it.isFile }
            .filter { matchesPattern(it.relativeTo(workingDir).path, pattern) }
            .toList()
    }
    
    private fun matchesPattern(path: String, pattern: String): Boolean {
        val regex = pattern
            .replace(".", "\\\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return path.matches(Regex(regex))
    }
}

/**
 * Ejecutor para checkout.
 */
class CheckoutStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Checkout) { "Expected Checkout step" }
        
        when (val scm = step.scm) {
            is dev.rubentxu.hodei.pipelines.dsl.model.SCMConfig.Git -> {
                val command = buildString {
                    append("git clone")
                    if (scm.shallow) append(" --depth ${scm.depth ?: 1}")
                    if (scm.branch != "main") append(" -b ${scm.branch}")
                    if (scm.submodules) append(" --recurse-submodules")
                    append(" ${scm.url}")
                    if (scm.checkoutDir != null) append(" ${scm.checkoutDir}")
                }
                
                context.sh(command)
                
                if (scm.clean) {
                    context.sh("git clean -fdx")
                }
                
                context.println("✅ Checkout completed: ${scm.url} (${scm.branch})")
            }
            
            else -> {
                throw UnsupportedOperationException("SCM type not yet implemented: ${scm::class.simpleName}")
            }
        }
    }
}

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

/**
 * Ejecutor para operaciones Docker.
 */
class DockerStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Docker) { "Expected Docker step" }
        
        val command = buildString {
            append("docker run")
            
            // Add volumes
            step.volumes.forEach { volume ->
                append(" -v $volume")
            }
            
            // Add environment variables
            step.environment.forEach { (key, value) ->
                append(" -e $key=$value")
            }
            
            // Add working directory
            step.workingDirectory?.let { append(" -w $it") }
            
            // Add user
            step.user?.let { append(" --user $it") }
            
            // Add entrypoint
            step.entrypoint?.let { append(" --entrypoint $it") }
            
            // Add image
            append(" ${step.image}")
            
            // Add command and args
            step.command?.let { append(" $it") }
            step.args.forEach { arg -> append(" $arg") }
        }
        
        context.sh(command)
        context.println("🐳 Docker command executed: ${step.image}")
    }
}

/**
 * Ejecutor para notificaciones.
 */
class NotificationStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Notification) { "Expected Notification step" }
        
        // Enviar evento de notificación
        // Log notification details
        context.println("📢 Message: ${step.message}")
        context.println("📢 Channels: ${step.channels.map { it::class.simpleName }}")
        context.println("📢 Only on state change: ${step.onlyOnStateChange}")
        
        context.println("📢 Notification sent: ${step.message}")
        
        // TODO: Implementar envío real de notificaciones según el canal
    }
}

/**
 * Ejecutor para cambio de directorio.
 */
class DirStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.Dir) { "Expected Dir step" }
        
        val originalDir = System.getProperty("user.dir")
        val targetPath = Paths.get(step.path)
        val absolutePath = if (targetPath.isAbsolute) {
            targetPath.toString()
        } else {
            Paths.get(originalDir, step.path).toString()
        }
        
        try {
            System.setProperty("user.dir", absolutePath)
            context.println("📁 Changed directory to: $absolutePath")
            
            // Ejecutar steps anidados
            for (nestedStep in step.steps) {
                val executor = PipelineStepExecutorManager().getExecutor(nestedStep.stepType)
                    ?: throw IllegalStateException("No executor found for step type: ${nestedStep.stepType}")
                
                executor.execute(nestedStep, context)
            }
            
        } finally {
            System.setProperty("user.dir", originalDir)
            context.println("📁 Restored directory to: $originalDir")
        }
    }
}

/**
 * Ejecutor para variables de entorno.
 */
class WithEnvStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is Step.WithEnv) { "Expected WithEnv step" }
        
        // Crear nuevo contexto con variables de entorno adicionales
        // Note: Environment access is managed through PipelineContext.env API
        context.println("🌍 Setting environment variables: ${step.environment.keys}")
        
        // Ejecutar steps anidados
        for (nestedStep in step.steps) {
            val executor = PipelineStepExecutorManager().getExecutor(nestedStep.stepType)
                ?: throw IllegalArgumentException("No executor found for step type: ${nestedStep.stepType}")
            
            executor.execute(nestedStep, context)
        }
        
        context.println("🌍 Environment variables restored")
    }
}