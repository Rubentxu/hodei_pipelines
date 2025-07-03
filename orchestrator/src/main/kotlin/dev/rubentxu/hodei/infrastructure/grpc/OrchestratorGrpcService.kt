package dev.rubentxu.hodei.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.v1.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.ConcurrentHashMap
import io.github.oshai.kotlinlogging.KotlinLogging
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.resourcemanagement.application.services.AvailableWorker

private val logger = KotlinLogging.logger {}

/**
 * Orchestrator-side implementation of the WorkerService.
 * This service handles bidirectional streaming communication with workers.
 */
class OrchestratorGrpcService(
    private val executionEngine: ExecutionEngine,
    private val workerManager: WorkerManager
) : WorkerServiceGrpcKt.WorkerServiceCoroutineImplBase(), WorkerCommunicationService {

    private val workerConnections = ConcurrentHashMap<String, WorkerConnection>()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    data class WorkerConnection(
        val workerId: String,
        val messageChannel: Channel<OrchestratorMessage>,
        val requestJob: Job,
        val responseJob: Job
    )

    override fun connect(requests: Flow<WorkerMessage>): Flow<OrchestratorMessage> {
        return channelFlow {
            var workerId: String? = null
            var connection: WorkerConnection? = null
            val messageChannel = Channel<OrchestratorMessage>(Channel.UNLIMITED)
            val executionCompletionChannel = Channel<String>(Channel.UNLIMITED) // Signal completion
            var retryCount = 0
            val maxRetries = 3
            
            logger.info { "New worker connection established" }
            
            try {
                lateinit var requestJob: Job
                lateinit var responseJob: Job
                
                // Start processing outgoing messages - emit directly to the channel flow
                responseJob = launch {
                    try {
                        messageChannel.consumeAsFlow().collect { message ->
                            logger.debug { "Sending message to worker $workerId: ${message.payloadCase}" }
                            send(message) // Use send instead of emit in channelFlow
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error sending messages to worker $workerId" }
                    }
                }
                
                // Process incoming messages with retry logic
                requestJob = launch {
                    try {
                        requests.collect { workerMessage ->
                            logger.debug { "Received message from worker: ${workerMessage.payloadCase}" }
                            
                            // Retry wrapper for critical message processing
                            suspend fun processWithRetry(block: suspend () -> Unit) {
                                var attempts = 0
                                while (attempts <= maxRetries) {
                                    try {
                                        block()
                                        return // Success
                                    } catch (e: Exception) {
                                        attempts++
                                        if (attempts > maxRetries) {
                                            logger.error(e) { "Failed to process message after $maxRetries retries" }
                                            throw e
                                        } else {
                                            logger.warn(e) { "Message processing failed, retrying (attempt $attempts/$maxRetries)" }
                                            delay((100 * attempts).toLong()) // Exponential backoff
                                        }
                                    }
                                }
                            }
                            
                            when (workerMessage.payloadCase) {
                                WorkerMessage.PayloadCase.REGISTER_REQUEST -> {
                                    val registerRequest = workerMessage.registerRequest
                                    workerId = registerRequest.workerId
                                    logger.info { "Worker $workerId registered" }
                                    
                                    // Store connection for sending messages back to worker
                                    connection = WorkerConnection(
                                        workerId = workerId!!,
                                        messageChannel = messageChannel,
                                        requestJob = requestJob,
                                        responseJob = responseJob
                                    )
                                    workerConnections[workerId!!] = connection!!
                                    logger.info { "Stored connection for worker $workerId" }
                                    
                                    // Register worker in the manager (synchronously)
                                    workerManager.registerWorker(workerId!!)
                                }
                                
                                WorkerMessage.PayloadCase.STATUS_UPDATE -> {
                                    val statusUpdate = workerMessage.statusUpdate
                                    logger.debug { "Received status update from worker $workerId: ${statusUpdate.message}" }
                                    
                                    // Forward to execution engine - find execution for this worker
                                    workerId?.let { wId ->
                                        launch {
                                            // Find active execution for this worker
                                            val executionId = findActiveExecutionForWorker(wId)
                                            if (executionId != null) {
                                                executionEngine.handleStatusUpdate(statusUpdate, executionId)
                                            } else {
                                                logger.warn { "No active execution found for worker $wId" }
                                            }
                                        }
                                    }
                                }
                                
                                WorkerMessage.PayloadCase.LOG_CHUNK -> {
                                    val logChunk = workerMessage.logChunk
                                    logger.debug { "Received log chunk from worker $workerId" }
                                    
                                    // Forward to execution engine - find execution for this worker
                                    workerId?.let { wId ->
                                        launch {
                                            val executionId = findActiveExecutionForWorker(wId)
                                            if (executionId != null) {
                                                executionEngine.handleLogChunk(logChunk, executionId)
                                            } else {
                                                logger.warn { "No active execution found for worker $wId" }
                                            }
                                        }
                                    }
                                }
                                
                                WorkerMessage.PayloadCase.EXECUTION_RESULT -> {
                                    val executionResult = workerMessage.executionResult
                                    println("*** GRPC RECEIVED EXECUTION RESULT from worker $workerId: success=${executionResult.success} ***")
                                    logger.info { "Received execution result from worker $workerId: success=${executionResult.success}, exitCode=${executionResult.exitCode}" }
                                    
                                    // Critical: Process execution result with retries
                                    workerId?.let { wId ->
                                        processWithRetry {
                                            val executionId = findActiveExecutionForWorker(wId)
                                            if (executionId != null) {
                                                logger.info { "Processing execution result for execution $executionId with worker $wId" }
                                                executionEngine.handleExecutionResult(executionResult, executionId)
                                                logger.info { "Successfully processed execution result for execution $executionId" }
                                                // Signal completion
                                                executionCompletionChannel.trySend(executionId)
                                                logger.info { "Sent completion signal for execution $executionId" }
                                            } else {
                                                logger.warn { "No active execution found for worker $wId" }
                                                throw IllegalStateException("No active execution found for worker $wId")
                                            }
                                        }
                                    }
                                }
                                
                                WorkerMessage.PayloadCase.PAYLOAD_NOT_SET,
                                null -> {
                                    logger.warn { "Received message with no payload from worker $workerId" }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        logger.error(e) { "Error processing messages from worker $workerId" }
                    }
                }
                
                // Wait for the request processing to complete
                try {
                    requestJob.join()
                    
                    // Wait reactively for execution completion signals
                    var completedExecutions = 0
                    var timeoutReached = false
                    
                    launch {
                        delay(5000L) // 5 second timeout
                        timeoutReached = true
                        executionCompletionChannel.close()
                    }
                    
                    // Collect completion signals until timeout or no more executions
                    try {
                        executionCompletionChannel.consumeAsFlow().collect { executionId ->
                            logger.info { "Execution $executionId completed, total: ${++completedExecutions}" }
                            // For tests, we can exit early after first completion
                            if (completedExecutions >= 1) {
                                logger.info { "Received completion for $completedExecutions executions, stopping collection" }
                                return@collect
                            }
                        }
                    } catch (e: Exception) {
                        if (!timeoutReached) {
                            logger.debug { "Execution completion channel closed normally" }
                        }
                    }
                    
                    if (timeoutReached) {
                        logger.warn { "Timeout reached waiting for execution completions. Processed: $completedExecutions" }
                    } else {
                        logger.info { "All executions completed successfully. Total: $completedExecutions" }
                    }
                    
                    // Give a final moment for any remaining async processing
                    delay(100L)
                } finally {
                    responseJob.cancel()
                }
                
            } catch (e: Exception) {
                logger.error(e) { "Error in worker connection for $workerId" }
            } finally {
                // Clean up
                workerId?.let { id ->
                    logger.info { "Worker $id disconnected" }
                    workerConnections.remove(id)
                    scope.launch {
                        try {
                            workerManager.unregisterWorker(id)
                        } catch (e: Exception) {
                            logger.error(e) { "Error unregistering worker $id" }
                        }
                    }
                }
                
                // Close the channels
                messageChannel.close()
                executionCompletionChannel.close()
                
                // Cancel connection jobs if they exist
                connection?.let {
                    it.requestJob.cancel()
                    it.responseJob.cancel()
                }
            }
        }
    }

    /**
     * Send an execution assignment to a specific worker
     */
    override suspend fun sendExecutionAssignment(workerId: String, assignment: ExecutionAssignment): Boolean {
        val connection = workerConnections[workerId]
        return if (connection != null) {
            val message = OrchestratorMessage.newBuilder()
                .setExecutionAssignment(assignment)
                .build()
            
            try {
                connection.messageChannel.send(message)
                logger.info { "Sent execution assignment to worker $workerId: ${assignment.executionId}" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to send execution assignment to worker $workerId" }
                false
            }
        } else {
            logger.warn { "Cannot send execution assignment - worker $workerId not connected" }
            false
        }
    }

    /**
     * Send a cancel signal to a specific worker
     */
    override suspend fun sendCancelSignal(workerId: String, cancelSignal: CancelSignal): Boolean {
        val connection = workerConnections[workerId]
        return if (connection != null) {
            val message = OrchestratorMessage.newBuilder()
                .setCancelSignal(cancelSignal)
                .build()
            
            try {
                connection.messageChannel.send(message)
                logger.info { "Sent cancel signal to worker $workerId: ${cancelSignal.reason}" }
                
                // Cancel the worker's jobs
                connection.requestJob.cancel()
                connection.responseJob.cancel()
                
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to send cancel signal to worker $workerId" }
                false
            }
        } else {
            logger.warn { "Cannot send cancel signal - worker $workerId not connected" }
            false
        }
    }

    /**
     * Send an artifact to a specific worker
     */
    override suspend fun sendArtifact(workerId: String, artifact: Artifact): Boolean {
        val connection = workerConnections[workerId]
        return if (connection != null) {
            val message = OrchestratorMessage.newBuilder()
                .setArtifact(artifact)
                .build()
            
            try {
                connection.messageChannel.send(message)
                logger.info { "Sent artifact to worker $workerId: ${artifact.artifactId}" }
                true
            } catch (e: Exception) {
                logger.error(e) { "Failed to send artifact to worker $workerId" }
                false
            }
        } else {
            logger.warn { "Cannot send artifact - worker $workerId not connected" }
            false
        }
    }

    /**
     * Get list of connected workers
     */
    override fun getConnectedWorkers(): Set<String> {
        return workerConnections.keys.toSet()
    }

    /**
     * Check if a worker is connected
     */
    override fun isWorkerConnected(workerId: String): Boolean {
        return workerConnections.containsKey(workerId)
    }

    /**
     * Find active execution for a worker by asking the execution engine
     */
    private fun findActiveExecutionForWorker(workerId: String): String? {
        // Get active executions from the execution engine
        val activeExecutions = executionEngine.getActiveExecutions()
        logger.debug { "Looking for execution for worker $workerId. Active executions: ${activeExecutions.map { "${it.execution.id.value} -> ${it.workerId}" }}" }
        return activeExecutions.find { context -> context.workerId == workerId }?.execution?.id?.value
    }

    /**
     * Shutdown the service
     */
    fun shutdown() {
        logger.info { "Shutting down orchestrator gRPC service" }
        
        // Close all worker connections
        workerConnections.values.forEach { connection ->
            connection.messageChannel.close()
            connection.requestJob.cancel()
            connection.responseJob.cancel()
        }
        workerConnections.clear()
        
        // Cancel the service scope
        scope.cancel()
    }
}

// Interface for execution engine
interface ExecutionEngine {
    suspend fun handleStatusUpdate(statusUpdate: StatusUpdate, executionId: String)
    suspend fun handleExecutionResult(result: ExecutionResult, executionId: String)
    suspend fun handleLogChunk(logChunk: LogChunk, executionId: String)
    fun getActiveExecutions(): List<ExecutionEngineService.ExecutionContext>
}

// Interface for worker manager
interface WorkerManager {
    suspend fun registerWorker(workerId: String)
    suspend fun unregisterWorker(workerId: String)
    suspend fun updateWorkerHeartbeat(workerId: String)
    suspend fun getPendingInstructions(workerId: String): List<String>
    suspend fun findAvailableWorker(resourceRequirements: Map<String, String>): AvailableWorker?
    suspend fun assignWorkerToExecution(workerId: String, executionId: dev.rubentxu.hodei.shared.domain.primitives.DomainId): Boolean
    suspend fun releaseWorker(workerId: String)
    suspend fun waitForWorkerRegistration(workerId: String, timeoutMs: Long): AvailableWorker?
}