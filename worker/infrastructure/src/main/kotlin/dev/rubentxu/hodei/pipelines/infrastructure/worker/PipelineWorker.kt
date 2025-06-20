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
        println("Starting Pipeline Worker: $workerName ($workerId)")
        
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
            
            println("Worker started successfully. Waiting for jobs...")
            
            // Wait for shutdown
            try {
                joinAll(heartbeatJob, jobListenerJob)
            } finally {
                stop()
            }
        } else {
            println("Failed to register worker with server")
        }
    }
    
    suspend fun stop() {
        println("Stopping Pipeline Worker...")
        isRunning = false
        
        try {
            // Unregister from server
            workerManagementStub.unregisterWorker(
                WorkerIdentifier.newBuilder()
                    .setValue(workerId)
                    .build()
            )
        } catch (e: Exception) {
            println("Error during unregistration: ${e.message}")
        }
        
        channel.shutdown()
        if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
            channel.shutdownNow()
        }
        
        println("Worker stopped")
    }
    
    private suspend fun registerWithServer(): Boolean {
        return try {
            val request = WorkerRegistrationRequest.newBuilder()
                .setWorkerId(WorkerIdentifier.newBuilder().setValue(workerId).build())
                .setWorkerName(workerName)
                .setAuthToken("worker-auth-token") // In real implementation, use proper auth
                .putAllCapabilities(capabilities)
                .setWorkerVersion("1.0.0")
                .setMaxConcurrentJobs(capabilities["maxConcurrentJobs"]?.toIntOrNull() ?: 5)
                .build()
            
            val response = workerManagementStub.registerWorker(request)
            
            if (response.success) {
                sessionToken = response.sessionToken
                println("Worker registered successfully. Session token: ${sessionToken}")
                true
            } else {
                println("Worker registration failed: ${response.message}")
                false
            }
        } catch (e: Exception) {
            println("Error during registration: ${e.message}")
            false
        }
    }
    
    private suspend fun startHeartbeat() {
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
                
                workerManagementStub.sendHeartbeat(heartbeat)
                
                delay(30_000) // Send heartbeat every 30 seconds
            } catch (e: Exception) {
                println("Heartbeat failed: ${e.message}")
                delay(5_000) // Retry after 5 seconds on failure
            }
        }
    }
    
    private suspend fun listenForJobs() {
        // For MVP, we'll implement a simple polling mechanism
        // In a real implementation, this would be a bidirectional gRPC stream
        
        while (isRunning) {
            try {
                // Simulate receiving job (in real implementation, this would come from gRPC stream)
                // For now, we'll create a demo job every 60 seconds for testing
                println("Worker ready to receive jobs...")
                delay(60_000)
                
            } catch (e: Exception) {
                println("Error in job listener: ${e.message}")
                delay(5_000)
            }
        }
    }
    
    suspend fun executeJobRequest(request: ExecuteJobRequest): Flow<JobOutputAndStatus> = flow {
        println("Executing job: ${request.jobDefinition.name}")
        
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
            
            // Execute using script executor
            scriptExecutor.execute(job, WorkerId(workerId)).collect { event ->
                when (event) {
                    is JobExecutionEvent.Started -> {
                        emit(JobOutputAndStatus.newBuilder()
                            .setStatusUpdate(JobExecutionStatus.newBuilder()
                                .setJobId(JobIdentifier.newBuilder().setValue(job.id.value).build())
                                .setStatus(dev.rubentxu.hodei.pipelines.proto.JobStatus.JOB_STATUS_RUNNING)
                                .setMessage("Job started")
                                .build())
                            .build())
                    }
                    
                    is JobExecutionEvent.Completed -> {
                        // Send any output chunks first
                        if (event.output.isNotEmpty()) {
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
                        // Handle other event types if needed
                    }
                }
            }
            
        } catch (e: Exception) {
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
    val workerId = System.getenv("WORKER_ID") ?: "worker-${System.currentTimeMillis()}"
    val workerName = System.getenv("WORKER_NAME") ?: "Pipeline Worker"
    val serverHost = System.getenv("SERVER_HOST") ?: "localhost"
    val serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 9090
    
    val worker = PipelineWorker(
        workerId = workerId,
        workerName = workerName,
        serverHost = serverHost,
        serverPort = serverPort
    )
    
    // Handle shutdown gracefully
    Runtime.getRuntime().addShutdownHook(Thread {
        runBlocking {
            worker.stop()
        }
    })
    
    try {
        worker.start()
    } catch (e: Exception) {
        println("Worker failed: ${e.message}")
        e.printStackTrace()
    }
}