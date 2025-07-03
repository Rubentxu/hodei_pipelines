package dev.rubentxu.hodei.shared.infrastructure.events

import dev.rubentxu.hodei.shared.domain.events.DomainEvent
import org.slf4j.LoggerFactory

/**
 * Helper service for publishing domain events from any context.
 * Provides a clean interface for bounded contexts to publish events
 * without directly depending on the EventBus implementation.
 */
class EventPublisher(
    private val eventBus: EventBus
) {
    private val logger = LoggerFactory.getLogger(EventPublisher::class.java)
    
    /**
     * Publish a domain event asynchronously
     */
    suspend fun publish(event: DomainEvent) {
        try {
            logger.debug("Publishing event: ${event::class.simpleName} for aggregate ${event.aggregateId}")
            eventBus.publish(event)
            logger.debug("Successfully published event: ${event::class.simpleName}")
        } catch (e: Exception) {
            logger.error("Failed to publish event: ${event::class.simpleName}", e)
            // In production, you might want to:
            // 1. Store failed events for retry
            // 2. Send to a dead letter queue
            // 3. Trigger alerts
            throw EventPublishingException("Failed to publish event: ${event::class.simpleName}", e)
        }
    }
    
    /**
     * Publish multiple events as a batch
     */
    suspend fun publishAll(events: List<DomainEvent>) {
        events.forEach { event ->
            publish(event)
        }
    }
}

/**
 * Exception thrown when event publishing fails
 */
class EventPublishingException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)