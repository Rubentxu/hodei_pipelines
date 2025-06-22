package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.proto.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * Mock gRPC JobExecutor service for integration testing
 * Simulates server behavior for comprehensive worker testing
 */
class MockJobExecutorService : JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase() {
    
    private val logger = KotlinLogging.logger {}
    private val connectedWorkers = ConcurrentHashMap<String, WorkerState>()
    private val receivedMessages = mutableListOf<TestMessage>()
    
    // Test configuration
    var simulateArtifactTransfer = true
    var simulateCacheQueries = true
    var artifactsToSend = mutableListOf<TestArtifact>()
    var jobsToSend = mutableListOf<TestJob>()
    var simulateErrors = false
    
    override fun jobExecutionChannel(requests: Flow<WorkerToServer>): Flow<ServerToWorker> = flow {
        logger.info { "Mock server: Worker connected to job execution channel" }
        
        var workerId: String? = null
        val responseChannel = Channel<ServerToWorker>(Channel.UNLIMITED)
        
        try {
            // Launch coroutine to handle worker messages
            requests.collect { workerMessage ->
                recordMessage(workerMessage)
                
                when (workerMessage.messageCase) {
                    WorkerToServer.MessageCase.HEARTBEAT -> {
                        val heartbeat = workerMessage.heartbeat
                        workerId = heartbeat.workerId.value
                        
                        logger.debug { "Mock server: Received heartbeat from worker $workerId" }
                        
                        // Update worker state
                        connectedWorkers[workerId!!] = WorkerState(
                            workerId = workerId!!,
                            status = heartbeat.status,
                            lastHeartbeat = java.time.Instant.now()
                        )
                        
                        // Send pending jobs if worker is ready
                        if (heartbeat.status == WorkerStatus.WORKER_STATUS_READY && 
                            heartbeat.activeJobsCount == 0 && 
                            jobsToSend.isNotEmpty()) {
                            
                            val job = jobsToSend.removeFirst()
                            logger.info { "Mock server: Sending job ${job.name} to worker $workerId" }
                            
                            // Send cache query first if enabled
                            if (simulateCacheQueries && job.artifacts.isNotEmpty()) {
                                val cacheQuery = ArtifactCacheQuery.newBuilder()
                                    .setJobId(job.id)
                                    .addAllArtifactIds(job.artifacts.map { it.id })
                                    .build()
                                
                                emit(ServerToWorker.newBuilder()
                                    .setCacheQuery(cacheQuery)
                                    .build())
                                
                                logger.debug { "Mock server: Sent cache query for ${job.artifacts.size} artifacts" }
                            }
                            
                            // Send artifacts if enabled
                            if (simulateArtifactTransfer && job.artifacts.isNotEmpty()) {
                                for (artifact in job.artifacts) {
                                    sendArtifactChunks(artifact, this@flow)
                                }
                            }
                            
                            // Send job request
                            val jobRequest = createJobRequest(job)
                            emit(ServerToWorker.newBuilder()
                                .setJobRequest(jobRequest)
                                .build())
                        }
                    }
                    
                    WorkerToServer.MessageCase.JOB_OUTPUT_AND_STATUS -> {
                        val jobOutput = workerMessage.jobOutputAndStatus
                        logger.info { "Mock server: Received job output/status: ${jobOutput.contentCase}" }
                        
                        // Handle job completion
                        if (jobOutput.hasStatusUpdate()) {
                            val status = jobOutput.statusUpdate.status
                            if (status == JobStatus.JOB_STATUS_SUCCESS || 
                                status == JobStatus.JOB_STATUS_FAILED) {
                                logger.info { "Mock server: Job completed with status $status" }
                            }
                        }
                    }
                    
                    WorkerToServer.MessageCase.ARTIFACT_ACK -> {
                        val ack = workerMessage.artifactAck
                        logger.info { "Mock server: Received artifact ACK for ${ack.artifactId}, success: ${ack.success}" }
                        
                        if (ack.cacheHit) {
                            logger.debug { "Mock server: Cache hit for artifact ${ack.artifactId}" }
                        }
                    }
                    
                    WorkerToServer.MessageCase.CACHE_RESPONSE -> {
                        val cacheResponse = workerMessage.cacheResponse
                        logger.info { "Mock server: Received cache response for job ${cacheResponse.jobId}" }
                        
                        val cacheHits = cacheResponse.artifactsList.count { it.cached }
                        val total = cacheResponse.artifactsList.size
                        logger.debug { "Mock server: Cache hit rate: $cacheHits/$total" }
                    }
                    
                    else -> {
                        logger.warn { "Mock server: Unknown message type: ${workerMessage.messageCase}" }
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Mock server: Error in job execution channel" }
        } finally {
            workerId?.let { id ->
                connectedWorkers.remove(id)
                logger.info { "Mock server: Worker $id disconnected" }
            }
        }
    }
    
    private suspend fun sendArtifactChunks(artifact: TestArtifact, channel: kotlinx.coroutines.flow.FlowCollector<ServerToWorker>) {
        logger.info { "Mock server: Sending artifact ${artifact.id} (${artifact.data.size} bytes)" }
        
        val chunkSize = 1024 // Small chunks for testing
        var sequence = 0
        var offset = 0
        
        while (offset < artifact.data.size) {
            val remainingBytes = artifact.data.size - offset
            val currentChunkSize = minOf(chunkSize, remainingBytes)
            val chunkData = artifact.data.sliceArray(offset until offset + currentChunkSize)
            val isLast = (offset + currentChunkSize) >= artifact.data.size
            
            val artifactChunk = ArtifactChunk.newBuilder()
                .setArtifactId(artifact.id)
                .setData(com.google.protobuf.ByteString.copyFrom(chunkData))
                .setSequence(sequence)
                .setIsLast(isLast)
                .setCompression(artifact.compression)
                .setOriginalSize(artifact.originalSize)
                .build()
            
            channel.emit(ServerToWorker.newBuilder()
                .setArtifactChunk(artifactChunk)
                .build())
            
            logger.debug { "Mock server: Sent chunk $sequence for artifact ${artifact.id} (${chunkData.size} bytes, last: $isLast)" }
            
            offset += currentChunkSize
            sequence++
            
            // Small delay between chunks
            delay(5)
        }
    }
    
    private fun createJobRequest(job: TestJob): ExecuteJobRequest {
        val jobDefinition = JobDefinition.newBuilder()
            .setId(JobIdentifier.newBuilder().setValue(job.id).build())
            .setName(job.name)
            .setScript(ScriptPayload.newBuilder().setContent(job.script).build())
            .build()
        
        val artifacts = job.artifacts.map { artifact ->
            Artifact.newBuilder()
                .setId(artifact.id)
                .setName(artifact.name)
                .setType(artifact.type)
                .setSize(artifact.data.size.toLong())
                .setChecksum(artifact.checksum)
                .setPath(artifact.path)
                .setCompressTransfer(artifact.compression != CompressionType.COMPRESSION_NONE)
                .build()
        }
        
        val config = JobExecutionMetadata.newBuilder()
            .setTimeoutSeconds(30)
            .setCaptureStdout(true)
            .setCaptureStderr(true)
            .addAllRequiredArtifacts(artifacts)
            .build()
        
        return ExecuteJobRequest.newBuilder()
            .setJobDefinition(jobDefinition)
            .setConfig(config)
            .build()
    }
    
    private fun recordMessage(message: WorkerToServer) {
        receivedMessages.add(TestMessage(
            timestamp = java.time.Instant.now(),
            messageType = message.messageCase.name,
            content = message.toString()
        ))
    }
    
    // Test utilities
    fun addJob(job: TestJob) {
        jobsToSend.add(job)
    }
    
    fun getConnectedWorkers(): Map<String, WorkerState> = connectedWorkers.toMap()
    
    fun getReceivedMessages(): List<TestMessage> = receivedMessages.toList()
    
    fun clearHistory() {
        receivedMessages.clear()
        connectedWorkers.clear()
    }
    
    fun waitForWorkerConnection(workerId: String, timeoutMs: Long = 5000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (connectedWorkers.containsKey(workerId)) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
    
    fun waitForMessageType(messageType: String, timeoutMs: Long = 5000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (receivedMessages.any { it.messageType == messageType }) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
    
    fun forceDisconnectWorker(workerId: String) {
        connectedWorkers.remove(workerId)
        logger.info { "Mock server: Forced disconnect of worker $workerId" }
    }
    
    fun waitForWorkerDisconnection(workerId: String, timeoutMs: Long = 3000): Boolean {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start < timeoutMs) {
            if (!connectedWorkers.containsKey(workerId)) {
                return true
            }
            Thread.sleep(10)
        }
        return false
    }
}

/**
 * Test data classes
 */
data class TestJob(
    val id: String,
    val name: String,
    val script: String,
    val artifacts: List<TestArtifact> = emptyList()
)

data class TestArtifact(
    val id: String,
    val name: String,
    val type: ArtifactType,
    val data: ByteArray,
    val checksum: String,
    val path: String,
    val compression: CompressionType = CompressionType.COMPRESSION_NONE,
    val originalSize: Int = data.size
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as TestArtifact
        return id == other.id
    }
    
    override fun hashCode(): Int = id.hashCode()
}

data class WorkerState(
    val workerId: String,
    val status: WorkerStatus,
    val lastHeartbeat: java.time.Instant
)

data class TestMessage(
    val timestamp: java.time.Instant,
    val messageType: String,
    val content: String
)