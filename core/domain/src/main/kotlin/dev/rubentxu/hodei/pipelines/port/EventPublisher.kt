package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.worker.Worker

/**
 * Port (Output) - Event Publisher for domain events
 * Enables decoupled communication between domain and infrastructure
 */
interface EventPublisher {
    suspend fun publishJobEvent(event: JobDomainEvent)
    suspend fun publishWorkerEvent(event: WorkerDomainEvent)
}

/**
 * Domain Events for Jobs
 */
sealed class JobDomainEvent {
    data class JobCreated(val job: Job) : JobDomainEvent()
    data class JobStarted(val job: Job) : JobDomainEvent()
    data class JobCompleted(val job: Job) : JobDomainEvent()
    data class JobFailed(val job: Job) : JobDomainEvent()
    data class JobCancelled(val job: Job) : JobDomainEvent()
}

/**
 * Domain Events for Workers
 */
sealed class WorkerDomainEvent {
    data class WorkerRegistered(val worker: Worker) : WorkerDomainEvent()
    data class WorkerUnregistered(val worker: Worker) : WorkerDomainEvent()
    data class WorkerStatusChanged(val worker: Worker, val previousStatus: String) : WorkerDomainEvent()
    data class WorkerJobAssigned(val worker: Worker, val jobId: String) : WorkerDomainEvent()
    data class WorkerJobCompleted(val worker: Worker, val jobId: String) : WorkerDomainEvent()
}