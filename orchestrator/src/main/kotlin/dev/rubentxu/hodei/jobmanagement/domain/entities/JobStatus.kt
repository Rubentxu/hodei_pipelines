package dev.rubentxu.hodei.jobmanagement.domain.entities

import kotlinx.serialization.Serializable

@Serializable
enum class JobStatus {
    PENDING,
    QUEUED,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED;
    
    fun canTransitionTo(newStatus: JobStatus): Boolean = when (this) {
        PENDING -> newStatus in setOf(QUEUED, CANCELLED)
        QUEUED -> newStatus in setOf(RUNNING, CANCELLED)
        RUNNING -> newStatus in setOf(COMPLETED, FAILED, CANCELLED, QUEUED)
        COMPLETED -> false
        FAILED -> newStatus == QUEUED
        CANCELLED -> false
    }
    
    val isTerminal: Boolean
        get() = this in setOf(COMPLETED, FAILED, CANCELLED)
    
    val isActive: Boolean
        get() = this in setOf(PENDING, QUEUED, RUNNING)
}