package dev.rubentxu.hodei.execution.domain.entities

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED;
    
    fun canTransitionTo(newStatus: ExecutionStatus): Boolean = when (this) {
        PENDING -> newStatus in setOf(RUNNING, CANCELLED)
        RUNNING -> newStatus in setOf(SUCCESS, FAILED, CANCELLED)
        SUCCESS -> false
        FAILED -> false
        CANCELLED -> false
    }
    
    val isTerminal: Boolean
        get() = this in setOf(SUCCESS, FAILED, CANCELLED)
    
    val isActive: Boolean
        get() = this in setOf(PENDING, RUNNING)
}