package dev.rubentxu.hodei.resourcemanagement.infrastructure.docker

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Statistics
import com.github.dockerjava.api.command.InspectContainerResponse
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.resourcemanagement.domain.ports.*
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

/**
 * Docker implementation of IResourceMonitor.
 * Monitors Docker daemon resources and container statistics.
 * 
 * Provides real-time resource monitoring, capacity tracking, and
 * health status for Docker-based resource pools.
 */
class DockerResourceMonitor(
    private val dockerClient: DockerClient,
    private val dockerConfig: DockerMonitoringConfig = DockerMonitoringConfig()
) : IResourceMonitor {
    
    private val logger = LoggerFactory.getLogger(DockerResourceMonitor::class.java)
    
    // Cache for resource metrics to reduce Docker API calls
    private val metricsCache = ConcurrentHashMap<String, CachedMetric>()
    private val cacheExpirationMs = 30_000L // 30 seconds
    
    override suspend fun getUtilization(resourcePoolId: DomainId): Either<String, ResourcePoolUtilization> = 
        withContext(Dispatchers.IO) {
            try {
                logger.debug("Getting resource utilization for pool ${resourcePoolId.value}")
                
                // Get Docker daemon info
                val systemInfo = dockerClient.infoCmd().exec()
                
                // Get containers for this resource pool
                val containers = dockerClient.listContainersCmd()
                    .withLabelFilter(mapOf("hodei.resource-pool" to resourcePoolId.value))
                    .withShowAll(false) // Only running containers
                    .exec()
                
                // Calculate aggregate resource usage
                val totalCpuUsage = calculateTotalCpuUsage(containers.map { it.id })
                val totalMemoryUsage = calculateTotalMemoryUsage(containers.map { it.id })
                val storageUsage = calculateStorageUsage(resourcePoolId)
                val networkUsage = calculateNetworkUsage(containers.map { it.id })
                
                val utilization = ResourcePoolUtilization(
                    poolId = resourcePoolId,
                    totalCpu = (systemInfo.ncpu?.toDouble() ?: 1.0),
                    usedCpu = totalCpuUsage.usedCores,
                    totalMemoryBytes = systemInfo.memTotal ?: 0L,
                    usedMemoryBytes = totalMemoryUsage.usedBytes,
                    totalDiskBytes = storageUsage.total * 1024 * 1024 * 1024, // Convert GB to bytes
                    usedDiskBytes = storageUsage.used * 1024 * 1024 * 1024, // Convert GB to bytes
                    runningJobs = containers.count { it.state == "running" },
                    queuedJobs = 0, // Will be provided by job queue monitor
                    timestamp = Clock.System.now()
                )
                
                utilization.right()
                
            } catch (e: Exception) {
                logger.error("Failed to get resource utilization", e)
                "Failed to get resource utilization: ${e.message}".left()
            }
        }
    
    override fun subscribeToResourceUpdates(resourcePoolId: DomainId): Flow<ResourcePoolUtilization> = flow {
        try {
            logger.info("Starting resource monitoring for pool ${resourcePoolId.value}")
            
            while (true) {
                when (val result = getUtilization(resourcePoolId)) {
                    is Either.Right -> emit(result.value)
                    is Either.Left -> {
                        logger.warn("Failed to get utilization update: ${result.value}")
                        // Continue monitoring even if one update fails
                    }
                }
                
                delay(dockerConfig.monitoringIntervalMs)
            }
            
        } catch (e: Exception) {
            logger.error("Resource monitoring failed for pool ${resourcePoolId.value}", e)
            throw e
        }
    }
    
    override suspend fun getResourceCapacity(resourcePoolId: DomainId): Result<ResourceCapacity> =
        withContext(Dispatchers.IO) {
            try {
                val systemInfo = dockerClient.infoCmd().exec()
                
                val capacity = ResourceCapacity(
                    resourcePoolId = resourcePoolId,
                    totalCpuCores = systemInfo.ncpu ?: 1,
                    totalMemoryMB = (systemInfo.memTotal ?: 0L) / (1024 * 1024),
                    totalStorageGB = calculateTotalStorage(),
                    maxInstances = calculateMaxInstances(systemInfo),
                    availableInstanceTypes = listOf(
                        InstanceType.SMALL,
                        InstanceType.MEDIUM,
                        InstanceType.LARGE,
                        InstanceType.XLARGE,
                        InstanceType.CUSTOM
                    )
                )
                
                Result.success(capacity)
                
            } catch (e: Exception) {
                logger.error("Failed to get resource capacity", e)
                Result.failure(e)
            }
        }
    
    override suspend fun getNodesHealth(resourcePoolId: DomainId): Result<List<NodeHealth>> =
        withContext(Dispatchers.IO) {
            try {
                val systemInfo = dockerClient.infoCmd().exec()
                val version = dockerClient.versionCmd().exec()
                
                // For Docker, we treat the Docker daemon as the single "node"
                val nodeHealth = NodeHealth(
                    nodeId = DomainId("docker-daemon"),
                    nodeName = systemInfo.name ?: "docker-daemon",
                    status = if (systemInfo.containersRunning ?: 0 >= 0) NodeStatus.READY else NodeStatus.NOT_READY,
                    lastHeartbeat = Clock.System.now(),
                    uptime = calculateDockerUptime(systemInfo),
                    conditions = buildDockerConditions(systemInfo)
                )
                
                Result.success(listOf(nodeHealth))
                
            } catch (e: Exception) {
                logger.error("Failed to get nodes health", e)
                Result.failure(e)
            }
        }
    
    override suspend fun getResourceMetrics(
        resourcePoolId: DomainId,
        startTime: Instant,
        endTime: Instant,
        interval: MetricInterval
    ): Result<List<ResourceMetric>> =
        withContext(Dispatchers.IO) {
            try {
                logger.debug("Getting resource metrics for pool ${resourcePoolId.value} from $startTime to $endTime")
                
                // For MVP, return current metrics only
                // In production, this would query historical data from a monitoring system
                val currentUtilization = getUtilization(resourcePoolId)
                
                val metrics = when (currentUtilization) {
                    is Either.Right -> {
                        val util = currentUtilization.value
                        listOf(
                            ResourceMetric(
                                timestamp = util.timestamp,
                                resourceType = ResourceType.CPU_USAGE,
                                value = util.cpuUtilizationPercent,
                                unit = ResourceUnit.PERCENTAGE
                            ),
                            ResourceMetric(
                                timestamp = util.timestamp,
                                resourceType = ResourceType.MEMORY_USAGE,
                                value = util.memoryUtilizationPercent,
                                unit = ResourceUnit.PERCENTAGE
                            ),
                            ResourceMetric(
                                timestamp = util.timestamp,
                                resourceType = ResourceType.INSTANCE_COUNT,
                                value = util.runningJobs.toDouble(),
                                unit = ResourceUnit.COUNT
                            )
                        )
                    }
                    is Either.Left -> emptyList()
                }
                
                Result.success(metrics)
                
            } catch (e: Exception) {
                logger.error("Failed to get resource metrics", e)
                Result.failure(e)
            }
        }
    
    override suspend fun checkResourceAvailability(
        resourcePoolId: DomainId,
        resourceRequest: ResourceRequest
    ): Result<ResourceAvailability> =
        withContext(Dispatchers.IO) {
            try {
                val capacity = getResourceCapacity(resourcePoolId).getOrThrow()
                val utilization = getUtilization(resourcePoolId)
                
                when (utilization) {
                    is Either.Right -> {
                        val util = utilization.value
                        
                        val cpuAvailable = util.availableCpu >= resourceRequest.cpuCores
                        val memoryAvailable = util.availableMemoryBytes >= (resourceRequest.memoryMB * 1024 * 1024)
                        val instanceSlotAvailable = (capacity.maxInstances - util.runningJobs) >= resourceRequest.instanceCount
                        
                        val available = cpuAvailable && memoryAvailable && instanceSlotAvailable
                        val reasons = mutableListOf<String>()
                        
                        if (!cpuAvailable) reasons.add("Insufficient CPU cores (need ${resourceRequest.cpuCores}, available ${util.availableCpu})")
                        if (!memoryAvailable) reasons.add("Insufficient memory (need ${resourceRequest.memoryMB}MB, available ${util.availableMemoryBytes / (1024 * 1024)}MB)")
                        if (!instanceSlotAvailable) reasons.add("Insufficient instance slots (need ${resourceRequest.instanceCount}, available ${capacity.maxInstances - util.runningJobs})")
                        
                        val availability = ResourceAvailability(
                            available = available,
                            reason = if (reasons.isNotEmpty()) reasons.joinToString("; ") else null,
                            estimatedWaitTime = if (!available) estimateWaitTime(resourceRequest, util) else null,
                            alternativeOptions = if (!available) generateAlternatives(resourceRequest, capacity) else emptyList()
                        )
                        
                        Result.success(availability)
                    }
                    is Either.Left -> {
                        Result.success(
                            ResourceAvailability(
                                available = false,
                                reason = "Unable to check resource availability: ${utilization.value}"
                            )
                        )
                    }
                }
                
            } catch (e: Exception) {
                logger.error("Failed to check resource availability", e)
                Result.failure(e)
            }
        }
    
    override suspend fun getQuotaUsage(resourcePoolId: DomainId): Result<QuotaUsage> =
        withContext(Dispatchers.IO) {
            try {
                // Docker doesn't have built-in quotas like Kubernetes
                // We can implement soft limits based on configuration
                val capacity = getResourceCapacity(resourcePoolId).getOrThrow()
                val utilization = getUtilization(resourcePoolId)
                
                val quotas = when (utilization) {
                    is Either.Right -> {
                        val util = utilization.value
                        mapOf(
                            "cpu.cores" to QuotaLimit(
                                limit = capacity.totalCpuCores.toLong(),
                                used = util.usedCpu.toLong(),
                                unit = ResourceUnit.CORES
                            ),
                            "memory.megabytes" to QuotaLimit(
                                limit = capacity.totalMemoryMB,
                                used = util.usedMemoryBytes / (1024 * 1024),
                                unit = ResourceUnit.MEGABYTES
                            ),
                            "instances.count" to QuotaLimit(
                                limit = capacity.maxInstances.toLong(),
                                used = util.runningJobs.toLong(),
                                unit = ResourceUnit.COUNT
                            )
                        )
                    }
                    is Either.Left -> emptyMap()
                }
                
                val quotaUsage = QuotaUsage(
                    resourcePoolId = resourcePoolId,
                    namespace = "docker", // Docker doesn't have namespaces like K8s
                    quotas = quotas
                )
                
                Result.success(quotaUsage)
                
            } catch (e: Exception) {
                logger.error("Failed to get quota usage", e)
                Result.failure(e)
            }
        }
    
    private suspend fun calculateTotalCpuUsage(containerIds: List<String>): CpuUsageResult = 
        withContext(Dispatchers.IO) {
            try {
                var totalUsedCores = 0.0
                var totalPercentage = 0.0
                
                containerIds.forEach { containerId ->
                    val stats = getContainerStats(containerId)
                    if (stats != null) {
                        val cpuPercent = calculateCpuPercentage(stats)
                        totalPercentage += cpuPercent
                        totalUsedCores += cpuPercent / 100.0 // Convert percentage to cores
                    }
                }
                
                CpuUsageResult(totalUsedCores, totalPercentage)
                
            } catch (e: Exception) {
                logger.warn("Failed to calculate CPU usage", e)
                CpuUsageResult(0.0, 0.0)
            }
        }
    
    private suspend fun calculateTotalMemoryUsage(containerIds: List<String>): MemoryUsageResult = 
        withContext(Dispatchers.IO) {
            try {
                var totalUsedBytes = 0L
                var totalPercentage = 0.0
                
                containerIds.forEach { containerId ->
                    val stats = getContainerStats(containerId)
                    if (stats != null) {
                        val memoryUsage = stats.memoryStats
                        val usedBytes = memoryUsage?.usage ?: 0L
                        val limitBytes = memoryUsage?.limit ?: 0L
                        
                        totalUsedBytes += usedBytes
                        if (limitBytes > 0) {
                            totalPercentage += (usedBytes.toDouble() / limitBytes.toDouble()) * 100.0
                        }
                    }
                }
                
                MemoryUsageResult(totalUsedBytes, totalPercentage)
                
            } catch (e: Exception) {
                logger.warn("Failed to calculate memory usage", e)
                MemoryUsageResult(0L, 0.0)
            }
        }
    
    private suspend fun calculateStorageUsage(resourcePoolId: DomainId): ResourceUsage = 
        withContext(Dispatchers.IO) {
            try {
                // Get Docker system disk usage - simplified approach
                val systemInfo = dockerClient.infoCmd().exec()
                
                // Estimate storage usage based on containers and images
                val images = dockerClient.listImagesCmd().exec()
                val containers = dockerClient.listContainersCmd().withShowAll(true).exec()
                
                val totalImagesSize = images.sumOf { it.size ?: 0L }
                val totalContainersSize = containers.sumOf { it.sizeRw ?: 0L }
                
                val totalUsedBytes = totalImagesSize + totalContainersSize
                val totalUsedGB = totalUsedBytes / (1024 * 1024 * 1024)
                
                // Estimate total available storage (this is approximate)
                val totalStorageGB = calculateTotalStorage()
                
                ResourceUsage(
                    used = totalUsedGB,
                    total = totalStorageGB,
                    percentage = if (totalStorageGB > 0) (totalUsedGB.toDouble() / totalStorageGB.toDouble()) * 100.0 else 0.0,
                    unit = ResourceUnit.GIGABYTES
                )
                
            } catch (e: Exception) {
                logger.warn("Failed to calculate storage usage", e)
                ResourceUsage(0L, 0L, 0.0, ResourceUnit.GIGABYTES)
            }
        }
    
    private suspend fun calculateNetworkUsage(containerIds: List<String>): NetworkUsage = 
        withContext(Dispatchers.IO) {
            try {
                var totalInbound = 0L
                var totalOutbound = 0L
                var totalConnections = 0
                
                containerIds.forEach { containerId ->
                    val stats = getContainerStats(containerId)
                    if (stats != null) {
                        val networks = stats.networks
                        networks?.values?.forEach { network ->
                            totalInbound += network.rxBytes ?: 0L
                            totalOutbound += network.txBytes ?: 0L
                        }
                        totalConnections++
                    }
                }
                
                NetworkUsage(
                    inboundBytesPerSecond = totalInbound, // Simplified - not actual per-second rate
                    outboundBytesPerSecond = totalOutbound,
                    connectionsCount = totalConnections
                )
                
            } catch (e: Exception) {
                logger.warn("Failed to calculate network usage", e)
                NetworkUsage(0L, 0L, 0)
            }
        }
    
    private fun getContainerStats(containerId: String): Statistics? {
        return try {
            val cacheKey = "stats-$containerId"
            val cached = metricsCache[cacheKey]
            
            if (cached != null && (System.currentTimeMillis() - cached.timestamp) < cacheExpirationMs) {
                return cached.data as Statistics
            }
            
            val stats = dockerClient.statsCmd(containerId)
                .withNoStream(true)
                .exec(StatsCallback())
                .awaitResult()
            
            if (stats != null) {
                metricsCache[cacheKey] = CachedMetric(System.currentTimeMillis(), stats)
            }
            stats
            
        } catch (e: Exception) {
            logger.debug("Failed to get stats for container $containerId", e)
            null
        }
    }
    
    private fun calculateCpuPercentage(stats: Statistics): Double {
        val cpuStats = stats.cpuStats
        val preCpuStats = stats.preCpuStats
        
        val cpuDelta = (cpuStats?.cpuUsage?.totalUsage ?: 0L) - (preCpuStats?.cpuUsage?.totalUsage ?: 0L)
        val systemDelta = (cpuStats?.systemCpuUsage ?: 0L) - (preCpuStats?.systemCpuUsage ?: 0L)
        val onlineCpus = cpuStats?.onlineCpus ?: 1
        
        return if (systemDelta > 0 && cpuDelta > 0) {
            (cpuDelta.toDouble() / systemDelta.toDouble()) * onlineCpus * 100.0
        } else {
            0.0
        }
    }
    
    private fun calculateTotalStorage(): Long {
        return try {
            val systemInfo = dockerClient.infoCmd().exec()
            // This is a rough estimate - Docker doesn't provide direct storage info
            // In production, you'd want to check the actual disk space of Docker's data directory
            100L // 100GB default estimate
        } catch (e: Exception) {
            100L
        }
    }
    
    private fun calculateMaxInstances(systemInfo: com.github.dockerjava.api.model.Info): Int {
        val memoryMB = (systemInfo.memTotal ?: 0L) / (1024 * 1024)
        val cpuCores = systemInfo.ncpu ?: 1
        
        // Estimate max instances based on minimum resource requirements
        val maxByCpu = cpuCores * 4 // Assume 0.25 CPU per instance minimum
        val maxByMemory = (memoryMB / 512).toInt() // Assume 512MB per instance minimum
        
        return minOf(maxByCpu, maxByMemory, 50) // Cap at 50 containers
    }
    
    private fun calculateDockerUptime(systemInfo: com.github.dockerjava.api.model.Info): Long {
        // Docker doesn't provide uptime directly, return 0 for MVP
        return 0L
    }
    
    private fun buildDockerConditions(systemInfo: com.github.dockerjava.api.model.Info): List<NodeCondition> {
        val conditions = mutableListOf<NodeCondition>()
        val now = Clock.System.now()
        
        // Ready condition
        conditions.add(
            NodeCondition(
                type = ConditionType.READY,
                status = true,
                message = "Docker daemon is responding",
                lastTransition = now
            )
        )
        
        // Memory pressure check (if more than 90% used)
        val memoryTotal = systemInfo.memTotal ?: 0L
        val memoryUsed = memoryTotal * 0.1 // Rough estimate
        if (memoryTotal > 0 && (memoryUsed / memoryTotal.toDouble()) > 0.9) {
            conditions.add(
                NodeCondition(
                    type = ConditionType.MEMORY_PRESSURE,
                    status = true,
                    message = "High memory usage detected",
                    lastTransition = now
                )
            )
        }
        
        return conditions
    }
    
    private fun estimateWaitTime(resourceRequest: ResourceRequest, utilization: ResourcePoolUtilization): Long {
        // Simple estimation based on current load
        // In production, this would use historical data and predictive models
        return when {
            utilization.cpuUtilizationPercent > 90 -> 300L // 5 minutes
            utilization.memoryUtilizationPercent > 90 -> 180L // 3 minutes
            else -> 60L // 1 minute
        }
    }
    
    private fun generateAlternatives(
        resourceRequest: ResourceRequest,
        capacity: ResourceCapacity
    ): List<ResourceSuggestion> {
        val alternatives = mutableListOf<ResourceSuggestion>()
        
        // Suggest smaller instance types if available
        if (resourceRequest.cpuCores > 1) {
            alternatives.add(
                ResourceSuggestion(
                    instanceType = InstanceType.SMALL,
                    availableCount = 5,
                    estimatedCost = 0.1
                )
            )
        }
        
        return alternatives
    }
}

/**
 * Internal data classes for caching and calculations
 */
private data class CachedMetric(
    val timestamp: Long,
    val data: Any
)

private data class CpuUsageResult(
    val usedCores: Double,
    val percentage: Double
)

private data class MemoryUsageResult(
    val usedBytes: Long,
    val percentage: Double
)

/**
 * Docker configuration for resource monitoring
 */
data class DockerMonitoringConfig(
    val monitoringIntervalMs: Long = 15_000L, // 15 seconds
    val metricsRetentionHours: Int = 24,
    val enableDetailedMetrics: Boolean = true
)

/**
 * Callback for Docker stats command
 */
private class StatsCallback : com.github.dockerjava.api.async.ResultCallback.Adapter<Statistics>() {
    private var result: Statistics? = null
    
    override fun onNext(item: Statistics) {
        result = item
    }
    
    fun awaitResult(): Statistics? {
        awaitCompletion()
        return result
    }
}