package dev.rubentxu.hodei.resourcemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Resource utilization information for a resource pool
 */
@Serializable
data class ResourceUtilization(
    val poolId: DomainId,
    val totalCpu: Double,
    val usedCpu: Double,
    val totalMemoryBytes: Long,
    val usedMemoryBytes: Long,
    val totalDiskBytes: Long,
    val usedDiskBytes: Long,
    val runningJobs: Int,
    val queuedJobs: Int,
    val timestamp: Instant
) {
    val availableCpu: Double get() = totalCpu - usedCpu
    val availableMemoryBytes: Long get() = totalMemoryBytes - usedMemoryBytes
    val availableDiskBytes: Long get() = totalDiskBytes - usedDiskBytes
    
    val cpuUtilizationPercent: Double get() = 
        if (totalCpu > 0) (usedCpu / totalCpu) * 100.0 else 0.0
        
    val memoryUtilizationPercent: Double get() = 
        if (totalMemoryBytes > 0) (usedMemoryBytes.toDouble() / totalMemoryBytes.toDouble()) * 100.0 else 0.0
        
    val diskUtilizationPercent: Double get() = 
        if (totalDiskBytes > 0) (usedDiskBytes.toDouble() / totalDiskBytes.toDouble()) * 100.0 else 0.0
}