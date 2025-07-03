package dev.rubentxu.hodei.domain.pool

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PoolQuota(
    val id: DomainId,
    val poolId: DomainId,
    val namespace: String,
    val limits: ResourceLimits,
    val usage: ResourceUsage = ResourceUsage(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String
) {
    init {
        require(namespace.isNotBlank()) { "Namespace cannot be blank" }
    }
    
    fun updateUsage(newUsage: ResourceUsage): PoolQuota =
        copy(usage = newUsage, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun canAllocate(request: ResourceRequest): Boolean =
        usage.canAccommodate(request, limits)
    
    fun allocate(request: ResourceRequest): PoolQuota {
        require(canAllocate(request)) { 
            "Cannot allocate resources: would exceed quota limits" 
        }
        return updateUsage(usage.allocate(request))
    }
    
    fun deallocate(request: ResourceRequest): PoolQuota =
        updateUsage(usage.deallocate(request))
}

@Serializable
data class ResourceLimits(
    val maxCpuCores: Double,
    val maxMemoryGB: Double,
    val maxJobs: Int,
    val maxDiskGB: Double = 0.0
) {
    init {
        require(maxCpuCores > 0) { "Max CPU cores must be positive" }
        require(maxMemoryGB > 0) { "Max memory must be positive" }
        require(maxJobs > 0) { "Max jobs must be positive" }
        require(maxDiskGB >= 0) { "Max disk cannot be negative" }
    }
}

@Serializable
data class ResourceUsage(
    val usedCpuCores: Double = 0.0,
    val usedMemoryGB: Double = 0.0,
    val activeJobs: Int = 0,
    val usedDiskGB: Double = 0.0
) {
    fun canAccommodate(request: ResourceRequest, limits: ResourceLimits): Boolean =
        usedCpuCores + request.cpuCores <= limits.maxCpuCores &&
        usedMemoryGB + request.memoryGB <= limits.maxMemoryGB &&
        activeJobs + 1 <= limits.maxJobs &&
        usedDiskGB + request.diskGB <= limits.maxDiskGB
    
    fun allocate(request: ResourceRequest): ResourceUsage =
        copy(
            usedCpuCores = usedCpuCores + request.cpuCores,
            usedMemoryGB = usedMemoryGB + request.memoryGB,
            activeJobs = activeJobs + 1,
            usedDiskGB = usedDiskGB + request.diskGB
        )
    
    fun deallocate(request: ResourceRequest): ResourceUsage =
        copy(
            usedCpuCores = maxOf(0.0, usedCpuCores - request.cpuCores),
            usedMemoryGB = maxOf(0.0, usedMemoryGB - request.memoryGB),
            activeJobs = maxOf(0, activeJobs - 1),
            usedDiskGB = maxOf(0.0, usedDiskGB - request.diskGB)
        )
}

@Serializable
data class ResourceRequest(
    val cpuCores: Double,
    val memoryGB: Double,
    val diskGB: Double = 0.0
) {
    init {
        require(cpuCores > 0) { "CPU cores must be positive" }
        require(memoryGB > 0) { "Memory must be positive" }
        require(diskGB >= 0) { "Disk cannot be negative" }
    }
}