package dev.rubentxu.hodei.integration

import dev.rubentxu.hodei.pipelines.v1.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job
import dev.rubentxu.hodei.domain.worker.WorkerFactory
import dev.rubentxu.hodei.domain.worker.WorkerInstance
import dev.rubentxu.hodei.shared.domain.errors.WorkerCreationError
import dev.rubentxu.hodei.shared.domain.errors.WorkerDeletionError
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import arrow.core.Either
import arrow.core.right
import arrow.core.left
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.infrastructure.grpc.OrchestratorGrpcService
import dev.rubentxu.hodei.jobmanagement.infrastructure.persistence.InMemoryJobRepository
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Test helper class for simulating a worker client in tests
 */
class TestWorkerClient(
    val workerId: String,
    private val scope: TestScope
) {
    private val outgoingMessages = Channel<WorkerMessage>(Channel.UNLIMITED)
    private val incomingMessages = mutableListOf<OrchestratorMessage>()
    
    val outgoingFlow: Flow<WorkerMessage> = outgoingMessages.receiveAsFlow()
    
    suspend fun register() {
        val registerRequest = RegisterRequest.newBuilder()
            .setWorkerId(workerId)
            .build()
        
        val message = WorkerMessage.newBuilder()
            .setRegisterRequest(registerRequest)
            .build()
        
        outgoingMessages.send(message)
    }
    
    suspend fun sendStatusUpdate(eventType: EventType, message: String) {
        val statusUpdate = StatusUpdate.newBuilder()
            .setEventType(eventType)
            .setMessage(message)
            .setTimestamp(System.currentTimeMillis())
            .build()
        
        val workerMessage = WorkerMessage.newBuilder()
            .setStatusUpdate(statusUpdate)
            .build()
        
        outgoingMessages.send(workerMessage)
    }
    
    suspend fun sendExecutionResult(success: Boolean, exitCode: Int = 0, details: String = "") {
        val executionResult = ExecutionResult.newBuilder()
            .setSuccess(success)
            .setExitCode(exitCode)
            .setDetails(details)
            .build()
        
        val message = WorkerMessage.newBuilder()
            .setExecutionResult(executionResult)
            .build()
        
        outgoingMessages.send(message)
    }
    
    suspend fun sendLogChunk(stream: LogStream, content: String) {
        val logChunk = LogChunk.newBuilder()
            .setStream(stream)
            .setContent(com.google.protobuf.ByteString.copyFromUtf8(content))
            .build()
        
        val message = WorkerMessage.newBuilder()
            .setLogChunk(logChunk)
            .build()
        
        outgoingMessages.send(message)
    }
    
    fun collectIncomingMessages(flow: Flow<OrchestratorMessage>): Job {
        return scope.launch {
            try {
                flow.collect { message ->
                    logger.debug { "TestWorker $workerId received message: ${message.payloadCase}" }
                    incomingMessages.add(message)
                }
            } catch (e: Exception) {
                logger.error(e) { "Error collecting messages for worker $workerId" }
            }
        }
    }
    
    suspend fun expectExecutionAssignment(timeout: Duration = 5.seconds): ExecutionAssignment {
        val startTime = System.currentTimeMillis()
        return withTimeout(timeout) {
            while (System.currentTimeMillis() - startTime < timeout.inWholeMilliseconds) {
                val message = incomingMessages.find { it.hasExecutionAssignment() }
                if (message != null) {
                    logger.debug { "Worker $workerId found execution assignment: ${message.executionAssignment.executionId}" }
                    return@withTimeout message.executionAssignment
                }
                kotlinx.coroutines.delay(50) // More frequent checks
            }
            throw IllegalStateException("No execution assignment received within timeout of ${timeout}. Received ${incomingMessages.size} messages total.")
        }
    }
    
    suspend fun expectCancelSignal(timeout: Duration = 5.seconds): CancelSignal {
        return withTimeout(timeout) {
            while (true) {
                val message = incomingMessages.find { it.hasCancelSignal() }
                if (message != null) {
                    return@withTimeout message.cancelSignal
                }
                kotlinx.coroutines.delay(100)
            }
            throw IllegalStateException("No cancel signal received within timeout")
        }
    }
    
    fun getReceivedMessages(): List<OrchestratorMessage> = incomingMessages.toList()
    
    fun close() {
        outgoingMessages.close()
    }
}

