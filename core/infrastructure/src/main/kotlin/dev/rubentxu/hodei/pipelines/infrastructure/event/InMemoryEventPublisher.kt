package dev.rubentxu.hodei.pipelines.infrastructure.event

import dev.rubentxu.hodei.pipelines.port.EventPublisher
import dev.rubentxu.hodei.pipelines.port.JobDomainEvent
import dev.rubentxu.hodei.pipelines.port.WorkerDomainEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mu.KotlinLogging

/**
 * In-Memory implementation of EventPublisher for MVP
 * Publishes events to in-memory streams for local consumption
 */
class InMemoryEventPublisher : EventPublisher {

    private val logger = KotlinLogging.logger {}

    private val _jobEvents = MutableSharedFlow<JobDomainEvent>()
    private val _workerEvents = MutableSharedFlow<WorkerDomainEvent>()
    
    val jobEvents: SharedFlow<JobDomainEvent> = _jobEvents.asSharedFlow()
    val workerEvents: SharedFlow<WorkerDomainEvent> = _workerEvents.asSharedFlow()
    
    override suspend fun publishJobEvent(event: JobDomainEvent) {
        logger.info { "Publishing Job Event: ${event::class.simpleName} - ${event.toString()}" }
        logger.debug { "Job Event Details: $event" }
        try {
            _jobEvents.emit(event)
            logger.trace { "Job event emitted successfully: ${event::class.simpleName} - ${event.toString()}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish job event: ${event::class.simpleName} - ${event.toString()} - ${e.message}" }
        }
    }
    
    override suspend fun publishWorkerEvent(event: WorkerDomainEvent) {
        logger.info { "Publishing Worker Event: ${event::class.simpleName}" }
        logger.debug { "Worker Event Details: $event" }
        try {
            _workerEvents.emit(event)
            logger.trace { "Worker event emitted successfully: ${event::class.simpleName}" }
        } catch (e: Exception) {
            logger.error(e) { "Failed to publish worker event: ${event::class.simpleName} - ${e.message}" }
        }
    }
}