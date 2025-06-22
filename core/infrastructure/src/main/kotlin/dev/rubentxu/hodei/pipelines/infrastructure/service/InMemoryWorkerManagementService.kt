package dev.rubentxu.hodei.pipelines.infrastructure.service

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory implementation of WorkerManagementService for MVP
 * Manages worker lifecycle and health monitoring
 */
class InMemoryWorkerManagementService(
    private val workerRepository: WorkerRepository
) : WorkerManagementService {

    private val logger = KotlinLogging.logger {}
    private val heartbeats = ConcurrentHashMap<String, HeartbeatData>()
    private val sessionTokens = ConcurrentHashMap<String, String>()
    private val _workerEvents = MutableSharedFlow<WorkerEvent>()
    
    override suspend fun registerWorker(request: WorkerRegistrationRequest): WorkerRegistrationResult {
        logger.info { "Registering worker: ${request.name} with ID: ${request.workerId.value}" }
        logger.debug { "Worker registration request details: $request" }
        return try {
            // Check if worker already exists
            val existingWorker = workerRepository.findById(request.workerId)
            if (existingWorker.isSuccess) {
                logger.warn { "Worker with ID ${request.workerId.value} already registered" }
                return WorkerRegistrationResult(
                    success = false,
                    errorMessage = "Worker already registered"
                )
            }
            
            // Generate session token
            val sessionToken = java.util.UUID.randomUUID().toString()
            sessionTokens[request.workerId.value] = sessionToken
            logger.debug { "Generated session token for worker ${request.workerId.value}: $sessionToken" }

            // Create Worker object
            val worker = Worker(
                id = request.workerId,
                name = request.name,
                capabilities = dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities.builder()
                    .os(request.os)
                    .arch(request.arch)
                    .maxConcurrentJobs(request.maxConcurrentJobs)
                    .build()
            )

            // Save the worker to the repository
            workerRepository.save(worker)
            logger.info { "Worker ${request.name} with ID ${request.workerId.value} successfully registered." }

            // Emit registration event
            _workerEvents.emit(WorkerEvent.WorkerRegistered(worker))
            logger.debug { "Published WorkerRegistered event for worker ${request.workerId.value}" }

            WorkerRegistrationResult(
                success = true,
                sessionToken = sessionToken,
                heartbeatIntervalSeconds = 30
            )
        } catch (e: Exception) {
            logger.error(e) { "Registration failed for worker ${request.name} with ID ${request.workerId.value}: ${e.message}" }
            WorkerRegistrationResult(
                success = false,
                errorMessage = "Registration failed: ${e.message}"
            )
        }
    }
    
    override suspend fun unregisterWorker(workerId: WorkerId): Boolean {
        logger.info { "Unregistering worker with ID: ${workerId.value}" }
        return try {
            heartbeats.remove(workerId.value)
            sessionTokens.remove(workerId.value)
            _workerEvents.emit(WorkerEvent.WorkerUnregistered(workerId))
            logger.debug { "Worker ${workerId.value} unregistered and events emitted." }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to unregister worker with ID ${workerId.value}: ${e.message}" }
            false
        }
    }
    
    suspend fun sendHeartbeat(workerId: WorkerId, status: HeartbeatData): Boolean {
        logger.debug { "Received heartbeat from worker ${workerId.value} with status: $status" }
        return try {
            heartbeats[workerId.value] = status
            _workerEvents.emit(WorkerEvent.WorkerHeartbeatReceived(workerId, status))
            logger.trace { "Heartbeat processed and event emitted for worker ${workerId.value}" }
            true
        } catch (e: Exception) {
            logger.warn(e) { "Failed to process heartbeat from worker ${workerId.value}: ${e.message}" }
            false
        }
    }
    
    override suspend fun getWorkerInfo(workerId: WorkerId): Worker? {
        logger.debug { "Fetching worker info for worker ID: ${workerId.value}" }
        val result = workerRepository.findById(workerId)
        return if (result.isSuccess) {
            logger.debug { "Worker info retrieved successfully for worker ID: ${workerId.value}" }
            result.getOrNull()
        } else {
            logger.warn { "Worker info not found for worker ID: ${workerId.value}" }
            null
        }
    }
    
    override fun subscribeToWorkerEvents(): Flow<WorkerEvent> {
        logger.debug { "Subscribing to worker events" }
        return _workerEvents.asSharedFlow()
    }
}