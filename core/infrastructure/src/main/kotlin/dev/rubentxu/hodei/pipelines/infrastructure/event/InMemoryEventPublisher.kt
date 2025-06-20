package dev.rubentxu.hodei.pipelines.infrastructure.event

import dev.rubentxu.hodei.pipelines.port.EventPublisher
import dev.rubentxu.hodei.pipelines.port.JobDomainEvent
import dev.rubentxu.hodei.pipelines.port.WorkerDomainEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-Memory implementation of EventPublisher for MVP
 * Publishes events to in-memory streams for local consumption
 */
class InMemoryEventPublisher : EventPublisher {
    
    private val _jobEvents = MutableSharedFlow<JobDomainEvent>()
    private val _workerEvents = MutableSharedFlow<WorkerDomainEvent>()
    
    val jobEvents: SharedFlow<JobDomainEvent> = _jobEvents.asSharedFlow()
    val workerEvents: SharedFlow<WorkerDomainEvent> = _workerEvents.asSharedFlow()
    
    override suspend fun publishJobEvent(event: JobDomainEvent) {
        _jobEvents.emit(event)
    }
    
    override suspend fun publishWorkerEvent(event: WorkerDomainEvent) {
        _workerEvents.emit(event)
    }
}