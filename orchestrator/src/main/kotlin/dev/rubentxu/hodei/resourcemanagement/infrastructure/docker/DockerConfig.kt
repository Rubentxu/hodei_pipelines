package dev.rubentxu.hodei.resourcemanagement.infrastructure.docker

/**
 * Docker configuration
 */
data class DockerConfig(
    val dockerHost: String = "unix:///var/run/docker.sock",
    val tlsVerify: Boolean = false,
    val certPath: String? = null,
    val apiVersion: String = "1.41",
    val maxConnections: Int = 100,
    val connectionTimeoutSeconds: Long = 30,
    val responseTimeoutSeconds: Long = 45,
    val stopTimeoutSeconds: Int = 30,
    val defaultWorkerImage: String = "hodei/worker:latest",
    val orchestratorHost: String = "host.docker.internal",
    val orchestratorPort: Int = 9090,
    val networkName: String = "bridge"
)

/**
 * Docker health information
 */
data class DockerHealthInfo(
    val isHealthy: Boolean,
    val version: String,
    val apiVersion: String,
    val totalMemory: Long = 0L,
    val availableMemory: Long = 0L,
    val cpuCount: Int = 0,
    val containersRunning: Int = 0,
    val containersPaused: Int = 0,
    val containersStopped: Int = 0,
    val imagesCount: Int = 0,
    val errorMessage: String? = null
)

/**
 * Shared Docker container specification
 */
data class DockerContainerSpec(
    val name: String,
    val labels: Map<String, String>,
    val environment: List<String>,
    val command: List<String>,
    val hostConfig: com.github.dockerjava.api.model.HostConfig
)

/**
 * Shared Docker resource requirements
 */
data class DockerResourceRequirements(
    val memoryBytes: Long,
    val cpuCount: Int,
    val cpuShares: Int
)

/**
 * Internal data classes for Docker container management
 */
data class DockerContainerInstance(
    val instance: dev.rubentxu.hodei.resourcemanagement.domain.ports.ComputeInstance,
    val containerId: String,
    val dockerSpec: DockerContainerSpec,
    val templateName: String? = null
)