package dev.rubentxu.hodei.pipelines.infrastructure.repository

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities
import dev.rubentxu.hodei.pipelines.port.WorkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory implementation of WorkerRepository for MVP
 * Not suitable for production - data is lost on restart
 */
class InMemoryWorkerRepository : WorkerRepository {
    
    private val workers = ConcurrentHashMap<String, Worker>()
    
    override suspend fun save(worker: Worker): Result<WorkerId> {
        workers[worker.id.value] = worker
        return Result.success(worker.id)
    }
    
    override suspend fun findById(id: WorkerId): Result<Worker> {
        val worker = workers[id.value]
        return if (worker != null) {
            Result.success(worker)
        } else {
            Result.failure(Exception("Worker not found: ${id.value}"))
        }
    }
    
    override suspend fun findByStatus(status: WorkerStatus): Result<Worker> {
        val worker = workers.values.find { it.status == status }
        return if (worker != null) {
            Result.success(worker)
        } else {
            Result.failure(Exception("Worker with status $status not found"))
        }
    }
    
    override suspend fun findAvailableWorkers(): Result<List<Worker>> {
        val availableWorkers = workers.values.filter { 
            it.status == WorkerStatus.IDLE && it.activeJobs < getMaxConcurrentJobs(it.capabilities) 
        }
        return Result.success(availableWorkers)
    }
    
    override suspend fun findByCapabilities(os: String, arch: String): Result<List<Worker>> {
        val matchingWorkers = workers.values.filter { 
            it.capabilities.getOperatingSystem() == os && it.capabilities.getArchitecture() == arch
        }
        return Result.success(matchingWorkers)
    }
    
    override suspend fun count(): Long {
        return workers.size.toLong()
    }
    
    private fun getMaxConcurrentJobs(capabilities: WorkerCapabilities): Int {
        return capabilities.toMap()["maxConcurrentJobs"]?.toIntOrNull() ?: Int.MAX_VALUE
    }
    
    override suspend fun findAll(): Flow<Worker> {
        return workers.values.asFlow()
    }
    
    override suspend fun delete(id: WorkerId): Boolean {
        return workers.remove(id.value) != null
    }
    
    override suspend fun existsById(id: WorkerId): Boolean {
        return workers.containsKey(id.value)
    }
    
    // Helper methods for testing/debugging
    fun clear() {
        workers.clear()
    }
    
    fun size(): Int = workers.size
}