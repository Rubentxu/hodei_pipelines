package dev.rubentxu.hodei.pipelines.infrastructure.grpc

import com.google.protobuf.Empty
import dev.rubentxu.hodei.pipelines.application.RegisterWorkerUseCase
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.mappers.WorkerMappers
import dev.rubentxu.hodei.pipelines.port.WorkerRepository
import dev.rubentxu.hodei.pipelines.proto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging

/**
 * gRPC implementation of WorkerManagementService
 * Handles worker registration, heartbeat, and lifecycle management
 */
class WorkerManagementServiceImpl(
    private val registerWorkerUseCase: RegisterWorkerUseCase,
    private val workerRepository: WorkerRepository
) : WorkerManagementServiceGrpcKt.WorkerManagementServiceCoroutineImplBase() {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * Register a worker in the system
     */
    override suspend fun registerWorker(request: WorkerRegistrationRequest): WorkerRegistrationResponse {
        logger.info { "Registering worker: ${request.workerName} (${request.workerId.value})" }
        
        return try {
            // Convert gRPC request to domain request
            val domainRequest = WorkerMappers.toDomain(request)
            
            // Execute use case
            val domainResponse = registerWorkerUseCase.execute(domainRequest)
            
            // Convert domain response to gRPC response
            val grpcResponse = WorkerMappers.toGrpcResponse(domainResponse)
            
            if (domainResponse.success) {
                logger.info { "Worker registered successfully: ${domainResponse.workerId?.value}" }
            } else {
                logger.warn { "Worker registration failed: ${domainResponse.errorMessage}" }
            }
            
            grpcResponse
            
        } catch (e: Exception) {
            logger.error(e) { "Error registering worker: ${request.workerName}" }
            
            WorkerRegistrationResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Internal server error: ${e.message}")
                .setSessionToken("")
                .setHeartbeatIntervalSeconds(30)
                .build()
        }
    }
    
    
    /**
     * Get information about a specific worker
     */
    override suspend fun getWorkerInfo(request: WorkerIdentifier): WorkerInfo {
        logger.debug { "Getting worker info for: ${request.value}" }
        
        return try {
            val workerId = WorkerId(request.value)
            val workerResult = workerRepository.findById(workerId)
            
            if (workerResult.isSuccess) {
                val worker = workerResult.getOrThrow()
                WorkerMappers.toGrpcWorkerInfo(worker)
            } else {
                logger.warn { "Worker not found: ${request.value}" }
                throw Exception("Worker not found: ${request.value}")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error getting worker info: ${request.value}" }
            throw e
        }
    }
    
    /**
     * List all registered workers
     */
    override fun listWorkers(request: Empty): Flow<WorkerInfo> = flow {
        logger.debug { "Listing all workers" }
        
        try {
            val workersFlow = workerRepository.findAll()
            
            workersFlow.collect { worker ->
                emit(WorkerMappers.toGrpcWorkerInfo(worker))
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error listing workers" }
        }
    }
    
    /**
     * Unregister a worker from the system
     */
    override suspend fun unregisterWorker(request: WorkerIdentifier): Empty {
        logger.info { "Unregistering worker: ${request.value}" }
        
        try {
            val workerId = WorkerId(request.value)
            
            // Find worker first
            val workerResult = workerRepository.findById(workerId)
            if (workerResult.isSuccess) {
                val worker = workerResult.getOrThrow()
                
                // Mark worker as offline
                val offlineWorker = worker.goOffline()
                
                // Save the updated status (in a real implementation, you might actually delete the worker)
                workerRepository.save(offlineWorker)
                
                logger.info { "Worker unregistered successfully: ${request.value}" }
            } else {
                logger.warn { "Worker not found for unregistration: ${request.value}" }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error unregistering worker: ${request.value}" }
        }
        
        return Empty.getDefaultInstance()
    }
}