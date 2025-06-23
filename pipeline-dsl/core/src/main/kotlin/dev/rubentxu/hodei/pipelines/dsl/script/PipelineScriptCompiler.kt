package dev.rubentxu.hodei.pipelines.dsl.script

import dev.rubentxu.hodei.pipelines.dsl.model.Pipeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import java.io.File

private val logger = KotlinLogging.logger {}

/**
 * Compilador de scripts .pipeline.kts que integra con el sistema de workers existente.
 * 
 * Proporciona compilación y evaluación segura de scripts de pipeline con
 * soporte completo para el DSL y integración con el sistema de eventos.
 */
class PipelineScriptCompiler {
    
    private val scriptingHost = BasicJvmScriptingHost()
    
    /**
     * Compila y evalúa un script de pipeline desde archivo.
     */
    suspend fun compileFromFile(filePath: String): Pipeline = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            throw IllegalArgumentException("Pipeline script file not found: $filePath")
        }
        
        logger.info { "Compiling pipeline script: $filePath" }
        
        val source = file.toScriptSource()
        return@withContext compileScript(source, filePath)
    }
    
    /**
     * Compila y evalúa un script de pipeline desde string.
     */
    suspend fun compileFromString(scriptContent: String, scriptName: String = "inline-script"): Pipeline = 
        withContext(Dispatchers.Default) {
            logger.info { "Compiling inline pipeline script: $scriptName" }
            
            val source = scriptContent.toScriptSource(scriptName)
            return@withContext compileScript(source, scriptName)
        }
    
    /**
     * Valida un script sin ejecutarlo completamente.
     */
    suspend fun validateScript(filePath: String): List<String> = withContext(Dispatchers.IO) {
        val file = File(filePath)
        if (!file.exists()) {
            return@withContext listOf("Pipeline script file not found: $filePath")
        }
        
        logger.debug { "Validating pipeline script: $filePath" }
        
        try {
            val source = file.toScriptSource()
            
            // Solo compilar, no evaluar
            val compilationResult = scriptingHost.compiler(
                source, 
                PipelineScriptCompilationConfiguration
            )
            
            val errors = mutableListOf<String>()
            
            compilationResult.reports.forEach { report ->
                when (report.severity) {
                    ScriptDiagnostic.Severity.ERROR -> {
                        val location = report.location?.let { loc ->
                            " at line ${loc.start.line}, column ${loc.start.col}"
                        } ?: ""
                        errors.add("${report.message}$location")
                    }
                    ScriptDiagnostic.Severity.WARNING -> {
                        logger.warn { "Script warning: ${report.message}" }
                    }
                    else -> {
                        logger.debug { "Script info: ${report.message}" }
                    }
                }
            }
            
            if (compilationResult !is ResultWithDiagnostics.Success) {
                errors.add("Script compilation failed")
            }
            
            return@withContext errors
            
        } catch (e: Exception) {
            logger.error(e) { "Script validation failed" }
            return@withContext listOf("Validation error: ${e.message}")
        }
    }
    
    /**
     * Compila y evalúa un script usando la configuración integrada.
     */
    private suspend fun compileScript(source: SourceCode, scriptName: String): Pipeline {
        try {
            val startTime = System.currentTimeMillis()
            
            // Compilar el script
            val compilationResult = scriptingHost.compiler(
                source, 
                PipelineScriptCompilationConfiguration
            )
            
            // Verificar errores de compilación
            val errors = mutableListOf<String>()
            compilationResult.reports.forEach { report ->
                when (report.severity) {
                    ScriptDiagnostic.Severity.ERROR -> {
                        val location = report.location?.let { loc ->
                            " at line ${loc.start.line}, column ${loc.start.col}"
                        } ?: ""
                        errors.add("${report.message}$location")
                    }
                    ScriptDiagnostic.Severity.WARNING -> {
                        logger.warn { "Script warning: ${report.message}" }
                    }
                    else -> {
                        logger.debug { "Script info: ${report.message}" }
                    }
                }
            }
            
            if (errors.isNotEmpty()) {
                throw PipelineCompilationException("Script compilation failed:\\n${errors.joinToString("\\n")}")
            }
            
            if (compilationResult !is ResultWithDiagnostics.Success) {
                throw PipelineCompilationException("Script compilation failed with unknown error")
            }
            
            // Evaluar el script
            val evaluationResult = scriptingHost.evaluator(
                compilationResult.value,
                PipelineScriptEvaluationConfiguration
            )
            
            // Verificar errores de evaluación
            evaluationResult.reports.forEach { report ->
                when (report.severity) {
                    ScriptDiagnostic.Severity.ERROR -> {
                        errors.add("Evaluation error: ${report.message}")
                    }
                    ScriptDiagnostic.Severity.WARNING -> {
                        logger.warn { "Script evaluation warning: ${report.message}" }
                    }
                    else -> {
                        logger.debug { "Script evaluation info: ${report.message}" }
                    }
                }
            }
            
            if (errors.isNotEmpty()) {
                throw PipelineCompilationException("Script evaluation failed:\\n${errors.joinToString("\\n")}")
            }
            
            if (evaluationResult !is ResultWithDiagnostics.Success) {
                throw PipelineCompilationException("Script evaluation failed with unknown error")
            }
            
            // Extraer el pipeline del resultado
            val returnValue = evaluationResult.value.returnValue
            val pipeline = when (returnValue) {
                is ResultValue.Value -> {
                    val result = returnValue.value
                    when (result) {
                        is Pipeline -> {
                            logger.info { "Pipeline '${result.name}' compiled successfully in ${System.currentTimeMillis() - startTime}ms" }
                            result
                        }
                        else -> {
                            throw PipelineCompilationException(
                                "Script must return a Pipeline object, got: ${result?.let { it::class.java.simpleName } ?: "null"}"
                            )
                        }
                    }
                }
                is ResultValue.Error -> {
                    throw PipelineCompilationException("Script execution error: ${returnValue.error.message ?: "Unknown error"}")
                }
                else -> {
                    throw PipelineCompilationException("No return value from script")
                }
            }
            
            return pipeline
            
        } catch (e: PipelineCompilationException) {
            throw e
        } catch (e: Exception) {
            logger.error(e) { "Unexpected error during script compilation" }
            throw PipelineCompilationException("Compilation failed: ${e.message}", e)
        }
    }
    
    /**
     * Obtiene información sobre el DSL disponible.
     */
    fun getDslInfo(): PipelineDslInfo {
        return PipelineDslInfo(
            version = PipelineScript.VERSION,
            description = PipelineScript.DESCRIPTION,
            availableImports = listOf(
                "dev.rubentxu.hodei.pipelines.dsl.pipeline",
                "dev.rubentxu.hodei.pipelines.dsl.model.*",
                "dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.*",
                "dev.rubentxu.hodei.pipelines.port.*"
            ),
            supportedStepTypes = listOf(
                "sh", "bat", "echo", "archiveArtifacts", "publishTestResults",
                "checkout", "script", "docker", "notification", "dir", "withEnv"
            )
        )
    }
}

/**
 * Excepción específica para errores de compilación de pipelines.
 */
class PipelineCompilationException(
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * Información sobre el DSL disponible.
 */
data class PipelineDslInfo(
    val version: String,
    val description: String,
    val availableImports: List<String>,
    val supportedStepTypes: List<String>
)