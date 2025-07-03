package dev.rubentxu.hodei.domain.quota

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class QuotaViolation(
    val id: DomainId,
    val poolId: DomainId,
    val quotaId: DomainId,
    val violationType: ViolationType,
    val resource: ResourceType,
    val limit: Double,
    val attempted: Double,
    val current: Double,
    val severity: ViolationSeverity,
    val action: ViolationAction,
    val message: String,
    val context: ViolationContext,
    val timestamp: Instant,
    val resolvedAt: Instant? = null,
    val resolvedBy: String? = null
) {
    fun resolve(resolvedBy: String): QuotaViolation =
        copy(
            resolvedAt = kotlinx.datetime.Clock.System.now(),
            resolvedBy = resolvedBy
        )
    
    val isResolved: Boolean
        get() = resolvedAt != null
    
    val durationMinutes: Long?
        get() = resolvedAt?.let { 
            (it.epochSeconds - timestamp.epochSeconds) / 60 
        }
    
    companion object {
        fun create(
            poolId: DomainId,
            quotaId: DomainId,
            violationType: ViolationType,
            resource: ResourceType,
            limit: Double,
            attempted: Double,
            current: Double,
            severity: ViolationSeverity,
            action: ViolationAction,
            context: ViolationContext
        ): QuotaViolation {
            val message = generateMessage(violationType, resource, limit, attempted, current, action)
            
            return QuotaViolation(
                id = DomainId.generate(),
                poolId = poolId,
                quotaId = quotaId,
                violationType = violationType,
                resource = resource,
                limit = limit,
                attempted = attempted,
                current = current,
                severity = severity,
                action = action,
                message = message,
                context = context,
                timestamp = kotlinx.datetime.Clock.System.now()
            )
        }
        
        private fun generateMessage(
            type: ViolationType,
            resource: ResourceType,
            limit: Double,
            attempted: Double,
            current: Double,
            action: ViolationAction
        ): String {
            val resourceName = resource.name.lowercase().replace("_", " ")
            
            return when (type) {
                ViolationType.HARD_LIMIT_EXCEEDED -> 
                    "Hard limit exceeded for $resourceName: attempted $attempted, limit $limit (current: $current). Action: ${action.name.lowercase()}"
                ViolationType.SOFT_LIMIT_EXCEEDED -> 
                    "Soft limit exceeded for $resourceName: attempted $attempted, limit $limit (current: $current). Action: ${action.name.lowercase()}"
                ViolationType.THRESHOLD_WARNING -> 
                    "Warning threshold reached for $resourceName: current $current, limit $limit (${(current/limit*100).toInt()}%)"
                ViolationType.CRITICAL_USAGE -> 
                    "Critical usage detected for $resourceName: current $current, limit $limit (${(current/limit*100).toInt()}%)"
            }
        }
    }
}

@Serializable
data class ViolationContext(
    val jobId: DomainId? = null,
    val workerId: DomainId? = null,
    val executionId: DomainId? = null,
    val userId: String? = null,
    val operationType: String? = null,
    val additionalData: Map<String, String> = emptyMap()
) {
    companion object {
        fun forJob(jobId: DomainId, userId: String? = null): ViolationContext =
            ViolationContext(
                jobId = jobId,
                userId = userId,
                operationType = "job_execution"
            )
        
        fun forWorker(workerId: DomainId, operationType: String = "worker_assignment"): ViolationContext =
            ViolationContext(
                workerId = workerId,
                operationType = operationType
            )
        
        fun forExecution(executionId: DomainId, jobId: DomainId? = null, workerId: DomainId? = null): ViolationContext =
            ViolationContext(
                executionId = executionId,
                jobId = jobId,
                workerId = workerId,
                operationType = "execution_start"
            )
    }
}

@Serializable
enum class ViolationType {
    HARD_LIMIT_EXCEEDED,    // Hard quota exceeded, operation blocked
    SOFT_LIMIT_EXCEEDED,    // Soft quota exceeded, operation allowed but alerted
    THRESHOLD_WARNING,      // Warning threshold reached
    CRITICAL_USAGE         // Critical usage level reached
}

@Serializable
enum class ViolationAction {
    BLOCKED,               // Operation was blocked
    ALLOWED_WITH_WARNING,  // Operation allowed but warning sent
    QUEUED,               // Operation queued until resources available
    SCALED_UP,            // Triggered auto-scaling
    NOTIFICATION_SENT,    // Notification sent to administrators
    NO_ACTION            // No action taken (advisory only)
}

@Serializable
data class QuotaEnforcementResult(
    val allowed: Boolean,
    val action: ViolationAction,
    val violations: List<ResourceViolation> = emptyList(),
    val warnings: List<ResourceAlert> = emptyList(),
    val quotaViolation: QuotaViolation? = null,
    val message: String? = null
) {
    val hasViolations: Boolean
        get() = violations.isNotEmpty()
    
    val hasWarnings: Boolean
        get() = warnings.isNotEmpty()
    
    val requiresAttention: Boolean
        get() = hasViolations || hasWarnings
    
    companion object {
        fun allowed(): QuotaEnforcementResult =
            QuotaEnforcementResult(allowed = true, action = ViolationAction.NO_ACTION)
        
        fun blocked(violations: List<ResourceViolation>, quotaViolation: QuotaViolation): QuotaEnforcementResult =
            QuotaEnforcementResult(
                allowed = false,
                action = ViolationAction.BLOCKED,
                violations = violations,
                quotaViolation = quotaViolation,
                message = "Operation blocked due to quota violations"
            )
        
        fun allowedWithWarning(warnings: List<ResourceAlert>): QuotaEnforcementResult =
            QuotaEnforcementResult(
                allowed = true,
                action = ViolationAction.ALLOWED_WITH_WARNING,
                warnings = warnings,
                message = "Operation allowed but approaching quota limits"
            )
    }
}