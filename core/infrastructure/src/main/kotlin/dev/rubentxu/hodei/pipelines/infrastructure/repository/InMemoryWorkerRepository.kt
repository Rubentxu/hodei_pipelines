package dev.rubentxu.hodei.pipelines.infrastructure.repository

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities
import dev.rubentxu.hodei.pipelines.port.WorkerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory implementation of WorkerRepository for MVP
 * Not suitable for production - data is lost on restart
 */
class InMemoryWorkerRepository : WorkerRepository {

    private val logger = KotlinLogging.logger {}

    private val workers = ConcurrentHashMap<String, Worker>()
    
    override suspend fun save(worker: Worker): Result<WorkerId> {
        logger.debug { "Saving worker with ID: ${worker.id.value}, name: ${worker.name}, and status: ${worker.status}" }
        workers[worker.id.value] = worker
        logger.trace { "Worker ${worker.id.value} saved successfully." }
        return Result.success(worker.id)
    }
    
    override suspend fun findById(id: WorkerId): Result<Worker> {
        logger.debug { "Finding worker by ID: ${id.value}" }
        val worker = workers[id.value]
        return if (worker != null) {
            logger.trace { "Worker ${id.value} found." }
            Result.success(worker)
        } else {
            logger.warn { "Worker ${id.value} not found." }
            Result.failure(Exception("Worker not found: ${id.value}"))
        }
    }
    
    override suspend fun findByStatus(status: WorkerStatus): Result<Worker> {
        logger.debug { "Finding worker by status: $status" }
        val worker = workers.values.find { it.status == status }
        return if (worker != null) {
            logger.trace { "Worker with status $status found." }
            Result.success(worker)
        } else {
            logger.warn { "Worker with status $status not found." }
            Result.failure(Exception("Worker with status $status not found"))
        }
    }
    
    override suspend fun findAvailableWorkers(): Result<List<Worker>> {
        logger.debug { "Finding available workers" }
        val availableWorkers = workers.values.filter {
            it.status == WorkerStatus.IDLE && it.activeJobs < getMaxConcurrentJobs(it.capabilities) 
        }
        logger.debug { "Found ${availableWorkers.size} available workers." }
        return Result.success(availableWorkers)
    }
    
    override suspend fun findByCapabilities(os: String, arch: String): Result<List<Worker>> {
        logger.debug { "Finding workers by capabilities - OS: $os, Arch: $arch" }
        val matchingWorkers = workers.values.filter {
            it.capabilities.getOperatingSystem() == os && it.capabilities.getArchitecture() == arch
        }
        logger.debug { "Found ${matchingWorkers.size} workers with OS: $os and Arch: $arch" }
        return Result.success(matchingWorkers)
    }
    
    override suspend fun count(): Long {
        val count = workers.size.toLong()
        logger.debug { "Total worker count: $count" }
        return count
    }
    
    private fun getMaxConcurrentJobs(capabilities: WorkerCapabilities): Int {
        val maxJobs = capabilities.toMap()["maxConcurrentJobs"]?.toIntOrNull() ?: Int.MAX_VALUE
        logger.trace { "Max concurrent jobs for worker capabilities: $maxJobs" }
        return maxJobs
    }
    
    override suspend fun findAll(): Flow<Worker> {
        logger.debug { "Finding all workers" }
        return workers.values.asFlow()
    }
    
    override suspend fun delete(id: WorkerId): Boolean {
        logger.info { "Deleting worker with ID: ${id.value}" }
        val removed = workers.remove(id.value) != null
        if (removed) {
            logger.debug { "Worker ${id.value} deleted successfully." }
        } else {
            logger.warn { "Worker ${id.value} not found for deletion." }
        }
        return removed
    }
    
    override suspend fun existsById(id: WorkerId): Boolean {
        logger.debug { "Checking if worker exists with ID: ${id.value}" }
        val exists = workers.containsKey(id.value)
        logger.debug { "Worker ${id.value} exists: $exists" }
        return exists
    }
    
    // Helper methods for testing/debugging
    fun clear() {
        logger.info { "Clearing all workers from the repository" }
        workers.clear()
    }
    
    fun size(): Int {
        val size = workers.size
        logger.debug { "Worker repository size: $size" }
        return size
    }
}
