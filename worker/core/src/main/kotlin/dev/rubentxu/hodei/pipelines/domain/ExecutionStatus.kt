package dev.rubentxu.hodei.pipelines.domain

enum class ExecutionStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}