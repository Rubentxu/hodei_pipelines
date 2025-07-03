package dev.rubentxu.hodei.resourcemanagement.infrastructure.docker

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.domain.pool.ResourcePool
import dev.rubentxu.hodei.domain.pool.PoolStatus
import dev.rubentxu.hodei.domain.pool.ProviderType
import dev.rubentxu.hodei.domain.pool.PoolCapacity
import dev.rubentxu.hodei.domain.pool.PoolPolicies
import dev.rubentxu.hodei.domain.pool.ScalingPolicy
import dev.rubentxu.hodei.domain.pool.PlacementPolicy
import dev.rubentxu.hodei.domain.pool.CostPolicy
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import java.time.Duration

private val logger = KotlinLogging.logger {}

/**
 * Docker Environment Bootstrap - MVP Implementation
 * 
 * Provides zero-configuration Docker environment discovery and setup.
 * This is the core component that enables the CLI to auto-discover and
 * configure Docker environments for worker execution.
 */
class DockerEnvironmentBootstrap(
    private val dockerConfig: DockerConfig = DockerConfig()
) {
    
    private val dockerClient: DockerClient by lazy {
        val config = DefaultDockerClientConfig.createDefaultConfigBuilder()
            .withDockerHost(dockerConfig.dockerHost)
            .withDockerTlsVerify(dockerConfig.tlsVerify)
            .withDockerCertPath(dockerConfig.certPath)
            .withApiVersion(dockerConfig.apiVersion)
            .build()

        val httpClient = ApacheDockerHttpClient.Builder()
            .dockerHost(config.dockerHost)
            .sslConfig(config.sslConfig)
            .maxConnections(dockerConfig.maxConnections)
            .connectionTimeout(Duration.ofSeconds(dockerConfig.connectionTimeoutSeconds))
            .responseTimeout(Duration.ofSeconds(dockerConfig.responseTimeoutSeconds))
            .build()

        DockerClientBuilder.getInstance(config)
            .withDockerHttpClient(httpClient)
            .build()
    }
    
    /**
     * Check if Docker daemon is available and accessible
     */
    suspend fun isDockerAvailable(): Boolean = withContext(Dispatchers.IO) {
        try {
            dockerClient.pingCmd().exec()
            true
        } catch (e: Exception) {
            logger.warn("Docker daemon not available", e)
            false
        }
    }
    
    /**
     * Get comprehensive Docker environment information
     */
    suspend fun getDockerEnvironmentInfo(): Result<DockerEnvironmentInfo> = withContext(Dispatchers.IO) {
        try {
            val info = dockerClient.infoCmd().exec()
            val version = dockerClient.versionCmd().exec()
            
            Result.success(
                DockerEnvironmentInfo(
                    version = version.version ?: "unknown",
                    apiVersion = version.apiVersion ?: "unknown",
                    totalMemory = info.memTotal ?: 0L,
                    cpuCount = info.ncpu ?: 1,
                    containersRunning = info.containersRunning ?: 0,
                    containersPaused = info.containersPaused ?: 0,
                    containersStopped = info.containersStopped ?: 0,
                    imagesCount = info.images ?: 0,
                    serverVersion = info.serverVersion ?: "unknown",
                    operatingSystem = info.operatingSystem ?: "unknown",
                    architecture = info.architecture ?: "unknown",
                    kernelVersion = info.kernelVersion ?: "unknown",
                    isSwarmMode = info.swarm?.nodeID != null,
                    dockerRootDir = info.dockerRootDir ?: "/var/lib/docker"
                )
            )
        } catch (e: Exception) {
            logger.error("Failed to get Docker environment info", e)
            Result.failure(e)
        }
    }
    
    /**
     * Calculate optimal worker configuration based on available resources
     */
    suspend fun calculateOptimalConfiguration(): OptimalConfiguration = withContext(Dispatchers.IO) {
        try {
            val info = dockerClient.infoCmd().exec()
            val totalMemoryMB = (info.memTotal ?: 0L) / (1024 * 1024)
            val totalCpus = info.ncpu ?: 1
            
            // Conservative allocation: leave 25% of resources for system and Docker daemon
            val availableMemoryMB = (totalMemoryMB * 0.75).toLong()
            val availableCpus = (totalCpus * 0.75)
            
            // Calculate workers based on minimum resource requirements
            // Minimum per worker: 512MB RAM, 0.5 CPU
            val maxWorkersByMemory = (availableMemoryMB / 512).toInt()
            val maxWorkersByCpu = (availableCpus / 0.5).toInt()
            
            // Take the most restrictive limit
            val maxWorkers = minOf(maxWorkersByMemory, maxWorkersByCpu, 10) // Cap at 10 for safety
            
            // Calculate resources per worker
            val memoryPerWorkerMB = if (maxWorkers > 0) availableMemoryMB / maxWorkers else 512L
            val cpuPerWorker = if (maxWorkers > 0) availableCpus / maxWorkers else 0.5
            
            OptimalConfiguration(
                maxWorkers = maxOf(1, maxWorkers), // At least 1 worker
                memoryPerWorkerMB = maxOf(512L, memoryPerWorkerMB),
                cpuPerWorker = maxOf(0.5, cpuPerWorker),
                totalAvailableMemoryMB = availableMemoryMB,
                totalAvailableCpus = availableCpus
            )
        } catch (e: Exception) {
            logger.error("Failed to calculate optimal configuration", e)
            // Fallback configuration
            OptimalConfiguration(
                maxWorkers = 2,
                memoryPerWorkerMB = 1024L,
                cpuPerWorker = 1.0,
                totalAvailableMemoryMB = 2048L,
                totalAvailableCpus = 2.0
            )
        }
    }
    
    /**
     * Validate Docker compatibility for Hodei workers
     */
    suspend fun validateDockerCompatibility(): Result<CompatibilityReport> = withContext(Dispatchers.IO) {
        try {
            val version = dockerClient.versionCmd().exec()
            val info = dockerClient.infoCmd().exec()
            
            val issues = mutableListOf<String>()
            val warnings = mutableListOf<String>()
            
            // Check Docker version
            val dockerVersion = version.version ?: "unknown"
            if (dockerVersion.startsWith("20.") || dockerVersion.startsWith("24.") || dockerVersion.startsWith("25.")) {
                // Good versions
            } else if (dockerVersion.startsWith("19.")) {
                warnings.add("Docker version $dockerVersion is older than recommended (20.x+)")
            } else {
                issues.add("Docker version $dockerVersion may not be fully compatible")
            }
            
            // Check API version
            val apiVersion = version.apiVersion ?: "unknown"
            val supportedApiVersions = listOf("1.40", "1.41", "1.42", "1.43")
            if (!supportedApiVersions.any { apiVersion.startsWith(it) }) {
                warnings.add("Docker API version $apiVersion may not be fully supported")
            }
            
            // Check available memory
            val totalMemoryMB = (info.memTotal ?: 0L) / (1024 * 1024)
            if (totalMemoryMB < 1024) {
                issues.add("Insufficient memory: ${totalMemoryMB}MB available, 1GB+ recommended")
            } else if (totalMemoryMB < 2048) {
                warnings.add("Low memory: ${totalMemoryMB}MB available, 2GB+ recommended for best performance")
            }
            
            // Check CPU count
            val cpuCount = info.ncpu ?: 1
            if (cpuCount < 2) {
                warnings.add("Single CPU core detected, multiple cores recommended for concurrent jobs")
            }
            
            // Check storage driver
            val storageDriver = info.driver
            if (storageDriver != null && storageDriver !in listOf("overlay2", "aufs", "devicemapper")) {
                warnings.add("Storage driver '$storageDriver' may have performance implications")
            }
            
            val isCompatible = issues.isEmpty()
            
            if (isCompatible) {
                Result.success(
                    CompatibilityReport(
                        isCompatible = true,
                        issues = issues,
                        warnings = warnings,
                        dockerVersion = dockerVersion,
                        apiVersion = apiVersion,
                        recommendation = if (warnings.isEmpty()) 
                            "Docker environment is fully compatible and optimized for Hodei workers"
                        else
                            "Docker environment is compatible with minor recommendations"
                    )
                )
            } else {
                Result.failure(
                    Exception("Docker compatibility issues: ${issues.joinToString(", ")}")
                )
            }
        } catch (e: Exception) {
            logger.error("Failed to validate Docker compatibility", e)
            Result.failure(e)
        }
    }
    
    /**
     * Register the Docker environment as a resource pool
     */
    suspend fun registerAsResourcePool(
        poolId: DomainId,
        poolName: String,
        maxWorkers: Int
    ): Result<ResourcePool> = withContext(Dispatchers.IO) {
        try {
            val now = Clock.System.now()
            
            val pool = ResourcePool(
                id = poolId,
                name = poolName,
                provider = ProviderType.DOCKER,
                config = buildJsonObject {
                    put("dockerHost", dockerConfig.dockerHost)
                    put("orchestratorHost", dockerConfig.orchestratorHost)
                    put("orchestratorPort", dockerConfig.orchestratorPort.toString())
                    put("defaultWorkerImage", dockerConfig.defaultWorkerImage)
                    put("apiVersion", dockerConfig.apiVersion)
                },
                policies = PoolPolicies(
                    scaling = ScalingPolicy(
                        minWorkers = 0,
                        maxWorkers = maxWorkers,
                        targetUtilization = 0.8
                    ),
                    placement = PlacementPolicy(),
                    cost = CostPolicy()
                ),
                status = PoolStatus.ACTIVE,
                capacity = PoolCapacity(
                    totalCpuCores = 4.0, // Default values, could be calculated from Docker info
                    totalMemoryGB = 8.0,
                    availableCpuCores = 4.0,
                    availableMemoryGB = 8.0,
                    activeWorkers = 0,
                    pendingWorkers = 0
                ),
                createdAt = now,
                updatedAt = now,
                createdBy = "hodei-cli"
            )
            
            // In a real implementation, this would save to the repository
            logger.info("Docker resource pool registered: ${pool.name} (${pool.id.value})")
            
            Result.success(pool)
        } catch (e: Exception) {
            logger.error("Failed to register Docker resource pool", e)
            Result.failure(e)
        }
    }
    
    /**
     * Perform health check on Docker daemon
     */
    suspend fun performHealthCheck(): Result<HealthCheckResult> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            
            // Ping Docker daemon
            dockerClient.pingCmd().exec()
            val pingTime = System.currentTimeMillis() - startTime
            
            // Get basic info
            val info = dockerClient.infoCmd().exec()
            val version = dockerClient.versionCmd().exec()
            
            // Check if daemon is responsive
            val isHealthy = pingTime < 5000 // Ping should be under 5 seconds
            
            Result.success(
                HealthCheckResult(
                    isHealthy = isHealthy,
                    responseTimeMs = pingTime,
                    version = version.version ?: "unknown",
                    apiVersion = version.apiVersion ?: "unknown",
                    containersRunning = info.containersRunning ?: 0,
                    memoryUsagePercent = calculateMemoryUsagePercent(info),
                    issues = if (isHealthy) emptyList() else listOf("Docker daemon response time too high: ${pingTime}ms")
                )
            )
        } catch (e: Exception) {
            logger.error("Docker health check failed", e)
            Result.success(
                HealthCheckResult(
                    isHealthy = false,
                    responseTimeMs = -1,
                    version = "unknown",
                    apiVersion = "unknown",
                    containersRunning = 0,
                    memoryUsagePercent = 0.0,
                    issues = listOf("Docker daemon is not accessible: ${e.message}")
                )
            )
        }
    }
    
    private fun calculateMemoryUsagePercent(info: com.github.dockerjava.api.model.Info): Double {
        val totalMemory = info.memTotal ?: 0L
        if (totalMemory == 0L) return 0.0
        
        // Rough estimation based on running containers
        val runningContainers = info.containersRunning ?: 0
        val estimatedUsage = runningContainers * 512L * 1024 * 1024 // 512MB per container estimate
        
        return minOf(100.0, (estimatedUsage.toDouble() / totalMemory.toDouble()) * 100.0)
    }
    
    /**
     * Data classes for Docker environment information
     */
    data class DockerEnvironmentInfo(
        val version: String,
        val apiVersion: String,
        val totalMemory: Long,
        val cpuCount: Int,
        val containersRunning: Int,
        val containersPaused: Int,
        val containersStopped: Int,
        val imagesCount: Int,
        val serverVersion: String,
        val operatingSystem: String,
        val architecture: String,
        val kernelVersion: String,
        val isSwarmMode: Boolean,
        val dockerRootDir: String
    )
    
    data class OptimalConfiguration(
        val maxWorkers: Int,
        val memoryPerWorkerMB: Long,
        val cpuPerWorker: Double,
        val totalAvailableMemoryMB: Long,
        val totalAvailableCpus: Double
    )
    
    data class CompatibilityReport(
        val isCompatible: Boolean,
        val issues: List<String>,
        val warnings: List<String>,
        val dockerVersion: String,
        val apiVersion: String,
        val recommendation: String
    )
    
    data class HealthCheckResult(
        val isHealthy: Boolean,
        val responseTimeMs: Long,
        val version: String,
        val apiVersion: String,
        val containersRunning: Int,
        val memoryUsagePercent: Double,
        val issues: List<String>
    )
}