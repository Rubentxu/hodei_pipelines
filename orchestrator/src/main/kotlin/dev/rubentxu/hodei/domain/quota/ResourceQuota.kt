package dev.rubentxu.hodei.domain.quota

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ResourceQuota(
    val id: DomainId,
    val poolId: DomainId,
    val limits: ResourceLimits,
    val policy: QuotaPolicy = QuotaPolicy.HARD,
    val alertThresholds: AlertThresholds = AlertThresholds.default(),
    val enabled: Boolean = true,
    val description: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String
) {
    fun updateLimits(newLimits: ResourceLimits): ResourceQuota =
        copy(
            limits = newLimits,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun updatePolicy(newPolicy: QuotaPolicy): ResourceQuota =
        copy(
            policy = newPolicy,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun enable(): ResourceQuota =
        copy(
            enabled = true,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun disable(): ResourceQuota =
        copy(
            enabled = false,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun updateAlertThresholds(thresholds: AlertThresholds): ResourceQuota =
        copy(
            alertThresholds = thresholds,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    companion object {
        fun create(
            poolId: DomainId,
            limits: ResourceLimits,
            policy: QuotaPolicy = QuotaPolicy.HARD,
            description: String? = null,
            createdBy: String
        ): ResourceQuota {
            val now = kotlinx.datetime.Clock.System.now()
            return ResourceQuota(
                id = DomainId.generate(),
                poolId = poolId,
                limits = limits,
                policy = policy,
                alertThresholds = AlertThresholds.default(),
                enabled = true,
                description = description,
                createdAt = now,
                updatedAt = now,
                createdBy = createdBy
            )
        }
    }
}

@Serializable
data class ResourceLimits(
    val maxCpuCores: Double? = null,
    val maxMemoryGB: Double? = null,
    val maxStorageGB: Double? = null,
    val maxNetworkMbps: Double? = null,
    val maxConcurrentJobs: Int? = null,
    val maxConcurrentWorkers: Int? = null,
    val maxJobDurationMinutes: Long? = null,
    val customLimits: Map<String, Double> = emptyMap()
) {
    init {
        maxCpuCores?.let { require(it > 0) { "CPU cores limit must be positive" } }
        maxMemoryGB?.let { require(it > 0) { "Memory limit must be positive" } }
        maxStorageGB?.let { require(it >= 0) { "Storage limit cannot be negative" } }
        maxNetworkMbps?.let { require(it >= 0) { "Network limit cannot be negative" } }
        maxConcurrentJobs?.let { require(it > 0) { "Concurrent jobs limit must be positive" } }
        maxConcurrentWorkers?.let { require(it > 0) { "Concurrent workers limit must be positive" } }
        maxJobDurationMinutes?.let { require(it > 0) { "Job duration limit must be positive" } }
    }
    
    val isUnlimited: Boolean
        get() = maxCpuCores == null && maxMemoryGB == null && maxStorageGB == null &&
                maxConcurrentJobs == null && maxConcurrentWorkers == null
    
    fun hasLimit(resource: ResourceType): Boolean = when (resource) {
        ResourceType.CPU -> maxCpuCores != null
        ResourceType.MEMORY -> maxMemoryGB != null
        ResourceType.STORAGE -> maxStorageGB != null
        ResourceType.NETWORK -> maxNetworkMbps != null
        ResourceType.CONCURRENT_JOBS -> maxConcurrentJobs != null
        ResourceType.CONCURRENT_WORKERS -> maxConcurrentWorkers != null
        ResourceType.JOB_DURATION -> maxJobDurationMinutes != null
    }
    
    fun getLimit(resource: ResourceType): Double? = when (resource) {
        ResourceType.CPU -> maxCpuCores
        ResourceType.MEMORY -> maxMemoryGB
        ResourceType.STORAGE -> maxStorageGB
        ResourceType.NETWORK -> maxNetworkMbps
        ResourceType.CONCURRENT_JOBS -> maxConcurrentJobs?.toDouble()
        ResourceType.CONCURRENT_WORKERS -> maxConcurrentWorkers?.toDouble()
        ResourceType.JOB_DURATION -> maxJobDurationMinutes?.toDouble()
    }
    
    companion object {
        fun unlimited(): ResourceLimits = ResourceLimits()
        
        fun basic(
            maxCpuCores: Double = 10.0,
            maxMemoryGB: Double = 32.0,
            maxConcurrentJobs: Int = 5
        ): ResourceLimits = ResourceLimits(
            maxCpuCores = maxCpuCores,
            maxMemoryGB = maxMemoryGB,
            maxConcurrentJobs = maxConcurrentJobs
        )
    }
}

@Serializable
data class AlertThresholds(
    val cpuWarningPercent: Double = 80.0,
    val memoryWarningPercent: Double = 80.0,
    val storageWarningPercent: Double = 80.0,
    val jobsWarningPercent: Double = 80.0,
    val workersWarningPercent: Double = 80.0
) {
    init {
        require(cpuWarningPercent in 0.0..100.0) { "CPU warning threshold must be between 0-100%" }
        require(memoryWarningPercent in 0.0..100.0) { "Memory warning threshold must be between 0-100%" }
        require(storageWarningPercent in 0.0..100.0) { "Storage warning threshold must be between 0-100%" }
        require(jobsWarningPercent in 0.0..100.0) { "Jobs warning threshold must be between 0-100%" }
        require(workersWarningPercent in 0.0..100.0) { "Workers warning threshold must be between 0-100%" }
    }
    
    companion object {
        fun default(): AlertThresholds = AlertThresholds()
        
        fun conservative(): AlertThresholds = AlertThresholds(
            cpuWarningPercent = 70.0,
            memoryWarningPercent = 70.0,
            storageWarningPercent = 70.0,
            jobsWarningPercent = 70.0,
            workersWarningPercent = 70.0
        )
    }
}

@Serializable
enum class QuotaPolicy {
    HARD,    // Bloquea completamente cuando se excede el límite
    SOFT,    // Permite exceder pero envía alertas
    ADVISORY // Solo envía alertas, no bloquea
}

@Serializable
enum class ResourceType {
    CPU,
    MEMORY,
    STORAGE,
    NETWORK,
    CONCURRENT_JOBS,
    CONCURRENT_WORKERS,
    JOB_DURATION
}