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
    suspend fun save(worker: Worker): Worker
    suspend fun findById(id: WorkerId): Worker?
    suspend fun findByStatus(status: WorkerStatus): List<Worker>
    suspend fun findAvailableWorkers(): List<Worker>
    suspend fun findByCapabilities(os: String, arch: String): List<Worker>
    suspend fun findAll(): Flow<Worker>
    suspend fun delete(id: WorkerId): Boolean
    suspend fun existsById(id: WorkerId): Boolean
    suspend fun count(): Long
}