package dev.rubentxu.hodei.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.v1.*

/**
 * Interface for sending messages to workers.
 * This breaks the circular dependency between ExecutionEngine and OrchestratorGrpcService
 */
interface WorkerCommunicationService {
    
    /**
     * Send an execution assignment to a specific worker
     */
    suspend fun sendExecutionAssignment(workerId: String, assignment: ExecutionAssignment): Boolean
    
    /**
     * Send a cancel signal to a specific worker
     */
    suspend fun sendCancelSignal(workerId: String, cancelSignal: CancelSignal): Boolean
    
    /**
     * Send an artifact to a specific worker
     */
    suspend fun sendArtifact(workerId: String, artifact: Artifact): Boolean
    
    /**
     * Check if a worker is connected
     */
    fun isWorkerConnected(workerId: String): Boolean
    
    /**
     * Get list of connected workers
     */
    fun getConnectedWorkers(): Set<String>
}

/**
 * Default implementation that does nothing - used when no gRPC service is available
 */
class NoOpWorkerCommunicationService : WorkerCommunicationService {
    
    override suspend fun sendExecutionAssignment(workerId: String, assignment: ExecutionAssignment): Boolean {
        // No-op implementation for testing or when gRPC is not available
        return false
    }
    
    override suspend fun sendCancelSignal(workerId: String, cancelSignal: CancelSignal): Boolean {
        return false
    }
    
    override suspend fun sendArtifact(workerId: String, artifact: Artifact): Boolean {
        return false
    }
    
    override fun isWorkerConnected(workerId: String): Boolean {
        return false
    }
    
    override fun getConnectedWorkers(): Set<String> {
        return emptySet()
    }
}

/**
 * Mock implementation for testing that returns success
 */
class MockWorkerCommunicationService : WorkerCommunicationService {
    
    override suspend fun sendExecutionAssignment(workerId: String, assignment: ExecutionAssignment): Boolean {
        // Mock implementation for testing - always returns success
        return true
    }
    
    override suspend fun sendCancelSignal(workerId: String, cancelSignal: CancelSignal): Boolean {
        return true
    }
    
    override suspend fun sendArtifact(workerId: String, artifact: Artifact): Boolean {
        return true
    }
    
    override fun isWorkerConnected(workerId: String): Boolean {
        return true
    }
    
    override fun getConnectedWorkers(): Set<String> {
        return setOf("mock-worker")
    }
}