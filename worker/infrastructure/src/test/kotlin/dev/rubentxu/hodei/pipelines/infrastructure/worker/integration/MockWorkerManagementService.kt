package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.proto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock gRPC WorkerManagement service for integration testing
 */
class MockWorkerManagementService : WorkerManagementServiceGrpcKt.WorkerManagementServiceCoroutineImplBase() {
    
    private val logger = KotlinLogging.logger {}
    private val registeredWorkers = ConcurrentHashMap<String, RegisteredWorker>()
    
    // Test configuration
    var registrationShouldFail = false
    var registrationFailureMessage = "Registration failed for testing"
    var sessionTokenPrefix = "test-session-"
    var heartbeatInterval = 10
    
    override suspend fun registerWorker(request: WorkerRegistrationRequest): WorkerRegistrationResponse {
        val workerId = request.workerId.value
        logger.info { "Mock server: Registering worker $workerId" }
        
        if (registrationShouldFail) {
            logger.warn { "Mock server: Simulating registration failure for worker $workerId" }
            return WorkerRegistrationResponse.newBuilder()
                .setSuccess(false)
                .setMessage(registrationFailureMessage)
                .build()
        }
        
        val sessionToken = "$sessionTokenPrefix$workerId-${System.currentTimeMillis()}"
        
        registeredWorkers[workerId] = RegisteredWorker(
            workerId = workerId,
            workerName = request.workerName,
            capabilities = request.capabilitiesMap.toMap(),
            sessionToken = sessionToken,
            registrationTime = java.time.Instant.now()
        )
        
        logger.info { "Mock server: Worker $workerId registered successfully with session token: $sessionToken" }
        
        return WorkerRegistrationResponse.newBuilder()
            .setSuccess(true)
            .setMessage("Worker registered successfully")
            .setSessionToken(sessionToken)
            .setHeartbeatIntervalSeconds(heartbeatInterval)
            .build()
    }
    
    override suspend fun getWorkerInfo(request: WorkerIdentifier): WorkerInfo {
        val workerId = request.value
        val worker = registeredWorkers[workerId]
        
        return if (worker != null) {
            logger.debug { "Mock server: Returning info for worker $workerId" }
            WorkerInfo.newBuilder()
                .setId(WorkerIdentifier.newBuilder().setValue(workerId).build())
                .setName(worker.workerName)
                .setStatus(WorkerStatus.WORKER_STATUS_READY)
                .putAllCapabilities(worker.capabilities)
                .setRegisteredAt(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(worker.registrationTime.epochSecond)
                    .setNanos(worker.registrationTime.nano)
                    .build())
                .setLastHeartbeat(com.google.protobuf.Timestamp.newBuilder()
                    .setSeconds(java.time.Instant.now().epochSecond)
                    .setNanos(java.time.Instant.now().nano)
                    .build())
                .setActiveJobsCount(0)
                .setMaxConcurrentJobs(1)
                .addTags("test-worker")
                .build()
        } else {
            logger.warn { "Mock server: Worker $workerId not found" }
            WorkerInfo.getDefaultInstance()
        }
    }
    
    override fun listWorkers(request: com.google.protobuf.Empty): Flow<WorkerInfo> = flow {
        logger.debug { "Mock server: Listing ${registeredWorkers.size} workers" }
        
        registeredWorkers.forEach { (workerId, _) ->
            val workerInfo = getWorkerInfo(WorkerIdentifier.newBuilder().setValue(workerId).build())
            emit(workerInfo)
        }
    }
    
    override suspend fun unregisterWorker(request: WorkerIdentifier): com.google.protobuf.Empty {
        val workerId = request.value
        logger.info { "Mock server: Unregistering worker $workerId" }
        
        registeredWorkers.remove(workerId)
        
        return com.google.protobuf.Empty.getDefaultInstance()
    }
    
    // Test utilities
    fun getRegisteredWorkers(): Map<String, RegisteredWorker> = registeredWorkers.toMap()
    
    fun isWorkerRegistered(workerId: String): Boolean = registeredWorkers.containsKey(workerId)
    
    fun getWorkerSession(workerId: String): String? = registeredWorkers[workerId]?.sessionToken
    
    fun clearRegistrations() {
        registeredWorkers.clear()
    }
    
    fun waitForWorkerRegistration(workerId: String, timeoutMs: Long = 5000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (registeredWorkers.containsKey(workerId)) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
}

/**
 * Registered worker data
 */
data class RegisteredWorker(
    val workerId: String,
    val workerName: String,
    val capabilities: Map<String, String>,
    val sessionToken: String,
    val registrationTime: java.time.Instant
)