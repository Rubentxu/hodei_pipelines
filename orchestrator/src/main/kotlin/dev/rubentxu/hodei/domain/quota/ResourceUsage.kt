package dev.rubentxu.hodei.domain.quota

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ResourceUsage(
    val poolId: DomainId,
    val currentCpuCores: Double = 0.0,
    val currentMemoryGB: Double = 0.0,
    val currentStorageGB: Double = 0.0,
    val currentNetworkMbps: Double = 0.0,
    val currentJobs: Int = 0,
    val currentWorkers: Int = 0,
    val customUsage: Map<String, Double> = emptyMap(),
    val timestamp: Instant = kotlinx.datetime.Clock.System.now()
) {
    fun addJobResources(cpuCores: Double, memoryGB: Double, storageGB: Double = 0.0): ResourceUsage =
        copy(
            currentCpuCores = currentCpuCores + cpuCores,
            currentMemoryGB = currentMemoryGB + memoryGB,
            currentStorageGB = currentStorageGB + storageGB,
            currentJobs = currentJobs + 1,
            timestamp = kotlinx.datetime.Clock.System.now()
        )
    
    fun removeJobResources(cpuCores: Double, memoryGB: Double, storageGB: Double = 0.0): ResourceUsage =
        copy(
            currentCpuCores = (currentCpuCores - cpuCores).coerceAtLeast(0.0),
            currentMemoryGB = (currentMemoryGB - memoryGB).coerceAtLeast(0.0),
            currentStorageGB = (currentStorageGB - storageGB).coerceAtLeast(0.0),
            currentJobs = (currentJobs - 1).coerceAtLeast(0),
            timestamp = kotlinx.datetime.Clock.System.now()
        )
    
    fun addWorker(): ResourceUsage =
        copy(
            currentWorkers = currentWorkers + 1,
            timestamp = kotlinx.datetime.Clock.System.now()
        )
    
    fun removeWorker(): ResourceUsage =
        copy(
            currentWorkers = (currentWorkers - 1).coerceAtLeast(0),
            timestamp = kotlinx.datetime.Clock.System.now()
        )
    
    fun getCurrentUsage(resource: ResourceType): Double = when (resource) {
        ResourceType.CPU -> currentCpuCores
        ResourceType.MEMORY -> currentMemoryGB
        ResourceType.STORAGE -> currentStorageGB
        ResourceType.NETWORK -> currentNetworkMbps
        ResourceType.CONCURRENT_JOBS -> currentJobs.toDouble()
        ResourceType.CONCURRENT_WORKERS -> currentWorkers.toDouble()
        ResourceType.JOB_DURATION -> 0.0 // Duration is calculated differently
    }
    
    fun calculateUsagePercentage(limits: ResourceLimits, resource: ResourceType): Double? {
        val limit = limits.getLimit(resource) ?: return null
        val current = getCurrentUsage(resource)
        return if (limit > 0) (current / limit) * 100.0 else 0.0
    }
    
    fun isWithinLimits(limits: ResourceLimits): Boolean {
        return ResourceType.values().all { resource ->
            val limit = limits.getLimit(resource)
            if (limit != null) {
                getCurrentUsage(resource) <= limit
            } else {
                true
            }
        }
    }
    
    fun getViolations(limits: ResourceLimits): List<ResourceViolation> {
        val violations = mutableListOf<ResourceViolation>()
        
        ResourceType.values().forEach { resource ->
            val limit = limits.getLimit(resource)
            if (limit != null) {
                val current = getCurrentUsage(resource)
                if (current > limit) {
                    violations.add(
                        ResourceViolation(
                            resource = resource,
                            limit = limit,
                            current = current,
                            excessAmount = current - limit,
                            excessPercentage = ((current - limit) / limit) * 100.0
                        )
                    )
                }
            }
        }
        
        return violations
    }
    
    fun shouldTriggerAlert(limits: ResourceLimits, thresholds: AlertThresholds): List<ResourceAlert> {
        val alerts = mutableListOf<ResourceAlert>()
        
        // CPU Alert
        val cpuPercentage = calculateUsagePercentage(limits, ResourceType.CPU)
        if (cpuPercentage != null && cpuPercentage >= thresholds.cpuWarningPercent) {
            alerts.add(ResourceAlert(ResourceType.CPU, cpuPercentage, thresholds.cpuWarningPercent))
        }
        
        // Memory Alert
        val memoryPercentage = calculateUsagePercentage(limits, ResourceType.MEMORY)
        if (memoryPercentage != null && memoryPercentage >= thresholds.memoryWarningPercent) {
            alerts.add(ResourceAlert(ResourceType.MEMORY, memoryPercentage, thresholds.memoryWarningPercent))
        }
        
        // Storage Alert
        val storagePercentage = calculateUsagePercentage(limits, ResourceType.STORAGE)
        if (storagePercentage != null && storagePercentage >= thresholds.storageWarningPercent) {
            alerts.add(ResourceAlert(ResourceType.STORAGE, storagePercentage, thresholds.storageWarningPercent))
        }
        
        // Jobs Alert
        val jobsPercentage = calculateUsagePercentage(limits, ResourceType.CONCURRENT_JOBS)
        if (jobsPercentage != null && jobsPercentage >= thresholds.jobsWarningPercent) {
            alerts.add(ResourceAlert(ResourceType.CONCURRENT_JOBS, jobsPercentage, thresholds.jobsWarningPercent))
        }
        
        // Workers Alert
        val workersPercentage = calculateUsagePercentage(limits, ResourceType.CONCURRENT_WORKERS)
        if (workersPercentage != null && workersPercentage >= thresholds.workersWarningPercent) {
            alerts.add(ResourceAlert(ResourceType.CONCURRENT_WORKERS, workersPercentage, thresholds.workersWarningPercent))
        }
        
        return alerts
    }
    
    companion object {
        fun empty(poolId: DomainId): ResourceUsage = ResourceUsage(poolId = poolId)
    }
}

@Serializable
data class ResourceViolation(
    val resource: ResourceType,
    val limit: Double,
    val current: Double,
    val excessAmount: Double,
    val excessPercentage: Double
) {
    val severity: ViolationSeverity
        get() = when {
            excessPercentage > 50.0 -> ViolationSeverity.CRITICAL
            excessPercentage > 20.0 -> ViolationSeverity.HIGH
            excessPercentage > 10.0 -> ViolationSeverity.MEDIUM
            else -> ViolationSeverity.LOW
        }
}

@Serializable
data class ResourceAlert(
    val resource: ResourceType,
    val currentPercentage: Double,
    val thresholdPercentage: Double
) {
    val severity: AlertSeverity
        get() = when {
            currentPercentage >= 95.0 -> AlertSeverity.CRITICAL
            currentPercentage >= 90.0 -> AlertSeverity.HIGH
            currentPercentage >= 85.0 -> AlertSeverity.MEDIUM
            else -> AlertSeverity.LOW
        }
}

@Serializable
enum class ViolationSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}

@Serializable
enum class AlertSeverity {
    LOW, MEDIUM, HIGH, CRITICAL
}