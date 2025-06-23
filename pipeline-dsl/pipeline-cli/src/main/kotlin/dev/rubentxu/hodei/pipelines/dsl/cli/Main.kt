package dev.rubentxu.hodei.pipelines.dsl.cli

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.file
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineEngine
import dev.rubentxu.hodei.pipelines.dsl.script.PipelineScriptCompiler
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*

/**
 * CLI principal para el Pipeline DSL integrado.
 * 
 * Proporciona comandos para compilar, validar y ejecutar pipelines
 * usando el sistema integrado con workers.
 */
class PipelineDslCli : CliktCommand(
    name = "pipeline-dsl",
    help = "🚀 Pipeline DSL CLI - Superior CI/CD Pipeline System"
) {
    override fun run() = Unit
}

/**
 * Comando para ejecutar un pipeline.
 */
class ExecuteCommand : CliktCommand(
    name = "execute",
    help = "Execute a pipeline script"
) {
    private val pipelineFile by argument(
        name = "PIPELINE_FILE",
        help = "Path to the .pipeline.kts file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val jobId by option(
        "--job-id",
        help = "Job ID for execution (auto-generated if not provided)"
    ).default(UUID.randomUUID().toString())
    
    private val workerId by option(
        "--worker-id",
        help = "Worker ID for execution"
    ).default("cli-worker-${System.currentTimeMillis()}")
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    override fun run() = runBlocking {
        try {
            echo("🚀 Executing pipeline: ${pipelineFile.name}")
            echo("📋 Job ID: $jobId")
            echo("🔧 Worker ID: $workerId")
            echo()
            
            // Compilar el pipeline
            val compiler = PipelineScriptCompiler()
            val pipeline = compiler.compileFromFile(pipelineFile.absolutePath)
            
            echo("✅ Pipeline compiled successfully: ${pipeline.name}")
            if (pipeline.description != null) {
                echo("📄 Description: ${pipeline.description}")
            }
            echo("🏗️ Stages: ${pipeline.stages.size}")
            echo("📦 Total steps: ${pipeline.getTotalStepCount()}")
            echo()
            
            // Crear channels para output y eventos
            val outputChannel = Channel<PipelineOutputChunk>(Channel.UNLIMITED)
            val eventChannel = Channel<PipelineExecutionEvent>(Channel.UNLIMITED)
            
            // Configurar listeners para output y eventos
            val outputJob = launch {
                for (output in outputChannel) {
                    if (output.isError) {
                        System.err.write(output.data)
                        System.err.flush()
                    } else {
                        System.out.write(output.data)
                        System.out.flush()
                    }
                }
            }
            
            val eventJob = launch {
                for (event in eventChannel) {
                    if (verbose) {
                        echo("📡 Event: ${event::class.simpleName}")
                    }
                }
            }
            
            // Ejecutar el pipeline
            val executor = PipelineEngine()
            val result = executor.execute(
                pipeline = pipeline,
                jobId = jobId,
                workerId = workerId,
                outputChannel = outputChannel,
                eventChannel = eventChannel
            )
            
            // Cerrar channels
            outputChannel.close()
            eventChannel.close()
            
            // Esperar a que terminen los jobs de procesamiento
            outputJob.join()
            eventJob.join()
            
            // Mostrar resultado
            echo()
            if (result.success) {
                echo("✅ Pipeline execution completed successfully!")
                echo("⏱️ Duration: ${result.duration}ms")
                echo("🎯 All ${result.stageResults.size} stages completed")
            } else {
                echo("❌ Pipeline execution failed!")
                echo("⏱️ Duration: ${result.duration}ms")
                result.failedStage?.let { echo("💥 Failed stage: $it") }
                result.error?.let { echo("🔍 Error: $it") }
                
                // Exit with error code
                System.exit(1)
            }
            
        } catch (e: Exception) {
            echo("💥 Execution failed: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }
}

/**
 * Comando para compilar y validar un pipeline.
 */
class CompileCommand : CliktCommand(
    name = "compile",
    help = "Compile and validate a pipeline script"
) {
    private val pipelineFile by argument(
        name = "PIPELINE_FILE",
        help = "Path to the .pipeline.kts file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    override fun run() = runBlocking {
        try {
            echo("🔧 Compiling pipeline: ${pipelineFile.name}")
            
            val compiler = PipelineScriptCompiler()
            val startTime = System.currentTimeMillis()
            val pipeline = compiler.compileFromFile(pipelineFile.absolutePath)
            val duration = System.currentTimeMillis() - startTime
            
            echo("✅ Compilation successful!")
            echo()
            echo("📋 Pipeline: ${pipeline.name}")
            pipeline.description?.let { echo("📄 Description: $it") }
            echo("🏗️ Stages: ${pipeline.stages.size}")
            echo("📦 Total steps: ${pipeline.getTotalStepCount()}")
            echo("⏱️ Compilation time: ${duration}ms")
            
            if (verbose) {
                echo()
                echo("🔍 Detailed Analysis:")
                echo("  Environment variables: ${pipeline.environment.size}")
                echo("  Triggers: ${pipeline.triggers.size}")
                echo("  Parameters: ${pipeline.parameters.size}")
                echo("  Produced artifacts: ${pipeline.getAllProducedArtifacts().size}")
                echo("  Required artifacts: ${pipeline.getAllRequiredArtifacts().size}")
                
                // Validar dependencias
                val unsatisfiedDeps = pipeline.validateArtifactDependencies()
                if (unsatisfiedDeps.isNotEmpty()) {
                    echo("⚠️ Unsatisfied dependencies: ${unsatisfiedDeps.joinToString(", ")}")
                }
            }
            
        } catch (e: Exception) {
            echo("❌ Compilation failed: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }
}

/**
 * Comando para validar un pipeline sin compilación completa.
 */
class ValidateCommand : CliktCommand(
    name = "validate",
    help = "Validate a pipeline script syntax"
) {
    private val pipelineFile by argument(
        name = "PIPELINE_FILE",
        help = "Path to the .pipeline.kts file"
    ).file(mustExist = true, canBeDir = false, mustBeReadable = true)
    
    override fun run() = runBlocking {
        try {
            echo("🔍 Validating pipeline: ${pipelineFile.name}")
            
            val compiler = PipelineScriptCompiler()
            val errors = compiler.validateScript(pipelineFile.absolutePath)
            
            if (errors.isEmpty()) {
                echo("✅ Validation successful! No syntax errors found.")
            } else {
                echo("❌ Validation failed with ${errors.size} error(s):")
                errors.forEach { error ->
                    echo("  • $error")
                }
                System.exit(1)
            }
            
        } catch (e: Exception) {
            echo("💥 Validation error: ${e.message}")
            System.exit(1)
        }
    }
}

/**
 * Comando para mostrar información del DSL.
 */
class InfoCommand : CliktCommand(
    name = "info",
    help = "Show Pipeline DSL information"
) {
    override fun run() {
        val compiler = PipelineScriptCompiler()
        val dslInfo = compiler.getDslInfo()
        
        echo("📖 Pipeline DSL Information")
        echo("=".repeat(50))
        echo("Version: ${dslInfo.version}")
        echo("Description: ${dslInfo.description}")
        echo()
        echo("🔧 Supported Step Types:")
        dslInfo.supportedStepTypes.forEach { stepType ->
            echo("  • $stepType")
        }
        echo()
        echo("📚 Available Imports:")
        dslInfo.availableImports.forEach { import ->
            echo("  • $import")
        }
        echo()
        echo("🚀 Features:")
        echo("  ✅ Type-safe pipeline definitions")
        echo("  ✅ Real-time output streaming")
        echo("  ✅ Event-driven architecture")
        echo("  ✅ Integration with worker system")
        echo("  ✅ Artifact dependency management")
        echo("  ✅ Parallel stage execution")
        echo("  ✅ Conditional stage execution")
        echo("  ✅ Docker support")
        echo("  ✅ Notification system")
    }
}

/**
 * Punto de entrada principal del CLI.
 */
fun main(args: Array<String>) {
    PipelineDslCli()
        .subcommands(
            ExecuteCommand(),
            CompileCommand(),
            ValidateCommand(),
            InfoCommand()
        )
        .main(args)
}