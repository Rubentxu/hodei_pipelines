package dev.rubentxu.hodei.application.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.DomainError
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.domain.quota.*
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceQuotas
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceLimit
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Service to integrate the new detailed quota system with the existing ResourcePool quotas
 * Provides backward compatibility and migration support
 */
class QuotaIntegrationService(
    private val quotaService: QuotaService,
    private val resourceMonitoringService: ResourceMonitoringService
) {
    
    suspend fun migrateResourcePoolQuotas(resourcePool: ResourcePool): Either<DomainError, ResourceQuota> {
        logger.info { "Migrating ResourcePool quotas to detailed quota system for pool ${resourcePool.id}" }
        
        // Check if quota already exists
        return quotaService.getQuotaByPoolId(resourcePool.id).flatMap { existingQuota ->
            if (existingQuota != null) {
                logger.info { "Quota already exists for pool ${resourcePool.id}, updating with ResourcePool data" }
                updateQuotaFromResourcePool(existingQuota, resourcePool)
            } else {
                logger.info { "Creating new quota from ResourcePool data for pool ${resourcePool.id}" }
                createQuotaFromResourcePool(resourcePool)
            }
        }
    }
    
    private suspend fun createQuotaFromResourcePool(resourcePool: ResourcePool): Either<DomainError, ResourceQuota> {
        val resourceLimits = convertResourceQuotasToLimits(resourcePool.resourceQuotas)
        
        return quotaService.createQuota(
            poolId = resourcePool.id,
            limits = resourceLimits,
            policy = if (resourcePool.resourceQuotas.hasLimits()) QuotaPolicy.HARD else QuotaPolicy.ADVISORY,
            description = "Migrated from ResourcePool: ${resourcePool.name}",
            createdBy = "system-migration"
        ).flatMap { quota ->
            // Start monitoring for this pool
            resourceMonitoringService.startMonitoring(resourcePool.id).flatMap {
                quota.right()
            }
        }
    }
    
    private suspend fun updateQuotaFromResourcePool(
        existingQuota: ResourceQuota,
        resourcePool: ResourcePool
    ): Either<DomainError, ResourceQuota> {
        val newLimits = convertResourceQuotasToLimits(resourcePool.resourceQuotas)
        
        return quotaService.updateQuotaLimits(existingQuota.id, newLimits)
    }
    
    private fun convertResourceQuotasToLimits(resourceQuotas: ResourceQuotas): ResourceLimits {
        return ResourceLimits(
            maxCpuCores = resourceQuotas.cpu?.let { parseResourceValue(it.limits, "cpu") },
            maxMemoryGB = resourceQuotas.memory?.let { parseResourceValue(it.limits, "memory") },
            maxStorageGB = resourceQuotas.storage?.let { parseResourceValue(it.limits, "storage") },
            maxConcurrentJobs = resourceQuotas.maxConcurrentJobs,
            maxConcurrentWorkers = resourceQuotas.maxWorkers,
            customLimits = resourceQuotas.customLimits.mapValues { it.value.toDoubleOrNull() ?: 0.0 }
        )
    }
    
    private fun parseResourceValue(resourceString: String, type: String): Double? {
        return try {
            when (type.lowercase()) {
                "cpu" -> {
                    // Parse CPU values like "100m", "1", "2.5"
                    when {
                        resourceString.endsWith("m") -> resourceString.dropLast(1).toDouble() / 1000.0
                        else -> resourceString.toDouble()
                    }
                }
                "memory", "storage" -> {
                    // Parse memory/storage values like "1Gi", "500Mi", "2Ti"
                    when {
                        resourceString.endsWith("Ti") -> resourceString.dropLast(2).toDouble() * 1024.0
                        resourceString.endsWith("Gi") -> resourceString.dropLast(2).toDouble()
                        resourceString.endsWith("Mi") -> resourceString.dropLast(2).toDouble() / 1024.0
                        resourceString.endsWith("Ki") -> resourceString.dropLast(2).toDouble() / (1024.0 * 1024.0)
                        else -> resourceString.toDoubleOrNull()
                    }
                }
                else -> resourceString.toDoubleOrNull()
            }
        } catch (e: Exception) {
            logger.warn { "Failed to parse resource value '$resourceString' for type '$type': ${e.message}" }
            null
        }
    }
    
    suspend fun syncResourcePoolWithQuota(resourcePool: ResourcePool): Either<DomainError, ResourceQuotas> {
        logger.debug { "Syncing ResourcePool ${resourcePool.id} with detailed quota system" }
        
        return quotaService.getQuotaByPoolId(resourcePool.id).flatMap { quota ->
            if (quota != null) {
                val syncedResourceQuotas = convertLimitsToResourceQuotas(quota.limits)
                syncedResourceQuotas.right()
            } else {
                // No detailed quota exists, return current ResourcePool quotas
                resourcePool.resourceQuotas.right()
            }
        }
    }
    
    private fun convertLimitsToResourceQuotas(limits: ResourceLimits): ResourceQuotas {
        return ResourceQuotas(
            cpu = limits.maxCpuCores?.let { 
                ResourceLimit(
                    requests = "${(it * 0.1).toInt()}",  // 10% of limit as request
                    limits = it.toString()
                )
            },
            memory = limits.maxMemoryGB?.let {
                ResourceLimit(
                    requests = "${(it * 0.1).toInt()}Gi",  // 10% of limit as request
                    limits = "${it.toInt()}Gi"
                )
            },
            storage = limits.maxStorageGB?.let {
                ResourceLimit(
                    requests = "${(it * 0.1).toInt()}Gi",  // 10% of limit as request
                    limits = "${it.toInt()}Gi"
                )
            },
            maxWorkers = limits.maxConcurrentWorkers,
            maxJobs = null, // Not tracked in new system
            maxConcurrentJobs = limits.maxConcurrentJobs,
            customLimits = limits.customLimits.mapValues { it.value.toString() }
        )
    }
    
    suspend fun validateResourcePoolOperation(
        poolId: DomainId,
        operation: ResourcePoolOperation
    ): Either<DomainError, QuotaEnforcementResult> {
        logger.debug { "Validating ResourcePool operation for pool $poolId: $operation" }
        
        return when (operation) {
            is ResourcePoolOperation.AddWorker -> {
                quotaService.checkQuotaEnforcement(
                    poolId = poolId,
                    requestedResources = ResourceRequest(
                        cpuCores = operation.workerCpuCores,
                        memoryGB = operation.workerMemoryGB
                    ),
                    context = ViolationContext.forWorker(operation.workerId, "worker_addition")
                )
            }
            is ResourcePoolOperation.StartJob -> {
                quotaService.checkQuotaEnforcement(
                    poolId = poolId,
                    requestedResources = ResourceRequest(
                        cpuCores = operation.jobCpuCores,
                        memoryGB = operation.jobMemoryGB,
                        storageGB = operation.jobStorageGB
                    ),
                    context = ViolationContext.forJob(operation.jobId, operation.userId)
                )
            }
            is ResourcePoolOperation.CheckLimits -> {
                quotaService.checkQuotaEnforcement(
                    poolId = poolId,
                    requestedResources = ResourceRequest(
                        cpuCores = operation.totalCpuCores,
                        memoryGB = operation.totalMemoryGB,
                        storageGB = operation.totalStorageGB
                    ),
                    context = ViolationContext(operationType = "limit_check")
                )
            }
        }
    }
    
    suspend fun updateResourcePoolUsage(
        poolId: DomainId,
        operation: ResourcePoolUsageOperation
    ): Either<DomainError, ResourceUsage> {
        logger.debug { "Updating ResourcePool usage for pool $poolId: $operation" }
        
        return when (operation) {
            is ResourcePoolUsageOperation.WorkerAdded -> {
                quotaService.updateUsage(poolId, UsageOperation.AddWorker)
            }
            is ResourcePoolUsageOperation.WorkerRemoved -> {
                quotaService.updateUsage(poolId, UsageOperation.RemoveWorker)
            }
            is ResourcePoolUsageOperation.JobStarted -> {
                quotaService.updateUsage(
                    poolId, 
                    UsageOperation.AddJob(
                        operation.cpuCores,
                        operation.memoryGB,
                        operation.storageGB
                    )
                )
            }
            is ResourcePoolUsageOperation.JobCompleted -> {
                quotaService.updateUsage(
                    poolId,
                    UsageOperation.RemoveJob(
                        operation.cpuCores,
                        operation.memoryGB,
                        operation.storageGB
                    )
                )
            }
        }
    }
    
    suspend fun getResourcePoolQuotaStatus(poolId: DomainId): Either<DomainError, ResourcePoolQuotaStatus> {
        return quotaService.getQuotaByPoolId(poolId).flatMap { quota ->
            quotaService.getCurrentUsage(poolId).flatMap { usage ->
                quotaService.getUnresolvedViolations(poolId).flatMap { violations ->
                    ResourcePoolQuotaStatus(
                        hasQuota = quota != null,
                        quotaEnabled = quota?.enabled ?: false,
                        currentUsage = usage,
                        quotaLimits = quota?.limits,
                        utilizationPercentages = calculateUtilizationPercentages(usage, quota?.limits),
                        activeViolations = violations,
                        quotaPolicy = quota?.policy
                    ).right()
                }
            }
        }
    }
    
    private fun calculateUtilizationPercentages(
        usage: ResourceUsage,
        limits: ResourceLimits?
    ): Map<ResourceType, Double> {
        if (limits == null) return emptyMap()
        
        return ResourceType.values().mapNotNull { resourceType ->
            usage.calculateUsagePercentage(limits, resourceType)?.let { 
                resourceType to it 
            }
        }.toMap()
    }
}

