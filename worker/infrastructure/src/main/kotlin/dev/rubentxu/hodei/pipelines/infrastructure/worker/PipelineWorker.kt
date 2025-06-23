package dev.rubentxu.hodei.pipelines.infrastructure.worker

import com.google.protobuf.ByteString
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.proto.*
import dev.rubentxu.hodei.pipelines.proto.JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub
import dev.rubentxu.hodei.pipelines.proto.WorkerManagementServiceGrpcKt.WorkerManagementServiceCoroutineStub
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import dev.rubentxu.hodei.pipelines.domain.job.Job as DomainJob
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition as DomainJobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobId as DomainJobId
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload as DomainJobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus as DomainJobStatus
import dev.rubentxu.hodei.pipelines.proto.JobDefinition as GrpcJobDefinition
import dev.rubentxu.hodei.pipelines.proto.JobExecutionStatus as GrpcJobExecutionStatus
import dev.rubentxu.hodei.pipelines.proto.WorkerStatus as GrpcWorkerStatus
import dev.rubentxu.hodei.pipelines.proto.WorkerIdentifier as GrpcWorkerIdentifier
import dev.rubentxu.hodei.pipelines.proto.WorkerRegistrationRequest as GrpcWorkerRegistrationRequest
import dev.rubentxu.hodei.pipelines.proto.WorkerHeartbeat as GrpcWorkerHeartbeat
import dev.rubentxu.hodei.pipelines.proto.WorkerToServer as GrpcWorkerToServer
import dev.rubentxu.hodei.pipelines.proto.ServerToWorker as GrpcServerToWorker
import dev.rubentxu.hodei.pipelines.proto.ExecuteJobRequest as GrpcExecuteJobRequest
import dev.rubentxu.hodei.pipelines.proto.JobOutputAndStatus as GrpcJobOutputAndStatus
import dev.rubentxu.hodei.pipelines.proto.JobOutputChunk as GrpcJobOutputChunk
import dev.rubentxu.hodei.pipelines.proto.ArtifactChunk as GrpcArtifactChunk
import dev.rubentxu.hodei.pipelines.proto.ArtifactAck as GrpcArtifactAck
import dev.rubentxu.hodei.pipelines.proto.ArtifactCacheQuery as GrpcArtifactCacheQuery
import dev.rubentxu.hodei.pipelines.proto.ArtifactCacheResponse as GrpcArtifactCacheResponse
import dev.rubentxu.hodei.pipelines.proto.ArtifactCacheInfo as GrpcArtifactCacheInfo
import dev.rubentxu.hodei.pipelines.proto.ArtifactCacheStatus as GrpcArtifactCacheStatus
import dev.rubentxu.hodei.pipelines.proto.CompressionType as GrpcCompressionType
import dev.rubentxu.hodei.pipelines.proto.JobIdentifier as GrpcJobIdentifier
import dev.rubentxu.hodei.pipelines.proto.JobStatus as GrpcJobStatus

private val logger = KotlinLogging.logger {}

