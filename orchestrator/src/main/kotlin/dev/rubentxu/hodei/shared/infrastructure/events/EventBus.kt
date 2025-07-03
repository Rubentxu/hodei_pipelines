package dev.rubentxu.hodei.shared.infrastructure.events

import dev.rubentxu.hodei.shared.domain.events.DomainEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

/**
 * Event Bus interface for inter-context communication.
 * Enables bounded contexts to communicate through domain events
 * while maintaining loose coupling.
 */
interface EventBus {
    
    /**
     * Publish a domain event to all interested subscribers
     */
    suspend fun publish(event: DomainEvent)
    
    /**
     * Subscribe to events of a specific type
     * @param eventType The type of events to subscribe to
     * @return Flow of events of the specified type
     */
    fun <T : DomainEvent> subscribe(eventType: Class<T>): Flow<T>
    
    /**
     * Subscribe to all domain events
     * @return Flow of all published domain events
     */
    fun subscribeToAll(): Flow<DomainEvent>
}

/**
 * Simple in-memory implementation of EventBus for MVP.
 * In production, this could be replaced with a more robust
 * implementation using message queues, Kafka, etc.
 */
class InMemoryEventBus : EventBus {
    
    private val eventFlow = MutableSharedFlow<DomainEvent>(
        replay = 0,
        extraBufferCapacity = 1000
    )
    
    override suspend fun publish(event: DomainEvent) {
        eventFlow.emit(event)
    }
    
    override fun <T : DomainEvent> subscribe(eventType: Class<T>): Flow<T> {
        return eventFlow
            .filter { eventType.isInstance(it) }
            .map { eventType.cast(it) }
    }
    
    override fun subscribeToAll(): Flow<DomainEvent> {
        return eventFlow
    }
}