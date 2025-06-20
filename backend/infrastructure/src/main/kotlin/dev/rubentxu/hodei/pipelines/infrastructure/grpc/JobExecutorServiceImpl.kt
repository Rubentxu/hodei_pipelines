package dev.rubentxu.hodei.pipelines.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.application.CreateAndExecuteJobUseCase
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.mappers.JobMappers
import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.proto.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import mu.KotlinLogging

/**
 * gRPC implementation of JobExecutorService
 * Handles job execution, monitoring, and control
 */
class JobExecutorServiceImpl(
    private val createAndExecuteJobUseCase: CreateAndExecuteJobUseCase,
    private val jobExecutor: JobExecutor
) : JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase() {
    
    private val logger = KotlinLogging.logger {}
    
    /**
     * Execute a job with bidirectional streaming
     * Receives job input and parameters, returns output and status updates
     */
    override fun executeJob(requests: Flow<JobInputChunk>): Flow<JobOutputAndStatus> = flow {
        logger.info { "Starting job execution stream" }
        
        try {
            var initRequest: ExecuteJobRequest? = null
            
            // Collect the first request which should contain the job definition
            requests.collect { chunk ->
                when {
                    chunk.hasInitRequest() -> {
                        initRequest = chunk.initRequest
                        logger.info { "Received job execution request: ${chunk.initRequest.jobDefinition.name}" }
                        
                        // Convert gRPC request to domain request
                        val domainRequest = JobMappers.toDomain(chunk.initRequest)
                        
                        // Execute the job and stream results
                        createAndExecuteJobUseCase.execute(domainRequest).collect { result ->
                            val grpcOutput = JobMappers.toGrpcOutput(result)
                            emit(grpcOutput)
                        }
                    }
                    
                    chunk.hasParameter() -> {
                        // Handle job parameters (for advanced use cases)
                        logger.debug { "Received job parameter: ${chunk.parameter.name}" }
                        // For MVP, we don't need to handle runtime parameters
                    }
                }
            }
            
            if (initRequest == null) {
                logger.error { "No initial request received in job execution stream" }
                emit(JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(JobExecutionStatus.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue("unknown").build())
                        .setStatus(JobStatus.JOB_STATUS_FAILED)
                        .setMessage("No job definition received")
                        .setExitCode(1)
                        .build())
                    .build())
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error in job execution stream" }
            emit(JobOutputAndStatus.newBuilder()
                .setStatusUpdate(JobExecutionStatus.newBuilder()
                    .setJobId(JobIdentifier.newBuilder().setValue("unknown").build())
                    .setStatus(JobStatus.JOB_STATUS_FAILED)
                    .setMessage("Job execution failed: ${e.message}")
                    .setExitCode(1)
                    .build())
                .build())
        } finally {
            logger.info { "Job execution stream completed" }
        }
    }
    
    /**
     * Send control signal to a running job (cancel, pause, resume)
     */
    override suspend fun sendSignal(request: ControlSignal): JobExecutionStatus {
        logger.info { "Received control signal: ${request.type} - ${request.message}" }
        
        return try {
            // Extract job ID from the signal message (simplified for MVP)
            // In a real implementation, you'd need a proper job identifier in the signal
            val jobIdValue = extractJobIdFromMessage(request.message)
            val jobId = JobId(jobIdValue)
            
            // Convert signal to domain signal
            val domainSignal = JobMappers.toDomainSignal(request)
            
            // Send signal to job executor
            val success = jobExecutor.sendSignal(jobId, domainSignal)
            
            JobExecutionStatus.newBuilder()
                .setJobId(JobIdentifier.newBuilder().setValue(jobIdValue).build())
                .setStatus(if (success) JobStatus.JOB_STATUS_CANCELLED else JobStatus.JOB_STATUS_FAILED)
                .setMessage(if (success) "Signal sent successfully" else "Failed to send signal")
                .build()
                
        } catch (e: Exception) {
            logger.error(e) { "Error sending control signal" }
            
            JobExecutionStatus.newBuilder()
                .setJobId(JobIdentifier.newBuilder().setValue("unknown").build())
                .setStatus(JobStatus.JOB_STATUS_FAILED)
                .setMessage("Failed to send signal: ${e.message}")
                .setExitCode(1)
                .build()
        }
    }
    
    /**
     * Extract job ID from signal message
     * This is a simplified implementation for MVP
     */
    private fun extractJobIdFromMessage(message: String): String {
        // Look for patterns like "job:12345" or just return a default
        val jobIdRegex = Regex("job:([a-zA-Z0-9-]+)")
        return jobIdRegex.find(message)?.groupValues?.get(1) ?: "unknown"
    }
}