@kotlinx.serialization.Serializable
sealed class ResourcePoolOperation {
    @kotlinx.serialization.Serializable
    data class AddWorker(
        val workerId: DomainId,
        val workerCpuCores: Double,
        val workerMemoryGB: Double
    ) : ResourcePoolOperation()
    
    @kotlinx.serialization.Serializable
    data class StartJob(
        val jobId: DomainId,
        val jobCpuCores: Double,
        val jobMemoryGB: Double,
        val jobStorageGB: Double = 0.0,
        val userId: String? = null
    ) : ResourcePoolOperation()
    
    @kotlinx.serialization.Serializable
    data class CheckLimits(
        val totalCpuCores: Double,
        val totalMemoryGB: Double,
        val totalStorageGB: Double = 0.0
    ) : ResourcePoolOperation()
}

@kotlinx.serialization.Serializable
sealed class ResourcePoolUsageOperation {
    @kotlinx.serialization.Serializable
    object WorkerAdded : ResourcePoolUsageOperation()
    
    @kotlinx.serialization.Serializable
    object WorkerRemoved : ResourcePoolUsageOperation()
    
    @kotlinx.serialization.Serializable
    data class JobStarted(
        val cpuCores: Double,
        val memoryGB: Double,
        val storageGB: Double = 0.0
    ) : ResourcePoolUsageOperation()
    
    @kotlinx.serialization.Serializable
    data class JobCompleted(
        val cpuCores: Double,
        val memoryGB: Double,
        val storageGB: Double = 0.0
    ) : ResourcePoolUsageOperation()
}

@kotlinx.serialization.Serializable
data class ResourcePoolQuotaStatus(
    val hasQuota: Boolean,
    val quotaEnabled: Boolean,
    val currentUsage: ResourceUsage,
    val quotaLimits: ResourceLimits?,
    val utilizationPercentages: Map<ResourceType, Double>,
    val activeViolations: List<QuotaViolation>,
    val quotaPolicy: QuotaPolicy?
) {
    val isNearLimits: Boolean
        get() = utilizationPercentages.values.any { it > 80.0 }
    
    val hasActiveViolations: Boolean
        get() = activeViolations.isNotEmpty()
    
    val overallUtilization: Double
        get() = if (utilizationPercentages.isEmpty()) 0.0 
                else utilizationPercentages.values.average()
}