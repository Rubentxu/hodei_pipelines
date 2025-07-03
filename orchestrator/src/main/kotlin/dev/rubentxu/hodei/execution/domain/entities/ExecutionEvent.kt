package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// ExecutionEvent is now a regular interface, not extending DomainEvent
@Serializable
sealed interface ExecutionEvent {
    val eventId: DomainId
    val timestamp: Instant
    val aggregateId: DomainId // executionId
    val eventType: String
    val version: Int
}

@Serializable
data class ExecutionCreated(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val jobId: DomainId,
    val poolId: DomainId
) : ExecutionEvent {
    override val eventType: String = "ExecutionCreated"
}

@Serializable
data class ExecutionStarted(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val workerId: DomainId
) : ExecutionEvent {
    override val eventType: String = "ExecutionStarted"
}

@Serializable
data class StageStarted(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val stageName: String,
    val stageIndex: Int
) : ExecutionEvent {
    override val eventType: String = "StageStarted"
}

@Serializable
data class StepStarted(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val stageName: String,
    val stepName: String,
    val stepIndex: Int
) : ExecutionEvent {
    override val eventType: String = "StepStarted"
}

@Serializable
data class StepCompleted(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val stageName: String,
    val stepName: String,
    val stepIndex: Int,
    val exitCode: Int,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "StepCompleted"
}

@Serializable
data class StepFailed(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val stageName: String,
    val stepName: String,
    val stepIndex: Int,
    val exitCode: Int,
    val errorMessage: String,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "StepFailed"
}

@Serializable
data class StageCompleted(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val stageName: String,
    val stageIndex: Int,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "StageCompleted"
}

@Serializable
data class StageFailed(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val stageName: String,
    val stageIndex: Int,
    val errorMessage: String,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "StageFailed"
}

@Serializable
data class ExecutionCompleted(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val exitCode: Int,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "ExecutionCompleted"
}

@Serializable
data class ExecutionFailed(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val exitCode: Int?,
    val errorMessage: String,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "ExecutionFailed"
}

@Serializable
data class ExecutionCancelled(
    override val eventId: DomainId,
    override val timestamp: Instant,
    override val aggregateId: DomainId,
    override val version: Int,
    val reason: String,
    val durationSeconds: Long
) : ExecutionEvent {
    override val eventType: String = "ExecutionCancelled"
}