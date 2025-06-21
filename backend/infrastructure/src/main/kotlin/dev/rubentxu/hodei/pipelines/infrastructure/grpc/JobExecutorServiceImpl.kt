package dev.rubentxu.hodei.pipelines.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.application.CreateAndExecuteJobUseCase
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.mappers.JobMappers
import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.proto.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import mu.KotlinLogging
import java.io.File
import java.security.MessageDigest

/**
 * gRPC implementation of JobExecutorService
 * Handles job execution, monitoring, and control
 */
class JobExecutorServiceImpl(
    private val createAndExecuteJobUseCase: CreateAndExecuteJobUseCase,
    private val jobExecutor: JobExecutor
) : JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase() {

    private val logger = KotlinLogging.logger {}
    private val activeWorkers = mutableMapOf<String, WorkerSession>()
    private val jobQueue = mutableListOf<ExecuteJobRequest>()

    /**
     * Establishes a bidirectional channel for job execution.
     * The server receives messages from workers (like heartbeats or job status)
     * and sends messages to workers (like job requests or control signals).
     */
    override fun jobExecutionChannel(requests: Flow<WorkerToServer>): Flow<ServerToWorker> = flow {
        logger.info { "Job execution channel opened for worker" }
        
        var workerId: String? = null
        
        try {
            requests.collect { workerMessage ->
                when (workerMessage.messageCase) {
                    WorkerToServer.MessageCase.HEARTBEAT -> {
                        val heartbeat = workerMessage.heartbeat
                        workerId = heartbeat.workerId.value
                        
                        logger.debug { "Received heartbeat from worker: $workerId" }
                        
                        // Update worker session
                        activeWorkers[workerId!!] = WorkerSession(
                            workerId = workerId!!,
                            status = heartbeat.status,
                            activeJobsCount = heartbeat.activeJobsCount,
                            lastHeartbeat = java.time.Instant.now(),
                            channel = this@flow
                        )
                        
                        // Check if worker can take more jobs
                        if (heartbeat.status == WorkerStatus.WORKER_STATUS_READY && 
                            heartbeat.activeJobsCount == 0 && 
                            jobQueue.isNotEmpty()) {
                            
                            val nextJob = jobQueue.removeFirst()
                            logger.info { "Assigning job ${nextJob.jobDefinition.name} to worker $workerId" }
                            
                            // First, send artifacts if any
                            if (nextJob.config.requiredArtifactsList.isNotEmpty()) {
                                logger.info { "Transferring ${nextJob.config.requiredArtifactsList.size} artifacts for job ${nextJob.jobDefinition.name}" }
                                
                                for (artifact in nextJob.config.requiredArtifactsList) {
                                    transferArtifactToWorker(artifact, this@flow)
                                }
                                
                                // Small delay to ensure artifacts are processed before job starts
                                delay(100)
                            }
                            
                            // Then send the job request
                            emit(ServerToWorker.newBuilder()
                                .setJobRequest(nextJob)
                                .build())
                        }
                    }
                    
                    WorkerToServer.MessageCase.JOB_OUTPUT_AND_STATUS -> {
                        val jobOutput = workerMessage.jobOutputAndStatus
                        
                        when (jobOutput.contentCase) {
                            JobOutputAndStatus.ContentCase.OUTPUT_CHUNK -> {
                                val chunk = jobOutput.outputChunk
                                logger.debug { "Received output chunk from job: ${chunk.jobId.value}" }
                                // Here you would typically store or forward the output
                            }
                            
                            JobOutputAndStatus.ContentCase.STATUS_UPDATE -> {
                                val statusUpdate = jobOutput.statusUpdate
                                logger.info { "Job ${statusUpdate.jobId.value} status: ${statusUpdate.status}" }
                                
                                // Update job status and handle completion
                                if (statusUpdate.status == JobStatus.JOB_STATUS_SUCCESS ||
                                    statusUpdate.status == JobStatus.JOB_STATUS_FAILED ||
                                    statusUpdate.status == JobStatus.JOB_STATUS_CANCELLED) {
                                    
                                    logger.info { "Job ${statusUpdate.jobId.value} completed with status: ${statusUpdate.status}" }
                                    
                                    // Worker is now available for more jobs
                                    if (jobQueue.isNotEmpty() && workerId != null) {
                                        val nextJob = jobQueue.removeFirst()
                                        logger.info { "Assigning next job ${nextJob.jobDefinition.name} to worker $workerId" }
                                        
                                        // Transfer artifacts first if needed
                                        if (nextJob.config.requiredArtifactsList.isNotEmpty()) {
                                            logger.info { "Transferring ${nextJob.config.requiredArtifactsList.size} artifacts for next job" }
                                            
                                            for (artifact in nextJob.config.requiredArtifactsList) {
                                                transferArtifactToWorker(artifact, this@flow)
                                            }
                                            
                                            delay(100)
                                        }
                                        
                                        emit(ServerToWorker.newBuilder()
                                            .setJobRequest(nextJob)
                                            .build())
                                    }
                                }
                            }
                            
                            else -> {
                                logger.warn { "Received unknown job output content type: ${jobOutput.contentCase}" }
                            }
                        }
                    }
                    
                    WorkerToServer.MessageCase.ARTIFACT_ACK -> {
                        val artifactAck = workerMessage.artifactAck
                        if (artifactAck.success) {
                            logger.info { "Artifact ${artifactAck.artifactId} transferred successfully to worker $workerId" }
                            // Verify checksum matches
                            if (artifactAck.calculatedChecksum.isNotEmpty()) {
                                // In a real implementation, you'd verify against the expected checksum
                                logger.debug { "Worker calculated checksum: ${artifactAck.calculatedChecksum}" }
                            }
                        } else {
                            logger.error { "Artifact ${artifactAck.artifactId} transfer failed: ${artifactAck.message}" }
                            // Handle transfer failure - could retry or fail the job
                        }
                    }
                    
                    else -> {
                        logger.warn { "Received unknown message type from worker: ${workerMessage.messageCase}" }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error in job execution channel for worker: $workerId" }
        } finally {
            // Clean up worker session when channel closes
            workerId?.let { id ->
                activeWorkers.remove(id)
                logger.info { "Worker $id disconnected, removed from active workers" }
            }
        }
    }
    
    /**
     * Queue a job for execution
     */
    fun queueJob(jobRequest: ExecuteJobRequest) {
        logger.info { "Queuing job: ${jobRequest.jobDefinition.name}" }
        
        // Try to assign to an available worker immediately
        val availableWorker = activeWorkers.values.find { worker ->
            worker.status == WorkerStatus.WORKER_STATUS_READY && worker.activeJobsCount == 0
        }
        
        if (availableWorker != null) {
            logger.info { "Assigning job ${jobRequest.jobDefinition.name} immediately to worker ${availableWorker.workerId}" }
            
            // This would require a way to send messages to specific workers
            // For now, we'll add to queue and let the next heartbeat handle it
            jobQueue.add(jobRequest)
        } else {
            logger.info { "No available workers, adding job to queue: ${jobRequest.jobDefinition.name}" }
            jobQueue.add(jobRequest)
        }
    }
    
    /**
     * Get current system statistics
     */
    fun getSystemStats(): SystemStats {
        return SystemStats(
            activeWorkers = activeWorkers.size,
            queuedJobs = jobQueue.size,
            totalJobs = jobQueue.size + activeWorkers.values.sumOf { it.activeJobsCount }
        )
    }
    
    /**
     * Transfer artifact to worker in chunks (Fase 1: Basic implementation)
     */
    private suspend fun transferArtifactToWorker(
        artifact: Artifact, 
        channel: kotlinx.coroutines.flow.FlowCollector<ServerToWorker>
    ) {
        logger.info { "Starting transfer of artifact ${artifact.id} (${artifact.name})" }
        
        try {
            // In a real implementation, you'd read the artifact from storage
            // For now, we'll simulate with demo data
            val artifactData = createDemoArtifactData(artifact)
            
            val chunkSize = 64 * 1024 // 64KB chunks for Fase 1
            var sequence = 0
            var offset = 0
            
            while (offset < artifactData.size) {
                val remainingBytes = artifactData.size - offset
                val currentChunkSize = minOf(chunkSize, remainingBytes)
                val chunkData = artifactData.sliceArray(offset until offset + currentChunkSize)
                val isLast = (offset + currentChunkSize) >= artifactData.size
                
                val artifactChunk = ArtifactChunk.newBuilder()
                    .setArtifactId(artifact.id)
                    .setData(com.google.protobuf.ByteString.copyFrom(chunkData))
                    .setSequence(sequence)
                    .setIsLast(isLast)
                    .build()
                
                channel.emit(ServerToWorker.newBuilder()
                    .setArtifactChunk(artifactChunk)
                    .build())
                
                logger.debug { "Sent chunk $sequence for artifact ${artifact.id} (${chunkData.size} bytes, last: $isLast)" }
                
                offset += currentChunkSize
                sequence++
                
                // Small delay between chunks to avoid overwhelming the worker
                delay(10)
            }
            
            logger.info { "Completed transfer of artifact ${artifact.id} in $sequence chunks" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to transfer artifact ${artifact.id}" }
            throw e
        }
    }
    
    /**
     * Create demo artifact data for testing (Fase 1)
     */
    private fun createDemoArtifactData(artifact: Artifact): ByteArray {
        // For demonstration, create a simple text file with artifact info
        val content = """
            Artifact: ${artifact.name}
            Type: ${artifact.type}
            ID: ${artifact.id}
            Path: ${artifact.path}
            
            This is a demo artifact created for testing the transfer functionality.
            In a real implementation, this would be read from actual storage.
            
            Generated at: ${java.time.Instant.now()}
        """.trimIndent()
        
        return content.toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Calculate SHA-256 checksum
     */
    private fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}

/**
 * Represents an active worker session
 */
data class WorkerSession(
    val workerId: String,
    val status: WorkerStatus,
    val activeJobsCount: Int,
    val lastHeartbeat: java.time.Instant,
    val channel: kotlinx.coroutines.flow.FlowCollector<ServerToWorker>
)

/**
 * System statistics
 */
data class SystemStats(
    val activeWorkers: Int,
    val queuedJobs: Int,
    val totalJobs: Int
)