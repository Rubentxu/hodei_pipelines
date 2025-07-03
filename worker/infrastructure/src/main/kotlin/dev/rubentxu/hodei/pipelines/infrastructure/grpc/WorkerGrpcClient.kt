package dev.rubentxu.hodei.pipelines.infrastructure.grpc

import dev.rubentxu.hodei.pipelines.domain.WorkerId
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineEngine
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineExecutionResult
import dev.rubentxu.hodei.pipelines.dsl.model.*
import dev.rubentxu.hodei.pipelines.dsl.builders.PipelineBuilder
import dev.rubentxu.hodei.pipelines.dsl.pipeline
import dev.rubentxu.hodei.pipelines.v1.*
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * gRPC client for Worker that implements the bidirectional streaming protocol
 * defined in shared/proto/worker_service.proto
 */
class WorkerGrpcClient(
    private val workerId: String,
    private val serverHost: String,
    private val serverPort: Int,
    private val pipelineEngine: PipelineEngine
) : Closeable {
    
    private val channel: ManagedChannel = ManagedChannelBuilder.forAddress(serverHost, serverPort)
        .usePlaintext()
        .build()

    private val workerStub: WorkerServiceGrpcKt.WorkerServiceCoroutineStub = 
        WorkerServiceGrpcKt.WorkerServiceCoroutineStub(channel)

    private val workerScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val outgoingMessages = Channel<WorkerMessage>(Channel.UNLIMITED)
    
    @Volatile
    private var isConnected = false
    
    suspend fun connect() {
        logger.info { "Connecting worker $workerId to server $serverHost:$serverPort" }
        
        workerScope.launch {
            try {
                // Send initial registration
                val registerRequest = RegisterRequest.newBuilder()
                    .setWorkerId(workerId)
                    .build()
                
                val registerMessage = WorkerMessage.newBuilder()
                    .setRegisterRequest(registerRequest)
                    .build()
                
                outgoingMessages.send(registerMessage)
                
                // Start bidirectional streaming
                val incomingMessages = workerStub.connect(outgoingMessages.receiveAsFlow())
                
                isConnected = true
                logger.info { "Worker $workerId connected successfully" }
                
                // Process incoming messages from orchestrator
                incomingMessages.collect { orchestratorMessage ->
                    processOrchestratorMessage(orchestratorMessage)
                }
                
            } catch (e: Exception) {
                logger.error(e) { "Error in worker connection" }
                isConnected = false
            }
        }
    }
    
    private suspend fun processOrchestratorMessage(message: OrchestratorMessage) {
        when (message.payloadCase) {
            OrchestratorMessage.PayloadCase.EXECUTION_ASSIGNMENT -> {
                val assignment = message.executionAssignment
                logger.info { "Received execution assignment: ${assignment.executionId}" }
                
                // Launch execution in separate coroutine
                workerScope.launch {
                    executeAssignment(assignment)
                }
            }
            
            OrchestratorMessage.PayloadCase.CANCEL_SIGNAL -> {
                val cancelSignal = message.cancelSignal
                logger.info { "Received cancel signal: ${cancelSignal.reason}" }
                // TODO: Implement job cancellation
            }
            
            OrchestratorMessage.PayloadCase.ARTIFACT -> {
                val artifact = message.artifact
                logger.info { "Received artifact: ${artifact.artifactId}" }
                // TODO: Handle artifact download
            }
            
            else -> {
                logger.warn { "Unknown orchestrator message type: ${message.payloadCase}" }
            }
        }
    }
    
    private suspend fun executeAssignment(assignment: ExecutionAssignment) {
        try {
            logger.info { "Starting execution of ${assignment.executionId}" }
            
            // Send status update - execution started
            sendStatusUpdate(EventType.STAGE_STARTED, "Execution started")
            
            // Execute the assignment using pipeline DSL
            val result = executeDefinition(assignment.executionId, assignment.definition)
            
            // Send final result based on execution outcome
            if (result.success) {
                sendExecutionResult(true, 0, "Execution completed successfully")
                sendStatusUpdate(EventType.STAGE_COMPLETED, "Execution completed")
            } else {
                sendExecutionResult(false, 1, result.error ?: "Execution failed")
                sendStatusUpdate(EventType.STAGE_COMPLETED, "Execution failed: ${result.error}")
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Error executing assignment ${assignment.executionId}" }
            sendExecutionResult(false, 1, "Execution failed: ${e.message}")
        }
    }
    
    private suspend fun executeDefinition(executionId: String, definition: ExecutionDefinition): PipelineExecutionResult {
        return when (definition.taskCase) {
            ExecutionDefinition.TaskCase.SHELL -> {
                val shellTask = definition.shell
                executeShellTask(executionId, shellTask, definition.envVarsMap)
            }
            ExecutionDefinition.TaskCase.KOTLIN_SCRIPT -> {
                val kotlinScriptTask = definition.kotlinScript
                executeKotlinScriptTask(executionId, kotlinScriptTask, definition.envVarsMap)
            }
            else -> {
                throw IllegalArgumentException("Unknown task type: ${definition.taskCase}")
            }
        }
    }
    
    private suspend fun executeShellTask(
        executionId: String,
        shellTask: ShellTask,
        envVars: Map<String, String>
    ): PipelineExecutionResult {
        logger.info { "Executing shell task with ${shellTask.commandsList.size} commands" }
        
        // Create a pipeline dynamically for shell execution
        val pipeline = pipeline("shell-task-$executionId") {
            environment(envVars)
            stages {
                stage("execute") {
                    steps {
                        shellTask.commandsList.forEach { command ->
                            sh(command)
                        }
                    }
                }
            }
        }
        
        // Create channels for output and events
        val outputChannel = Channel<PipelineOutputChunk>(Channel.UNLIMITED)
        val eventChannel = Channel<PipelineExecutionEvent>(Channel.UNLIMITED)
        
        return coroutineScope {
            // Launch coroutines to handle output and events
            val outputJob = launch {
                for (chunk in outputChannel) {
                    sendLogChunk(chunk.data, chunk.isError)
                }
            }
            
            val eventJob = launch {
                for (event in eventChannel) {
                    when (event) {
                        is PipelineExecutionEvent.StageStarted -> {
                            sendStatusUpdate(EventType.STAGE_STARTED, "Started stage: ${event.stageName}")
                        }
                        is PipelineExecutionEvent.StageCompleted -> {
                            sendStatusUpdate(EventType.STAGE_COMPLETED, "Completed stage: ${event.stageName}")
                        }
                        is PipelineExecutionEvent.StepStarted -> {
                            sendStatusUpdate(EventType.STEP_STARTED, "Started step: ${event.stepType}")
                        }
                        is PipelineExecutionEvent.StepCompleted -> {
                            sendStatusUpdate(EventType.STEP_COMPLETED, "Completed step: ${event.stepType}")
                        }
                        else -> {
                            logger.debug { "Received event: ${event::class.simpleName}" }
                        }
                    }
                }
            }
            
            try {
                // Execute pipeline
                val result = pipelineEngine.execute(
                    pipeline = pipeline,
                    jobId = executionId,
                    workerId = workerId,
                    outputChannel = outputChannel,
                    eventChannel = eventChannel,
                    runtimeEnvironment = envVars
                )
                
                result
            } finally {
                outputChannel.close()
                eventChannel.close()
                outputJob.cancel()
                eventJob.cancel()
            }
        }
    }
    
    private suspend fun executeKotlinScriptTask(
        executionId: String,
        kotlinScriptTask: KotlinScriptTask,
        envVars: Map<String, String>
    ): PipelineExecutionResult {
        logger.info { "Executing Kotlin script task" }
        
        // Create a pipeline dynamically for Kotlin script execution
        val pipeline = pipeline("kotlin-script-$executionId") {
            environment(envVars)
            stages {
                stage("execute") {
                    steps {
                        // Write script content to temporary file and execute
                        val tempScriptFile = "/tmp/kotlin-script-$executionId.kts"
                        sh("echo '${kotlinScriptTask.scriptContent.replace("'", "'\\''")}' > $tempScriptFile")
                        script(
                            scriptFile = tempScriptFile,
                            interpreter = "kotlin",
                            parameters = emptyMap() // TODO: Convert kotlinScriptTask.parameters to Map<String, String>
                        )
                    }
                }
            }
        }
        
        // Create channels for output and events
        val outputChannel = Channel<PipelineOutputChunk>(Channel.UNLIMITED)
        val eventChannel = Channel<PipelineExecutionEvent>(Channel.UNLIMITED)
        
        return coroutineScope {
            // Launch coroutines to handle output and events
            val outputJob = launch {
                for (chunk in outputChannel) {
                    sendLogChunk(chunk.data, chunk.isError)
                }
            }
            
            val eventJob = launch {
                for (event in eventChannel) {
                    when (event) {
                        is PipelineExecutionEvent.StageStarted -> {
                            sendStatusUpdate(EventType.STAGE_STARTED, "Started stage: ${event.stageName}")
                        }
                        is PipelineExecutionEvent.StageCompleted -> {
                            sendStatusUpdate(EventType.STAGE_COMPLETED, "Completed stage: ${event.stageName}")
                        }
                        is PipelineExecutionEvent.StepStarted -> {
                            sendStatusUpdate(EventType.STEP_STARTED, "Started step: ${event.stepType}")
                        }
                        is PipelineExecutionEvent.StepCompleted -> {
                            sendStatusUpdate(EventType.STEP_COMPLETED, "Completed step: ${event.stepType}")
                        }
                        else -> {
                            logger.debug { "Received event: ${event::class.simpleName}" }
                        }
                    }
                }
            }
            
            try {
                // Execute pipeline
                val result = pipelineEngine.execute(
                    pipeline = pipeline,
                    jobId = executionId,
                    workerId = workerId,
                    outputChannel = outputChannel,
                    eventChannel = eventChannel,
                    runtimeEnvironment = envVars
                )
                
                result
            } finally {
                outputChannel.close()
                eventChannel.close()
                outputJob.cancel()
                eventJob.cancel()
            }
        }
    }
    
    private suspend fun sendStatusUpdate(eventType: EventType, message: String) {
        try {
            val statusUpdate = StatusUpdate.newBuilder()
                .setEventType(eventType)
                .setMessage(message)
                .setTimestamp(System.currentTimeMillis())
                .build()
            
            val workerMessage = WorkerMessage.newBuilder()
                .setStatusUpdate(statusUpdate)
                .build()
            
            outgoingMessages.send(workerMessage)
            logger.debug { "Sent status update: $eventType - $message" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send status update" }
        }
    }
    
    private suspend fun sendLogChunk(data: ByteArray, isStderr: Boolean) {
        try {
            val logChunk = LogChunk.newBuilder()
                .setStream(if (isStderr) LogStream.STDERR else LogStream.STDOUT)
                .setContent(com.google.protobuf.ByteString.copyFrom(data))
                .build()
            
            val workerMessage = WorkerMessage.newBuilder()
                .setLogChunk(logChunk)
                .build()
            
            outgoingMessages.send(workerMessage)
            logger.debug { "Sent log chunk: ${data.size} bytes (${if (isStderr) "stderr" else "stdout"})" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send log chunk" }
        }
    }
    
    private suspend fun sendExecutionResult(success: Boolean, exitCode: Int, details: String) {
        try {
            val executionResult = ExecutionResult.newBuilder()
                .setSuccess(success)
                .setExitCode(exitCode)
                .setDetails(details)
                .build()
            
            val workerMessage = WorkerMessage.newBuilder()
                .setExecutionResult(executionResult)
                .build()
            
            outgoingMessages.send(workerMessage)
            logger.info { "Sent execution result: success=$success, exitCode=$exitCode" }
        } catch (e: Exception) {
            logger.warn(e) { "Failed to send execution result" }
        }
    }
    
    fun isConnected(): Boolean = isConnected
    
    override fun close() {
        logger.info { "Closing worker gRPC client for $workerId" }
        isConnected = false
        workerScope.cancel()
        channel.shutdown()
        
        runBlocking {
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS)
            } catch (e: Exception) {
                logger.warn(e) { "Error waiting for channel termination" }
            }
        }
    }
}