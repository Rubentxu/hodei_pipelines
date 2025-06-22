package dev.rubentxu.hodei.pipelines.domain.worker.model.execution

import dev.rubentxu.hodei.pipelines.domain.job.JobStatus

/**
 * Result of job execution
 */
data class JobExecutionResult(
    val exitCode: Int,
    val status: JobStatus,
    val metrics: Map<String, Any> = emptyMap(),
    val output: String = "",
    val errorMessage: String? = null
) {
    companion object {
        fun success(exitCode: Int = 0, output: String = "", metrics: Map<String, Any> = emptyMap()) =
            JobExecutionResult(exitCode, JobStatus.COMPLETED, metrics, output)

        fun failure(exitCode: Int = 1, errorMessage: String, metrics: Map<String, Any> = emptyMap()) =
            JobExecutionResult(exitCode, JobStatus.FAILED, metrics, "", errorMessage)
    }
}