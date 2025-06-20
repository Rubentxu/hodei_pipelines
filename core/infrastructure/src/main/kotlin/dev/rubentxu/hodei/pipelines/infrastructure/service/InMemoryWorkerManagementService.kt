package dev.rubentxu.hodei.pipelines.infrastructure.service

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory implementation of WorkerManagementService for MVP
 * Manages worker lifecycle and health monitoring
 */
class InMemoryWorkerManagementService(
    private val workerRepository: WorkerRepository
) : WorkerManagementService {
    
    private val heartbeats = ConcurrentHashMap<String, HeartbeatData>()
    private val sessionTokens = ConcurrentHashMap<String, String>()
    private val _workerEvents = MutableSharedFlow<WorkerEvent>()
    
    override suspend fun registerWorker(request: WorkerRegistrationRequest): WorkerRegistrationResult {
        return try {
            // Check if worker already exists
            val existingWorker = workerRepository.findById(request.workerId)
            if (existingWorker.isSuccess) {
                return WorkerRegistrationResult(
                    success = false,
                    errorMessage = "Worker already registered"
                )
            }
            
            // Generate session token
            val sessionToken = java.util.UUID.randomUUID().toString()
            sessionTokens[request.workerId.value] = sessionToken
            
            // Emit registration event
            _workerEvents.emit(WorkerEvent.WorkerRegistered(
                Worker(
                    id = request.workerId,
                    name = request.name,
                    capabilities = dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities.builder()
                        .os(request.os)
                        .arch(request.arch)
                        .maxConcurrentJobs(request.maxConcurrentJobs)
                        .build()
                )
            ))
            
            WorkerRegistrationResult(
                success = true,
                sessionToken = sessionToken,
                heartbeatIntervalSeconds = 30
            )
        } catch (e: Exception) {
            WorkerRegistrationResult(
                success = false,
                errorMessage = "Registration failed: ${e.message}"
            )
        }
    }
    
    override suspend fun unregisterWorker(workerId: WorkerId): Boolean {
        return try {
            heartbeats.remove(workerId.value)
            sessionTokens.remove(workerId.value)
            _workerEvents.emit(WorkerEvent.WorkerUnregistered(workerId))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun sendHeartbeat(workerId: WorkerId, status: HeartbeatData): Boolean {
        return try {
            heartbeats[workerId.value] = status
            _workerEvents.emit(WorkerEvent.WorkerHeartbeatReceived(workerId, status))
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun getWorkerInfo(workerId: WorkerId): Worker? {
        val result = workerRepository.findById(workerId)
        return result.getOrNull()
    }
    
    override fun subscribeToWorkerEvents(): Flow<WorkerEvent> {
        return _workerEvents.asSharedFlow()
    }
}