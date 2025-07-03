package dev.rubentxu.hodei.resourcemanagement.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.domain.worker.*
import dev.rubentxu.hodei.pipelines.v1.*
import dev.rubentxu.hodei.infrastructure.grpc.WorkerManager
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.withTimeoutOrNull
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration.Companion.milliseconds

private val logger = KotlinLogging.logger {}

data class AvailableWorker(
    val workerId: String,
    val poolId: String
)

class WorkerManagerService : WorkerManager {
    
    private val registeredWorkers = ConcurrentHashMap<String, Worker>()
    private val workerMutex = Mutex()
    
    override suspend fun registerWorker(workerId: String) {
        workerMutex.withLock {
            logger.info { "Registering worker $workerId" }
            
            val worker = Worker(
                id = DomainId(workerId),
                poolId = DomainId("default"),
                status = WorkerStatus.IDLE,
                capabilities = WorkerCapabilities(
                    cpu = "2",
                    memory = "4Gi",
                    storage = "20Gi"
                ),
                resourceAllocation = WorkerResources(
                    cpuCores = 2.0,
                    memoryGB = 4.0,
                    diskGB = 20.0
                ),
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now()
            )
            
            registeredWorkers[workerId] = worker
            logger.info { "Worker $workerId registered successfully" }
        }
    }
    
    override suspend fun unregisterWorker(workerId: String) {
        workerMutex.withLock {
            val worker = registeredWorkers.remove(workerId)
            if (worker != null) {
                logger.info { "Worker $workerId unregistered" }
            } else {
                logger.warn { "Attempted to unregister unknown worker $workerId" }
            }
        }
    }
    
    override suspend fun updateWorkerHeartbeat(workerId: String) {
        val worker = registeredWorkers[workerId]
        if (worker != null) {
            val updatedWorker = worker.heartbeat()
            registeredWorkers[workerId] = updatedWorker
            logger.debug { "Heartbeat updated for worker $workerId" }
        } else {
            logger.warn { "Received heartbeat from unregistered worker $workerId" }
        }
    }
    
    /**
     * Wait for a worker to register within a timeout period
     * @param workerId The ID of the worker to wait for
     * @param timeoutMs Timeout in milliseconds
     * @return The registered worker or null if timeout exceeded
     */
    override suspend fun waitForWorkerRegistration(workerId: String, timeoutMs: Long): AvailableWorker? {
        logger.info { "Waiting for worker $workerId to register (timeout: ${timeoutMs}ms)" }
        
        return withTimeoutOrNull(timeoutMs.milliseconds) {
            // Poll for worker registration
            while (true) {
                val worker = registeredWorkers[workerId]
                if (worker != null) {
                    logger.info { "Worker $workerId registered successfully" }
                    return@withTimeoutOrNull AvailableWorker(
                        workerId = worker.id.value,
                        poolId = worker.poolId.value
                    )
                }
                // Short delay to avoid busy waiting
                kotlinx.coroutines.delay(100)
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }
    
    override suspend fun getPendingInstructions(workerId: String): List<String> {
        // Simplified - return empty list for now
        return emptyList()
    }
    
    // Additional methods for domain functionality
    override suspend fun findAvailableWorker(resourceRequirements: Map<String, String>): AvailableWorker? {
        return registeredWorkers.values
            .filter { it.status == WorkerStatus.IDLE }
            .map { worker ->
                AvailableWorker(
                    workerId = worker.id.value,
                    poolId = worker.poolId.value
                )
            }
            .firstOrNull()
    }
    
    override suspend fun assignWorkerToExecution(workerId: String, executionId: DomainId): Boolean {
        val worker = registeredWorkers[workerId]
        return if (worker != null && worker.status == WorkerStatus.IDLE) {
            val updatedWorker = worker.assignExecution(executionId)
            registeredWorkers[workerId] = updatedWorker
            logger.info { "Worker $workerId assigned to execution ${executionId.value}" }
            true
        } else {
            logger.warn { "Failed to assign worker $workerId to execution ${executionId.value}" }
            false
        }
    }
    
    override suspend fun releaseWorker(workerId: String) {
        val worker = registeredWorkers[workerId]
        if (worker != null) {
            val updatedWorker = worker.releaseExecution()
            registeredWorkers[workerId] = updatedWorker
            logger.info { "Worker $workerId released" }
        } else {
            logger.warn { "Attempted to release unknown worker $workerId" }
        }
    }
    
    suspend fun getRegisteredWorkers(): List<Worker> {
        return registeredWorkers.values.toList()
    }
    
    suspend fun getWorkerById(workerId: String): Worker? {
        return registeredWorkers[workerId]
    }
    
    suspend fun getWorkersByPool(poolName: String): List<Worker> {
        return registeredWorkers.values
            .filter { it.poolId.value == poolName }
            .toList()
    }
    
    suspend fun getAllWorkers(): List<Worker> {
        return registeredWorkers.values.toList()
    }
}