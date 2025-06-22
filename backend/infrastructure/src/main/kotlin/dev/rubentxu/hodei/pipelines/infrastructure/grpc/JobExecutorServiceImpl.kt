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
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream

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
    
    // Cache optimization: Jobs waiting for cache verification
    private val pendingCacheVerification = mutableMapOf<String, PendingJob>()
    
    // Transfer metrics tracking
    private val transferMetrics = TransferMetricsTracker()

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
                            
                            // First, check cache and transfer artifacts if any
                            if (nextJob.config.requiredArtifactsList.isNotEmpty()) {
                                logger.info { "Processing ${nextJob.config.requiredArtifactsList.size} artifacts for job ${nextJob.jobDefinition.name}" }
                                
                                // Phase 2: Optimized cache-aware artifact transfer
                                logger.debug { "Starting optimized artifact transfer for job ${nextJob.jobDefinition.name}" }
                                
                                val cacheQuery = ArtifactCacheQuery.newBuilder()
                                    .addAllArtifactIds(nextJob.config.requiredArtifactsList.map { it.id })
                                    .setJobId(nextJob.jobDefinition.id.value)
                                    .build()
                                
                                // Store job as pending cache verification
                                pendingCacheVerification[nextJob.jobDefinition.id.value] = PendingJob(
                                    jobRequest = nextJob,
                                    workerId = workerId!!,
                                    channel = this@flow,
                                    requestTime = java.time.Instant.now()
                                )
                                
                                // Send cache query
                                emit(ServerToWorker.newBuilder()
                                    .setCacheQuery(cacheQuery)
                                    .build())
                                
                                logger.debug { "Cache query sent for job ${nextJob.jobDefinition.name}, waiting for response..." }
                            } else {
                                // No artifacts required, send job immediately
                                logger.debug { "No artifacts required for job ${nextJob.jobDefinition.name}, sending job directly" }
                                emit(ServerToWorker.newBuilder()
                                    .setJobRequest(nextJob)
                                    .build())
                            }
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
                            val cacheMsg = if (artifactAck.cacheHit) " (cache hit)" else " (transferred)"
                            logger.info { "Artifact ${artifactAck.artifactId} handled successfully$cacheMsg on worker $workerId" }
                            
                            // Log cache metrics
                            if (artifactAck.hasCacheStatus()) {
                                val cacheStatus = artifactAck.cacheStatus
                                logger.debug { "Worker cache: ${cacheStatus.cachedArtifactsCount} artifacts, ${cacheStatus.cacheSizeBytes} bytes" }
                            }
                            
                            if (artifactAck.calculatedChecksum.isNotEmpty()) {
                                logger.debug { "Worker calculated checksum: ${artifactAck.calculatedChecksum}" }
                            }
                        } else {
                            logger.error { "Artifact ${artifactAck.artifactId} transfer failed: ${artifactAck.message}" }
                        }
                    }
                    
                    WorkerToServer.MessageCase.CACHE_RESPONSE -> {
                        val cacheResponse = workerMessage.cacheResponse
                        logger.info { "Cache response for job ${cacheResponse.jobId}: ${cacheResponse.artifactsList.size} artifacts checked" }
                        
                        val cacheHits = cacheResponse.artifactsList.count { !it.needsTransfer }
                        val totalArtifacts = cacheResponse.artifactsList.size
                        val hitRate = if (totalArtifacts > 0) (cacheHits * 100) / totalArtifacts else 0
                        
                        logger.info { "Cache hit rate: $hitRate% ($cacheHits/$totalArtifacts)" }
                        
                        // Track cache metrics
                        transferMetrics.recordCacheQuery(cacheResponse.jobId, totalArtifacts, cacheHits)
                        
                        // Process pending job with optimized artifact transfer
                        val pendingJob = pendingCacheVerification[cacheResponse.jobId]
                        if (pendingJob != null) {
                            // Remove from pending list
                            pendingCacheVerification.remove(cacheResponse.jobId)
                            
                            // Transfer only artifacts that need it
                            val artifactsToTransfer = cacheResponse.artifactsList
                                .filter { it.needsTransfer }
                                .mapNotNull { artifactInfo ->
                                    pendingJob.jobRequest.config.requiredArtifactsList
                                        .find { it.id == artifactInfo.artifactId }
                                }
                            
                            if (artifactsToTransfer.isNotEmpty()) {
                                logger.info { "Transferring ${artifactsToTransfer.size}/${totalArtifacts} artifacts for job ${cacheResponse.jobId}" }
                                
                                // Track transfer start
                                val transferStartTime = java.time.Instant.now()
                                
                                for (artifact in artifactsToTransfer) {
                                    transferArtifactToWorker(artifact, pendingJob.channel)
                                    transferMetrics.recordArtifactTransfer(artifact.id, artifact.size)
                                }
                                
                                // Record transfer completion time
                                transferMetrics.recordTransferSession(cacheResponse.jobId, transferStartTime, artifactsToTransfer.size)
                                
                                // Small delay between artifacts
                                delay(50)
                            } else {
                                logger.info { "All artifacts cached! Skipping transfer for job ${cacheResponse.jobId}" }
                            }
                            
                            // Send the job request after artifact handling
                            pendingJob.channel.emit(ServerToWorker.newBuilder()
                                .setJobRequest(pendingJob.jobRequest)
                                .build())
                                
                            // Log cache optimization metrics
                            cacheResponse.artifactsList.forEach { artifactInfo ->
                                if (artifactInfo.cached) {
                                    logger.debug { "✓ Cache hit: ${artifactInfo.artifactId} (checksum: ${artifactInfo.cachedChecksum})" }
                                } else {
                                    logger.debug { "⚡ Transferring: ${artifactInfo.artifactId}" }
                                }
                            }
                        } else {
                            logger.warn { "Received cache response for unknown job: ${cacheResponse.jobId}" }
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
     * Get transfer metrics
     */
    fun getTransferMetrics(): TransferMetrics {
        return transferMetrics.getMetrics()
    }
    
    /**
     * Transfer artifact to worker in chunks (Fase 2: With compression)
     */
    private suspend fun transferArtifactToWorker(
        artifact: Artifact, 
        channel: kotlinx.coroutines.flow.FlowCollector<ServerToWorker>
    ) {
        logger.info { "Starting transfer of artifact ${artifact.id} (${artifact.name})" }
        
        try {
            // In a real implementation, you'd read the artifact from storage
            val originalData = createDemoArtifactData(artifact)
            
            // Apply compression if requested
            val (finalData, compressionType, originalSize) = if (artifact.compressTransfer) {
                val compressed = compressData(originalData, CompressionType.COMPRESSION_GZIP)
                val compressionRatio = ((originalData.size - compressed.size) * 100) / originalData.size
                logger.info { "Compressed artifact ${artifact.id}: ${originalData.size} → ${compressed.size} bytes (${compressionRatio}% reduction)" }
                Triple(compressed, CompressionType.COMPRESSION_GZIP, originalData.size)
            } else {
                Triple(originalData, CompressionType.COMPRESSION_NONE, originalData.size)
            }
            
            val chunkSize = 64 * 1024 // 64KB chunks
            var sequence = 0
            var offset = 0
            
            while (offset < finalData.size) {
                val remainingBytes = finalData.size - offset
                val currentChunkSize = minOf(chunkSize, remainingBytes)
                val chunkData = finalData.sliceArray(offset until offset + currentChunkSize)
                val isLast = (offset + currentChunkSize) >= finalData.size
                
                val artifactChunk = ArtifactChunk.newBuilder()
                    .setArtifactId(artifact.id)
                    .setData(com.google.protobuf.ByteString.copyFrom(chunkData))
                    .setSequence(sequence)
                    .setIsLast(isLast)
                    .setCompression(compressionType)
                    .setOriginalSize(originalSize)
                    .build()
                
                channel.emit(ServerToWorker.newBuilder()
                    .setArtifactChunk(artifactChunk)
                    .build())
                
                logger.debug { "Sent chunk $sequence for artifact ${artifact.id} (${chunkData.size} bytes, compression: $compressionType, last: $isLast)" }
                
                offset += currentChunkSize
                sequence++
                
                // Small delay between chunks
                delay(10)
            }
            
            logger.info { "Completed transfer of artifact ${artifact.id} in $sequence chunks (compression: $compressionType)" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to transfer artifact ${artifact.id}" }
            throw e
        }
    }
    
    /**
     * Create demo artifact data for testing (Fase 2: More realistic size)
     */
    private fun createDemoArtifactData(artifact: Artifact): ByteArray {
        // Create larger demo data to better test compression
        val baseContent = """
            Artifact: ${artifact.name}
            Type: ${artifact.type}
            ID: ${artifact.id}
            Path: ${artifact.path}
            Version: ${artifact.version}
            
            This is a demo artifact created for testing the transfer functionality.
            In a real implementation, this would be read from actual storage.
            
            Generated at: ${java.time.Instant.now()}
            
            """.trimIndent()
        
        // Add repetitive content to make compression more effective
        val repeatedContent = """
            # Sample configuration data
            database.host=localhost
            database.port=5432
            database.name=hodei_pipelines
            database.user=hodei_user
            database.password=secure_password
            
            # Server configuration
            server.port=8080
            server.host=0.0.0.0
            server.threads=10
            
            # Logging configuration
            logging.level=INFO
            logging.file=/var/log/hodei.log
            logging.max_size=100MB
            
            """.trimIndent()
        
        // Repeat the content to simulate a larger file
        val fullContent = baseContent + (1..20).joinToString("") { "\n$repeatedContent" }
        
        return fullContent.toByteArray(Charsets.UTF_8)
    }
    
    /**
     * Compress data using specified compression type
     */
    private fun compressData(data: ByteArray, compressionType: CompressionType): ByteArray {
        return when (compressionType) {
            CompressionType.COMPRESSION_GZIP -> {
                val outputStream = ByteArrayOutputStream()
                GZIPOutputStream(outputStream).use { gzipStream ->
                    gzipStream.write(data)
                }
                outputStream.toByteArray()
            }
            CompressionType.COMPRESSION_ZSTD -> {
                // For now, fallback to GZIP (ZSTD would require additional dependency)
                logger.warn { "ZSTD compression not implemented, falling back to GZIP" }
                compressData(data, CompressionType.COMPRESSION_GZIP)
            }
            else -> data
        }
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

/**
 * Represents a job pending cache verification before artifact transfer
 */
data class PendingJob(
    val jobRequest: ExecuteJobRequest,
    val workerId: String,
    val channel: kotlinx.coroutines.flow.FlowCollector<ServerToWorker>,
    val requestTime: java.time.Instant
)

/**
 * Transfer metrics tracking
 */
class TransferMetricsTracker {
    private val cacheQueries = mutableListOf<CacheQueryMetric>()
    private val artifactTransfers = mutableListOf<ArtifactTransferMetric>()
    private val transferSessions = mutableListOf<TransferSessionMetric>()
    
    fun recordCacheQuery(jobId: String, totalArtifacts: Int, cacheHits: Int) {
        cacheQueries.add(CacheQueryMetric(
            jobId = jobId,
            totalArtifacts = totalArtifacts,
            cacheHits = cacheHits,
            timestamp = java.time.Instant.now()
        ))
    }
    
    fun recordArtifactTransfer(artifactId: String, sizeBytes: Long) {
        artifactTransfers.add(ArtifactTransferMetric(
            artifactId = artifactId,
            sizeBytes = sizeBytes,
            timestamp = java.time.Instant.now()
        ))
    }
    
    fun recordTransferSession(jobId: String, startTime: java.time.Instant, artifactCount: Int) {
        transferSessions.add(TransferSessionMetric(
            jobId = jobId,
            startTime = startTime,
            endTime = java.time.Instant.now(),
            artifactCount = artifactCount
        ))
    }
    
    fun getMetrics(): TransferMetrics {
        val totalCacheQueries = cacheQueries.size
        val totalCacheHits = cacheQueries.sumOf { it.cacheHits }
        val totalArtifactsQueried = cacheQueries.sumOf { it.totalArtifacts }
        val averageCacheHitRate = if (totalArtifactsQueried > 0) (totalCacheHits * 100.0) / totalArtifactsQueried else 0.0
        
        val totalBytesTransferred = artifactTransfers.sumOf { it.sizeBytes }
        val averageTransferTime = if (transferSessions.isNotEmpty()) {
            transferSessions.map { java.time.Duration.between(it.startTime, it.endTime).toMillis() }.average()
        } else 0.0
        
        return TransferMetrics(
            totalCacheQueries = totalCacheQueries,
            totalCacheHits = totalCacheHits,
            averageCacheHitRate = averageCacheHitRate,
            totalArtifactsTransferred = artifactTransfers.size,
            totalBytesTransferred = totalBytesTransferred,
            averageTransferTimeMs = averageTransferTime,
            transferSessions = transferSessions.size
        )
    }
}

/**
 * Cache query metric
 */
data class CacheQueryMetric(
    val jobId: String,
    val totalArtifacts: Int,
    val cacheHits: Int,
    val timestamp: java.time.Instant
)

/**
 * Artifact transfer metric
 */
data class ArtifactTransferMetric(
    val artifactId: String,
    val sizeBytes: Long,
    val timestamp: java.time.Instant
)

/**
 * Transfer session metric
 */
data class TransferSessionMetric(
    val jobId: String,
    val startTime: java.time.Instant,
    val endTime: java.time.Instant,
    val artifactCount: Int
)

/**
 * Aggregated transfer metrics
 */
data class TransferMetrics(
    val totalCacheQueries: Int,
    val totalCacheHits: Int,
    val averageCacheHitRate: Double,
    val totalArtifactsTransferred: Int,
    val totalBytesTransferred: Long,
    val averageTransferTimeMs: Double,
    val transferSessions: Int
)