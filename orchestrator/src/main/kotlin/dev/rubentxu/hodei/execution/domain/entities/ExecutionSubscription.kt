package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ExecutionSubscription(
    val id: DomainId = DomainId.generate(),
    val subscriberId: String = UUID.randomUUID().toString(),
    val executionId: DomainId,
    val jobId: DomainId? = null,
    val subscriptionType: SubscriptionType = SubscriptionType.ALL,
    val deliveryMethod: DeliveryMethod = DeliveryMethod.SSE,
    val webhookUrl: String? = null,
    val includeEvents: Boolean = true,
    val includeLogs: Boolean = true,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
enum class SubscriptionType {
    EVENTS_ONLY,
    LOGS_ONLY,
    ALL
}

@Serializable
enum class DeliveryMethod {
    SSE,        // Server-Sent Events
    WEBSOCKET,  // WebSocket
    WEBHOOK     // HTTP webhook
}