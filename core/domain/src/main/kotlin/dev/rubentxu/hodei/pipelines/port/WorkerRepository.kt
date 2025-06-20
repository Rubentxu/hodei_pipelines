package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import kotlinx.coroutines.flow.Flow

/**
 * Port (Output) - Worker Repository for data persistence
 * Follows hexagonal architecture principles - domain defines the contract
 */
interface WorkerRepository {
    suspend fun save(worker: Worker): Result<WorkerId> // Result<WorkerId> instead of Worker
    suspend fun findById(id: WorkerId): Result<Worker> // Result<Worker> instead of Worker?
    suspend fun findByStatus(status: WorkerStatus): Result<Worker> // List<Worker>
    suspend fun findAvailableWorkers(): Result<List<Worker>> // Result<L> // List<Worker>
    suspend fun findByCapabilities(os: String, arch: String): Result<List<Worker>> // List<Worker>
    suspend fun findAll(): Flow<Worker>
    suspend fun delete(id: WorkerId): Boolean
    suspend fun existsById(id: WorkerId): Boolean
    suspend fun count(): Long
}

