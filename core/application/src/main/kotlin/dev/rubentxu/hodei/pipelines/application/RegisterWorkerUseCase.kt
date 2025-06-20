package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.domain.worker.*
import dev.rubentxu.hodei.pipelines.port.*
import mu.KotlinLogging
import java.util.UUID

/**
 * Use Case: Register Worker
 * Handles worker registration in the system
 */
class RegisterWorkerUseCase(
    private val workerRepository: WorkerRepository,
    private val eventPublisher: EventPublisher
) {

    private val logger = KotlinLogging.logger {}

    suspend fun execute(request: RegisterWorkerRequest): RegisterWorkerResponse {
        logger.info { "Registering worker: ${request.name}" }
        logger.debug { "Worker registration request: $request" }
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
            
            logger.debug { "Creating worker with ID: ${worker.id.value}" }
            // Save worker
            val saveResult = workerRepository.save(worker)
            if (saveResult.isFailure) {
                logger.error(saveResult.exceptionOrNull()) { "Failed to save worker: ${worker.id.value}" }
                return RegisterWorkerResponse.failure("Failed to save worker: ${saveResult.exceptionOrNull()?.message}")
            }
            
            // Publish event
            eventPublisher.publishWorkerEvent(WorkerDomainEvent.WorkerRegistered(worker))
            logger.info { "Worker registered: ${worker.id.value}" }

            // Generate session token (simplified for MVP)
            val sessionToken = UUID.randomUUID().toString()
            logger.debug { "Generated session token for worker ${worker.id.value}: $sessionToken" }

            RegisterWorkerResponse.success(worker.id, sessionToken)
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to register worker: ${e.message}" }
            RegisterWorkerResponse.failure("Failed to register worker: ${e.message}")
        } finally {
            logger.info { "Worker registration flow completed for worker: ${request.name}" }
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