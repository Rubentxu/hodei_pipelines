package dev.rubentxu.hodei.execution.domain.entities

/**
 * Types of events that can occur during execution
 */
enum class EventType {
    STATUS_UPDATE,
    STAGE_STARTED,
    STAGE_COMPLETED,
    STEP_STARTED,
    STEP_COMPLETED,
    EXECUTION_STARTED,
    EXECUTION_COMPLETED,
    EXECUTION_FAILED,
    EXECUTION_CANCELLED
}