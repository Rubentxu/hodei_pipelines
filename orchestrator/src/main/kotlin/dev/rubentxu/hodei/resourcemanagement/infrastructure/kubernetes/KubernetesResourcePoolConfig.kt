package dev.rubentxu.hodei.resourcemanagement.infrastructure.kubernetes

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import kotlinx.datetime.Clock

/**
 * Factory for creating Kubernetes-based resource pools.
 * Each pool represents a Kubernetes namespace with resource quotas.
 */
object KubernetesResourcePoolFactory {
    
    /**
     * Creates a Kubernetes resource pool for a specific namespace
     */
    fun createKubernetesPool(
        name: String,
        namespace: String,
        description: String? = null,
        maxJobs: Int? = 50,
        maxCpuCores: Double? = null,
        maxMemoryGi: Double? = null,
        labels: Map<String, String> = emptyMap()
    ): ResourcePool {
        val now = Clock.System.now()
        
        return ResourcePool(
            id = DomainId("k8s-namespace-$namespace"),
            name = name,
            description = description ?: "Kubernetes namespace: $namespace",
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxJobs = maxJobs,
            labels = labels + mapOf(
                "platform" to "kubernetes",
                "namespace" to namespace
            ),
            createdAt = now,
            updatedAt = now,
            createdBy = "system"
        )
    }
    
    /**
     * Creates default Kubernetes pools for common namespaces
     */
    fun createDefaultPools(): List<ResourcePool> {
        return listOf(
            createKubernetesPool(
                name = "Default Kubernetes Pool",
                namespace = "default",
                description = "Default namespace for general workloads",
                maxJobs = 20,
                maxCpuCores = 16.0,
                maxMemoryGi = 64.0,
                labels = mapOf("environment" to "development")
            ),
            createKubernetesPool(
                name = "Production Kubernetes Pool",
                namespace = "production",
                description = "Production namespace with strict resource limits",
                maxJobs = 100,
                maxCpuCores = 32.0,
                maxMemoryGi = 128.0,
                labels = mapOf(
                    "environment" to "production",
                    "tier" to "critical"
                )
            ),
            createKubernetesPool(
                name = "Staging Kubernetes Pool",
                namespace = "staging",
                description = "Staging namespace for pre-production testing",
                maxJobs = 50,
                maxCpuCores = 8.0,
                maxMemoryGi = 32.0,
                labels = mapOf(
                    "environment" to "staging",
                    "tier" to "standard"
                )
            ),
            createKubernetesPool(
                name = "Hodei Workers Pool",
                namespace = "hodei-workers",
                description = "Dedicated namespace for Hodei pipeline workers",
                maxJobs = 200,
                maxCpuCores = 64.0,
                maxMemoryGi = 256.0,
                labels = mapOf(
                    "environment" to "production",
                    "dedicated" to "hodei"
                )
            )
        )
    }
    
    private fun buildConfiguration(
        namespace: String,
        maxCpuCores: Double?,
        maxMemoryGi: Double?
    ): Map<String, Any> {
        val config = mutableMapOf<String, Any>(
            "namespace" to namespace,
            "serviceAccount" to "hodei-worker",
            "imagePullPolicy" to "Always"
        )
        
        // Add resource quotas if specified
        if (maxCpuCores != null || maxMemoryGi != null) {
            val resourceQuota = mutableMapOf<String, String>()
            maxCpuCores?.let { resourceQuota["requests.cpu"] = it.toString() }
            maxMemoryGi?.let { resourceQuota["requests.memory"] = "${it}Gi" }
            config["resourceQuota"] = resourceQuota
        }
        
        // Add default node selector
        config["nodeSelector"] = mapOf(
            "hodei.io/worker-node" to "true"
        )
        
        // Add default tolerations for worker nodes
        config["tolerations"] = listOf(
            mapOf(
                "key" to "hodei.io/worker",
                "operator" to "Equal",
                "value" to "true",
                "effect" to "NoSchedule"
            )
        )
        
        return config
    }
}

/**
 * Configuration for Kubernetes worker pods
 */
data class KubernetesWorkerConfig(
    val image: String = "hodei/worker:latest",
    val imagePullSecrets: List<String> = emptyList(),
    val serviceAccount: String = "hodei-worker",
    val securityContext: SecurityContext = SecurityContext(),
    val resources: WorkerResources = WorkerResources(),
    val volumes: List<WorkerVolume> = emptyList(),
    val envFrom: List<EnvFromSource> = emptyList()
)

/**
 * Security context for worker pods
 */
data class SecurityContext(
    val runAsNonRoot: Boolean = true,
    val runAsUser: Long? = 1000,
    val runAsGroup: Long? = 1000,
    val fsGroup: Long? = 1000,
    val readOnlyRootFilesystem: Boolean = false,
    val allowPrivilegeEscalation: Boolean = false,
    val capabilities: Capabilities? = null
)

/**
 * Linux capabilities
 */
data class Capabilities(
    val add: List<String> = emptyList(),
    val drop: List<String> = listOf("ALL")
)

/**
 * Worker resource specifications
 */
data class WorkerResources(
    val small: ResourceRequirements = ResourceRequirements(
        requests = mapOf("cpu" to "1", "memory" to "2Gi"),
        limits = mapOf("cpu" to "1", "memory" to "2Gi")
    ),
    val medium: ResourceRequirements = ResourceRequirements(
        requests = mapOf("cpu" to "2", "memory" to "4Gi"),
        limits = mapOf("cpu" to "2", "memory" to "4Gi")
    ),
    val large: ResourceRequirements = ResourceRequirements(
        requests = mapOf("cpu" to "4", "memory" to "8Gi"),
        limits = mapOf("cpu" to "4", "memory" to "8Gi")
    ),
    val xlarge: ResourceRequirements = ResourceRequirements(
        requests = mapOf("cpu" to "8", "memory" to "16Gi"),
        limits = mapOf("cpu" to "8", "memory" to "16Gi")
    )
)

/**
 * Worker volume configuration
 */
data class WorkerVolume(
    val name: String,
    val mountPath: String,
    val volumeSource: VolumeSource,
    val readOnly: Boolean = false
)

/**
 * Volume source types
 */
sealed class VolumeSource {
    data class ConfigMap(val name: String) : VolumeSource()
    data class Secret(val name: String) : VolumeSource()
    data class EmptyDir(val sizeLimit: String? = null) : VolumeSource()
    data class PersistentVolumeClaim(val claimName: String) : VolumeSource()
}

/**
 * Environment from source
 */
data class EnvFromSource(
    val configMapRef: String? = null,
    val secretRef: String? = null,
    val prefix: String? = null
)