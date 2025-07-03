package dev.rubentxu.hodei.pipelines.domain

data class ExecutionResult(
    val exitCode: Int,
    val status: ExecutionStatus,
    val metrics: Map<String, Any> = emptyMap(),
    val output: String = "",
    val errorMessage: String? = null
) {
    companion object {
        fun success(exitCode: Int = 0, output: String = "", metrics: Map<String, Any> = emptyMap()) =
            ExecutionResult(exitCode, ExecutionStatus.COMPLETED, metrics, output)

        fun failure(exitCode: Int = 1, errorMessage: String, metrics: Map<String, Any> = emptyMap()) =
            ExecutionResult(exitCode, ExecutionStatus.FAILED, metrics, "", errorMessage)
    }
}