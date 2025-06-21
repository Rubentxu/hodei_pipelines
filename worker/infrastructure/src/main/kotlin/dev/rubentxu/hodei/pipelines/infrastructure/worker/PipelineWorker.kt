package dev.rubentxu.hodei.pipelines.infrastructure.worker

import com.google.protobuf.ByteString
import dev.rubentxu.hodei.pipelines.domain.job.Job as DomainJob
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition as DomainJobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobId as DomainJobId
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload as DomainJobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus as DomainJobStatus
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.proto.ControlSignal
import dev.rubentxu.hodei.pipelines.proto.ExecuteJobRequest
import dev.rubentxu.hodei.pipelines.proto.JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub
import dev.rubentxu.hodei.pipelines.proto.JobOutputAndStatus
import dev.rubentxu.hodei.pipelines.proto.ServerToWorker
import dev.rubentxu.hodei.pipelines.proto.WorkerHeartbeat
import dev.rubentxu.hodei.pipelines.proto.WorkerIdentifier
import dev.rubentxu.hodei.pipelines.proto.WorkerManagementServiceGrpcKt.WorkerManagementServiceCoroutineStub
import dev.rubentxu.hodei.pipelines.proto.WorkerRegistrationRequest
import dev.rubentxu.hodei.pipelines.proto.WorkerToServer
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.TimeUnit
import dev.rubentxu.hodei.pipelines.proto.JobDefinition as GrpcJobDefinition

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
    private val workerManagementStub: WorkerManagementServiceCoroutineStub = WorkerManagementServiceCoroutineStub(channel)

    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        logger.info { "Starting worker $workerId..." }
        workerScope.launch {
            registerWorker()
            manageCommunicationChannel()
        }
    }

    private suspend fun registerWorker() {
        try {
            val request = WorkerRegistrationRequest.newBuilder()
                .setWorkerId(WorkerIdentifier.newBuilder().setValue(workerId).build())
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
        val toServerChannel = Channel<WorkerToServer>(Channel.UNLIMITED)
        coroutineScope {
            // Heartbeat coroutine
            launch {
                while (isActive) {
                    val heartbeat = WorkerHeartbeat.newBuilder()
                        .setWorkerId(WorkerIdentifier.newBuilder().setValue(workerId).build())
                        .setStatus(dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_READY)
                        .build()
                    val message = WorkerToServer.newBuilder().setHeartbeat(heartbeat).build()
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
                            ServerToWorker.MessageCase.JOB_REQUEST -> {
                                val jobRequest = serverMessage.jobRequest
                                logger.info { "Received job request: ${jobRequest.jobDefinition.name}" }
                                launch {
                                    executeJobRequest(jobRequest)
                                        .map { event -> convertEventToWorkerMessage(event) }
                                        .collect { message -> toServerChannel.send(message) }
                                }
                            }
                            ServerToWorker.MessageCase.CONTROL_SIGNAL -> {
                                val controlSignal = serverMessage.controlSignal
                                logger.info { "Received control signal: ${controlSignal.type}" }
                                // Handle control signals (e.g., cancel job)
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

    private fun executeJobRequest(request: ExecuteJobRequest): Flow<JobExecutionEvent> {
        val grpcJobDef = request.jobDefinition
        val domainJobDef = grpcJobDef.toDomain()
        val domainJob = DomainJob(
            id = DomainJobId(grpcJobDef.id.value),
            definition = domainJobDef,
            status = DomainJobStatus.QUEUED
        )
        return scriptExecutor.execute(domainJob, WorkerId(workerId))
    }

    private fun convertEventToWorkerMessage(event: JobExecutionEvent): WorkerToServer {
        val jobOutputAndStatus = when (event) {
            is JobExecutionEvent.Started ->
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        dev.rubentxu.hodei.pipelines.proto.JobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.RUNNING.toGrpc())
                            .build()
                    )
                    .build()
            is JobExecutionEvent.OutputReceived ->
                JobOutputAndStatus.newBuilder()
                    .setOutputChunk(
                        dev.rubentxu.hodei.pipelines.proto.JobOutputChunk.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setData(ByteString.copyFrom(event.chunk.data))
                            .setIsStderr(event.chunk.isError)
                            .setTimestamp(com.google.protobuf.Timestamp.newBuilder()
                                .setSeconds(event.chunk.timestamp.epochSecond)
                                .setNanos(event.chunk.timestamp.nano)
                                .build())
                            .setCompressed(false)
                            .setCompressionType(dev.rubentxu.hodei.pipelines.proto.CompressionType.COMPRESSION_NONE)
                            .build()
                    )
                    .build()
            is JobExecutionEvent.Completed ->
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        dev.rubentxu.hodei.pipelines.proto.JobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.COMPLETED.toGrpc())
                            .setExitCode(event.exitCode)
                            .setMessage("Job completed successfully")
                            .build()
                    )
                    .build()
            is JobExecutionEvent.Failed ->
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        dev.rubentxu.hodei.pipelines.proto.JobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.FAILED.toGrpc())
                            .setMessage(event.error)
                            .setExitCode(event.exitCode ?: 1)
                            .build()
                    )
                    .build()
            is JobExecutionEvent.Cancelled ->
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(
                        dev.rubentxu.hodei.pipelines.proto.JobExecutionStatus.newBuilder()
                            .setJobId(event.jobId.toGrpc())
                            .setStatus(DomainJobStatus.CANCELLED.toGrpc())
                            .setMessage("Job was cancelled")
                            .build()
                    )
                    .build()
            else -> throw IllegalArgumentException("Unknown event type: ${event::class.simpleName}")
        }
        return WorkerToServer.newBuilder().setJobOutputAndStatus(jobOutputAndStatus).build()
    }

    override fun close() {
        logger.info { "Shutting down worker $workerId..." }
        runBlocking {
            unregisterWorker()
        }
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
        workerScope.cancel()
    }

    private suspend fun unregisterWorker() {
        try {
            workerManagementStub.unregisterWorker(WorkerIdentifier.newBuilder().setValue(workerId).build())
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

fun DomainJobId.toGrpc(): dev.rubentxu.hodei.pipelines.proto.JobIdentifier {
    return dev.rubentxu.hodei.pipelines.proto.JobIdentifier.newBuilder().setValue(this.value).build()
}

fun DomainJobStatus.toGrpc(): dev.rubentxu.hodei.pipelines.proto.JobStatus {
    return when (this) {
        DomainJobStatus.QUEUED -> dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_QUEUED
        DomainJobStatus.RUNNING -> dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_RUNNING
        DomainJobStatus.COMPLETED -> dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_SUCCESS
        DomainJobStatus.FAILED -> dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_FAILED
        DomainJobStatus.CANCELLED -> dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_CANCELLED
    }
}
