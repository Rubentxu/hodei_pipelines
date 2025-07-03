package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Generic execution event for status updates and other events that don't fit the specific event types
 */
@Serializable
data class GenericExecutionEvent(
    val id: DomainId,
    val executionId: DomainId,
    val timestamp: Instant,
    val type: EventType,
    val stageName: String? = null,
    val stepName: String? = null,
    val message: String? = null,
    val metadata: Map<String, String> = emptyMap()
)