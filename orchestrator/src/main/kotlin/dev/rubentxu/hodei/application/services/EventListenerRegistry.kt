package dev.rubentxu.hodei.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.execution.domain.entities.GenericExecutionEvent
import dev.rubentxu.hodei.execution.domain.entities.EventType
import dev.rubentxu.hodei.execution.domain.entities.ExecutionLog
import dev.rubentxu.hodei.execution.domain.entities.ExecutionSubscription
import dev.rubentxu.hodei.execution.domain.entities.ExecutionUpdate
import dev.rubentxu.hodei.execution.domain.entities.DeliveryMethod
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry for managing execution event listeners and subscriptions.
 * Supports different delivery methods: SSE, WebSocket, and Webhooks.
 */
class EventListenerRegistry {
    private val logger = LoggerFactory.getLogger(EventListenerRegistry::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Map of executionId to list of subscriptions
    private val subscriptions = ConcurrentHashMap<String, MutableList<ExecutionSubscription>>()
    
    // Map of subscriptionId to subscription details
    private val subscriptionById = ConcurrentHashMap<String, ExecutionSubscription>()
    
    // Channels for streaming events per subscription
    private val subscriptionChannels = ConcurrentHashMap<String, Channel<ExecutionUpdate>>()
    
    /**
     * Register a new listener for execution events
     */
    suspend fun register(subscription: ExecutionSubscription): String {
        val subscriptionId = subscription.subscriberId
        
        logger.info("Registering subscription $subscriptionId for execution ${subscription.executionId}")
        
        // Store subscription
        subscriptionById[subscriptionId] = subscription
        subscriptions.computeIfAbsent(subscription.executionId.value) { mutableListOf() }
            .add(subscription)
        
        // Create channel for streaming subscriptions
        if (subscription.deliveryMethod in listOf(DeliveryMethod.SSE, DeliveryMethod.WEBSOCKET)) {
            val channel = Channel<ExecutionUpdate>(Channel.UNLIMITED)
            subscriptionChannels[subscriptionId] = channel
        }
        
        return subscriptionId
    }
    
    /**
     * Unregister a listener
     */
    suspend fun unregister(subscriptionId: String) {
        logger.info("Unregistering subscription $subscriptionId")
        
        val subscription = subscriptionById.remove(subscriptionId) ?: return
        
        // Remove from execution subscriptions
        subscriptions[subscription.executionId.value]?.remove(subscription)
        
        // Close and remove channel
        subscriptionChannels.remove(subscriptionId)?.close()
    }
    
    /**
     * Notify all registered listeners about an execution event
     */
    suspend fun notifyEvent(executionId: DomainId, event: GenericExecutionEvent) {
        val subs = subscriptions[executionId.value] ?: return
        
        logger.debug("Notifying ${subs.size} listeners about event for execution $executionId")
        
        subs.forEach { subscription ->
            if (subscription.includeEvents) {
                
                when (subscription.deliveryMethod) {
                    DeliveryMethod.SSE, DeliveryMethod.WEBSOCKET -> {
                        // Send to channel
                        subscriptionChannels[subscription.subscriberId]?.trySend(
                            ExecutionUpdate.EventUpdate(
                                executionId = executionId,
                                timestamp = event.timestamp,
                                event = event
                            )
                        )
                    }
                    DeliveryMethod.WEBHOOK -> {
                        // Queue webhook delivery
                        scope.launch {
                            deliverWebhook(subscription, ExecutionUpdate.EventUpdate(
                                executionId = executionId,
                                timestamp = event.timestamp,
                                event = event
                            ))
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Notify all registered listeners about execution logs
     */
    suspend fun notifyLog(executionId: DomainId, log: ExecutionLog) {
        val subs = subscriptions[executionId.value] ?: return
        
        subs.forEach { subscription ->
            if (subscription.includeLogs) {
                when (subscription.deliveryMethod) {
                    DeliveryMethod.SSE, DeliveryMethod.WEBSOCKET -> {
                        // Send to channel
                        subscriptionChannels[subscription.subscriberId]?.trySend(
                            ExecutionUpdate.LogUpdate(
                                executionId = executionId,
                                timestamp = log.timestamp,
                                log = log
                            )
                        )
                    }
                    DeliveryMethod.WEBHOOK -> {
                        // Batch logs for webhooks to avoid overwhelming
                        // Implementation would batch and send periodically
                    }
                }
            }
        }
    }
    
    /**
     * Get event stream for a subscription
     */
    fun getEventStream(subscriptionId: String): Flow<ExecutionUpdate>? {
        val channel = subscriptionChannels[subscriptionId] ?: return null
        return channel.receiveAsFlow()
    }
    
    /**
     * Clean up subscriptions for completed execution
     */
    suspend fun cleanupExecution(executionId: DomainId) {
        logger.info("Cleaning up subscriptions for execution $executionId")
        
        val subs = subscriptions.remove(executionId.value) ?: return
        
        subs.forEach { subscription ->
            subscriptionById.remove(subscription.subscriberId)
            subscriptionChannels.remove(subscription.subscriberId)?.close()
        }
    }
    
    private suspend fun deliverWebhook(subscription: ExecutionSubscription, update: ExecutionUpdate) {
        // Implementation would make HTTP call to webhook URL
        logger.debug("Delivering webhook to ${subscription.webhookUrl} for subscription ${subscription.subscriberId}")
    }
}

// Removed: Using domain ExecutionSubscription instead

// Moved to domain entities

// Removed: Using domain ExecutionUpdate instead