/**
 * Helper to create execution definitions for tests
 */
object TestExecutionDefinitions {
    fun createShellExecution(commands: List<String>, envVars: Map<String, String> = emptyMap()): ExecutionDefinition {
        val shellTask = ShellTask.newBuilder()
            .addAllCommands(commands)
            .build()
        
        return ExecutionDefinition.newBuilder()
            .setShell(shellTask)
            .putAllEnvVars(envVars)
            .build()
    }
    
    fun createKotlinScriptExecution(
        scriptContent: String, 
        parameters: Map<String, Any> = emptyMap(),
        envVars: Map<String, String> = emptyMap()
    ): ExecutionDefinition {
        val structBuilder = com.google.protobuf.Struct.newBuilder()
        
        parameters.forEach { (key, value) ->
            val protoValue = when (value) {
                is String -> com.google.protobuf.Value.newBuilder().setStringValue(value).build()
                is Number -> com.google.protobuf.Value.newBuilder().setNumberValue(value.toDouble()).build()
                is Boolean -> com.google.protobuf.Value.newBuilder().setBoolValue(value).build()
                else -> com.google.protobuf.Value.newBuilder().setStringValue(value.toString()).build()
            }
            structBuilder.putFields(key, protoValue)
        }
        
        val kotlinTask = KotlinScriptTask.newBuilder()
            .setScriptContent(scriptContent)
            .setParameters(structBuilder.build())
            .build()
        
        return ExecutionDefinition.newBuilder()
            .setKotlinScript(kotlinTask)
            .putAllEnvVars(envVars)
            .build()
    }
}

/**
 * Helper to create execution assignments for tests
 */
object TestExecutionAssignments {
    fun createSimpleShellAssignment(
        executionId: String,
        commands: List<String>,
        artifacts: List<ArtifactMetadata> = emptyList()
    ): ExecutionAssignment {
        return ExecutionAssignment.newBuilder()
            .setExecutionId(executionId)
            .setDefinition(TestExecutionDefinitions.createShellExecution(commands))
            .addAllRequiredArtifacts(artifacts)
            .build()
    }
}

/**
 * Helper to create properly configured services for testing
 */
object TestServiceFactory {
    
    data class TestServices(
        val workerManager: WorkerManagerService,
        val executionEngine: ExecutionEngineService,
        val grpcService: OrchestratorGrpcService
    )
    
    fun createServices(
        jobRepository: InMemoryJobRepository = InMemoryJobRepository(),
        workerManager: WorkerManagerService = WorkerManagerService()
    ): TestServices {
        val mockWorkerFactory = object : WorkerFactory {
            override suspend fun createWorker(job: dev.rubentxu.hodei.jobmanagement.domain.entities.Job, resourcePool: ResourcePool): Either<WorkerCreationError, WorkerInstance> {
                // Check if we have registered workers and use their IDs
                val allWorkers = workerManager.getAllWorkers()
                if (allWorkers.isNotEmpty()) {
                    val workerId = allWorkers.first().id.value
                    return WorkerInstance(
                        workerId = workerId,
                        poolId = resourcePool.id.value,
                        poolType = resourcePool.type,
                        instanceType = "test"
                    ).right()
                } else {
                    return WorkerCreationError.InsufficientResourcesError("No workers available").left()
                }
            }
            override suspend fun destroyWorker(workerId: String): Either<WorkerDeletionError, Unit> = Unit.right()
            override fun supportsPoolType(poolType: String): Boolean = true
        }
        val executionEngine = ExecutionEngineService(jobRepository, workerManager = workerManager, workerFactory = mockWorkerFactory)
        val grpcService = OrchestratorGrpcService(executionEngine, workerManager)
        
        // Configure the communication to avoid circular dependency
        executionEngine.configureWorkerCommunication(grpcService)
        
        return TestServices(workerManager, executionEngine, grpcService)
    }
}