class PipelineWorker(
    private val workerId: String,
    private val workerName: String,
    private val serverHost: String,
    private val serverPort: Int,
    private val scriptExecutor: PipelineScriptExecutor
) : Closeable {

    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(serverHost, serverPort)
        .usePlaintext()
        .build()

    private val jobExecutorStub: JobExecutorServiceCoroutineStub = JobExecutorServiceCoroutineStub(channel)
    private val workerManagementStub: WorkerManagementServiceCoroutineStub =
        WorkerManagementServiceCoroutineStub(channel)

    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Artifact management (Fase 2: With cache)
    private val artifactCache = mutableMapOf<String, ArtifactDownload>()
    private val artifactDirectory = File(System.getProperty("java.io.tmpdir"), "hodei-artifacts-$workerId")
    private val persistentArtifacts = mutableMapOf<String, CachedArtifact>()

    init {
        // Create artifact directory
        if (!artifactDirectory.exists()) {
            artifactDirectory.mkdirs()
            logger.info { "Created artifact directory: ${artifactDirectory.absolutePath}" }
        }

        // Load existing artifacts from cache
        loadPersistedArtifacts()
    }

    fun start() {
        logger.info { "Starting worker $workerId..." }
        workerScope.launch {
            registerWorker()
            manageCommunicationChannel()
        }
    }

    private suspend fun registerWorker() {
        try {
            val request = GrpcWorkerRegistrationRequest.newBuilder()
                .setWorkerId(GrpcWorkerIdentifier.newBuilder().setValue(workerId).build())
                .setWorkerName(workerName)
                .putAllCapabilities(mapOf("os" to System.getProperty("os.name")))
                .build()
            val response = workerManagementStub.registerWorker(request)
            if (response.success) {
                logger.info { "Worker $workerId registered successfully. Session token: ${response.sessionToken}" }
            } else {
                logger.error { "Worker registration failed: ${response.message}" }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error registering worker" }
        }
    }

    private suspend fun manageCommunicationChannel() {
        val toServerChannel = Channel<GrpcWorkerToServer>(Channel.UNLIMITED)
        coroutineScope {
            // Heartbeat coroutine
            launch {
                while (isActive) {
                    val heartbeat = GrpcWorkerHeartbeat.newBuilder()
                        .setWorkerId(GrpcWorkerIdentifier.newBuilder().setValue(workerId).build())
                        .setStatus(GrpcWorkerStatus.WORKER_STATUS_READY)
                        .build()
                    val message = GrpcWorkerToServer.newBuilder().setHeartbeat(heartbeat).build()
                    toServerChannel.send(message)
                    delay(10000) // 10 seconds
                }
            }

            // Main communication listener
            launch {
                try {
                    val fromServerFlow = jobExecutorStub.jobExecutionChannel(toServerChannel.receiveAsFlow())
                    fromServerFlow.collect { serverMessage ->
                        when (serverMessage.messageCase) {
                            GrpcServerToWorker.MessageCase.JOB_REQUEST -> {
                                val jobRequest = serverMessage.jobRequest
                                logger.info { "Received job request: ${jobRequest.jobDefinition.name}" }
                                launch {
                                    executeJobRequest(jobRequest)
                                        .map { event -> convertEventToWorkerMessage(event) }
                                        .collect { message -> toServerChannel.send(message) }
                                }
                            }

                            GrpcServerToWorker.MessageCase.CONTROL_SIGNAL -> {
                                val controlSignal = serverMessage.controlSignal
                                logger.info { "Received control signal: ${controlSignal.type}" }
                                // Handle control signals (e.g., cancel job)
                            }

                            GrpcServerToWorker.MessageCase.ARTIFACT_CHUNK -> {
                                val artifactChunk = serverMessage.artifactChunk
                                logger.debug { "Received artifact chunk for ${artifactChunk.artifactId}, sequence ${artifactChunk.sequence}" }

                                launch {
                                    val ack = handleArtifactChunk(artifactChunk)
                                    val ackMessage = GrpcWorkerToServer.newBuilder()
                                        .setArtifactAck(ack)
                                        .build()
                                    toServerChannel.send(ackMessage)
                                }
                            }

                            GrpcServerToWorker.MessageCase.CACHE_QUERY -> {
                                val cacheQuery = serverMessage.cacheQuery
                                logger.debug { "Received cache query for job ${cacheQuery.jobId} with ${cacheQuery.artifactIdsList.size} artifacts" }

                                launch {
                                    val cacheResponse = handleCacheQuery(cacheQuery)
                                    val responseMessage = GrpcWorkerToServer.newBuilder()
                                        .setCacheResponse(cacheResponse)
                                        .build()
                                    toServerChannel.send(responseMessage)
                                }
                            }

                            else -> {
                                logger.warn { "Received unknown message from server: ${serverMessage.messageCase}" }
                            }
                        }
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error in communication channel" }
                }
            }.invokeOnCompletion { cause ->
                if (cause != null) {
                    logger.error(cause) { "Communication channel completed with error" }
                }
                logger.info { "Communication channel closed." }
            }
        }
    }

    private fun executeJobRequest(request: GrpcExecuteJobRequest): Flow<JobExecutionEvent> {
        val grpcJobDef = request.jobDefinition
        val domainJobDef = grpcJobDef.toDomain()
        val domainJob = DomainJob(
            id = DomainJobId(grpcJobDef.id.value),
            definition = domainJobDef,
            status = DomainJobStatus.QUEUED
        )
        logger.info { "Executing job ${domainJob.id.value} of type ${determineJobTypeFromPayload(domainJobDef.payload)}" }
        return scriptExecutor.execute(domainJob, WorkerId(workerId))
    }
    
    private fun determineJobTypeFromPayload(payload: DomainJobPayload): String {
        return when (payload) {
            is DomainJobPayload.Script -> "SCRIPT"
            is DomainJobPayload.Command -> "COMMAND"
            is DomainJobPayload.CompiledScript -> "COMPILED_SCRIPT"
            else -> "UNKNOWN"
        }
    }

    private fun convertEventToWorkerMessage(event: JobExecutionEvent): GrpcWorkerToServer {
        val jobOutputAndStatus = when (event) {
            is JobExecutionEvent.Started ->
                GrpcJobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        GrpcJobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.RUNNING.toGrpc())
                            .build()
                    )
                    .build()

            is JobExecutionEvent.OutputReceived ->
                GrpcJobOutputAndStatus.newBuilder()
                    .setOutputChunk(
                        GrpcJobOutputChunk.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setData(ByteString.copyFrom(event.chunk.data))
                            .setIsStderr(event.chunk.isError)
                            .setTimestamp(
                                com.google.protobuf.Timestamp.newBuilder()
                                    .setSeconds(event.chunk.timestamp.epochSecond)
                                    .setNanos(event.chunk.timestamp.nano)
                                    .build()
                            )
                            .setCompressed(false)
                            .setCompressionType(GrpcCompressionType.COMPRESSION_NONE)
                            .build()
                    )
                    .build()

            is JobExecutionEvent.Completed ->
                GrpcJobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        GrpcJobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.COMPLETED.toGrpc())
                            .setExitCode(event.exitCode)
                            .setMessage("Job completed successfully")
                            .build()
                    )
                    .build()

            is JobExecutionEvent.Failed ->
                GrpcJobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        GrpcJobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.FAILED.toGrpc())
                            .setMessage(event.error)
                            .setExitCode(event.exitCode ?: 1)
                            .build()
                    )
                    .build()

            is JobExecutionEvent.Cancelled ->
                GrpcJobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        GrpcJobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.CANCELLED.toGrpc())
                            .setMessage("Job was cancelled")
                            .build()
                    )
                    .build()

            else -> throw IllegalArgumentException("Unknown event type: ${event::class.simpleName}")
        }
        return GrpcWorkerToServer.newBuilder().setJobOutputAndStatus(jobOutputAndStatus).build()
    }

    override fun close() {
        logger.info { "Shutting down worker $workerId..." }
        workerScope.cancel()
        runBlocking {
            unregisterWorker()
        }
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }

    private suspend fun unregisterWorker() {
        try {
            workerManagementStub.unregisterWorker(GrpcWorkerIdentifier.newBuilder().setValue(workerId).build())
            logger.info { "Worker $workerId unregistered successfully." }
        } catch (e: Exception) {
            logger.error(e) { "Error unregistering worker" }
        }
    }

    private fun createDefaultScript(jobName: String, commandLines: List<String>): String {
        val scriptBuilder = StringBuilder()
        scriptBuilder.appendLine("#!/bin/sh")
        scriptBuilder.appendLine("echo 'Executing job: $jobName'")
        commandLines.forEach {
            scriptBuilder.appendLine(it)
        }
        return scriptBuilder.toString()
    }

    /**
     * Handle incoming artifact chunk (Fase 2: With compression and enhanced cache)
     */
    private suspend fun handleArtifactChunk(chunk: GrpcArtifactChunk): GrpcArtifactAck {
        return try {
            val artifactId = chunk.artifactId

            // Check if artifact is already cached
            val cachedArtifact = persistentArtifacts[artifactId]
            if (cachedArtifact != null) {
                logger.info { "Artifact $artifactId already in cache, skipping transfer" }
                return GrpcArtifactAck.newBuilder()
                    .setArtifactId(artifactId)
                    .setSuccess(true)
                    .setCacheHit(true)
                    .setMessage("Artifact found in cache")
                    .setCalculatedChecksum(cachedArtifact.checksum)
                    .setCacheStatus(buildCacheStatus())
                    .build()
            }

            // Get or create artifact download state
            val download = artifactCache.getOrPut(artifactId) {
                ArtifactDownload(
                    artifactId = artifactId,
                    buffer = mutableListOf(),
                    expectedChecksum = "",
                    compressionType = chunk.compression,
                    originalSize = chunk.originalSize
                )
            }

            // Add chunk data to buffer
            download.buffer.add(chunk.data.toByteArray())

            logger.debug { "Added chunk ${chunk.sequence} for artifact $artifactId (${chunk.data.size()} bytes, compression: ${chunk.compression})" }

            // If this is the last chunk, finalize the artifact
            if (chunk.isLast) {
                val success = finalizeArtifactWithDecompression(download)

                if (success) {
                    // Calculate final checksum of decompressed data
                    val finalData = getFinalArtifactData(download)
                    val calculatedChecksum = calculateSha256(finalData)

                    // Store in persistent cache
                    persistentArtifacts[artifactId] = CachedArtifact(
                        id = artifactId,
                        checksum = calculatedChecksum,
                        size = finalData.size.toLong(),
                        cachedAt = java.time.Instant.now()
                    )

                    logger.info { "Successfully received and cached artifact $artifactId (${finalData.size} bytes)" }

                    // Remove from temporary cache
                    artifactCache.remove(artifactId)

                    GrpcArtifactAck.newBuilder()
                        .setArtifactId(artifactId)
                        .setSuccess(true)
                        .setCacheHit(false)
                        .setMessage("Artifact received and cached successfully")
                        .setCalculatedChecksum(calculatedChecksum)
                        .setCacheStatus(buildCacheStatus())
                        .build()
                } else {
                    GrpcArtifactAck.newBuilder()
                        .setArtifactId(artifactId)
                        .setSuccess(false)
                        .setCacheHit(false)
                        .setMessage("Failed to finalize artifact")
                        .build()
                }
            } else {
                // Acknowledge chunk received
                GrpcArtifactAck.newBuilder()
                    .setArtifactId(artifactId)
                    .setSuccess(true)
                    .setCacheHit(false)
                    .setMessage("Chunk ${chunk.sequence} received")
                    .build()
            }

        } catch (e: Exception) {
            logger.error(e) { "Error handling artifact chunk for ${chunk.artifactId}" }

            GrpcArtifactAck.newBuilder()
                .setArtifactId(chunk.artifactId)
                .setSuccess(false)
                .setCacheHit(false)
                .setMessage("Error: ${e.message}")
                .build()
        }
    }

    /**
     * Finalize artifact by writing to disk
     */
    private fun finalizeArtifact(download: ArtifactDownload): Boolean {
        return try {
            val finalData = download.buffer.flatMap { it.toList() }.toByteArray()
            val artifactFile = File(artifactDirectory, "${download.artifactId}.artifact")

            artifactFile.writeBytes(finalData)
            logger.info { "Artifact ${download.artifactId} saved to ${artifactFile.absolutePath}" }

            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to save artifact ${download.artifactId}" }
            false
        }
    }

    /**
     * Handle cache query from server (Fase 2)
     */
    private suspend fun handleCacheQuery(query: GrpcArtifactCacheQuery): GrpcArtifactCacheResponse {
        logger.debug { "Processing cache query for job ${query.jobId} with ${query.artifactIdsList.size} artifacts" }

        val artifactInfos = query.artifactIdsList.map { artifactId ->
            val cachedArtifact = persistentArtifacts[artifactId]

            GrpcArtifactCacheInfo.newBuilder()
                .setArtifactId(artifactId)
                .setCached(cachedArtifact != null)
                .setCachedChecksum(cachedArtifact?.checksum ?: "")
                .setNeedsTransfer(cachedArtifact == null)
                .build()
        }

        return GrpcArtifactCacheResponse.newBuilder()
            .setJobId(query.jobId)
            .addAllArtifacts(artifactInfos)
            .build()
    }

    /**
     * Load persisted artifacts from disk (Fase 2)
     */
    private fun loadPersistedArtifacts() {
        try {
            val metadataFile = File(artifactDirectory, "artifact_metadata.txt")
            if (metadataFile.exists()) {
                metadataFile.readLines().forEach { line ->
                    val parts = line.split("|")
                    if (parts.size >= 4) {
                        val id = parts[0]
                        val checksum = parts[1]
                        val size = parts[2].toLongOrNull() ?: 0L
                        val cachedAt = try {
                            java.time.Instant.parse(parts[3])
                        } catch (e: Exception) {
                            java.time.Instant.now()
                        }

                        // Verify artifact file still exists
                        val artifactFile = File(artifactDirectory, "$id.artifact")
                        if (artifactFile.exists()) {
                            persistentArtifacts[id] = CachedArtifact(id, checksum, size, cachedAt)
                            logger.debug { "Loaded cached artifact: $id" }
                        }
                    }
                }
                logger.info { "Loaded ${persistentArtifacts.size} cached artifacts from disk" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to load persisted artifacts: ${e.message}" }
        }
    }

    /**
     * Finalize artifact with decompression support (Fase 2)
     */
    private fun finalizeArtifactWithDecompression(download: ArtifactDownload): Boolean {
        return try {
            val compressedData = download.buffer.flatMap { it.toList() }.toByteArray()
            val finalData = when (download.compressionType) {
                GrpcCompressionType.COMPRESSION_GZIP -> {
                    decompressGzip(compressedData)
                }

                GrpcCompressionType.COMPRESSION_ZSTD -> {
                    // For now, fallback to no decompression (ZSTD would require additional dependency)
                    logger.warn { "ZSTD decompression not implemented, using compressed data as-is" }
                    compressedData
                }

                else -> compressedData
            }

            val artifactFile = File(artifactDirectory, "${download.artifactId}.artifact")
            artifactFile.writeBytes(finalData)

            // Update metadata file
            saveArtifactMetadata(download.artifactId, calculateSha256(finalData), finalData.size.toLong())

            logger.info { "Artifact ${download.artifactId} saved and decompressed to ${artifactFile.absolutePath}" }
            true
        } catch (e: Exception) {
            logger.error(e) { "Failed to finalize artifact ${download.artifactId}" }
            false
        }
    }

    /**
     * Get final artifact data (decompressed if needed)
     */
    private fun getFinalArtifactData(download: ArtifactDownload): ByteArray {
        val compressedData = download.buffer.flatMap { it.toList() }.toByteArray()
        return when (download.compressionType) {
            GrpcCompressionType.COMPRESSION_GZIP -> {
                decompressGzip(compressedData)
            }

            GrpcCompressionType.COMPRESSION_ZSTD -> {
                logger.warn { "ZSTD decompression not implemented" }
                compressedData
            }

            else -> compressedData
        }
    }

    /**
     * Build cache status for response
     */
    private fun buildCacheStatus(): GrpcArtifactCacheStatus {
        val totalCacheSize = persistentArtifacts.values.sumOf { it.size }

        return GrpcArtifactCacheStatus.newBuilder()
            .setHasArtifact(persistentArtifacts.isNotEmpty())
            .setCachedArtifactsCount(persistentArtifacts.size)
            .setCacheSizeBytes(totalCacheSize)
            .build()
    }

    /**
     * Decompress GZIP data
     */
    private fun decompressGzip(compressedData: ByteArray): ByteArray {
        return GZIPInputStream(ByteArrayInputStream(compressedData)).use { gzipStream ->
            gzipStream.readBytes()
        }
    }

    /**
     * Save artifact metadata to disk
     */
    private fun saveArtifactMetadata(artifactId: String, checksum: String, size: Long) {
        try {
            val metadataFile = File(artifactDirectory, "artifact_metadata.txt")
            val timestamp = java.time.Instant.now()
            val line = "$artifactId|$checksum|$size|$timestamp\n"
            metadataFile.appendText(line)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to save artifact metadata for $artifactId" }
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

// Mapper functions
fun GrpcJobDefinition.toDomain(): DomainJobDefinition {
    val payload = when (this.payloadCase) {
        GrpcJobDefinition.PayloadCase.SCRIPT -> DomainJobPayload.Script(this.script.content)
        GrpcJobDefinition.PayloadCase.COMMAND -> DomainJobPayload.Command(this.command.commandLineList)
        else -> throw IllegalArgumentException("Unsupported or missing payload in JobDefinition")
    }
    return DomainJobDefinition(
        name = this.name,
        payload = payload,
        workingDirectory = this.workingDirectory
    )
}

fun DomainJobId.toGrpc(): GrpcJobIdentifier {
    return GrpcJobIdentifier.newBuilder().setValue(this.value).build()
}

fun DomainJobStatus.toGrpc(): GrpcJobStatus {
    return when (this) {
        DomainJobStatus.QUEUED -> GrpcJobStatus.JOB_STATUS_QUEUED
        DomainJobStatus.RUNNING -> GrpcJobStatus.JOB_STATUS_RUNNING
        DomainJobStatus.COMPLETED -> GrpcJobStatus.JOB_STATUS_SUCCESS
        DomainJobStatus.FAILED -> GrpcJobStatus.JOB_STATUS_FAILED
        DomainJobStatus.CANCELLED -> GrpcJobStatus.JOB_STATUS_CANCELLED
    }
}

/**
 * Data class to manage artifact download state (Fase 2)
 */
data class ArtifactDownload(
    val artifactId: String,
    val buffer: MutableList<ByteArray>,
    val expectedChecksum: String,
    val compressionType: GrpcCompressionType = GrpcCompressionType.COMPRESSION_NONE,
    val originalSize: Int = 0
)

/**
 * Data class for cached artifacts (Fase 2)
 */
data class CachedArtifact(
    val id: String,
    val checksum: String,
    val size: Long,
    val cachedAt: java.time.Instant
)
