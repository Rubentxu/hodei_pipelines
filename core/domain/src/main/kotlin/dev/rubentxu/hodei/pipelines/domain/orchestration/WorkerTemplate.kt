package dev.rubentxu.hodei.pipelines.domain.orchestration

import java.time.Instant

/**
 * Worker Template ID
 */
@JvmInline
value class WorkerTemplateId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkerTemplateId cannot be blank" }
    }
}

/**
 * Worker Template - Defines how workers should be created
 */
data class WorkerTemplate(
    val id: WorkerTemplateId,
    val name: String,
    val description: String = "",
    val image: String,
    val resources: ResourceRequirements,
    val capabilities: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val environment: Map<String, String> = emptyMap(),
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<Toleration> = emptyList(),
    val affinity: NodeAffinity? = null,
    val serviceAccount: String? = null,
    val securityContext: SecurityContext? = null,
    val volumes: List<VolumeMount> = emptyList(),
    val createdAt: Instant = Instant.now(),
    val version: String = "1.0.0"
) {
    
    /**
     * Check if template matches worker requirements
     */
    fun matches(requirements: WorkerRequirements): Boolean {
        // Check if template has all required capabilities
        val hasRequiredCapabilities = requirements.capabilities.all { (key, value) ->
            capabilities[key] == value
        }
        
        // Check if template has required labels
        val hasRequiredLabels = requirements.labels.all { (key, value) ->
            labels[key] == value
        }
        
        // Check resource requirements
        val hasResourcesAvailable = resources.canSatisfy(requirements.resources)
        
        return hasRequiredCapabilities && hasRequiredLabels && hasResourcesAvailable
    }
    
    /**
     * Create worker name based on template
     */
    fun generateWorkerName(): String = "${name.lowercase().replace(" ", "-")}-${System.currentTimeMillis()}"
    
    /**
     * Merge with additional configuration
     */
    fun withOverrides(
        additionalEnv: Map<String, String> = emptyMap(),
        additionalLabels: Map<String, String> = emptyMap(),
        resourceOverrides: ResourceRequirements? = null
    ): WorkerTemplate = copy(
        environment = environment + additionalEnv,
        labels = labels + additionalLabels,
        resources = resourceOverrides ?: resources
    )
}

/**
 * Resource Requirements for workers
 */
data class ResourceRequirements(
    val cpu: String = "500m",           // CPU in millicores (e.g., "1000m" = 1 CPU)
    val memory: String = "1Gi",         // Memory (e.g., "2Gi", "512Mi")
    val storage: String? = null,        // Storage (e.g., "10Gi")
    val gpu: Int = 0,                   // Number of GPUs
    val limits: ResourceLimits? = null  // Resource limits (if different from requests)
) {
    /**
     * Check if current resources can satisfy requirements
     */
    fun canSatisfy(requirements: ResourceRequirements): Boolean {
        return compareCpu(cpu, requirements.cpu) >= 0 &&
               compareMemory(memory, requirements.memory) >= 0 &&
               gpu >= requirements.gpu
    }
    
    private fun compareCpu(current: String, required: String): Int {
        return parseCpuMillicores(current).compareTo(parseCpuMillicores(required))
    }
    
    private fun compareMemory(current: String, required: String): Int {
        return parseMemoryBytes(current).compareTo(parseMemoryBytes(required))
    }
    
    private fun parseCpuMillicores(cpu: String): Long {
        return when {
            cpu.endsWith("m") -> cpu.dropLast(1).toLong()
            else -> cpu.toLong() * 1000
        }
    }
    
    private fun parseMemoryBytes(memory: String): Long {
        return when {
            memory.endsWith("Gi") -> memory.dropLast(2).toLong() * 1024 * 1024 * 1024
            memory.endsWith("Mi") -> memory.dropLast(2).toLong() * 1024 * 1024
            memory.endsWith("Ki") -> memory.dropLast(2).toLong() * 1024
            else -> memory.toLong()
        }
    }
}

/**
 * Resource Limits (different from requests)
 */
data class ResourceLimits(
    val cpu: String? = null,
    val memory: String? = null,
    val storage: String? = null
)

/**
 * Worker Requirements when scheduling jobs
 */
data class WorkerRequirements(
    val capabilities: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val resources: ResourceRequirements = ResourceRequirements(),
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<Toleration> = emptyList()
)

/**
 * Kubernetes Toleration
 */
data class Toleration(
    val key: String,
    val operator: TolerationOperator = TolerationOperator.EQUAL,
    val value: String? = null,
    val effect: TaintEffect? = null,
    val tolerationSeconds: Long? = null
)

enum class TolerationOperator {
    EQUAL, EXISTS
}

enum class TaintEffect {
    NO_SCHEDULE, PREFER_NO_SCHEDULE, NO_EXECUTE
}

/**
 * Node Affinity rules
 */
data class NodeAffinity(
    val requiredDuringSchedulingIgnoredDuringExecution: NodeSelector? = null,
    val preferredDuringSchedulingIgnoredDuringExecution: List<PreferredSchedulingTerm> = emptyList()
)

data class NodeSelector(
    val nodeSelectorTerms: List<NodeSelectorTerm>
)

data class NodeSelectorTerm(
    val matchExpressions: List<NodeSelectorRequirement> = emptyList(),
    val matchFields: List<NodeSelectorRequirement> = emptyList()
)

data class NodeSelectorRequirement(
    val key: String,
    val operator: NodeSelectorOperator,
    val values: List<String> = emptyList()
)

enum class NodeSelectorOperator {
    IN, NOT_IN, EXISTS, DOES_NOT_EXIST, GT, LT
}

data class PreferredSchedulingTerm(
    val weight: Int,
    val preference: NodeSelectorTerm
)

/**
 * Security Context for workers
 */
data class SecurityContext(
    val runAsUser: Long? = null,
    val runAsGroup: Long? = null,
    val runAsNonRoot: Boolean? = null,
    val readOnlyRootFilesystem: Boolean? = null,
    val allowPrivilegeEscalation: Boolean? = null,
    val capabilities: Capabilities? = null
)

data class Capabilities(
    val add: List<String> = emptyList(),
    val drop: List<String> = emptyList()
)

/**
 * Volume Mount configuration
 */
data class VolumeMount(
    val name: String,
    val mountPath: String,
    val readOnly: Boolean = false,
    val subPath: String? = null,
    val volumeSource: VolumeSource
)

sealed class VolumeSource {
    data class EmptyDir(val sizeLimit: String? = null) : VolumeSource()
    data class HostPath(val path: String, val type: HostPathType? = null) : VolumeSource()
    data class ConfigMap(val name: String, val items: List<KeyToPath> = emptyList()) : VolumeSource()
    data class Secret(val secretName: String, val items: List<KeyToPath> = emptyList()) : VolumeSource()
    data class PersistentVolumeClaim(val claimName: String) : VolumeSource()
}

enum class HostPathType {
    UNSET, DIRECTORY_OR_CREATE, DIRECTORY, FILE_OR_CREATE, FILE, SOCKET, CHAR_DEVICE, BLOCK_DEVICE
}

data class KeyToPath(
    val key: String,
    val path: String,
    val mode: Int? = null
)