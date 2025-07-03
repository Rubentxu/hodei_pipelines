package dev.rubentxu.hodei.shared.domain.events

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Base interface for all context-specific events
 */
interface ContextEvent : DomainEvent

// ================================
// Job Management Context Events
// ================================

@Serializable
data class JobCreated(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId,
    override val eventType: String = "JobCreated",
    override val version: Int = 1,
    val jobName: String,
    val templateId: DomainId?,
    val priority: String,
    val namespace: String
) : ContextEvent

@Serializable
data class JobCancelled(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId,
    override val eventType: String = "JobCancelled",
    override val version: Int = 1,
    val reason: String,
    val cancelledBy: String
) : ContextEvent

@Serializable
data class JobCompleted(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId,
    override val eventType: String = "JobCompleted",
    override val version: Int = 1,
    val executionId: DomainId,
    val success: Boolean,
    val completedAt: Instant
) : ContextEvent

// ================================
// Execution Context Events
// ================================

@Serializable
data class ExecutionStarted(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // executionId
    override val eventType: String = "ExecutionStarted",
    override val version: Int = 1,
    val jobId: DomainId,
    val workerId: String,
    val resourcePoolId: DomainId
) : ContextEvent

@Serializable
data class ExecutionCompleted(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // executionId
    override val eventType: String = "ExecutionCompleted",
    override val version: Int = 1,
    val jobId: DomainId,
    val success: Boolean,
    val exitCode: Int,
    val duration: Long // milliseconds
) : ContextEvent

@Serializable
data class ExecutionFailed(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // executionId
    override val eventType: String = "ExecutionFailed",
    override val version: Int = 1,
    val jobId: DomainId,
    val errorMessage: String,
    val retryable: Boolean
) : ContextEvent

// ================================
// Resource Management Context Events
// ================================

@Serializable
data class ResourcesAllocated(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // resourcePoolId
    override val eventType: String = "ResourcesAllocated",
    override val version: Int = 1,
    val workerId: String,
    val instanceType: String,
    val allocatedResources: Map<String, String>
) : ContextEvent

@Serializable
data class ResourcesReleased(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // resourcePoolId
    override val eventType: String = "ResourcesReleased",
    override val version: Int = 1,
    val workerId: String,
    val reason: String
) : ContextEvent

@Serializable
data class QuotaViolation(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // resourcePoolId
    override val eventType: String = "QuotaViolation",
    override val version: Int = 1,
    val quotaType: String,
    val currentUsage: Long,
    val limit: Long,
    val severity: String // WARNING, CRITICAL
) : ContextEvent

// ================================
// Template Management Context Events
// ================================

@Serializable
data class TemplateCreated(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // templateId
    override val eventType: String = "TemplateCreated",
    override val version: Int = 1,
    val templateName: String,
    val templateVersion: String,
    val createdBy: String
) : ContextEvent

@Serializable
data class TemplateUpdated(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // templateId
    override val eventType: String = "TemplateUpdated",
    override val version: Int = 1,
    val newVersion: String,
    val changes: List<String>,
    val updatedBy: String
) : ContextEvent

@Serializable
data class TemplateUsed(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // templateId
    override val eventType: String = "TemplateUsed",
    override val version: Int = 1,
    val templateVersion: String,
    val jobId: DomainId,
    val usedBy: String
) : ContextEvent

// ================================
// Scheduling Context Events
// ================================

@Serializable
data class JobPlaced(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // jobId
    override val eventType: String = "JobPlaced",
    override val version: Int = 1,
    val resourcePoolId: DomainId,
    val strategy: String,
    val placementScore: Double? = null
) : ContextEvent

@Serializable
data class PlacementFailed(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // jobId
    override val eventType: String = "PlacementFailed",
    override val version: Int = 1,
    val reason: String,
    val triedPools: List<DomainId>,
    val strategy: String
) : ContextEvent

// ================================
// Security Context Events
// ================================

@Serializable
data class UserAuthenticated(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // userId
    override val eventType: String = "UserAuthenticated",
    override val version: Int = 1,
    val username: String,
    val authMethod: String,
    val ipAddress: String? = null
) : ContextEvent

@Serializable
data class PermissionDenied(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // userId
    override val eventType: String = "PermissionDenied",
    override val version: Int = 1,
    val resource: String,
    val action: String,
    val reason: String
) : ContextEvent

@Serializable
data class AuditEvent(
    override val eventId: DomainId = DomainId.generate(),
    override val timestamp: Instant = kotlinx.datetime.Clock.System.now(),
    override val aggregateId: DomainId, // userId or systemId
    override val eventType: String = "AuditEvent",
    override val version: Int = 1,
    val action: String,
    val resource: String,
    val details: Map<String, String> = emptyMap(),
    val success: Boolean
) : ContextEvent