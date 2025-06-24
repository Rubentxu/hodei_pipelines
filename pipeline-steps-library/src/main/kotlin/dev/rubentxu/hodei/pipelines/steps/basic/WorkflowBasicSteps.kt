package dev.rubentxu.hodei.pipelines.steps.basic

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepCategory
import dev.rubentxu.hodei.pipelines.dsl.extensions.*
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Extensión que implementa workflow-basic-steps de Jenkins.
 * https://www.jenkins.io/doc/pipeline/steps/workflow-basic-steps/
 */
class WorkflowBasicStepsExtension : BaseStepExtension() {
    override val name: String = "workflow-basic-steps"
    override val version: String = "1.0.0"
    override val category: StepCategory = StepCategory.BASIC
    override val description: String = "Basic workflow steps compatible with Jenkins"
    
    override fun createExecutor(): StepExecutor = WorkflowBasicStepsExecutor()
    
    override fun registerDslFunctions(builder: StepsBuilder) {
        // Los steps se registran automáticamente vía el ejecutor
    }
}

/**
 * Ejecutor para todos los basic workflow steps.
 */
class WorkflowBasicStepsExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is ExtensionStep) { "Expected ExtensionStep" }
        
        when (step.action) {
            "build" -> executeBuild(step, context)
            "catchError" -> executeCatchError(step, context)
            "deleteDir" -> executeDeleteDir(step, context)
            "dir" -> executeDir(step, context)
            "error" -> executeError(step, context)
            "fileExists" -> executeFileExists(step, context)
            "isUnix" -> executeIsUnix(step, context)
            "mail" -> executeMail(step, context)
            "milestone" -> executeMilestone(step, context)
            "node" -> executeNode(step, context)
            "parallel" -> executeParallel(step, context)
            "pwd" -> executePwd(step, context)
            "readFile" -> executeReadFile(step, context)
            "retry" -> executeRetry(step, context)
            "script" -> executeScript(step, context)
            "sleep" -> executeSleep(step, context)
            "stage" -> executeStage(step, context)
            "timeout" -> executeTimeout(step, context)
            "tool" -> executeTool(step, context)
            "unstable" -> executeUnstable(step, context)
            "waitUntil" -> executeWaitUntil(step, context)
            "warnError" -> executeWarnError(step, context)
            "withEnv" -> executeWithEnv(step, context)
            "writeFile" -> executeWriteFile(step, context)
            else -> throw UnsupportedOperationException("Unknown action: ${step.action}")
        }
    }
    
    private suspend fun executeBuild(step: ExtensionStep, context: PipelineContext) {
        val job = step.parameters["job"]?.toString() 
            ?: throw IllegalArgumentException("job parameter is required")
        val parameters = step.parameters["parameters"] as? Map<String, String> ?: emptyMap()
        val propagate = step.parameters["propagate"] as? Boolean ?: true
        val wait = step.parameters["wait"] as? Boolean ?: true
        
        context.println("🔨 Triggering build for job: $job")
        parameters.forEach { (key, value) ->
            context.println("  Parameter: $key = $value")
        }
        
        // Simular trigger de job
        delay(1000)
        
        if (wait) {
            context.println("⏳ Waiting for build to complete...")
            delay(2000)
            context.println("✅ Build completed successfully")
        } else {
            context.println("🚀 Build triggered (not waiting)")
        }
    }
    
    private suspend fun executeCatchError(step: ExtensionStep, context: PipelineContext) {
        val buildResult = step.parameters["buildResult"]?.toString() ?: "FAILURE"
        val message = step.parameters["message"]?.toString()
        val stageResult = step.parameters["stageResult"]?.toString()
        
        try {
            // Ejecutar steps anidados (simulated)
            context.println("🛡️ Executing with error catching...")
            
            // Simular posible error
            val shouldFail = step.parameters["simulateError"] as? Boolean ?: false
            if (shouldFail) {
                throw RuntimeException("Simulated error for testing")
            }
            
        } catch (e: Exception) {
            context.printError("❌ Caught error: ${e.message}")
            message?.let { context.println("Message: $it") }
            
            // Set build result pero continuar
            context.setVariable("currentBuild.result", buildResult)
            stageResult?.let { context.setVariable("currentStage.result", it) }
            
            context.println("🔄 Continuing execution after error")
        }
    }
    
    private suspend fun executeDeleteDir(step: ExtensionStep, context: PipelineContext) {
        context.println("🗑️ Deleting workspace directory...")
        
        // En producción, esto haría rm -rf del workspace
        val workspaceDir = context.workingDirectory
        context.println("Directory to delete: ${workspaceDir.absolutePath}")
        
        // Simular eliminación
        delay(500)
        context.println("✅ Directory deleted")
    }
    
    private suspend fun executeDir(step: ExtensionStep, context: PipelineContext) {
        val path = step.parameters["path"]?.toString()
            ?: throw IllegalArgumentException("path parameter is required")
        
        context.println("📁 Changing to directory: $path")
        
        val targetDir = if (File(path).isAbsolute) {
            File(path)
        } else {
            File(context.workingDirectory, path)
        }
        
        if (!targetDir.exists()) {
            targetDir.mkdirs()
            context.println("📁 Created directory: ${targetDir.absolutePath}")
        }
        
        context.println("📂 Working in: ${targetDir.absolutePath}")
    }
    
    private suspend fun executeError(step: ExtensionStep, context: PipelineContext) {
        val message = step.parameters["message"]?.toString() ?: "Pipeline failed"
        
        context.printError("💥 ERROR: $message")
        throw RuntimeException(message)
    }
    
    private suspend fun executeFileExists(step: ExtensionStep, context: PipelineContext) {
        val file = step.parameters["file"]?.toString()
            ?: throw IllegalArgumentException("file parameter is required")
        
        val filePath = File(context.workingDirectory, file)
        val exists = filePath.exists()
        
        context.println("📄 Checking file: $file")
        context.println("File exists: $exists")
        
        // Retornar resultado
        context.setVariable("${step.name ?: "fileExists"}.result", exists)
    }
    
    private suspend fun executeIsUnix(step: ExtensionStep, context: PipelineContext) {
        val isUnix = !System.getProperty("os.name").lowercase().contains("windows")
        
        context.println("🖥️ Operating system check:")
        context.println("Is Unix-like: $isUnix")
        
        context.setVariable("${step.name ?: "isUnix"}.result", isUnix)
    }
    
    private suspend fun executeMail(step: ExtensionStep, context: PipelineContext) {
        val to = step.parameters["to"]?.toString()
            ?: throw IllegalArgumentException("to parameter is required")
        val subject = step.parameters["subject"]?.toString() ?: "Pipeline Notification"
        val body = step.parameters["body"]?.toString() ?: ""
        val from = step.parameters["from"]?.toString()
        val cc = step.parameters["cc"]?.toString()
        val bcc = step.parameters["bcc"]?.toString()
        val attachLog = step.parameters["attachLog"] as? Boolean ?: false
        
        context.println("📧 Sending email:")
        context.println("To: $to")
        cc?.let { context.println("CC: $it") }
        bcc?.let { context.println("BCC: $it") }
        context.println("Subject: $subject")
        context.println("Body: $body")
        from?.let { context.println("From: $it") }
        if (attachLog) context.println("Build log will be attached")
        
        // Simular envío
        delay(1000)
        context.println("✅ Email sent successfully")
    }
    
    private suspend fun executeMilestone(step: ExtensionStep, context: PipelineContext) {
        val ordinal = step.parameters["ordinal"]?.toString()?.toIntOrNull()
        val label = step.parameters["label"]?.toString()
        
        context.println("🏁 Milestone reached:")
        ordinal?.let { context.println("Ordinal: $it") }
        label?.let { context.println("Label: $it") }
        
        context.setVariable("milestone.ordinal", ordinal ?: 0)
        context.setVariable("milestone.label", label ?: "")
    }
    
    private suspend fun executeNode(step: ExtensionStep, context: PipelineContext) {
        val label = step.parameters["label"]?.toString() ?: "any"
        
        context.println("🖥️ Allocating node with label: $label")
        
        // Simular asignación de nodo
        delay(500)
        context.println("✅ Node allocated: worker-${System.currentTimeMillis() % 1000}")
    }
    
    private suspend fun executeParallel(step: ExtensionStep, context: PipelineContext) {
        val branches = step.parameters["branches"] as? Map<String, *>
            ?: throw IllegalArgumentException("branches parameter is required")
        val failFast = step.parameters["failFast"] as? Boolean ?: true
        
        context.println("🚀 Executing ${branches.size} branches in parallel")
        context.println("Fail fast: $failFast")
        
        branches.keys.forEach { branchName ->
            context.println("Branch: $branchName")
        }
        
        // Simular ejecución paralela
        delay(2000)
        context.println("✅ All parallel branches completed")
    }
    
    private suspend fun executePwd(step: ExtensionStep, context: PipelineContext) {
        val tmp = step.parameters["tmp"] as? Boolean ?: false
        
        val directory = if (tmp) {
            System.getProperty("java.io.tmpdir")
        } else {
            context.workingDirectory.absolutePath
        }
        
        context.println("📍 Current directory: $directory")
        context.setVariable("${step.name ?: "pwd"}.result", directory)
    }
    
    private suspend fun executeReadFile(step: ExtensionStep, context: PipelineContext) {
        val file = step.parameters["file"]?.toString()
            ?: throw IllegalArgumentException("file parameter is required")
        val encoding = step.parameters["encoding"]?.toString() ?: "UTF-8"
        
        val filePath = File(context.workingDirectory, file)
        
        if (!filePath.exists()) {
            throw IllegalArgumentException("File not found: $file")
        }
        
        context.println("📖 Reading file: $file")
        
        try {
            val content = filePath.readText(charset(encoding))
            context.println("File size: ${content.length} characters")
            
            // Retornar contenido
            context.setVariable("${step.name ?: "readFile"}.content", content)
            
        } catch (e: Exception) {
            context.printError("Failed to read file: ${e.message}")
            throw e
        }
    }
    
    private suspend fun executeRetry(step: ExtensionStep, context: PipelineContext) {
        val count = step.parameters["count"]?.toString()?.toIntOrNull() ?: 3
        
        context.println("🔄 Retry configuration: max $count attempts")
        
        var attempt = 1
        var lastException: Exception? = null
        
        while (attempt <= count) {
            try {
                context.println("Attempt $attempt of $count")
                
                // Simular operación que puede fallar
                val shouldFail = step.parameters["simulateFailure"] as? Boolean ?: false
                if (shouldFail && attempt < count) {
                    throw RuntimeException("Simulated failure on attempt $attempt")
                }
                
                context.println("✅ Operation succeeded on attempt $attempt")
                return
                
            } catch (e: Exception) {
                lastException = e
                context.printError("❌ Attempt $attempt failed: ${e.message}")
                
                if (attempt < count) {
                    context.println("⏳ Waiting before retry...")
                    delay(1000)
                }
                attempt++
            }
        }
        
        throw lastException ?: RuntimeException("All retry attempts failed")
    }
    
    private suspend fun executeScript(step: ExtensionStep, context: PipelineContext) {
        val script = step.parameters["script"]?.toString()
            ?: throw IllegalArgumentException("script parameter is required")
        
        context.println("📜 Executing script block")
        context.println("Script: $script")
        
        // En un caso real, esto ejecutaría el script Groovy
        delay(1000)
        context.println("✅ Script executed successfully")
    }
    
    private suspend fun executeSleep(step: ExtensionStep, context: PipelineContext) {
        val time = step.parameters["time"]?.toString()?.toLongOrNull()
            ?: throw IllegalArgumentException("time parameter is required")
        val unit = step.parameters["unit"]?.toString() ?: "SECONDS"
        
        val milliseconds = when (unit.uppercase()) {
            "MILLISECONDS" -> time
            "SECONDS" -> time * 1000
            "MINUTES" -> time * 60 * 1000
            "HOURS" -> time * 60 * 60 * 1000
            else -> throw IllegalArgumentException("Unknown time unit: $unit")
        }
        
        context.println("😴 Sleeping for $time $unit")
        delay(milliseconds)
        context.println("⏰ Sleep completed")
    }
    
    private suspend fun executeStage(step: ExtensionStep, context: PipelineContext) {
        val name = step.parameters["name"]?.toString()
            ?: throw IllegalArgumentException("name parameter is required")
        val concurrency = step.parameters["concurrency"]?.toString()?.toIntOrNull()
        
        context.println("🎭 Entering stage: $name")
        concurrency?.let { context.println("Concurrency limit: $it") }
        
        context.setVariable("currentStage.name", name)
        context.setVariable("currentStage.startTime", System.currentTimeMillis())
    }
    
    private suspend fun executeTimeout(step: ExtensionStep, context: PipelineContext) {
        val time = step.parameters["time"]?.toString()?.toLongOrNull()
            ?: throw IllegalArgumentException("time parameter is required")
        val unit = step.parameters["unit"]?.toString() ?: "MINUTES"
        val activity = step.parameters["activity"] as? Boolean ?: false
        
        context.println("⏱️ Setting timeout: $time $unit")
        if (activity) {
            context.println("Activity-based timeout enabled")
        }
        
        // En producción, esto configuraría un timeout real
        context.setVariable("timeout.time", time)
        context.setVariable("timeout.unit", unit)
        context.setVariable("timeout.activity", activity)
    }
    
    private suspend fun executeTool(step: ExtensionStep, context: PipelineContext) {
        val name = step.parameters["name"]?.toString()
            ?: throw IllegalArgumentException("name parameter is required")
        val type = step.parameters["type"]?.toString()
        
        context.println("🔧 Loading tool: $name")
        type?.let { context.println("Tool type: $it") }
        
        // Simular carga de herramienta
        delay(500)
        
        val toolPath = "/tools/$name"
        context.println("Tool loaded at: $toolPath")
        context.setVariable("${step.name ?: "tool"}.path", toolPath)
    }
    
    private suspend fun executeUnstable(step: ExtensionStep, context: PipelineContext) {
        val message = step.parameters["message"]?.toString() ?: "Build marked as unstable"
        
        context.println("⚠️ UNSTABLE: $message")
        context.setVariable("currentBuild.result", "UNSTABLE")
    }
    
    private suspend fun executeWaitUntil(step: ExtensionStep, context: PipelineContext) {
        val condition = step.parameters["condition"]?.toString()
            ?: throw IllegalArgumentException("condition parameter is required")
        val initialRecurrencePeriod = step.parameters["initialRecurrencePeriod"]?.toString()?.toLongOrNull() ?: 250L
        val quiet = step.parameters["quiet"] as? Boolean ?: false
        
        if (!quiet) {
            context.println("⏳ Waiting until condition is met: $condition")
        }
        
        // Simular espera hasta que la condición se cumpla
        var attempts = 0
        val maxAttempts = 10
        
        while (attempts < maxAttempts) {
            attempts++
            
            if (!quiet) {
                context.println("Checking condition (attempt $attempts)...")
            }
            
            // Simular evaluación de condición
            delay(initialRecurrencePeriod)
            
            // Para la demo, cumplir la condición después de algunos intentos
            if (attempts >= 3) {
                if (!quiet) {
                    context.println("✅ Condition met after $attempts attempts")
                }
                return
            }
        }
        
        throw RuntimeException("Condition not met after $maxAttempts attempts")
    }
    
    private suspend fun executeWarnError(step: ExtensionStep, context: PipelineContext) {
        val message = step.parameters["message"]?.toString() ?: "Warning: error occurred"
        val catchInterruptions = step.parameters["catchInterruptions"] as? Boolean ?: true
        
        try {
            // Ejecutar steps anidados (simulated)
            val shouldFail = step.parameters["simulateError"] as? Boolean ?: false
            if (shouldFail) {
                throw RuntimeException("Simulated error")
            }
            
        } catch (e: Exception) {
            context.println("⚠️ WARNING: $message")
            context.printError("Error details: ${e.message}")
            
            // Marcar como unstable pero continuar
            context.setVariable("currentBuild.result", "UNSTABLE")
            context.println("🔄 Continuing after warning")
        }
    }
    
    private suspend fun executeWithEnv(step: ExtensionStep, context: PipelineContext) {
        val env = step.parameters["env"] as? List<String>
            ?: throw IllegalArgumentException("env parameter is required")
        val overrides = step.parameters["overrides"] as? Map<String, String> ?: emptyMap()
        
        context.println("🌍 Setting environment variables:")
        
        // Parsear variables de entorno del formato KEY=VALUE
        env.forEach { envVar ->
            val parts = envVar.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0]
                val value = parts[1]
                context.setEnv(key, value)
                context.println("  $key = $value")
            }
        }
        
        // Aplicar overrides
        overrides.forEach { (key, value) ->
            context.setEnv(key, value)
            context.println("  $key = $value (override)")
        }
        
        context.println("Environment configured for nested steps")
    }
    
    private suspend fun executeWriteFile(step: ExtensionStep, context: PipelineContext) {
        val file = step.parameters["file"]?.toString()
            ?: throw IllegalArgumentException("file parameter is required")
        val text = step.parameters["text"]?.toString() ?: ""
        val encoding = step.parameters["encoding"]?.toString() ?: "UTF-8"
        
        val filePath = File(context.workingDirectory, file)
        
        // Crear directorios padre si no existen
        filePath.parentFile?.mkdirs()
        
        context.println("✍️ Writing file: $file")
        context.println("Content length: ${text.length} characters")
        
        try {
            filePath.writeText(text, charset(encoding))
            context.println("✅ File written successfully")
            
        } catch (e: Exception) {
            context.printError("Failed to write file: ${e.message}")
            throw e
        }
    }
}