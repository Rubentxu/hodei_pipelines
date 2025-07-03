package dev.rubentxu.hodei.domain.pool

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ResourcePool(
    val id: DomainId,
    val name: String,
    val provider: ProviderType,
    val config: JsonObject,
    val policies: PoolPolicies,
    val status: PoolStatus,
    val capacity: PoolCapacity,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String
) {
    init {
        require(name.isNotBlank()) { "Pool name cannot be blank" }
    }
    
    fun updateStatus(newStatus: PoolStatus): ResourcePool {
        require(status.canTransitionTo(newStatus)) { 
            "Cannot transition from $status to $newStatus" 
        }
        return copy(status = newStatus, updatedAt = kotlinx.datetime.Clock.System.now())
    }
    
    fun updateCapacity(newCapacity: PoolCapacity): ResourcePool =
        copy(capacity = newCapacity, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun drain(): ResourcePool = updateStatus(PoolStatus.DRAINING)
    
    fun maintenance(): ResourcePool = updateStatus(PoolStatus.MAINTENANCE)
    
    fun resume(): ResourcePool = updateStatus(PoolStatus.ACTIVE)
    
    fun canAcceptNewJobs(): Boolean = status.canAcceptJobs
    
    fun isHealthy(): Boolean = status.isHealthy
}

@Serializable
enum class ProviderType {
    KUBERNETES,
    DOCKER,
    AWS,
    GCP,
    AZURE,
    LOCAL
}

@Serializable
data class PoolPolicies(
    val scaling: ScalingPolicy,
    val placement: PlacementPolicy,
    val cost: CostPolicy
)

@Serializable
data class ScalingPolicy(
    val minWorkers: Int = 0,
    val maxWorkers: Int = 100,
    val targetUtilization: Double = 0.8,
    val scaleUpCooldownSeconds: Int = 300,
    val scaleDownCooldownSeconds: Int = 600
) {
    init {
        require(minWorkers >= 0) { "Min workers cannot be negative" }
        require(maxWorkers >= minWorkers) { "Max workers must be >= min workers" }
        require(targetUtilization in 0.1..1.0) { "Target utilization must be between 0.1 and 1.0" }
    }
}

@Serializable
data class PlacementPolicy(
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<String> = emptyList(),
    val affinity: Map<String, String> = emptyMap()
)

@Serializable
data class CostPolicy(
    val maxCostPerHour: Double? = null,
    val budgetAlerts: List<Double> = emptyList(),
    val shutdownOnBudgetExceeded: Boolean = false
)

@Serializable
data class PoolCapacity(
    val totalCpuCores: Double,
    val totalMemoryGB: Double,
    val availableCpuCores: Double,
    val availableMemoryGB: Double,
    val activeWorkers: Int = 0,
    val pendingWorkers: Int = 0
) {
    val utilizationCpu: Double
        get() = if (totalCpuCores > 0) (totalCpuCores - availableCpuCores) / totalCpuCores else 0.0
    
    val utilizationMemory: Double
        get() = if (totalMemoryGB > 0) (totalMemoryGB - availableMemoryGB) / totalMemoryGB else 0.0
    
    val overallUtilization: Double
        get() = maxOf(utilizationCpu, utilizationMemory)
}