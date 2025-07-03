package dev.rubentxu.hodei.resourcemanagement.domain.ports

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Instant

/**
 * Puerto para abstraer la complejidad de monitorizar recursos disponibles
 * en infraestructuras heterogéneas (Kubernetes, Docker, Cloud Containers, etc.)
 */
interface IResourceMonitor {
    
    /**
     * Get current resource utilization for a specific resource pool
     * @param resourcePoolId The ID of the resource pool to monitor
     * @return ResourcePoolUtilization snapshot
     */
    suspend fun getUtilization(resourcePoolId: DomainId): arrow.core.Either<String, dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization>
    
    /**
     * Get real-time resource utilization updates for a resource pool
     * @param resourcePoolId The ID of the resource pool to monitor
     * @return Flow of ResourcePoolUtilization updates
     */
    fun subscribeToResourceUpdates(resourcePoolId: DomainId): Flow<dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization>
    
    /**
     * Get capacity information for a resource pool
     * @param resourcePoolId The ID of the resource pool
     * @return ResourceCapacity information
     */
    suspend fun getResourceCapacity(resourcePoolId: DomainId): Result<ResourceCapacity>
    
    /**
     * Get health status for all nodes/instances in a resource pool
     * @param resourcePoolId The ID of the resource pool
     * @return List of NodeHealth objects
     */
    suspend fun getNodesHealth(resourcePoolId: DomainId): Result<List<NodeHealth>>
    
    /**
     * Get resource utilization metrics for a time range
     * @param resourcePoolId The ID of the resource pool
     * @param startTime Start of the time range
     * @param endTime End of the time range
     * @param interval Sampling interval for metrics
     * @return Historical resource metrics
     */
    suspend fun getResourceMetrics(
        resourcePoolId: DomainId,
        startTime: Instant,
        endTime: Instant,
        interval: MetricInterval = MetricInterval.MINUTE_5
    ): Result<List<ResourceMetric>>
    
    /**
     * Check if a resource pool has sufficient resources for a request
     * @param resourcePoolId The ID of the resource pool
     * @param resourceRequest The resource requirements to check
     * @return ResourceAvailability indicating if resources are available
     */
    suspend fun checkResourceAvailability(
        resourcePoolId: DomainId,
        resourceRequest: ResourceRequest
    ): Result<ResourceAvailability>
    
    /**
     * Get current quotas and their usage for a resource pool
     * @param resourcePoolId The ID of the resource pool
     * @return QuotaUsage information
     */
    suspend fun getQuotaUsage(resourcePoolId: DomainId): Result<QuotaUsage>
}

/**
 * Current resource utilization snapshot (ports version)
 */
data class ResourceUtilizationSnapshot(
    val resourcePoolId: DomainId,
    val timestamp: Instant,
    val cpu: ResourceUsage,
    val memory: ResourceUsage,
    val storage: ResourceUsage,
    val network: NetworkUsage,
    val activeInstances: Int,
    val totalInstances: Int
)

/**
 * Resource usage information for a specific resource type
 */
data class ResourceUsage(
    val used: Long,
    val total: Long,
    val percentage: Double,
    val unit: ResourceUnit
) {
    val available: Long get() = total - used
}

/**
 * Network usage information
 */
data class NetworkUsage(
    val inboundBytesPerSecond: Long,
    val outboundBytesPerSecond: Long,
    val connectionsCount: Int
)

/**
 * Resource capacity information for a pool
 */
data class ResourceCapacity(
    val resourcePoolId: DomainId,
    val totalCpuCores: Int,
    val totalMemoryMB: Long,
    val totalStorageGB: Long,
    val maxInstances: Int,
    val availableInstanceTypes: List<InstanceType>
)

/**
 * Health status of a node/instance
 */
data class NodeHealth(
    val nodeId: DomainId,
    val nodeName: String,
    val status: NodeStatus,
    val lastHeartbeat: Instant,
    val uptime: Long, // seconds
    val conditions: List<NodeCondition>
)

/**
 * Status of a node/instance
 */
enum class NodeStatus {
    READY,
    NOT_READY,
    UNKNOWN,
    MAINTENANCE,
    DRAINING
}

/**
 * Condition affecting a node
 */
data class NodeCondition(
    val type: ConditionType,
    val status: Boolean,
    val message: String,
    val lastTransition: Instant
)

/**
 * Types of node conditions
 */
enum class ConditionType {
    READY,
    MEMORY_PRESSURE,
    DISK_PRESSURE,
    PID_PRESSURE,
    NETWORK_UNAVAILABLE,
    CUSTOM
}

/**
 * Historical resource metric point
 */
data class ResourceMetric(
    val timestamp: Instant,
    val resourceType: ResourceType,
    val value: Double,
    val unit: ResourceUnit
)

/**
 * Types of resources that can be monitored
 */
enum class ResourceType {
    CPU_USAGE,
    MEMORY_USAGE,
    STORAGE_USAGE,
    NETWORK_IN,
    NETWORK_OUT,
    INSTANCE_COUNT,
    QUEUE_LENGTH
}

/**
 * Units for resource measurements
 */
enum class ResourceUnit {
    CORES,
    MEGABYTES,
    GIGABYTES,
    PERCENTAGE,
    BYTES_PER_SECOND,
    COUNT
}

/**
 * Metric sampling intervals
 */
enum class MetricInterval(val seconds: Long) {
    MINUTE_1(60),
    MINUTE_5(300),
    MINUTE_15(900),
    HOUR_1(3600),
    DAY_1(86400)
}

/**
 * Resource request for availability checking
 */
data class ResourceRequest(
    val cpuCores: Int,
    val memoryMB: Long,
    val storageGB: Long = 0,
    val gpu: Int = 0,
    val instanceCount: Int = 1,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Resource availability result
 */
data class ResourceAvailability(
    val available: Boolean,
    val reason: String? = null,
    val estimatedWaitTime: Long? = null, // seconds
    val alternativeOptions: List<ResourceSuggestion> = emptyList()
)

/**
 * Alternative resource suggestion
 */
data class ResourceSuggestion(
    val instanceType: InstanceType,
    val availableCount: Int,
    val estimatedCost: Double? = null
)

/**
 * Quota usage information
 */
data class QuotaUsage(
    val resourcePoolId: DomainId,
    val namespace: String? = null,
    val quotas: Map<String, QuotaLimit>
)

/**
 * Individual quota limit and usage
 */
data class QuotaLimit(
    val limit: Long,
    val used: Long,
    val unit: ResourceUnit
) {
    val available: Long get() = limit - used
    val percentage: Double get() = if (limit > 0) (used.toDouble() / limit.toDouble()) * 100.0 else 0.0
}