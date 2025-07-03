package dev.rubentxu.hodei.worker.client

import dev.rubentxu.hodei.pipelines.v1.*
import dev.rubentxu.hodei.worker.execution.ExecutionResult
import dev.rubentxu.hodei.worker.execution.KotlinScriptExecutor
import dev.rubentxu.hodei.worker.execution.ShellExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * gRPC client for worker communication with orchestrator
 */
class WorkerGrpcClient(
    private val workerId: String,
    private val orchestratorHost: String,
    private val orchestratorPort: Int,
    private val shellExecutor: ShellExecutor,
    private val kotlinScriptExecutor: KotlinScriptExecutor
) {
    
    private lateinit var channel: ManagedChannel
    private lateinit var stub: WorkerServiceGrpcKt.WorkerServiceCoroutineStub
    private val outgoingMessages = Channel<WorkerMessage>(Channel.UNLIMITED)
    private var connectionJob: Job? = null
    private var currentExecutionJob: Job? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    suspend fun connect() {
        logger.info { "Worker $workerId connecting to $orchestratorHost:$orchestratorPort" }
        
        channel = ManagedChannelBuilder.forAddress(orchestratorHost, orchestratorPort)
            .usePlaintext()
            .build()
        
        stub = WorkerServiceGrpcKt.WorkerServiceCoroutineStub(channel)
        
        // Start connection handling
        connectionJob = coroutineScope.launch {
            try {
                handleConnection()
            } catch (e: Exception) {
                logger.error(e) { "Connection handling failed" }
                throw e
            }
        }
        
        // Register with orchestrator
        register()
    }
    
    private suspend fun handleConnection() {
        val outgoingFlow = outgoingMessages.receiveAsFlow()
        
        logger.debug { "Starting bidirectional stream with orchestrator" }
        val incomingFlow = stub.connect(outgoingFlow)
        
        incomingFlow.collect { message ->
            logger.debug { "Worker $workerId received message: ${message.payloadCase}" }
            handleIncomingMessage(message)
        }
    }
    
    private suspend fun handleIncomingMessage(message: OrchestratorMessage) {
        when (message.payloadCase) {
            OrchestratorMessage.PayloadCase.EXECUTION_ASSIGNMENT -> {
                handleExecutionAssignment(message.executionAssignment)
            }
            OrchestratorMessage.PayloadCase.CANCEL_SIGNAL -> {
                handleCancelSignal(message.cancelSignal)
            }
            // Health check is handled by connection staying alive
            else -> {
                logger.warn { "Worker $workerId received unknown message type: ${message.payloadCase}" }
            }
        }
    }
    
    private suspend fun handleExecutionAssignment(assignment: ExecutionAssignment) {
        logger.info { "Worker $workerId received execution assignment: ${assignment.executionId}" }
        
        // Cancel any current execution
        currentExecutionJob?.cancel()
        
        // Start new execution
        currentExecutionJob = coroutineScope.launch {
            try {
                executeJob(assignment)
            } catch (e: CancellationException) {
                logger.info { "Execution ${assignment.executionId} was cancelled" }
                sendExecutionResult(assignment.executionId, false, -1, "Execution cancelled")
            } catch (e: Exception) {
                logger.error(e) { "Execution ${assignment.executionId} failed with error" }
                sendExecutionResult(assignment.executionId, false, -1, "Execution failed: ${e.message}")
            }
        }
    }
    
    private suspend fun executeJob(assignment: ExecutionAssignment) {
        val executionId = assignment.executionId
        val definition = assignment.definition
        
        sendStatusUpdate(EventType.STAGE_STARTED, "Starting execution")
        
        val result = when (definition.taskCase) {
            ExecutionDefinition.TaskCase.SHELL -> {
                logger.info { "Executing shell task for execution $executionId" }
                shellExecutor.execute(definition.shell, executionId) { logChunk ->
                    sendLogChunk(logChunk.stream, logChunk.content.toStringUtf8())
                }
            }
            ExecutionDefinition.TaskCase.KOTLIN_SCRIPT -> {
                logger.info { "Executing Kotlin script for execution $executionId" }
                kotlinScriptExecutor.execute(definition.kotlinScript, executionId) { logChunk ->
                    sendLogChunk(logChunk.stream, logChunk.content.toStringUtf8())
                }
            }
            else -> {
                logger.error { "Unknown task type: ${definition.taskCase}" }
                ExecutionResult(false, -1, "Unknown task type: ${definition.taskCase}")
            }
        }
        
        if (result.success) {
            sendStatusUpdate(EventType.STAGE_COMPLETED, "Execution completed successfully")
        } else {
            sendStatusUpdate(EventType.STAGE_COMPLETED, "Execution failed: ${result.details}")
        }
        
        sendExecutionResult(executionId, result.success, result.exitCode, result.details)
    }
    
    private suspend fun handleCancelSignal(cancelSignal: CancelSignal) {
        logger.info { "Worker $workerId received cancel signal: ${cancelSignal.reason}" }
        currentExecutionJob?.cancel()
    }
    
    
    private suspend fun register() {
        logger.info { "Registering worker $workerId with orchestrator" }
        
        val registerRequest = RegisterRequest.newBuilder()
            .setWorkerId(workerId)
            .build()
        
        val message = WorkerMessage.newBuilder()
            .setRegisterRequest(registerRequest)
            .build()
        
        outgoingMessages.send(message)
    }
    
    private suspend fun sendStatusUpdate(eventType: EventType, messageText: String) {
        val statusUpdate = StatusUpdate.newBuilder()
            .setEventType(eventType)
            .setMessage(messageText)
            .setTimestamp(System.currentTimeMillis())
            .build()
        
        val workerMessage = WorkerMessage.newBuilder()
            .setStatusUpdate(statusUpdate)
            .build()
        
        outgoingMessages.send(workerMessage)
    }
    
    private suspend fun sendLogChunk(stream: LogStream, content: String) {
        val logChunk = dev.rubentxu.hodei.pipelines.v1.LogChunk.newBuilder()
            .setStream(stream)
            .setContent(com.google.protobuf.ByteString.copyFromUtf8(content))
            .build()
        
        val message = WorkerMessage.newBuilder()
            .setLogChunk(logChunk)
            .build()
        
        outgoingMessages.send(message)
    }
    
    private suspend fun sendExecutionResult(executionId: String, success: Boolean, exitCode: Int, details: String) {
        logger.info { "Sending execution result for $executionId: success=$success, exitCode=$exitCode" }
        
        val executionResult = dev.rubentxu.hodei.pipelines.v1.ExecutionResult.newBuilder()
            .setSuccess(success)
            .setExitCode(exitCode)
            .setDetails(details)
            .build()
        
        val message = WorkerMessage.newBuilder()
            .setExecutionResult(executionResult)
            .build()
        
        outgoingMessages.send(message)
    }
    
    suspend fun awaitTermination() {
        connectionJob?.join()
    }
    
    suspend fun shutdown() {
        logger.info { "Shutting down worker gRPC client" }
        
        currentExecutionJob?.cancel()
        connectionJob?.cancel()
        outgoingMessages.close()
        
        if (::channel.isInitialized) {
            channel.shutdown()
            if (!channel.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn { "gRPC channel did not terminate gracefully, forcing shutdown" }
                channel.shutdownNow()
            }
        }
        
        coroutineScope.cancel()
    }
}