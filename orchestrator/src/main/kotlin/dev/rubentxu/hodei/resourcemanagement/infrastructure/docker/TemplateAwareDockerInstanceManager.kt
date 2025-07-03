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
import dev.rubentxu.hodei.templatemanagement.application.services.WorkerTemplateService
import dev.rubentxu.hodei.templatemanagement.domain.entities.WorkerTemplateSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Template-aware Docker implementation of IInstanceManager.
 * 
 * Uses worker templates to determine Docker image, configuration, and resources
 * instead of hardcoded values. This provides flexibility and consistency
 * across different deployment scenarios.
 */
class TemplateAwareDockerInstanceManager(
    private val workerTemplateService: WorkerTemplateService,
    private val dockerConfig: DockerConfig = DockerConfig()
) : IInstanceManager {
    
    private val logger = LoggerFactory.getLogger(TemplateAwareDockerInstanceManager::class.java)
    private val json = Json { ignoreUnknownKeys = true }
    
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
            
            // Get appropriate worker template for this resource pool
            val templateResult = workerTemplateService.getTemplateForResourcePool(
                resourcePoolType = "docker",
                requirements = spec.metadata
            )
            
            if (templateResult.isLeft()) {
                logger.warn("No worker template found for pool ${poolId.value}, using fallback configuration")
                return@withContext provisionWithFallback(poolId, spec)
            }
            
            val template = when (templateResult) {
                is Either.Right -> templateResult.value
                is Either.Left -> return@withContext provisionWithFallback(poolId, spec)
            }
            
            val workerSpecResult = workerTemplateService.parseWorkerTemplateSpec(template)
            if (workerSpecResult.isLeft()) {
                logger.warn("Failed to parse worker template spec, using fallback configuration")
                return@withContext provisionWithFallback(poolId, spec)
            }
            
            val workerSpec = when (workerSpecResult) {
                is Either.Right -> workerSpecResult.value
                is Either.Left -> return@withContext provisionWithFallback(poolId, spec)
            }
            
            // Create container spec from template
            val containerName = generateContainerName(spec)
            val dockerSpec = createContainerSpecFromTemplate(containerName, spec, workerSpec)
            
            // Use template image instead of hardcoded one
            val imageWithTag = workerSpec.runtime.fullImageName
            
            // Check if image exists locally, pull if necessary
            ensureImageAvailable(imageWithTag)
            
            // Create container
            val createResponse = dockerClient.createContainerCmd(imageWithTag)
                .withName(containerName)
                .withLabels(dockerSpec.labels)
                .withEnv(dockerSpec.environment)
                .withCmd(dockerSpec.command)
                .withHostConfig(dockerSpec.hostConfig)
                .exec()
            
            val containerId = createResponse.id
            logger.debug("Created container with ID: $containerId using template: ${template.name}")
            
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
                metadata = buildContainerMetadata(containerInfo, spec, template.name),
                createdAt = now,
                lastUpdatedAt = now
            )
            
            // Track the container
            activeContainers[instanceId.value] = DockerContainerInstance(
                instance = instance,
                containerId = containerId,
                dockerSpec = dockerSpec,
                templateName = template.name
            )
            
            logger.info("Successfully provisioned Docker container $containerName with template ${template.name}")
            instance.right()
            
        } catch (e: Exception) {
            logger.error("Failed to provision Docker container", e)
            ProvisioningError.ProvisioningFailedError(
                "Failed to create container: ${e.message}"
            ).left()
        }
    }
    
    /**
     * Fallback method for when templates are not available
     */
    private suspend fun provisionWithFallback(
        poolId: DomainId,
        spec: InstanceSpec
    ): Either<ProvisioningError, ComputeInstance> = withContext(Dispatchers.IO) {
        try {
            logger.info("Using fallback provisioning for pool ${poolId.value}")
            
            val containerName = generateContainerName(spec)
            val dockerSpec = createFallbackContainerSpec(containerName, spec)
            
            // Use fallback image
            val fallbackImage = dockerConfig.defaultWorkerImage
            ensureImageAvailable(fallbackImage)
            
            // Create container
            val createResponse = dockerClient.createContainerCmd(fallbackImage)
                .withName(containerName)
                .withLabels(dockerSpec.labels)
                .withEnv(dockerSpec.environment)
                .withCmd(dockerSpec.command)
                .withHostConfig(dockerSpec.hostConfig)
                .exec()
            
            val containerId = createResponse.id
            logger.debug("Created fallback container with ID: $containerId")
            
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
                metadata = buildContainerMetadata(containerInfo, spec, "fallback"),
                createdAt = now,
                lastUpdatedAt = now
            )
            
            // Track the container
            activeContainers[instanceId.value] = DockerContainerInstance(
                instance = instance,
                containerId = containerId,
                dockerSpec = dockerSpec,
                templateName = "fallback"
            )
            
            logger.info("Successfully provisioned fallback Docker container $containerName")
            instance.right()
            
        } catch (e: Exception) {
            logger.error("Failed to provision fallback Docker container", e)
            ProvisioningError.ProvisioningFailedError(
                "Failed to create fallback container: ${e.message}"
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
                
                logger.info("Successfully terminated container ${containerInstance.instance.name} (template: ${containerInstance.templateName})")
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
                        // Scale up - provision new containers using templates
                        val provisioned = mutableListOf<DomainId>()
                        val failed = mutableListOf<String>()
                        
                        repeat(targetCount - currentCount) {
                            val workerId = UUID.randomUUID().toString()
                            val spec = createScalingInstanceSpec(resourcePoolId, operationId, workerId)
                            
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
     * Create container specification from worker template
     */
    private fun createContainerSpecFromTemplate(
        containerName: String, 
        spec: InstanceSpec,
        workerSpec: WorkerTemplateSpec
    ): DockerContainerSpec {
        val resources = getResourcesFromTemplate(workerSpec)
        
        // Merge environment variables from template and instance spec
        val mergedEnvironment = (workerSpec.environment + spec.environment)
            .map { (k, v) -> "$k=$v" }
            .plus(listOf(
                "HODEI_ORCHESTRATOR_HOST=${dockerConfig.orchestratorHost}",
                "HODEI_ORCHESTRATOR_PORT=${dockerConfig.orchestratorPort}",
                "WORKER_ID=${spec.metadata["workerId"] ?: UUID.randomUUID().toString()}"
            ))
        
        // Merge labels from template and instance spec
        val mergedLabels = workerSpec.labels + spec.labels + mapOf(
            "hodei.worker" to "true",
            "hodei.instance-type" to spec.instanceType.name,
            "hodei.created-by" to "hodei-orchestrator",
            "hodei.template" to (spec.metadata["templateName"] ?: "unknown")
        )
        
        // Use command from template or fall back to instance spec
        val command = workerSpec.runtime.command ?: spec.command
        
        return DockerContainerSpec(
            name = containerName,
            labels = mergedLabels,
            environment = mergedEnvironment,
            command = command,
            hostConfig = HostConfig()
                .withMemory(resources.memoryBytes)
                .withCpuCount(resources.cpuCount.toLong())
                .withCpuShares(resources.cpuShares)
                .withAutoRemove(false) // We handle removal manually
                .withRestartPolicy(RestartPolicy.noRestart()) // Workers should not restart
        )
    }
    
    /**
     * Create fallback container specification when templates are not available
     */
    private fun createFallbackContainerSpec(containerName: String, spec: InstanceSpec): DockerContainerSpec {
        val resources = getResourcesForInstanceType(spec.instanceType)
        
        return DockerContainerSpec(
            name = containerName,
            labels = spec.labels + mapOf(
                "hodei.worker" to "true",
                "hodei.instance-type" to spec.instanceType.name,
                "hodei.created-by" to "hodei-orchestrator",
                "hodei.template" to "fallback"
            ),
            environment = spec.environment.map { (k, v) -> "$k=$v" } + listOf(
                "HODEI_ORCHESTRATOR_HOST=${dockerConfig.orchestratorHost}",
                "HODEI_ORCHESTRATOR_PORT=${dockerConfig.orchestratorPort}",
                "WORKER_ID=${spec.metadata["workerId"] ?: UUID.randomUUID().toString()}"
            ),
            command = spec.command,
            hostConfig = HostConfig()
                .withMemory(resources.memoryBytes)
                .withCpuCount(resources.cpuCount.toLong())
                .withCpuShares(resources.cpuShares)
                .withAutoRemove(false)
                .withRestartPolicy(RestartPolicy.noRestart())
        )
    }
    
    private suspend fun ensureImageAvailable(image: String) = withContext(Dispatchers.IO) {
        try {
            // Check if image exists locally
            val images = dockerClient.listImagesCmd()
                .withImageNameFilter(image)
                .exec()
            
            if (images.isEmpty()) {
                logger.info("Image $image not found locally, pulling...")
                dockerClient.pullImageCmd(image).exec(PullImageResultCallback()).awaitCompletion()
                logger.info("Successfully pulled image $image")
            }
        } catch (e: Exception) {
            logger.warn("Failed to pull image $image: ${e.message}")
            throw e
        }
    }
    
    private fun createScalingInstanceSpec(
        resourcePoolId: DomainId,
        operationId: DomainId,
        workerId: String
    ): InstanceSpec {
        return InstanceSpec(
            instanceType = InstanceType.SMALL,
            image = dockerConfig.defaultWorkerImage, // This will be overridden by template
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
    }
    
    private fun getResourcesFromTemplate(workerSpec: WorkerTemplateSpec): DockerResourceRequirements {
        // Parse CPU and memory from template spec
        val cpuRequest = parseResourceValue(workerSpec.resources.requests.cpu, "m", 100.0)
        val memoryRequest = parseResourceValue(workerSpec.resources.requests.memory, "Mi", 256.0)
        
        return DockerResourceRequirements(
            memoryBytes = (memoryRequest * 1024 * 1024).toLong(),
            cpuCount = maxOf(1, (cpuRequest / 1000).toInt()),
            cpuShares = (cpuRequest * 1.024).toInt() // Convert millicores to CPU shares
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
    
    private fun parseResourceValue(value: String, defaultUnit: String, defaultValue: Double): Double {
        try {
            val trimmed = value.trim()
            
            // Handle different units
            return when {
                trimmed.endsWith("m") -> trimmed.dropLast(1).toDouble() // millicores or millibytes
                trimmed.endsWith("Mi") -> trimmed.dropLast(2).toDouble() // Mebibytes
                trimmed.endsWith("Gi") -> trimmed.dropLast(2).toDouble() * 1024 // Gibibytes to Mebibytes
                trimmed.endsWith("Ki") -> trimmed.dropLast(2).toDouble() / 1024 // Kibibytes to Mebibytes
                else -> trimmed.toDoubleOrNull() ?: defaultValue
            }
        } catch (e: Exception) {
            logger.warn("Failed to parse resource value '$value', using default $defaultValue")
            return defaultValue
        }
    }
    
    private fun generateContainerName(spec: InstanceSpec): String {
        val workerId = spec.metadata["workerId"] ?: UUID.randomUUID().toString()
        return "hodei-worker-${workerId.take(8)}"
    }
    
    private fun mapDockerStateToInstanceStatus(state: InspectContainerResponse.ContainerState): InstanceStatus {
        return when {
            state.running == true -> InstanceStatus.RUNNING
            state.paused == true -> InstanceStatus.STOPPED
            state.restarting == true -> InstanceStatus.PROVISIONING
            state.dead == true -> InstanceStatus.FAILED
            state.status?.contains("exited") == true -> InstanceStatus.TERMINATED
            else -> InstanceStatus.PROVISIONING
        }
    }
    
    private fun buildContainerMetadata(
        containerInfo: InspectContainerResponse,
        spec: InstanceSpec,
        templateName: String
    ): Map<String, String> {
        return mapOf(
            "containerId" to containerInfo.id,
            "containerName" to (containerInfo.name?.removePrefix("/") ?: "unknown"),
            "image" to (containerInfo.config?.image ?: spec.image),
            "ipAddress" to (containerInfo.networkSettings?.ipAddress ?: "unknown"),
            "hostname" to (containerInfo.config?.hostName ?: "unknown"),
            "platform" to (containerInfo.platform ?: "linux"),
            "driver" to (containerInfo.driver ?: "unknown"),
            "workerId" to (spec.metadata["workerId"] ?: "unknown"),
            "templateName" to templateName
        ) + spec.metadata
    }
}

/**
 * Callback for image pull operations
 */
private class PullImageResultCallback : com.github.dockerjava.api.async.ResultCallback.Adapter<PullResponseItem>()