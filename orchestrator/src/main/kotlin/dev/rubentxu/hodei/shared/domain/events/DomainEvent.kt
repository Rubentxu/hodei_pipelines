package dev.rubentxu.hodei.shared.domain.events

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
sealed interface DomainEvent {
    val eventId: DomainId
    val timestamp: Instant
    val aggregateId: DomainId
    val eventType: String
    val version: Int
}