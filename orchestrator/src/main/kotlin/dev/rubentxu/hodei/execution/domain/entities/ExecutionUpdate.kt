package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed class ExecutionUpdate {
    abstract val executionId: DomainId
    abstract val timestamp: Instant
    
    @Serializable
    data class EventUpdate(
        override val executionId: DomainId,
        override val timestamp: Instant,
        val event: GenericExecutionEvent
    ) : ExecutionUpdate()
    
    @Serializable
    data class LogUpdate(
        override val executionId: DomainId,
        override val timestamp: Instant,
        val log: ExecutionLog
    ) : ExecutionUpdate()
    
    @Serializable
    data class StatusUpdate(
        override val executionId: DomainId,
        override val timestamp: Instant,
        val status: ExecutionStatus,
        val message: String? = null
    ) : ExecutionUpdate()
}