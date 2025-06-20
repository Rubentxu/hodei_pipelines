package dev.rubentxu.hodei.pipelines.infrastructure.script

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

/**
 * Pipeline Script Executor that provides a Gradle-like DSL for job execution
 * Similar to how Gradle Kotlin DSL works with tasks and dependencies
 */
class PipelineScriptExecutor : JobExecutor {
    
    override suspend fun execute(job: Job, workerId: dev.rubentxu.hodei.pipelines.domain.worker.WorkerId): Flow<JobExecutionEvent> = flow {
        emit(JobExecutionEvent.Started(job.id, workerId))
        
        try {
            val script = job.definition.environment["SCRIPT_CONTENT"] 
                ?: throw IllegalArgumentException("No SCRIPT_CONTENT found in job environment")
            
            val output = executeKotlinScript(script, job.definition.environment)
            
            emit(JobExecutionEvent.Completed(job.id, 0, output))
        } catch (e: Exception) {
            emit(JobExecutionEvent.Failed(job.id, e.message ?: "Script execution failed", 1))
        }
    }
    
    override suspend fun sendSignal(jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId, signal: dev.rubentxu.hodei.pipelines.port.ExecutionSignal): Boolean {
        // For MVP, just return true
        return true
    }
    
    override suspend fun getJobOutput(jobId: dev.rubentxu.hodei.pipelines.domain.job.JobId): Flow<dev.rubentxu.hodei.pipelines.port.JobOutputChunk> = flow {
        // For MVP, return empty flow
    }
    
    private suspend fun executeKotlinScript(scriptContent: String, environment: Map<String, String>): String {
        val outputCapture = StringBuilder()
        val pipelineContext = PipelineContext(environment, outputCapture)
        
        // Create script evaluation configuration
        val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<PipelineScript> {
            jvm {
                dependenciesFromCurrentContext(wholeClasspath = true)
            }
        }
        
        val evaluationConfiguration = ScriptEvaluationConfiguration {
            implicitReceivers(pipelineContext)
        }
        
        // Execute the script
        val host = BasicJvmScriptingHost()
        val result = host.eval(scriptContent.toScriptSource(), compilationConfiguration, evaluationConfiguration)
        
        when (result) {
            is ResultValue.Value -> {
                // After script execution, ensure any pending tasks are executed with proper context
                executeTasksWithContext(pipelineContext)
                return outputCapture.toString()
            }
            is ResultValue.Error -> {
                val errorMessage = result.error.message ?: "Unknown script error"
                throw RuntimeException("Script execution failed: $errorMessage")
            }
            else -> {
                throw RuntimeException("Unexpected script result: $result")
            }
        }
    }
    
    private fun executeTasksWithContext(context: PipelineContext) {
        // Reset all tasks before execution
        context.tasks.getAllTasks().values.forEach { it.reset() }
        
        // Tasks are executed when explicitly called in the script
        // This method is here for any cleanup or final task execution if needed
    }
}