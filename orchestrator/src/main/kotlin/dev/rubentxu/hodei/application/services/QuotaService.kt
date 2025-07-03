package dev.rubentxu.hodei.application.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.domain.quota.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class QuotaService(
    private val quotaRepository: QuotaRepository,
    private val usageRepository: UsageRepository,
    private val violationRepository: ViolationRepository
) {
    
    suspend fun createQuota(
        poolId: DomainId,
        limits: ResourceLimits,
        policy: QuotaPolicy = QuotaPolicy.HARD,
        description: String? = null,
        createdBy: String
    ): Either<DomainError, ResourceQuota> {
        logger.info { "Creating quota for pool $poolId with policy $policy" }
        
        return quotaRepository.exists(poolId).flatMap { exists ->
            if (exists) {
                return@flatMap BusinessLogicError.DuplicateEntity(
                    message = "Quota already exists for pool",
                    entityType = "ResourceQuota",
                    entityId = poolId.value
                ).left()
            } else {
                val quota = ResourceQuota.create(
                    poolId = poolId,
                    limits = limits,
                    policy = policy,
                    description = description,
                    createdBy = createdBy
                )
                
                quotaRepository.save(quota).flatMap { savedQuota ->
                    // Initialize usage tracking for this pool
                    val initialUsage = ResourceUsage.empty(poolId)
                    usageRepository.save(initialUsage).flatMap {
                        logger.info { "Created quota ${savedQuota.id} for pool $poolId" }
                        savedQuota.right()
                    }
                }
            }
        }
    }
    
    suspend fun updateQuotaLimits(
        quotaId: DomainId,
        newLimits: ResourceLimits
    ): Either<DomainError, ResourceQuota> {
        logger.info { "Updating limits for quota $quotaId" }
        
        return quotaRepository.findById(quotaId).flatMap { quota ->
            if (quota == null) {
                return@flatMap RepositoryError.NotFoundError(
                    message = "Quota not found",
                    entityType = "ResourceQuota",
                    entityId = quotaId.value
                ).left()
            } else {
                val updatedQuota = quota.updateLimits(newLimits)
                quotaRepository.save(updatedQuota).flatMap { savedQuota ->
                    logger.info { "Updated limits for quota $quotaId" }
                    savedQuota.right()
                }
            }
        }
    }
    
    suspend fun updateQuotaPolicy(
        quotaId: DomainId,
        newPolicy: QuotaPolicy
    ): Either<DomainError, ResourceQuota> {
        logger.info { "Updating policy for quota $quotaId to $newPolicy" }
        
        return quotaRepository.findById(quotaId).flatMap { quota ->
            if (quota == null) {
                return@flatMap RepositoryError.NotFoundError(
                    message = "Quota not found",
                    entityType = "ResourceQuota",
                    entityId = quotaId.value
                ).left()
            } else {
                val updatedQuota = quota.updatePolicy(newPolicy)
                quotaRepository.save(updatedQuota).flatMap { savedQuota ->
                    logger.info { "Updated policy for quota $quotaId to $newPolicy" }
                    savedQuota.right()
                }
            }
        }
    }
    
    suspend fun enableQuota(quotaId: DomainId): Either<DomainError, ResourceQuota> {
        return toggleQuotaStatus(quotaId, true)
    }
    
    suspend fun disableQuota(quotaId: DomainId): Either<DomainError, ResourceQuota> {
        return toggleQuotaStatus(quotaId, false)
    }
    
    private suspend fun toggleQuotaStatus(quotaId: DomainId, enabled: Boolean): Either<DomainError, ResourceQuota> {
        val action = if (enabled) "Enabling" else "Disabling"
        logger.info { "$action quota $quotaId" }
        
        return quotaRepository.findById(quotaId).flatMap { quota ->
            if (quota == null) {
                return@flatMap RepositoryError.NotFoundError(
                    message = "Quota not found",
                    entityType = "ResourceQuota",
                    entityId = quotaId.value
                ).left()
            } else {
                val updatedQuota = if (enabled) quota.enable() else quota.disable()
                quotaRepository.save(updatedQuota).flatMap { savedQuota ->
                    logger.info { "${action}d quota $quotaId" }
                    savedQuota.right()
                }
            }
        }
    }
    
    suspend fun getQuotaByPoolId(poolId: DomainId): Either<DomainError, ResourceQuota?> {
        return quotaRepository.findByPoolId(poolId)
    }
    
    suspend fun getCurrentUsage(poolId: DomainId): Either<DomainError, ResourceUsage> {
        return usageRepository.findByPoolId(poolId).flatMap { usage ->
            if (usage == null) {
                // Create empty usage if not exists
                val emptyUsage = ResourceUsage.empty(poolId)
                usageRepository.save(emptyUsage)
            } else {
                usage.right()
            }
        }
    }
    
    suspend fun checkQuotaEnforcement(
        poolId: DomainId,
        requestedResources: ResourceRequest,
        context: ViolationContext
    ): Either<DomainError, QuotaEnforcementResult> {
        logger.debug { "Checking quota enforcement for pool $poolId" }
        
        return quotaRepository.findByPoolId(poolId).flatMap { quota ->
            if (quota == null || !quota.enabled) {
                // No quota or disabled - allow operation
                QuotaEnforcementResult.allowed().right()
            } else {
                getCurrentUsage(poolId).flatMap { currentUsage ->
                    checkEnforcement(quota, currentUsage, requestedResources, context)
                }
            }
        }
    }
    
    private suspend fun checkEnforcement(
        quota: ResourceQuota,
        currentUsage: ResourceUsage,
        requestedResources: ResourceRequest,
        context: ViolationContext
    ): Either<DomainError, QuotaEnforcementResult> {
        // Calculate what usage would be after the operation
        val projectedUsage = currentUsage.addJobResources(
            cpuCores = requestedResources.cpuCores,
            memoryGB = requestedResources.memoryGB,
            storageGB = requestedResources.storageGB
        )
        
        // Check for violations
        val violations = projectedUsage.getViolations(quota.limits)
        val warnings = projectedUsage.shouldTriggerAlert(quota.limits, quota.alertThresholds)
        
        return when (quota.policy) {
            QuotaPolicy.HARD -> handleHardPolicy(quota, violations, warnings, context)
            QuotaPolicy.SOFT -> handleSoftPolicy(quota, violations, warnings, context)
            QuotaPolicy.ADVISORY -> handleAdvisoryPolicy(warnings)
        }
    }
    
    private suspend fun handleHardPolicy(
        quota: ResourceQuota,
        violations: List<ResourceViolation>,
        warnings: List<ResourceAlert>,
        context: ViolationContext
    ): Either<DomainError, QuotaEnforcementResult> {
        return if (violations.isNotEmpty()) {
            // Block operation and record violation
            val violation = QuotaViolation.create(
                poolId = quota.poolId,
                quotaId = quota.id,
                violationType = ViolationType.HARD_LIMIT_EXCEEDED,
                resource = violations.first().resource,
                limit = violations.first().limit,
                attempted = violations.first().current,
                current = violations.first().current - violations.first().excessAmount,
                severity = violations.first().severity,
                action = ViolationAction.BLOCKED,
                context = context
            )
            
            violationRepository.save(violation).flatMap {
                QuotaEnforcementResult.blocked(violations, violation).right()
            }
        } else if (warnings.isNotEmpty()) {
            QuotaEnforcementResult.allowedWithWarning(warnings).right()
        } else {
            QuotaEnforcementResult.allowed().right()
        }
    }
    
    private suspend fun handleSoftPolicy(
        quota: ResourceQuota,
        violations: List<ResourceViolation>,
        warnings: List<ResourceAlert>,
        context: ViolationContext
    ): Either<DomainError, QuotaEnforcementResult> {
        return if (violations.isNotEmpty()) {
            // Allow but record violation
            val violation = QuotaViolation.create(
                poolId = quota.poolId,
                quotaId = quota.id,
                violationType = ViolationType.SOFT_LIMIT_EXCEEDED,
                resource = violations.first().resource,
                limit = violations.first().limit,
                attempted = violations.first().current,
                current = violations.first().current - violations.first().excessAmount,
                severity = violations.first().severity,
                action = ViolationAction.ALLOWED_WITH_WARNING,
                context = context
            )
            
            violationRepository.save(violation).flatMap {
                QuotaEnforcementResult(
                    allowed = true,
                    action = ViolationAction.ALLOWED_WITH_WARNING,
                    violations = violations,
                    warnings = warnings,
                    quotaViolation = violation,
                    message = "Operation allowed but quota limits exceeded"
                ).right()
            }
        } else if (warnings.isNotEmpty()) {
            QuotaEnforcementResult.allowedWithWarning(warnings).right()
        } else {
            QuotaEnforcementResult.allowed().right()
        }
    }
    
    private fun handleAdvisoryPolicy(warnings: List<ResourceAlert>): Either<DomainError, QuotaEnforcementResult> {
        return if (warnings.isNotEmpty()) {
            QuotaEnforcementResult.allowedWithWarning(warnings).right()
        } else {
            QuotaEnforcementResult.allowed().right()
        }
    }
    
    suspend fun updateUsage(
        poolId: DomainId,
        operation: UsageOperation
    ): Either<DomainError, ResourceUsage> {
        logger.debug { "Updating usage for pool $poolId: $operation" }
        
        return usageRepository.updateUsage(poolId) { currentUsage ->
            when (operation) {
                is UsageOperation.AddJob -> currentUsage.addJobResources(
                    operation.cpuCores, operation.memoryGB, operation.storageGB
                )
                is UsageOperation.RemoveJob -> currentUsage.removeJobResources(
                    operation.cpuCores, operation.memoryGB, operation.storageGB
                )
                is UsageOperation.AddWorker -> currentUsage.addWorker()
                is UsageOperation.RemoveWorker -> currentUsage.removeWorker()
            }
        }
    }
    
    suspend fun getViolations(poolId: DomainId): Either<DomainError, List<QuotaViolation>> {
        return violationRepository.findByPoolId(poolId)
    }
    
    suspend fun getUnresolvedViolations(poolId: DomainId): Either<DomainError, List<QuotaViolation>> {
        return violationRepository.findByPoolIdAndUnresolved(poolId)
    }
    
    suspend fun resolveViolation(violationId: DomainId, resolvedBy: String): Either<DomainError, QuotaViolation> {
        logger.info { "Resolving violation $violationId by $resolvedBy" }
        return violationRepository.markResolved(violationId, resolvedBy)
    }
    
    suspend fun deleteQuota(quotaId: DomainId): Either<DomainError, Unit> {
        logger.info { "Deleting quota $quotaId" }
        
        return quotaRepository.findById(quotaId).flatMap { quota ->
            if (quota == null) {
                return@flatMap RepositoryError.NotFoundError(
                    message = "Quota not found",
                    entityType = "ResourceQuota",
                    entityId = quotaId.value
                ).left()
            } else {
                quotaRepository.delete(quotaId).flatMap {
                    // Also clean up usage tracking
                    usageRepository.delete(quota.poolId).flatMap {
                        logger.info { "Deleted quota $quotaId and associated usage tracking" }
                        Unit.right()
                    }
                }
            }
        }
    }
}

@kotlinx.serialization.Serializable
data class ResourceRequest(
    val cpuCores: Double,
    val memoryGB: Double,
    val storageGB: Double = 0.0,
    val networkMbps: Double = 0.0
)

@kotlinx.serialization.Serializable
sealed class UsageOperation {
    @kotlinx.serialization.Serializable
    data class AddJob(
        val cpuCores: Double,
        val memoryGB: Double,
        val storageGB: Double = 0.0
    ) : UsageOperation()
    
    @kotlinx.serialization.Serializable
    data class RemoveJob(
        val cpuCores: Double,
        val memoryGB: Double,
        val storageGB: Double = 0.0
    ) : UsageOperation()
    
    @kotlinx.serialization.Serializable
    object AddWorker : UsageOperation()
    
    @kotlinx.serialization.Serializable
    object RemoveWorker : UsageOperation()
}