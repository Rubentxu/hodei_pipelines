package dev.rubentxu.hodei.resourcemanagement.infrastructure.api.dto

import dev.rubentxu.hodei.resourcemanagement.domain.entities.*
import dev.rubentxu.hodei.infrastructure.api.dto.PaginationMetaDto
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class PoolDto(
    val id: String,
    val name: String,
    val provider: String,
    val status: String,
    val capacity: PoolCapacityDto,
    val policies: PoolPoliciesDto,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String
)

@Serializable
data class PoolCapacityDto(
    val totalCpuCores: Double,
    val totalMemoryGB: Double,
    val availableCpuCores: Double,
    val availableMemoryGB: Double,
    val activeWorkers: Int = 0,
    val pendingWorkers: Int = 0,
    val utilizationCpu: Double,
    val utilizationMemory: Double,
    val overallUtilization: Double
)

@Serializable
data class PoolPoliciesDto(
    val scaling: ScalingPolicyDto,
    val placement: PlacementPolicyDto,
    val cost: CostPolicyDto
)

@Serializable
data class ScalingPolicyDto(
    val minWorkers: Int = 0,
    val maxWorkers: Int = 100,
    val targetUtilization: Double = 0.8,
    val scaleUpCooldownSeconds: Int = 300,
    val scaleDownCooldownSeconds: Int = 600
)

@Serializable
data class PlacementPolicyDto(
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<String> = emptyList(),
    val affinity: Map<String, String> = emptyMap()
)

@Serializable
data class CostPolicyDto(
    val maxCostPerHour: Double? = null,
    val budgetAlerts: List<Double> = emptyList(),
    val shutdownOnBudgetExceeded: Boolean = false
)

// Request DTOs
@Serializable
data class CreatePoolRequest(
    val name: String,
    val provider: String,
    val capacity: PoolCapacityDto,
    val policies: PoolPoliciesDto
)

@Serializable
data class UpdatePoolRequest(
    val name: String? = null,
    val capacity: PoolCapacityDto? = null,
    val policies: PoolPoliciesDto? = null
)

// Response DTOs
@Serializable
data class PoolListResponse(
    val data: List<PoolDto>,
    val meta: PaginationMetaDto
)

@Serializable
data class PoolMetricsResponse(
    val poolId: String,
    val activeInstances: Int,
    val totalInstances: Int,
    val cpuUtilization: Double,
    val memoryUtilization: Double,
    val diskUtilization: Double,
    val message: String? = null
)

// Pool Quota DTOs
@Serializable
data class PoolQuotaDto(
    val namespace: String,
    val poolId: String,
    val limits: Map<String, String>,
    val used: Map<String, String>,
    val createdAt: Instant,
    val updatedAt: Instant
)

@Serializable
data class CreatePoolQuotaRequest(
    val namespace: String,
    val limits: Map<String, String>
)

@Serializable
data class UpdatePoolQuotaRequest(
    val limits: Map<String, String>
)

@Serializable
data class PoolQuotaListResponse(
    val data: List<PoolQuotaDto>,
    val meta: PaginationMetaDto
)

// Mappers
fun ResourcePool.toDto(): PoolDto = PoolDto(
    id = id.value,
    name = name,
    provider = type,
    status = status.name,
    capacity = PoolCapacityDto(
        totalCpuCores = 0.0, // These would need to be calculated based on actual usage
        totalMemoryGB = 0.0,
        availableCpuCores = 0.0,
        availableMemoryGB = 0.0,
        activeWorkers = 0,
        pendingWorkers = 0,
        utilizationCpu = 0.0,
        utilizationMemory = 0.0,
        overallUtilization = 0.0
    ),
    policies = PoolPoliciesDto(
        scaling = ScalingPolicyDto(
            minWorkers = 0,
            maxWorkers = maxWorkers,
            targetUtilization = 0.8,
            scaleUpCooldownSeconds = 300,
            scaleDownCooldownSeconds = 900
        ),
        placement = PlacementPolicyDto(
            nodeSelector = emptyMap(),
            tolerations = emptyList(),
            affinity = emptyMap()
        ),
        cost = CostPolicyDto(
            maxCostPerHour = null,
            budgetAlerts = emptyList(),
            shutdownOnBudgetExceeded = false
        )
    ),
    createdAt = createdAt,
    updatedAt = updatedAt,
    createdBy = createdBy
)

// Helper function to convert ResourceQuotas to PoolCapacityDto
fun ResourceQuotas.toCapacityDto(): PoolCapacityDto = PoolCapacityDto(
    totalCpuCores = 0.0, // Would need to be calculated from actual usage monitoring
    totalMemoryGB = 0.0,
    availableCpuCores = 0.0,
    availableMemoryGB = 0.0,
    activeWorkers = 0,
    pendingWorkers = 0,
    utilizationCpu = 0.0,
    utilizationMemory = 0.0,
    overallUtilization = 0.0
)