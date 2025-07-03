package dev.rubentxu.hodei.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.v1.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.stub.StreamObserver
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Implementation of the simplified WorkerService that handles bidirectional streaming
 * between workers and the orchestrator using the new protocol
 */
class WorkerServiceAdapter(
    private val executionEngine: ExecutionEngine
) : WorkerServiceGrpcKt.WorkerServiceCoroutineImplBase() {
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val workerConnections = ConcurrentHashMap<String, WorkerConnection>()
    
    data class WorkerConnection(
        val workerId: String,
        val responseObserver: StreamObserver<OrchestratorMessage>
    )
    
    override fun connect(requests: Flow<WorkerMessage>): Flow<OrchestratorMessage> = flow {
        logger.info { "New worker connection established" }
        
        var workerId: String? = null
        var connection: WorkerConnection? = null
        
        try {
            requests.collect { message ->
                when (message.payloadCase) {
                    WorkerMessage.PayloadCase.REGISTER_REQUEST -> {
                        workerId = message.registerRequest.workerId
                        logger.info { "Worker $workerId registering" }
                        
                        // Store connection for sending messages back to worker
                        // Note: In the real implementation, we'd handle this differently
                        // For now, we just acknowledge the registration
                        logger.info { "Worker $workerId registered successfully" }
                    }
                    
                    WorkerMessage.PayloadCase.STATUS_UPDATE -> {
                        val statusUpdate = message.statusUpdate
                        logger.debug { "Received status update from worker $workerId: ${statusUpdate.eventType}" }
                        
                        // We need to extract the execution ID from the status update
                        // In a real implementation, this would be handled differently
                        // For now, we'll assume the message contains execution context
                        workerId?.let { wId ->
                            executionEngine.handleStatusUpdate(statusUpdate, "unknown-execution-id")
                        }
                    }
                    
                    WorkerMessage.PayloadCase.LOG_CHUNK -> {
                        val logChunk = message.logChunk
                        logger.debug { "Received log chunk from worker $workerId" }
                        
                        workerId?.let { wId ->
                            executionEngine.handleLogChunk(logChunk, "unknown-execution-id")
                        }
                    }
                    
                    WorkerMessage.PayloadCase.EXECUTION_RESULT -> {
                        val result = message.executionResult
                        logger.info { "Received execution result from worker $workerId: success=${result.success}" }
                        
                        workerId?.let { wId ->
                            executionEngine.handleExecutionResult(result, "unknown-execution-id")
                        }
                    }
                    
                    else -> {
                        logger.warn { "Unknown message type: ${message.payloadCase}" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in worker connection for worker: $workerId" }
        } finally {
            workerId?.let { wId ->
                workerConnections.remove(wId)
                logger.info { "Worker $wId disconnected" }
            }
        }
    }
    
    // Method to send execution assignment to a specific worker
    fun sendExecutionAssignment(workerId: String, executionAssignment: ExecutionAssignment) {
        val connection = workerConnections[workerId]
        if (connection != null) {
            val message = OrchestratorMessage.newBuilder()
                .setExecutionAssignment(executionAssignment)
                .build()
            
            try {
                connection.responseObserver.onNext(message)
                logger.debug { "Sent execution assignment to worker $workerId" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send execution assignment to worker $workerId" }
            }
        } else {
            logger.warn { "No connection found for worker $workerId" }
        }
    }
    
    // Method to send cancel signal to a specific worker
    fun sendCancelSignal(workerId: String, cancelSignal: CancelSignal) {
        val connection = workerConnections[workerId]
        if (connection != null) {
            val message = OrchestratorMessage.newBuilder()
                .setCancelSignal(cancelSignal)
                .build()
            
            try {
                connection.responseObserver.onNext(message)
                logger.debug { "Sent cancel signal to worker $workerId" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send cancel signal to worker $workerId" }
            }
        } else {
            logger.warn { "No connection found for worker $workerId" }
        }
    }
    
    // Method to send artifact to a specific worker
    fun sendArtifact(workerId: String, artifact: Artifact) {
        val connection = workerConnections[workerId]
        if (connection != null) {
            val message = OrchestratorMessage.newBuilder()
                .setArtifact(artifact)
                .build()
            
            try {
                connection.responseObserver.onNext(message)
                logger.debug { "Sent artifact to worker $workerId" }
            } catch (e: Exception) {
                logger.error(e) { "Failed to send artifact to worker $workerId" }
            }
        } else {
            logger.warn { "No connection found for worker $workerId" }
        }
    }
}