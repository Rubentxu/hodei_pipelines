package dev.rubentxu.hodei.pipelines.infrastructure.script


import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.ScriptExecutor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

class PipelineScriptExecutor : ScriptExecutor {
    override fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent> = flow {
        emit(JobExecutionEvent.Started(job.id, workerId))
        val outputCapture = StringBuilder()
        val pipelineContext = PipelineContext(job.definition.environment, outputCapture)

        try {
            val scriptPayload = job.definition.payload as? JobPayload.Script
            if (scriptPayload == null) {
                emit(JobExecutionEvent.Failed(job.id, "Job payload is not a script.", 1))
                return@flow
            }
            val scriptContent = scriptPayload.content

            val compilationConfiguration = createJvmCompilationConfigurationFromTemplate<PipelineScript>()

            val evaluationConfiguration = ScriptEvaluationConfiguration {
                implicitReceivers(pipelineContext)
            }

            val result = BasicJvmScriptingHost().eval(
                scriptContent.toScriptSource(),
                compilationConfiguration,
                evaluationConfiguration
            )

            result.reports.forEach {
                if (it.severity >= ScriptDiagnostic.Severity.WARNING) {
                    val severity = it.severity
                    val message = it.message
                    val sourcePath = it.sourcePath
                    val line = it.location?.start?.line
                    outputCapture.appendLine("[$severity] $message ($sourcePath:$line)")
                }
            }

            when (result) {
                is ResultWithDiagnostics.Failure -> {
                    val errors = result.reports.joinToString("\n") { it.message }
                    emit(JobExecutionEvent.Failed(job.id, "Script compilation failed:\n$errors", 1))
                }

                is ResultWithDiagnostics.Success -> {
                    val returnValue = result.value.returnValue
                    if (returnValue is ResultValue.Error) {
                        val error = returnValue.error
                        val message = error.message ?: "Unknown runtime error"
                        val stackTrace = error.stackTraceToString()
                        emit(JobExecutionEvent.Failed(job.id, "Script runtime error: $message\n$stackTrace", 1))
                    } else {
                        emit(JobExecutionEvent.Completed(job.id, 0, outputCapture.toString()))
                    }
                }
            }

        } catch (e: Exception) {
            val message = e.message ?: "Unknown error"
            val stackTrace = e.stackTraceToString()
            emit(JobExecutionEvent.Failed(job.id, "An unexpected error occurred: $message\n$stackTrace", 1))
        }
    }
}