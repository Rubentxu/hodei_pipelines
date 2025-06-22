package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import kotlinx.coroutines.flow.Flow

/**
 * Port (Output) - Worker Management Service
 * Handles worker lifecycle, registration, and communication
 */
interface WorkerManagementService {
    suspend fun registerWorker(request: WorkerRegistrationRequest): WorkerRegistrationResult
    suspend fun unregisterWorker(workerId: WorkerId): Boolean
    suspend fun getWorkerInfo(workerId: WorkerId): Worker?
    fun subscribeToWorkerEvents(): Flow<WorkerEvent>
}

/**
 * Worker Registration Request
 */
data class WorkerRegistrationRequest(
    val workerId: WorkerId,
    val name: String,
    val os: String,
    val arch: String,
    val maxConcurrentJobs: Int,
    val authToken: String,
    val version: String = "1.0.0"
)

/**
 * Worker Registration Result
 */
data class WorkerRegistrationResult(
    val success: Boolean,
    val sessionToken: String? = null,
    val heartbeatIntervalSeconds: Int = 30,
    val errorMessage: String? = null
)

/**
 * Heartbeat Data
 */
data class HeartbeatData(
    val activeJobs: Int,
    val status: String,
    val timestamp: java.time.Instant = java.time.Instant.now()
)

/**
 * Worker Events
 */
sealed class WorkerEvent {
    data class WorkerRegistered(val worker: Worker) : WorkerEvent()
    data class WorkerUnregistered(val workerId: WorkerId) : WorkerEvent()
    data class WorkerStatusChanged(val workerId: WorkerId, val newStatus: String) : WorkerEvent()
    data class WorkerHeartbeatReceived(val workerId: WorkerId, val data: HeartbeatData) : WorkerEvent()
}