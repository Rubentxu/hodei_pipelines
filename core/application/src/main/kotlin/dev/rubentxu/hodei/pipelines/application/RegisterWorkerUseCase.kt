package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.domain.worker.*
import dev.rubentxu.hodei.pipelines.port.*
import java.util.UUID

/**
 * Use Case: Register Worker
 * Handles worker registration in the system
 */
class RegisterWorkerUseCase(
    private val workerRepository: WorkerRepository,
    private val eventPublisher: EventPublisher
) {
    
    suspend fun execute(request: RegisterWorkerRequest): RegisterWorkerResponse {
        return try {
            // Create worker
            val worker = Worker(
                id = WorkerId(UUID.randomUUID().toString()),
                name = request.name,
                capabilities = WorkerCapabilities.builder()
                    .os(request.os)
                    .arch(request.arch)
                    .maxConcurrentJobs(request.maxConcurrentJobs)
                    .build()
            )
            
            // Save worker
            val saveResult = workerRepository.save(worker)
            if (saveResult.isFailure) {
                return RegisterWorkerResponse.failure("Failed to save worker: ${saveResult.exceptionOrNull()?.message}")
            }
            
            // Publish event
            eventPublisher.publishWorkerEvent(WorkerDomainEvent.WorkerRegistered(worker))
            
            // Generate session token (simplified for MVP)
            val sessionToken = UUID.randomUUID().toString()
            
            RegisterWorkerResponse.success(worker.id, sessionToken)
            
        } catch (e: Exception) {
            RegisterWorkerResponse.failure("Failed to register worker: ${e.message}")
        }
    }
}

/**
 * Register Worker Request
 */
data class RegisterWorkerRequest(
    val name: String,
    val os: String,
    val arch: String,
    val maxConcurrentJobs: Int
)

/**
 * Register Worker Response
 */
data class RegisterWorkerResponse(
    val success: Boolean,
    val workerId: WorkerId? = null,
    val sessionToken: String? = null,
    val errorMessage: String? = null
) {
    companion object {
        fun success(workerId: WorkerId, sessionToken: String) = RegisterWorkerResponse(
            success = true,
            workerId = workerId,
            sessionToken = sessionToken
        )
        
        fun failure(message: String) = RegisterWorkerResponse(
            success = false,
            errorMessage = message
        )
    }
}