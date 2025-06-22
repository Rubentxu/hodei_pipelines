package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import kotlinx.coroutines.flow.Flow

/**
 * Port for Resource Management - Abstract interface for resource monitoring and management
 * Implementations could be: Kubernetes Metrics Server, Prometheus, CloudWatch, Azure Monitor, etc.
 */
interface ResourceManager {
    
    /**
     * Get current resource availability across the infrastructure
     * @return Current resource availability
     */
    suspend fun getResourceAvailability(): ResourceAvailability
    
    /**
     * Get resource usage for a specific worker pool
     * @param poolId The pool to get usage for
     * @return Resource usage information
     */
    suspend fun getPoolResourceUsage(poolId: WorkerPoolId): PoolResourceUsage
    
    /**
     * Get resource usage for a specific worker
     * @param workerId The worker to get usage for
     * @return Worker resource usage
     */
    suspend fun getWorkerResourceUsage(workerId: dev.rubentxu.hodei.pipelines.domain.worker.WorkerId): WorkerResourceUsage
    
    /**
     * Stream resource metrics in real-time
     * @return Flow of resource metrics
     */
    fun streamResourceMetrics(): Flow<ResourceMetrics>
    
    /**
     * Check if sufficient resources are available for scaling
     * @param requirements Resource requirements for new workers
     * @param count Number of workers to create
     * @return Resource availability check result
     */
    suspend fun checkResourceAvailability(requirements: ResourceRequirements, count: Int): ResourceAvailabilityCheck
    
    /**
     * Get resource quotas and limits
     * @return Current resource quotas
     */
    suspend fun getResourceQuotas(): ResourceQuotas
    
    /**
     * Get historical resource usage trends
     * @param period Time period to analyze
     * @return Resource usage trends
     */
    suspend fun getResourceTrends(period: java.time.Duration): ResourceTrends
    
    /**
     * Predict future resource needs based on trends
     * @param forecastPeriod Period to forecast
     * @return Resource forecast
     */
    suspend fun forecastResourceNeeds(forecastPeriod: java.time.Duration): ResourceForecast
}

/**
 * Resource usage for a worker pool
 */
data class PoolResourceUsage(
    val poolId: WorkerPoolId,
    val totalCpuUsage: String,
    val totalMemoryUsage: String,
    val averageCpuUtilization: Double,  // 0.0 - 1.0
    val averageMemoryUtilization: Double, // 0.0 - 1.0
    val peakCpuUtilization: Double,
    val peakMemoryUtilization: Double,
    val workers: List<WorkerResourceUsage>,
    val timestamp: java.time.Instant = java.time.Instant.now()
)

/**
 * Resource usage for a specific worker
 */
data class WorkerResourceUsage(
    val workerId: dev.rubentxu.hodei.pipelines.domain.worker.WorkerId,
    val cpuUsage: String,           // Current CPU usage (e.g., "250m")
    val memoryUsage: String,        // Current memory usage (e.g., "512Mi")
    val cpuUtilization: Double,     // CPU utilization percentage (0.0 - 1.0)
    val memoryUtilization: Double,  // Memory utilization percentage (0.0 - 1.0)
    val networkIn: Long,            // Network bytes in
    val networkOut: Long,           // Network bytes out
    val diskUsage: String? = null,  // Disk usage if available
    val timestamp: java.time.Instant = java.time.Instant.now()
)

/**
 * Real-time resource metrics
 */
data class ResourceMetrics(
    val clusterMetrics: ClusterMetrics,
    val poolMetrics: Map<WorkerPoolId, PoolResourceUsage>,
    val workerMetrics: Map<dev.rubentxu.hodei.pipelines.domain.worker.WorkerId, WorkerResourceUsage>,
    val timestamp: java.time.Instant = java.time.Instant.now()
)

/**
 * Cluster-level resource metrics
 */
data class ClusterMetrics(
    val totalNodes: Int,
    val healthyNodes: Int,
    val totalCpu: String,
    val availableCpu: String,
    val totalMemory: String,
    val availableMemory: String,
    val cpuUtilization: Double,     // 0.0 - 1.0
    val memoryUtilization: Double,  // 0.0 - 1.0
    val podCount: Int,
    val maxPods: Int
)

