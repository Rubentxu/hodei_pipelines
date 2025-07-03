package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Reactive state machine for job execution lifecycle
 */
class ExecutionStateMachine(
    private val executionId: DomainId,
    private val jobId: DomainId
) {
    private val _currentState = MutableStateFlow(ExecutionState.CREATED)
    val currentState: StateFlow<ExecutionState> = _currentState.asStateFlow()
    
    private val _messageHistory = MutableStateFlow<List<StateTransition>>(emptyList())
    val messageHistory: StateFlow<List<StateTransition>> = _messageHistory.asStateFlow()
    
    private val _pendingAcknowledgments = MutableStateFlow<Set<String>>(emptySet())
    val pendingAcknowledgments: StateFlow<Set<String>> = _pendingAcknowledgments.asStateFlow()
    
    /**
     * Reactive state transitions with acknowledgment tracking
     */
    suspend fun transitionTo(
        newState: ExecutionState, 
        messageId: String? = null,
        requiresAck: Boolean = false,
        metadata: Map<String, String> = emptyMap()
    ): Boolean {
        val currentStateValue = _currentState.value
        
        if (!isValidTransition(currentStateValue, newState)) {
            logger.warn { "Invalid state transition from $currentStateValue to $newState for execution $executionId" }
            return false
        }
        
        val transition = StateTransition(
            from = currentStateValue,
            to = newState,
            timestamp = Clock.System.now(),
            messageId = messageId,
            requiresAck = requiresAck,
            metadata = metadata
        )
        
        // Update state
        _currentState.value = newState
        
        // Add to history
        _messageHistory.value = _messageHistory.value + transition
        
        // Track pending acknowledgment
        if (requiresAck && messageId != null) {
            _pendingAcknowledgments.value = _pendingAcknowledgments.value + messageId
        }
        
        logger.info { "Execution $executionId transitioned from $currentStateValue to $newState" }
        
        return true
    }
    
    /**
     * Acknowledge a message and remove from pending
     */
    suspend fun acknowledgeMessage(messageId: String): Boolean {
        if (messageId in _pendingAcknowledgments.value) {
            _pendingAcknowledgments.value = _pendingAcknowledgments.value - messageId
            logger.debug { "Acknowledged message $messageId for execution $executionId" }
            return true
        }
        return false
    }
    
    /**
     * Check if all critical messages have been acknowledged
     */
    fun hasAllAcknowledgments(): Boolean = _pendingAcknowledgments.value.isEmpty()
    
    /**
     * Get corresponding job status for current execution state
     */
    fun getJobStatus(): JobStatus = when (_currentState.value) {
        ExecutionState.CREATED -> JobStatus.QUEUED
        ExecutionState.ASSIGNED -> JobStatus.PENDING
        ExecutionState.STARTED -> JobStatus.RUNNING
        ExecutionState.COMPLETED -> JobStatus.COMPLETED
        ExecutionState.FAILED -> JobStatus.FAILED
        ExecutionState.CANCELLED -> JobStatus.CANCELLED
        ExecutionState.TIMEOUT -> JobStatus.FAILED
    }
    
    private fun isValidTransition(from: ExecutionState, to: ExecutionState): Boolean {
        return when (from) {
            ExecutionState.CREATED -> to in setOf(ExecutionState.ASSIGNED, ExecutionState.CANCELLED)
            ExecutionState.ASSIGNED -> to in setOf(ExecutionState.STARTED, ExecutionState.CANCELLED, ExecutionState.TIMEOUT)
            ExecutionState.STARTED -> to in setOf(ExecutionState.COMPLETED, ExecutionState.FAILED, ExecutionState.CANCELLED, ExecutionState.TIMEOUT)
            ExecutionState.COMPLETED, ExecutionState.FAILED, ExecutionState.CANCELLED, ExecutionState.TIMEOUT -> false // Terminal states
        }
    }
}

/**
 * Execution states representing the lifecycle
 */
enum class ExecutionState {
    CREATED,      // Execution created but not assigned
    ASSIGNED,     // Assigned to worker, waiting for start
    STARTED,      // Worker started execution
    COMPLETED,    // Execution completed successfully
    FAILED,       // Execution failed
    CANCELLED,    // Execution cancelled
    TIMEOUT       // Execution timed out
}

/**
 * State transition record
 */
data class StateTransition(
    val from: ExecutionState,
    val to: ExecutionState,
    val timestamp: kotlinx.datetime.Instant,
    val messageId: String? = null,
    val requiresAck: Boolean = false,
    val metadata: Map<String, String> = emptyMap()
)