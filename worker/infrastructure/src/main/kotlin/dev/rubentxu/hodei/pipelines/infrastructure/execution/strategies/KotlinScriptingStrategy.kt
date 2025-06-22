package dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobType
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.PipelineContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.execution.JobExecutionResult
import dev.rubentxu.hodei.pipelines.domain.worker.ports.JobExecutionStrategy
import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptCompilationConfiguration
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.script.experimental.api.*
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost

private val logger = KotlinLogging.logger {}

/**
 * Strategy for executing Kotlin scripts using Kotlin Scripting API
 */
class KotlinScriptingStrategy : JobExecutionStrategy {

    override suspend fun execute(
        job: Job,
        workerId: WorkerId,
        outputHandler: (JobOutputChunk) -> Unit
    ): JobExecutionResult = coroutineScope {
        val startTime = System.currentTimeMillis()

        try {
            val scriptPayload = job.definition.payload as? JobPayload.Script
                ?: return@coroutineScope JobExecutionResult.failure(
                    errorMessage = "Job payload is not a script"
                )

            logger.info { "Executing Kotlin script for job ${job.id.value} on worker ${workerId.value}" }

            // Create output channel for real-time output
            val outputChannel = Channel<JobOutputChunk>(Channel.UNLIMITED)

            // Create pipeline context
            val pipelineContext = PipelineContext(
                jobId = job.id,
                workerId = workerId,
                environment = job.definition.environment,
                outputChannel = outputChannel,
                eventChannel = TODO(),
                securityManager = TODO(),
                libraryManager = TODO()
            )

            // Use the predefined compilation configuration
            val compilationConfiguration = PipelineScriptCompilationConfiguration

            // Setup evaluation configuration with the actual pipeline context instance
            val evaluationConfiguration = ScriptEvaluationConfiguration {
                implicitReceivers(pipelineContext)
            }

            // Start collecting output in parallel
            val outputCollector = mutableListOf<JobOutputChunk>()
            val outputJob = launch {
                for (chunk in outputChannel) {
                    outputCollector.add(chunk)
                    outputHandler(chunk)
                }
            }

            // Execute script
            val result = BasicJvmScriptingHost().eval(
                scriptPayload.content.toScriptSource(),
                compilationConfiguration,
                evaluationConfiguration
            )

            // Process compilation/evaluation reports
            val diagnostics = mutableListOf<String>()
            result.reports.forEach { report ->
                if (report.severity >= ScriptDiagnostic.Severity.WARNING) {
                    val message =
                        "[${report.severity}] ${report.message} (${report.sourcePath}:${report.location?.start?.line})"
                    diagnostics.add(message)
                    outputHandler(
                        JobOutputChunk(
                            message.toByteArray(),
                            isError = report.severity >= ScriptDiagnostic.Severity.ERROR
                        )
                    )
                }
            }

            // Close output channel and wait for all output to be collected
            outputChannel.close()
            outputJob.join()

            val executionTime = System.currentTimeMillis() - startTime
            val metrics = mapOf(
                "executionTimeMs" to executionTime,
                "diagnosticsCount" to diagnostics.size,
                "outputChunksCount" to outputCollector.size
            )

            when (result) {
                is ResultWithDiagnostics.Failure -> {
                    val errors = result.reports.joinToString("\\n") { it.message }
                    logger.error { "Script compilation failed for job ${job.id.value}: $errors" }
                    JobExecutionResult.failure(
                        errorMessage = "Script compilation failed:\\n$errors",
                        metrics = metrics
                    )
                }

                is ResultWithDiagnostics.Success -> {
                    val returnValue = result.value.returnValue
                    if (returnValue is ResultValue.Error) {
                        val error = returnValue.error
                        val message = error.message ?: "Unknown runtime error"
                        val stackTrace = error.stackTraceToString()
                        logger.error { "Script runtime error for job ${job.id.value}: $message" }
                        JobExecutionResult.failure(
                            errorMessage = "Script runtime error: $message\\n$stackTrace",
                            metrics = metrics
                        )
                    } else {
                        logger.info { "Script executed successfully for job ${job.id.value} in ${executionTime}ms" }
                        val output = outputCollector.joinToString("\\n") { String(it.data) }
                        JobExecutionResult.success(
                            output = output,
                            metrics = metrics
                        )
                    }
                }
            }

        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val message = e.message ?: "Unknown error"
            val stackTrace = e.stackTraceToString()
            logger.error(e) { "Unexpected error executing script for job ${job.id.value}" }

            JobExecutionResult.failure(
                errorMessage = "Unexpected error: $message\\n$stackTrace",
                metrics = mapOf("executionTimeMs" to executionTime)
            )
        }
    }

    override fun canHandle(jobType: JobType): Boolean {
        return jobType == JobType.SCRIPT
    }

    override fun getSupportedJobTypes(): Set<JobType> {
        return setOf(JobType.SCRIPT)
    }
}