package dev.rubentxu.hodei.resourcemanagement.infrastructure.docker

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.*
import com.github.dockerjava.api.command.InspectContainerResponse
import com.github.dockerjava.core.DefaultDockerClientConfig
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.ProvisioningError
import dev.rubentxu.hodei.resourcemanagement.domain.ports.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Docker implementation of IInstanceManager.
 * Manages worker containers on Docker daemon.
 * 
 * Handles container lifecycle, resource allocation, and Docker-specific operations.
 * Supports auto-discovery of local Docker environment and dynamic scaling.
 */
class DockerInstanceManager(
    private val dockerConfig: DockerConfig = DockerConfig()
) : IInstanceManager {
    
    private val logger = LoggerFactory.getLogger(DockerInstanceManager::class.java)
    
    // Docker client for API operations
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
    
    // Track active containers for management
    private val activeContainers = ConcurrentHashMap<String, DockerContainerInstance>()
    
    override suspend fun provisionInstance(
        poolId: DomainId,
        spec: InstanceSpec
    ): Either<ProvisioningError, ComputeInstance> = withContext(Dispatchers.IO) {
        try {
            logger.info("Provisioning Docker container in pool ${poolId.value} with spec: $spec")
            
            val containerName = generateContainerName(spec)
            val dockerSpec = createContainerSpec(containerName, spec)
            
            // Check if image exists locally, pull if necessary
            ensureImageAvailable(spec.image)
            
            // Create container
            val createResponse = dockerClient.createContainerCmd(spec.image)
                .withName(containerName)
                .withLabels(dockerSpec.labels)
                .withEnv(dockerSpec.environment)
                .withCmd(dockerSpec.command)
                .withHostConfig(dockerSpec.hostConfig)
                // .withNetworkingConfig(dockerSpec.networkingConfig) // Method removed in Docker Java API 3.4.1
                .exec()
            
            val containerId = createResponse.id
            logger.debug("Created container with ID: $containerId")
            
            // Start container
            dockerClient.startContainerCmd(containerId).exec()
            
            // Get container info
            val containerInfo = dockerClient.inspectContainerCmd(containerId).exec()
            
            val instanceId = DomainId("docker-${containerId.take(12)}")
            val now = Clock.System.now()
            
            val instance = ComputeInstance(
                id = instanceId,
                name = containerName,
                type = spec.instanceType,
                status = mapDockerStateToInstanceStatus(containerInfo.state),
                resourcePoolId = poolId,
                executionId = null,
                metadata = buildContainerMetadata(containerInfo, spec),
                createdAt = now,
                lastUpdatedAt = now
            )
            
            // Track the container
            activeContainers[instanceId.value] = DockerContainerInstance(
                instance = instance,
                containerId = containerId,
                dockerSpec = dockerSpec
            )
            
            logger.info("Successfully provisioned Docker container $containerName with ID $containerId")
            instance.right()
            
        } catch (e: Exception) {
            logger.error("Failed to provision Docker container", e)
            ProvisioningError.ProvisioningFailedError(
                "Failed to create container: ${e.message}"
            ).left()
        }
    }
    
    override suspend fun terminateInstance(instanceId: DomainId): Either<ProvisioningError, Unit> = 
        withContext(Dispatchers.IO) {
            try {
                logger.info("Terminating Docker container instance ${instanceId.value}")
                
                val containerInstance = activeContainers[instanceId.value]
                if (containerInstance == null) {
                    logger.warn("Container instance ${instanceId.value} not found")
                    return@withContext ProvisioningError.InvalidSpecError(
                        "Container instance ${instanceId.value} not found"
                    ).left()
                }
                
                val containerId = containerInstance.containerId
                
                // Stop container gracefully with timeout
                dockerClient.stopContainerCmd(containerId)
                    .withTimeout(dockerConfig.stopTimeoutSeconds)
                    .exec()
                
                // Remove container and its volumes
                dockerClient.removeContainerCmd(containerId)
                    .withRemoveVolumes(true)
                    .withForce(true)
                    .exec()
                
                activeContainers.remove(instanceId.value)
                
                logger.info("Successfully terminated container ${containerInstance.instance.name}")
                Unit.right()
                
            } catch (e: Exception) {
                logger.error("Failed to terminate Docker container", e)
                ProvisioningError.ProvisioningFailedError(
                    "Failed to terminate container: ${e.message}"
                ).left()
            }
        }
    
    override suspend fun getInstanceStatus(instanceId: DomainId): Result<InstanceStatus> =
        withContext(Dispatchers.IO) {
            try {
                val containerInstance = activeContainers[instanceId.value]
                if (containerInstance == null) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Container instance ${instanceId.value} not found")
                    )
                }
                
                val containerInfo = dockerClient.inspectContainerCmd(containerInstance.containerId).exec()
                val status = mapDockerStateToInstanceStatus(containerInfo.state)
                
                Result.success(status)
                
            } catch (e: Exception) {
                logger.error("Failed to get container status", e)
                Result.failure(e)
            }
        }
    
    override suspend fun listInstances(resourcePoolId: DomainId): Result<List<ComputeInstance>> =
        withContext(Dispatchers.IO) {
            try {
                val instances = activeContainers.values
                    .filter { it.instance.resourcePoolId == resourcePoolId }
                    .map { it.instance }
                
                logger.debug("Found ${instances.size} containers in pool ${resourcePoolId.value}")
                Result.success(instances)
                
            } catch (e: Exception) {
                logger.error("Failed to list containers", e)
                Result.failure(e)
            }
        }
    
    override suspend fun scaleInstances(
        resourcePoolId: DomainId,
        targetCount: Int
    ): Result<ScalingResult> =
        withContext(Dispatchers.IO) {
            try {
                logger.info("Scaling containers in pool ${resourcePoolId.value} to $targetCount")
                
                val currentInstances = activeContainers.values
                    .filter { it.instance.resourcePoolId == resourcePoolId }
                    .map { it.instance }
                
                val currentCount = currentInstances.size
                val operationId = DomainId("docker-scale-${UUID.randomUUID()}")
                
                when {
                    targetCount > currentCount -> {
                        // Scale up - provision new containers
                        val provisioned = mutableListOf<DomainId>()
                        val failed = mutableListOf<String>()
                        
                        repeat(targetCount - currentCount) {
                            val workerId = UUID.randomUUID().toString()
                            val spec = InstanceSpec(
                                instanceType = InstanceType.SMALL,
                                image = dockerConfig.defaultWorkerImage,
                                command = listOf("worker"),
                                environment = mapOf(
                                    "HODEI_ORCHESTRATOR_HOST" to dockerConfig.orchestratorHost,
                                    "HODEI_ORCHESTRATOR_PORT" to dockerConfig.orchestratorPort.toString(),
                                    "WORKER_ID" to workerId,
                                    "WORKER_LABELS" to "pool=${resourcePoolId.value},type=docker,scaling=auto"
                                ),
                                labels = mapOf(
                                    "hodei.worker" to "true",
                                    "hodei.resource-pool" to resourcePoolId.value,
                                    "hodei.scaling-operation" to operationId.value
                                ),
                                metadata = mapOf(
                                    "scalingOperation" to operationId.value,
                                    "workerId" to workerId,
                                    "poolId" to resourcePoolId.value
                                )
                            )
                            
                            when (val result = provisionInstance(resourcePoolId, spec)) {
                                is Either.Right -> provisioned.add(result.value.id)
                                is Either.Left -> failed.add(result.value.message)
                            }
                        }
                        
                        Result.success(
                            ScalingResult(
                                requestedCount = targetCount,
                                actualCount = currentCount + provisioned.size,
                                provisionedInstances = provisioned,
                                failedInstances = failed,
                                operationId = operationId
                            )
                        )
                    }
                    targetCount < currentCount -> {
                        // Scale down - terminate containers
                        val toTerminate = currentInstances.take(currentCount - targetCount)
                        val terminated = mutableListOf<DomainId>()
                        val failed = mutableListOf<String>()
                        
                        toTerminate.forEach { instance ->
                            when (val result = terminateInstance(instance.id)) {
                                is Either.Right -> terminated.add(instance.id)
                                is Either.Left -> failed.add("Failed to terminate ${instance.id.value}: ${result.value.message}")
                            }
                        }
                        
                        Result.success(
                            ScalingResult(
                                requestedCount = targetCount,
                                actualCount = currentCount - terminated.size,
                                provisionedInstances = emptyList(),
                                failedInstances = failed,
                                operationId = operationId
                            )
                        )
                    }
                    else -> {
                        // No scaling needed
                        Result.success(
                            ScalingResult(
                                requestedCount = targetCount,
                                actualCount = currentCount,
                                provisionedInstances = emptyList(),
                                failedInstances = emptyList(),
                                operationId = operationId
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                logger.error("Failed to scale containers", e)
                Result.failure(e)
            }
        }
    
    override suspend fun getAvailableInstanceTypes(resourcePoolId: DomainId): Result<List<InstanceType>> =
        withContext(Dispatchers.IO) {
            try {
                // Return Docker-appropriate instance types
                // In production, could check Docker daemon capabilities and constraints
                val availableTypes = listOf(
                    InstanceType.SMALL,   // 1 CPU, 2GB Memory
                    InstanceType.MEDIUM,  // 2 CPU, 4GB Memory
                    InstanceType.LARGE,   // 4 CPU, 8GB Memory
                    InstanceType.XLARGE,  // 8 CPU, 16GB Memory
                    InstanceType.CUSTOM   // User-defined resources
                )
                Result.success(availableTypes)
            } catch (e: Exception) {
                logger.error("Failed to get available instance types", e)
                Result.failure(e)
            }
        }
    
    /**
     * Check Docker daemon health and connectivity
     */
    suspend fun healthCheck(): Result<DockerHealthInfo> = withContext(Dispatchers.IO) {
        try {
            val info = dockerClient.infoCmd().exec()
            val version = dockerClient.versionCmd().exec()
            
            Result.success(
                DockerHealthInfo(
                    isHealthy = true,
                    version = version.version ?: "unknown",
                    apiVersion = version.apiVersion ?: "unknown",
                    totalMemory = info.memTotal ?: 0L,
                    availableMemory = info.memTotal?.minus(info.memTotal?.times(0.1)?.toLong() ?: 0L) ?: 0L, // Rough estimate
                    cpuCount = info.ncpu ?: 0,
                    containersRunning = info.containersRunning ?: 0,
                    containersPaused = info.containersPaused ?: 0,
                    containersStopped = info.containersStopped ?: 0,
                    imagesCount = info.images ?: 0
                )
            )
        } catch (e: Exception) {
            logger.error("Docker health check failed", e)
            Result.success(
                DockerHealthInfo(
                    isHealthy = false,
                    version = "unknown",
                    apiVersion = "unknown",
                    errorMessage = e.message
                )
            )
        }
    }
    
    private suspend fun ensureImageAvailable(image: String) = withContext(Dispatchers.IO) {
        try {
            // Check if image exists locally
            val images = dockerClient.listImagesCmd()
                .withImageNameFilter(image)
                .exec()
            
            if (images.isEmpty()) {
                logger.info("Image $image not found locally, pulling...")
                dockerClient.pullImageCmd(image).exec(DockerInstanceManagerPullImageResultCallback()).awaitCompletion()
                logger.info("Successfully pulled image $image")
            }
        } catch (e: Exception) {
            logger.warn("Failed to pull image $image: ${e.message}")
            throw e
        }
    }
    
    private fun createContainerSpec(containerName: String, spec: InstanceSpec): DockerContainerSpec {
        val resources = getResourcesForInstanceType(spec.instanceType)
        
        return DockerContainerSpec(
            name = containerName,
            labels = spec.labels + mapOf(
                "hodei.worker" to "true",
                "hodei.instance-type" to spec.instanceType.name,
                "hodei.created-by" to "hodei-orchestrator"
            ),
            environment = spec.environment.map { (k, v) -> "$k=$v" },
            command = spec.command,
            hostConfig = HostConfig()
                .withMemory(resources.memoryBytes)
                .withCpuCount(resources.cpuCount.toLong())
                .withCpuShares(resources.cpuShares)
                .withAutoRemove(false) // We handle removal manually
                .withRestartPolicy(RestartPolicy.noRestart()) // Workers should not restart
        )
    }
    
    private fun getResourcesForInstanceType(instanceType: InstanceType): DockerResourceRequirements {
        return when (instanceType) {
            InstanceType.SMALL -> DockerResourceRequirements(
                memoryBytes = 2L * 1024 * 1024 * 1024, // 2GB
                cpuCount = 1,
                cpuShares = 1024
            )
            InstanceType.MEDIUM -> DockerResourceRequirements(
                memoryBytes = 4L * 1024 * 1024 * 1024, // 4GB
                cpuCount = 2,
                cpuShares = 2048
            )
            InstanceType.LARGE -> DockerResourceRequirements(
                memoryBytes = 8L * 1024 * 1024 * 1024, // 8GB
                cpuCount = 4,
                cpuShares = 4096
            )
            InstanceType.XLARGE -> DockerResourceRequirements(
                memoryBytes = 16L * 1024 * 1024 * 1024, // 16GB
                cpuCount = 8,
                cpuShares = 8192
            )
            InstanceType.CUSTOM -> DockerResourceRequirements(
                memoryBytes = 1L * 1024 * 1024 * 1024, // 1GB
                cpuCount = 1,
                cpuShares = 512
            )
        }
    }
    
    private fun generateContainerName(spec: InstanceSpec): String {
        val workerId = spec.metadata["workerId"] ?: UUID.randomUUID().toString()
        return "hodei-worker-${workerId.take(8)}"
    }
    
    private fun mapDockerStateToInstanceStatus(state: InspectContainerResponse.ContainerState): InstanceStatus {
        return when {
            state.getRunning() == true -> InstanceStatus.RUNNING
            state.getPaused() == true -> InstanceStatus.STOPPED
            state.getRestarting() == true -> InstanceStatus.PROVISIONING
            state.getDead() == true -> InstanceStatus.FAILED
            state.getStatus()?.contains("exited") == true -> InstanceStatus.TERMINATED
            else -> InstanceStatus.PROVISIONING
        }
    }
    
    private fun buildContainerMetadata(
        containerInfo: InspectContainerResponse,
        spec: InstanceSpec
    ): Map<String, String> {
        return mapOf(
            "containerId" to containerInfo.getId(),
            "containerName" to (containerInfo.getName()?.removePrefix("/") ?: "unknown"),
            "image" to (containerInfo.getConfig()?.getImage() ?: spec.image),
            "ipAddress" to (containerInfo.getNetworkSettings()?.getIpAddress() ?: "unknown"),
            "hostname" to (containerInfo.getConfig()?.getHostName() ?: "unknown"),
            "platform" to (containerInfo.getPlatform() ?: "linux"),
            "driver" to (containerInfo.getDriver() ?: "unknown"),
            "workerId" to (spec.metadata["workerId"] ?: "unknown")
        ) + spec.metadata
    }
}

/**
 * Callback for image pull operations
 */
private class DockerInstanceManagerPullImageResultCallback : com.github.dockerjava.api.async.ResultCallback.Adapter<PullResponseItem>()