package dev.rubentxu.hodei.execution.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.execution.domain.entities.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import dev.rubentxu.hodei.pipelines.v1.*
import dev.rubentxu.hodei.pipelines.v1.EventType as GrpcEventType
import kotlinx.serialization.json.*
import dev.rubentxu.hodei.infrastructure.grpc.ExecutionEngine
import dev.rubentxu.hodei.infrastructure.grpc.WorkerCommunicationService
import dev.rubentxu.hodei.infrastructure.grpc.NoOpWorkerCommunicationService
import dev.rubentxu.hodei.infrastructure.grpc.WorkerManager
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.domain.worker.WorkerFactory
import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import dev.rubentxu.hodei.application.services.EventListenerRegistry
import dev.rubentxu.hodei.resourcemanagement.application.services.*
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap
import io.github.oshai.kotlinlogging.KotlinLogging
import java.security.SecureRandom
import java.util.Base64

private val logger = KotlinLogging.logger {}

class ExecutionEngineService(
    private val jobRepository: JobRepository,
    private var workerCommunicationService: WorkerCommunicationService = NoOpWorkerCommunicationService(),
    private val workerManager: WorkerManager,
    private val workerFactory: WorkerFactory,
    private val templateService: TemplateService? = null,
    private val eventListenerRegistry: EventListenerRegistry = EventListenerRegistry(),
    private val orchestratorToken: String = generateOrchestratorToken()
) : ExecutionEngine {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val activeExecutions = ConcurrentHashMap<String, ExecutionContext>()
    private val executionEvents = MutableSharedFlow<GenericExecutionEvent>(replay = 100)
    private val executionLogs = MutableSharedFlow<ExecutionLog>(replay = 1000)

    data class ExecutionContext(
        val execution: Execution,
        val job: Job,
        val workerId: String?,
        val resourcePool: ResourcePool,
        val template: dev.rubentxu.hodei.templatemanagement.domain.entities.Template? = null,
        val events: MutableList<GenericExecutionEvent> = mutableListOf(),
        val logs: MutableList<ExecutionLog> = mutableListOf(),
        val stateMachine: ExecutionStateMachine
    )

    /**
     * Configure the worker communication service.
     * This is called after the gRPC service is created to break circular dependency.
     */
    fun configureWorkerCommunication(service: WorkerCommunicationService) {
        this.workerCommunicationService = service
        logger.info { "Worker communication service configured" }
    }

    /**
     * Start execution with resource pool provided by orchestrator.
     * This method can ONLY be called by the orchestrator service.
     * 
     * @param job The job to execute
     * @param resourcePool The resource pool selected by the scheduler
     * @param orchestratorToken Security token to ensure only orchestrator can call this
     */
    suspend fun startExecution(
        job: Job, 
        resourcePool: ResourcePool,
        orchestratorToken: String
    ): Either<String, Execution> {
        // Validate orchestrator token
        if (orchestratorToken != this.orchestratorToken) {
            logger.error { "Unauthorized execution attempt for job ${job.id}. Invalid orchestrator token." }
            return "Unauthorized: Jobs can only be executed through the orchestrator service".left()
        }
        
        logger.info { "Starting execution for job ${job.id} in pool ${resourcePool.name}" }

        // Validate template if job uses one and get template for execution context
        var template: dev.rubentxu.hodei.templatemanagement.domain.entities.Template? = null
        if (job.templateId != null) {
            val templateValidation = validateTemplateForExecution(job)
            if (templateValidation.isLeft()) {
                return templateValidation.leftOrNull()!!.left()
            }
            // Get the template for use in job spec conversion
            template = getTemplateForJob(job)
        }

        // Create worker using factory
        val workerResult = workerFactory.createWorker(job, resourcePool)
        if (workerResult.isLeft()) {
            logger.error { "Failed to create worker for job ${job.id}: ${workerResult.leftOrNull()?.message}" }
            return "Failed to create worker: ${workerResult.leftOrNull()?.message}".left()
        }
        
        val workerInstance = workerResult.getOrNull()!!
        logger.info { "Created worker ${workerInstance.workerId} for job ${job.id}" }
        
        // Wait for worker to register (with timeout)
        val registeredWorkerNullable = workerManager.waitForWorkerRegistration(workerInstance.workerId, 30_000)
        if (registeredWorkerNullable == null) {
            logger.error { "Worker ${workerInstance.workerId} failed to register within timeout" }
            // Clean up the worker
            workerFactory.destroyWorker(workerInstance.workerId)
            return "Worker failed to register within timeout".left()
        }
        val registeredWorker: AvailableWorker = registeredWorkerNullable

        // Create execution
        val execution = Execution.create(
            jobId = job.id,
            workerId = DomainId(registeredWorker.workerId),
            spec = convertJobToExecutionSpec(job)
        )

        // Create reactive state machine for this execution
        val stateMachine = ExecutionStateMachine(execution.id, job.id)

        // Store execution context with state machine
        val context = ExecutionContext(
            execution = execution,
            job = job,
            workerId = registeredWorker.workerId,
            resourcePool = resourcePool,
            template = template,
            stateMachine = stateMachine
        )
        activeExecutions[execution.id.value] = context

        // Set up reactive state monitoring - use immediate dispatcher for tests
        scope.launch(kotlinx.coroutines.Dispatchers.Unconfined) {
            stateMachine.currentState.collect { state ->
                logger.debug { "Execution ${execution.id} state changed to $state" }
                // Update job status reactively based on state machine
                val currentContext = activeExecutions[execution.id.value]
                if (currentContext != null) {
                    val newJobStatus = stateMachine.getJobStatus()
                    val currentJob = currentContext.job
                    
                    if (currentJob.status != newJobStatus) {
                        try {
                            val updatedJob = when (state) {
                                ExecutionState.ASSIGNED -> currentJob.start(execution.id) // Use start() method
                                ExecutionState.STARTED -> currentJob.updateStatus(JobStatus.RUNNING)
                                ExecutionState.COMPLETED -> currentJob.complete()
                                ExecutionState.FAILED -> currentJob.fail("Execution failed")
                                ExecutionState.CANCELLED -> currentJob.cancel("Execution cancelled")
                                else -> currentJob
                            }
                            
                            jobRepository.update(updatedJob).fold(
                                { error -> logger.error { "Failed to update job status: $error" } },
                                { savedJob -> 
                                    logger.info { "Updated job ${currentJob.id} status from ${currentJob.status} to ${savedJob.status}" }
                                    // Update the context with the new job
                                    activeExecutions[execution.id.value] = currentContext.copy(job = savedJob)
                                }
                            )
                        } catch (e: Exception) {
                            logger.error(e) { "Error updating job status for execution ${execution.id}" }
                        }
                    }
                }
            }
        }

        // Initialize state machine
        stateMachine.transitionTo(ExecutionState.CREATED)

        // Create execution assignment for new protocol
        val executionAssignment = ExecutionAssignment.newBuilder()
            .setExecutionId(execution.id.value)
            .setDefinition(convertJobToExecutionDefinition(job, template))
            .addAllRequiredArtifacts(emptyList())
            .build()

        // Assign worker to execution
        val workerAssigned = workerManager.assignWorkerToExecution(registeredWorker.workerId, execution.id)
        if (!workerAssigned) {
            logger.error { "Failed to assign worker ${registeredWorker.workerId} to execution ${execution.id}" }
            workerFactory.destroyWorker(registeredWorker.workerId)
            return "Failed to assign worker".left()
        }

        // Send execution assignment to worker via new protocol with reactive acknowledgment
        val messageId = UUID.randomUUID().toString()
        val assignmentSent = workerCommunicationService.sendExecutionAssignment(registeredWorker.workerId, executionAssignment)
        
        if (assignmentSent) {
            // Transition to ASSIGNED state with acknowledgment tracking
            stateMachine.transitionTo(
                ExecutionState.ASSIGNED, 
                messageId = messageId,
                requiresAck = true,
                metadata = mapOf(
                    "workerId" to registeredWorker.workerId,
                    "messageType" to "execution_assignment"
                )
            )
            logger.info { "Execution assignment sent to worker ${registeredWorker.workerId} for execution ${execution.id}" }
        } else {
            logger.error { "Failed to send execution assignment to worker ${registeredWorker.workerId}" }
            workerFactory.destroyWorker(registeredWorker.workerId)
            return "Failed to send execution assignment".left()
        }

        // Update context with the worker assignment
        activeExecutions[execution.id.value] = context.copy(
            execution = execution.assignWorker(DomainId(registeredWorker.workerId))
        )

        logger.info { "Execution ${execution.id} started for job ${job.id} on worker ${registeredWorker.workerId}" }
        
        return execution.right()
    }

    suspend fun cancelExecution(executionId: DomainId, reason: String = "User requested"): Either<String, Unit> {
        val context = activeExecutions[executionId.value]
        if (context == null) {
            return "Execution not found".left()
        }

        val cancelSignal = CancelSignal.newBuilder()
            .setReason(reason)
            .build()

        context.workerId?.let { workerId ->
            workerCommunicationService.sendCancelSignal(workerId, cancelSignal)
        }

        return Unit.right()
    }

    override suspend fun handleStatusUpdate(statusUpdate: StatusUpdate, executionId: String) {
        val context = activeExecutions[executionId]
        if (context == null) {
            logger.warn { "Received status update for unknown execution $executionId" }
            return
        }

        logger.debug { "Execution $executionId status update: ${statusUpdate.eventType}" }

        // Use state machine for status updates that indicate execution has started
        when (statusUpdate.eventType) {
            GrpcEventType.STAGE_STARTED, GrpcEventType.STEP_STARTED -> {
                // Transition to STARTED if not already there
                if (context.stateMachine.currentState.value == ExecutionState.ASSIGNED) {
                    context.stateMachine.transitionTo(
                        ExecutionState.STARTED,
                        metadata = mapOf(
                            "eventType" to statusUpdate.eventType.name,
                            "message" to statusUpdate.message
                        )
                    )
                }
            }
            else -> {
                // Other status updates just get logged but don't change state
                logger.debug { "Status update ${statusUpdate.eventType} for execution $executionId" }
            }
        }

        // Update execution status based on event type
        val updatedExecution = when (statusUpdate.eventType) {
            GrpcEventType.STAGE_STARTED, GrpcEventType.STEP_STARTED -> 
                context.execution.updateStatus(ExecutionStatus.RUNNING)
            GrpcEventType.STAGE_COMPLETED, GrpcEventType.STEP_COMPLETED -> 
                context.execution // Keep current status
            else -> context.execution
        }

        // Create domain event
        val domainEvent = GenericExecutionEvent(
            id = DomainId.generate(),
            executionId = DomainId(executionId),
            timestamp = Clock.System.now(),
            type = convertEventType(statusUpdate.eventType),
            stageName = if (statusUpdate.eventType in listOf(GrpcEventType.STAGE_STARTED, GrpcEventType.STAGE_COMPLETED)) 
                statusUpdate.message else null,
            stepName = if (statusUpdate.eventType in listOf(GrpcEventType.STEP_STARTED, GrpcEventType.STEP_COMPLETED)) 
                statusUpdate.message else null,
            message = statusUpdate.message,
            metadata = emptyMap()
        )
        
        // Store event in context
        context.events.add(domainEvent)
        
        // Emit event for streaming
        executionEvents.emit(domainEvent)
        
        // Notify registered listeners
        scope.launch {
            eventListenerRegistry.notifyEvent(DomainId(executionId), domainEvent)
        }
        
        // Update context
        activeExecutions[executionId] = context.copy(execution = updatedExecution)
    }
    
    private fun convertEventType(grpcEventType: GrpcEventType): EventType {
        return when (grpcEventType) {
            GrpcEventType.STAGE_STARTED -> EventType.STAGE_STARTED
            GrpcEventType.STAGE_COMPLETED -> EventType.STAGE_COMPLETED
            GrpcEventType.STEP_STARTED -> EventType.STEP_STARTED
            GrpcEventType.STEP_COMPLETED -> EventType.STEP_COMPLETED
            else -> EventType.STATUS_UPDATE
        }
    }

    override suspend fun handleExecutionResult(result: ExecutionResult, executionId: String) {
        println("*** HANDLE EXECUTION RESULT CALLED for $executionId: success=${result.success} ***")
        logger.info { "Received execution result for execution $executionId: success=${result.success}, exitCode=${result.exitCode}" }
        val context = activeExecutions[executionId]
        if (context == null) {
            logger.warn { "Received execution result for unknown execution $executionId. Active executions: ${activeExecutions.keys}" }
            return
        }

        logger.debug { "Processing execution result for $executionId: success=${result.success}, exitCode=${result.exitCode}" }

        // Use state machine for reactive state transitions
        val targetState = if (result.success) ExecutionState.COMPLETED else ExecutionState.FAILED
        val transitionSuccessful = context.stateMachine.transitionTo(
            targetState,
            metadata = mapOf(
                "exitCode" to result.exitCode.toString(),
                "details" to result.details,
                "success" to result.success.toString()
            )
        )

        if (transitionSuccessful) {
            logger.info { "Execution $executionId transitioned to $targetState" }
            
            // Update execution status
            val updatedExecution = if (result.success) {
                context.execution.updateStatus(ExecutionStatus.SUCCESS)
            } else {
                context.execution.updateStatus(ExecutionStatus.FAILED)
            }

            // Update context
            activeExecutions[executionId] = context.copy(execution = updatedExecution)
            
            // Also update job directly to ensure immediate completion for tests
            val finalJob = if (result.success) {
                context.job.complete()
            } else {
                context.job.fail(result.details)
            }
            
            jobRepository.update(finalJob).fold(
                { error -> logger.error { "Failed to update job directly: $error" } },
                { savedJob -> 
                    logger.info { "Directly updated job ${context.job.id} to final status ${savedJob.status}" }
                    activeExecutions[executionId] = activeExecutions[executionId]!!.copy(job = savedJob)
                }
            )
            
            // Release worker
            context.workerId?.let { workerId ->
                workerManager.releaseWorker(workerId)
                // Destroy worker instance
                scope.launch {
                    workerFactory.destroyWorker(workerId)
                }
            }
            
            // Clean up event listeners
            scope.launch {
                eventListenerRegistry.cleanupExecution(DomainId(executionId))
            }

            // Remove from active executions synchronously to ensure completion in tests
            activeExecutions.remove(executionId)
            logger.info { "Removed execution $executionId from active executions" }
        } else {
            logger.error { "Failed to transition execution $executionId to $targetState" }
        }
    }

    override suspend fun handleLogChunk(logChunk: LogChunk, executionId: String) {
        val context = activeExecutions[executionId]
        if (context == null) {
            logger.warn { "Received log chunk for unknown execution $executionId" }
            return
        }

        // Convert gRPC log chunk to domain log
        val domainLog = ExecutionLog(
            id = DomainId.generate(),
            executionId = DomainId(executionId),
            timestamp = Clock.System.now(),
            level = LogLevel.INFO,
            stream = convertLogStream(logChunk.stream),
            stageName = null,
            stepName = null,
            message = String(logChunk.content.toByteArray()),
            metadata = emptyMap()
        )

        context.logs.add(domainLog)

        // Emit log for real-time streaming
        executionLogs.emit(domainLog)
        
        // Notify registered listeners
        scope.launch {
            eventListenerRegistry.notifyLog(DomainId(executionId), domainLog)
        }
    }

    // Streaming APIs for real-time updates
    fun getExecutionEvents(): Flow<GenericExecutionEvent> = executionEvents.asSharedFlow()
    fun getExecutionLogs(): Flow<ExecutionLog> = executionLogs.asSharedFlow()
    
    /**
     * Subscribe to execution events
     */
    suspend fun subscribeToExecution(subscription: ExecutionSubscription): Either<String, String> {
        val execution = activeExecutions[subscription.executionId.value]
        if (execution == null) {
            return "Execution ${subscription.executionId} not found".left()
        }
        
        val subscriptionId = eventListenerRegistry.register(subscription)
        logger.info { "Created subscription $subscriptionId for execution ${subscription.executionId}" }
        
        return subscriptionId.right()
    }
    
    /**
     * Unsubscribe from execution events
     */
    suspend fun unsubscribeFromExecution(subscriptionId: String) {
        eventListenerRegistry.unregister(subscriptionId)
    }
    
    /**
     * Get event stream for a subscription
     */
    fun getExecutionStream(subscriptionId: String): Flow<ExecutionUpdate>? {
        return eventListenerRegistry.getEventStream(subscriptionId)
    }

    fun getExecutionContext(executionId: DomainId): ExecutionContext? {
        return activeExecutions[executionId.value]
    }

    override fun getActiveExecutions(): List<ExecutionContext> {
        return activeExecutions.values.toList()
    }

    private suspend fun validateTemplateForExecution(job: Job): Either<String, Unit> {
        if (templateService == null) {
            logger.warn { "Template service not available, cannot validate template for job ${job.id}" }
            return "Template service not available".left()
        }

        val templateId = job.templateId!!
        val templateVersion = job.templateVersion?.value

        return try {
            val templateResult = if (templateVersion != null) {
                templateService.getTemplate(templateId).fold(
                    { error -> return "Template not found: ${error.message}".left() },
                    { template ->
                        templateService.getTemplateByNameAndVersion(template.name, templateVersion)
                    }
                )
            } else {
                templateService.getTemplate(templateId)
            }

            templateResult.fold(
                { error -> "Template validation failed: ${error.message}".left() },
                { template ->
                    if (template.status != TemplateStatus.PUBLISHED) {
                        "Template ${template.name} (${template.version.value}) is not published and cannot be used for job execution".left()
                    } else {
                        Unit.right()
                    }
                }
            )
        } catch (e: Exception) {
            logger.error(e) { "Error validating template for job ${job.id}" }
            "Error validating template: ${e.message}".left()
        }
    }

    private suspend fun getTemplateForJob(job: Job): dev.rubentxu.hodei.templatemanagement.domain.entities.Template? {
        if (templateService == null || job.templateId == null) return null
        
        return try {
            val templateResult = if (job.templateVersion != null) {
                templateService.getTemplate(job.templateId!!).fold(
                    { null },
                    { template ->
                        templateService.getTemplateByNameAndVersion(template.name, job.templateVersion!!.value).fold(
                            { null },
                            { it }
                        )
                    }
                )
            } else {
                templateService.getTemplate(job.templateId!!).fold({ null }, { it })
            }
            templateResult
        } catch (e: Exception) {
            logger.warn(e) { "Failed to get template for job ${job.id}" }
            null
        }
    }

    private fun convertJobToExecutionSpec(job: Job): Map<String, Any> {
        // Convert job to execution spec - simplified for now
        return mapOf(
            "name" to job.name,
            "parameters" to job.parameters,
            "resourceRequirements" to job.resourceRequirements
        )
    }


    private fun convertJobToExecutionDefinition(job: Job, template: dev.rubentxu.hodei.templatemanagement.domain.entities.Template?): ExecutionDefinition {
        val builder = ExecutionDefinition.newBuilder()
            .putAllEnvVars(convertParametersToStringMap(job.parameters))
        
        // For now, create a simple shell task since we don't have job content model
        // This will be properly implemented when the job content model is created
        builder.setShell(ShellTask.newBuilder()
            .addCommands("echo 'Job ${job.name} execution started'")
            .addCommands("echo 'This is a placeholder execution'")
            .build())
        
        return builder.build()
    }
    
    private fun convertParametersToStringMap(parameters: JsonObject): Map<String, String> {
        return parameters.mapValues { (_, value) ->
            when {
                value.toString().startsWith("\"") && value.toString().endsWith("\"") -> 
                    value.toString().removeSurrounding("\"")
                else -> value.toString()
            }
        }
    }



    private fun convertLogStream(grpcStream: dev.rubentxu.hodei.pipelines.v1.LogStream): dev.rubentxu.hodei.execution.domain.entities.LogStream {
        return when (grpcStream) {
            dev.rubentxu.hodei.pipelines.v1.LogStream.STDOUT -> dev.rubentxu.hodei.execution.domain.entities.LogStream.STDOUT
            dev.rubentxu.hodei.pipelines.v1.LogStream.STDERR -> dev.rubentxu.hodei.execution.domain.entities.LogStream.STDERR
            else -> dev.rubentxu.hodei.execution.domain.entities.LogStream.STDOUT
        }
    }

    // Deprecated methods - kept for now but not used

    private fun replaceJobParameters(value: String, job: Job): String {
        var result = value
        
        // Replace common parameter patterns
        job.parameters.forEach { (key, paramValue) ->
            val placeholder = "{{.params.$key}}"
            if (paramValue is JsonPrimitive) {
                result = result.replace(placeholder, paramValue.content)
            }
        }
        
        // Replace job metadata
        result = result.replace("{{.job.name}}", job.name)
        result = result.replace("{{.job.id}}", job.id.value)
        
        return result
    }

    private fun parseTimeoutToSeconds(timeout: String): Int {
        return try {
            when {
                timeout.endsWith("s") -> timeout.dropLast(1).toInt()
                timeout.endsWith("m") -> timeout.dropLast(1).toInt() * 60
                timeout.endsWith("h") -> timeout.dropLast(1).toInt() * 3600
                else -> timeout.toInt() // assume seconds
            }
        } catch (e: NumberFormatException) {
            logger.warn { "Invalid timeout format: $timeout, using default 300s" }
            300
        }
    }
    
    /**
     * Get the orchestrator token for this execution engine instance.
     * This is used by the orchestrator to authenticate execution requests.
     */
    fun getOrchestratorToken(): String = orchestratorToken
    
    companion object {
        /**
         * Generates a secure random token for orchestrator authentication
         */
        private fun generateOrchestratorToken(): String {
            val random = SecureRandom()
            val bytes = ByteArray(32)
            random.nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}