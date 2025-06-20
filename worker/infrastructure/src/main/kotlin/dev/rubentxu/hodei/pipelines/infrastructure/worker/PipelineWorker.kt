package dev.rubentxu.hodei.pipelines.infrastructure.worker

import com.google.protobuf.Empty
import com.google.protobuf.Timestamp
import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.proto.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import mu.KotlinLogging
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * Pipeline Worker - receives jobs via gRPC and executes them using Kotlin Script runtime
 * Similar to Jenkins agent but with Kotlin scripting instead of Groovy
 */
class PipelineWorker(
    private val workerId: String,
    private val workerName: String,
    private val serverHost: String = "localhost",
    private val serverPort: Int = 9090,
    private val capabilities: Map<String, String> = mapOf(
        "os" to System.getProperty("os.name").lowercase(),
        "arch" to System.getProperty("os.arch"),
        "maxConcurrentJobs" to "5"
    )
) {
    
    private val logger = KotlinLogging.logger {}
    private val channel: ManagedChannel = ManagedChannelBuilder
        .forAddress(serverHost, serverPort)
        .usePlaintext()
        .build()
    
    private val workerManagementStub = WorkerManagementServiceGrpcKt.WorkerManagementServiceCoroutineStub(channel)
    private val jobExecutorStub = JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)
    
    private val scriptExecutor = PipelineScriptExecutor()
    private var sessionToken: String? = null
    private var isRunning = false
    
    suspend fun start() {
        logger.info { "Starting Pipeline Worker: $workerName ($workerId)" }
        logger.info { "Connecting to Hodei Pipelines server at $serverHost:$serverPort" }
        logger.info { "Worker capabilities: $capabilities" }

        // Register with server
        if (registerWithServer()) {
            isRunning = true
            
            // Start heartbeat
            val heartbeatJob = CoroutineScope(Dispatchers.Default).launch {
                startHeartbeat()
            }
            
            // Start listening for jobs
            val jobListenerJob = CoroutineScope(Dispatchers.Default).launch {
                listenForJobs()
            }
            
            logger.info { "Worker started successfully. Ready to receive jobs." }

            // Wait for shutdown
            try {
                joinAll(heartbeatJob, jobListenerJob)
            } finally {
                stop()
            }
        } else {
            logger.error { "Failed to register worker with server - check network connectivity and server status" }
        }
    }
    
    suspend fun stop() {
        logger.info { "Stopping Pipeline Worker: $workerName ($workerId)" }
        isRunning = false
        
        try {
            // Unregister from server
            logger.debug { "Unregistering worker from server" }
            workerManagementStub.unregisterWorker(
                WorkerIdentifier.newBuilder()
                    .setValue(workerId)
                    .build()
            )
            logger.debug { "Worker successfully unregistered from server" }
        } catch (e: Exception) {
            logger.error(e) { "Error during worker unregistration: ${e.message}" }
        }
        
        channel.shutdown()
        logger.debug { "Waiting for gRPC channel to terminate..." }
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            logger.warn { "gRPC channel did not terminate gracefully, forcing shutdown" }
            channel.shutdownNow()
        }
        
        logger.info { "Worker stopped completely" }
    }
    
    private suspend fun registerWithServer(): Boolean {
        return try {
            logger.info { "Registering worker with server - ID: $workerId, Name: $workerName" }

            val request = WorkerRegistrationRequest.newBuilder()
                .setWorkerId(WorkerIdentifier.newBuilder().setValue(workerId).build())
                .setWorkerName(workerName)
                .setAuthToken("worker-auth-token") // In real implementation, use proper auth
                .putAllCapabilities(capabilities)
                .setWorkerVersion("1.0.0")
                .setMaxConcurrentJobs(capabilities["maxConcurrentJobs"]?.toIntOrNull() ?: 5)
                .build()
            
            logger.debug { "Sending worker registration request" }
            val response = workerManagementStub.registerWorker(request)
            
            if (response.success) {
                sessionToken = response.sessionToken
                logger.info { "Worker registered successfully with session token: ${sessionToken?.take(8)}..." }
                true
            } else {
                logger.warn { "Worker registration rejected: ${response.message}" }
                false
            }
        } catch (e: Exception) {
            logger.error(e) { "Failed to register worker with server: ${e.message}" }
            false
        }
    }
    
    private suspend fun startHeartbeat() {
        logger.info { "Starting worker heartbeat service" }
        while (isRunning) {
            try {
                val heartbeat = WorkerHeartbeat.newBuilder()
                    .setWorkerId(WorkerIdentifier.newBuilder().setValue(workerId).build())
                    .setStatus(dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_READY)
                    .setActiveJobsCount(0) // For MVP, always 0
                    .setTimestamp(Timestamp.newBuilder()
                        .setSeconds(Instant.now().epochSecond)
                        .setNanos(Instant.now().nano)
                        .build())
                    .build()
                
                logger.debug { "Sending heartbeat to server" }
                workerManagementStub.sendHeartbeat(heartbeat)
                
                delay(30_000) // Send heartbeat every 30 seconds
            } catch (e: Exception) {
                logger.warn(e) { "Heartbeat failed: ${e.message} - will retry in 5 seconds" }
                delay(5_000) // Retry after 5 seconds on failure
            }
        }
        logger.debug { "Heartbeat service stopped" }
    }
    
    private suspend fun listenForJobs() {
        // For MVP, we'll implement a simple polling mechanism
        // In a real implementation, this would be a bidirectional gRPC stream
        
        logger.info { "Starting job listener service" }
        while (isRunning) {
            try {
                // Simulate receiving job (in real implementation, this would come from gRPC stream)
                // For now, we'll create a demo job every 60 seconds for testing
                logger.debug { "Worker ready and polling for new jobs" }
                delay(60_000)
                
            } catch (e: Exception) {
                logger.error(e) { "Error in job listener: ${e.message} - will retry in 5 seconds" }
                delay(5_000)
            }
        }
        logger.debug { "Job listener service stopped" }
    }
    
    suspend fun executeJobRequest(request: ExecuteJobRequest): Flow<JobOutputAndStatus> = flow {
        logger.info { "Received job execution request: ${request.jobDefinition.id.value} - ${request.jobDefinition.name}" }
        logger.debug { "Job parameters: workDir=${request.jobDefinition.workingDirectory}, commands=${request.jobDefinition.commandList}" }

        try {
            // Convert gRPC request to domain job
            val job = Job(
                id = JobId(request.jobDefinition.id.value),
                definition = JobDefinition(
                    name = request.jobDefinition.name,
                    command = request.jobDefinition.commandList,
                    workingDirectory = request.jobDefinition.workingDirectory,
                    environment = request.jobDefinition.environmentMap.toMutableMap().apply {
                        // Add script content from job definition or parameters
                        putIfAbsent("SCRIPT_CONTENT", createDefaultScript(request.jobDefinition))
                    }
                )
            )
            
            logger.info { "Starting job execution: ID=${job.id.value}, Name=${job.definition.name}" }

            // Execute using script executor
            scriptExecutor.execute(job, WorkerId(workerId)).collect { event ->
                when (event) {
                    is JobExecutionEvent.Started -> {
                        logger.info { "Job ${job.id.value} execution started" }
                        emit(JobOutputAndStatus.newBuilder()
                            .setStatusUpdate(JobExecutionStatus.newBuilder()
                                .setJobId(JobIdentifier.newBuilder().setValue(job.id.value).build())
                                .setStatus(dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_RUNNING)
                                .setMessage("Job execution started")
                                .build())
                            .build())
                    }
                    
                    is JobExecutionEvent.Completed -> {
                        // Send any output chunks first
                        if (event.output.isNotEmpty()) {
                            logger.debug { "Sending job output chunk (${event.output.length} bytes)" }
                            emit(JobOutputAndStatus.newBuilder()
                                .setOutputChunk(JobOutputChunk.newBuilder()
                                    .setJobId(JobIdentifier.newBuilder().setValue(job.id.value).build())
                                    .setData(com.google.protobuf.ByteString.copyFromUtf8(event.output))
                                    .setIsStderr(false)
                                    .setTimestamp(Timestamp.newBuilder()
                                        .setSeconds(Instant.now().epochSecond)
                                        .setNanos(Instant.now().nano)
                                        .build())
                                    .build())
                                .build())
                        }
                        
                        // Send completion status
                        logger.info { "Job ${job.id.value} completed successfully with exit code: ${event.exitCode}" }
                        emit(JobOutputAndStatus.newBuilder()
                            .setStatusUpdate(JobExecutionStatus.newBuilder()
                                .setJobId(JobIdentifier.newBuilder().setValue(job.id.value).build())
                                .setStatus(dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_SUCCESS)
                                .setExitCode(event.exitCode)
                                .setMessage("Job completed successfully")
                                .build())
                            .build())
                    }
                    
                    is JobExecutionEvent.Failed -> {
                        logger.error { "Job ${job.id.value} execution failed: ${event.error}" }
                        emit(JobOutputAndStatus.newBuilder()
                            .setStatusUpdate(JobExecutionStatus.newBuilder()
                                .setJobId(JobIdentifier.newBuilder().setValue(job.id.value).build())
                                .setStatus(dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_FAILED)
                                .setExitCode(event.exitCode ?: 1)
                                .setMessage("Job failed: ${event.error}")
                                .build())
                            .build())
                    }
                    
                    else -> {
                        logger.debug { "Received unhandled event type: ${event::class.simpleName}" }
                    }
                }
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Unhandled exception during job execution: ${request.jobDefinition.id.value} - ${e.message}" }
            emit(JobOutputAndStatus.newBuilder()
                .setStatusUpdate(JobExecutionStatus.newBuilder()
                    .setJobId(JobIdentifier.newBuilder().setValue(request.jobDefinition.id.value).build())
                    .setStatus(dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_FAILED)
                    .setExitCode(1)
                    .setMessage("Job execution failed: ${e.message}")
                    .build())
                .build())
        }
    }
    
    private fun createDefaultScript(jobDefinition: dev.rubentxu.hodei.pipelines.proto.JobDefinition): String {
        // Create a default Kotlin script based on the job definition
        logger.debug { "Creating default script for job: ${jobDefinition.name}" }
        return """
            tasks.register("main") {
                doLast {
                    println("Executing job: ${jobDefinition.name}")
                    println("Commands: ${jobDefinition.commandList.joinToString(" ")}")
                    ${jobDefinition.commandList.joinToString("\n") { """    println("Running: $it")""" }}
                    println("Job completed successfully!")
                }
            }
            
            tasks.getByName("main").execute()
        """.trimIndent()
    }
}

/**
 * Main function to start the worker
 */
suspend fun main() {
    val logger = KotlinLogging.logger {}
    logger.info { "Initializing Hodei Pipelines Worker" }

    val workerId = System.getenv("WORKER_ID") ?: "worker-${System.currentTimeMillis()}"
    val workerName = System.getenv("WORKER_NAME") ?: "Pipeline Worker"
    val serverHost = System.getenv("SERVER_HOST") ?: "127.0.0.1"
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 9090

    logger.info { "Worker configuration: ID=$workerId, Name=$workerName, Server=$serverHost:$serverPort" }

    val worker = PipelineWorker(
        workerId = workerId,
        workerName = workerName,
        serverHost = serverHost,
        serverPort = serverPort
    )

    // Handle shutdown gracefully
    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Received shutdown signal, initiating graceful shutdown" }
        runBlocking {
            worker.stop()
        }
        logger.info { "Worker shutdown complete" }
    })

    try {
        logger.info { "Starting worker" }
        worker.start()
    } catch (e: Exception) {
        logger.error(e) { "Worker failed with unhandled exception: ${e.message}" }
        e.printStackTrace()
    }
}