/**
 * Resource availability check result
 */
sealed class ResourceAvailabilityCheck {
    data class Available(
        val canAccommodate: Int,  // How many workers can be accommodated
        val constraints: List<ResourceConstraint> = emptyList()
    ) : ResourceAvailabilityCheck()
    
    data class PartiallyAvailable(
        val canAccommodate: Int,  // Partial number that can be accommodated
        val requested: Int,       // Originally requested
        val limitingFactor: ResourceConstraint
    ) : ResourceAvailabilityCheck()
    
    data class Unavailable(
        val limitingFactors: List<ResourceConstraint>
    ) : ResourceAvailabilityCheck()
}

/**
 * Resource constraint information
 */
data class ResourceConstraint(
    val type: ConstraintType,
    val description: String,
    val current: String,
    val required: String,
    val suggestion: String? = null
)

enum class ConstraintType {
    CPU_LIMIT,
    MEMORY_LIMIT,
    NODE_CAPACITY,
    QUOTA_LIMIT,
    STORAGE_LIMIT,
    NETWORK_LIMIT,
    CUSTOM_CONSTRAINT
}

/**
 * Resource quotas and limits
 */
data class ResourceQuotas(
    val cpuQuota: String? = null,
    val memoryQuota: String? = null,
    val storageQuota: String? = null,
    val maxPods: Int? = null,
    val maxWorkers: Int? = null,
    val used: ResourceUsage,
    val limits: ResourceLimits? = null
)

/**
 * Current resource usage against quotas
 */
data class ResourceUsage(
    val cpuUsed: String,
    val memoryUsed: String,
    val storageUsed: String? = null,
    val podsUsed: Int,
    val workersUsed: Int
)

/**
 * Historical resource usage trends
 */
data class ResourceTrends(
    val period: java.time.Duration,
    val cpuTrend: ResourceTrend,
    val memoryTrend: ResourceTrend,
    val workerCountTrend: ResourceTrend,
    val patterns: List<UsagePattern>
)

/**
 * Resource trend data
 */
data class ResourceTrend(
    val metric: String,
    val dataPoints: List<DataPoint>,
    val trend: TrendDirection,
    val averageValue: Double,
    val peakValue: Double,
    val lowValue: Double
)

enum class TrendDirection {
    INCREASING,
    DECREASING,
    STABLE,
    VOLATILE
}

/**
 * Data point for trends
 */
data class DataPoint(
    val timestamp: java.time.Instant,
    val value: Double
)

/**
 * Usage patterns identified from historical data
 */
data class UsagePattern(
    val type: PatternType,
    val description: String,
    val confidence: Double, // 0.0 - 1.0
    val timeWindows: List<TimeWindow>
)

enum class PatternType {
    DAILY_PEAK,
    WEEKLY_CYCLE,
    BURST_ACTIVITY,
    GRADUAL_INCREASE,
    SEASONAL_PATTERN
}

/**
 * Time window for patterns
 */
data class TimeWindow(
    val start: java.time.LocalTime,
    val end: java.time.LocalTime,
    val daysOfWeek: Set<java.time.DayOfWeek> = emptySet()
)

/**
 * Resource forecast
 */
data class ResourceForecast(
    val forecastPeriod: java.time.Duration,
    val predictions: List<ResourcePrediction>,
    val confidence: Double, // 0.0 - 1.0
    val recommendations: List<ScalingRecommendation>
)

/**
 * Resource prediction for a specific time
 */
data class ResourcePrediction(
    val timestamp: java.time.Instant,
    val predictedCpuUsage: Double,
    val predictedMemoryUsage: Double,
    val predictedWorkerCount: Int,
    val confidence: Double
)

/**
 * Scaling recommendation based on forecast
 */
data class ScalingRecommendation(
    val action: RecommendedAction,
    val timeWindow: java.time.Instant,
    val poolId: WorkerPoolId,
    val suggestedWorkerCount: Int,
    val reason: String,
    val priority: RecommendationPriority
)

enum class RecommendedAction {
    SCALE_UP,
    SCALE_DOWN,
    MAINTAIN,
    PRE_SCALE
}

enum class RecommendationPriority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}