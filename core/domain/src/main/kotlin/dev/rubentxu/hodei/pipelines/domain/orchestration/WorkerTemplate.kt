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
    val capabilities: WorkerTemplateCapabilities = WorkerTemplateCapabilities(),
    val labels: Map<String, String> = emptyMap(),
    val env: Map<String, String> = emptyMap(),
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<WorkerToleration> = emptyList(),
    val affinity: WorkerAffinity? = null,
    val serviceAccountName: String? = null,
    val securityContext: PodSecurityContext? = null,
    val containerSecurityContext: ContainerSecurityContext? = null,
    val volumes: List<VolumeSpec> = emptyList(),
    val volumeMounts: List<VolumeMountSpec> = emptyList(),
    val ports: List<ContainerPort> = emptyList(),
    val command: List<String> = emptyList(),
    val args: List<String> = emptyList(),
    val imagePullPolicy: String? = null,
    val initContainers: List<InitContainer> = emptyList(),
    val dnsPolicy: String? = null,
    val hostname: String? = null,
    val priorityClassName: String? = null,
    val livenessProbe: ProbeSpec? = null,
    val readinessProbe: ProbeSpec? = null,
    val createdAt: Instant = Instant.now(),
    val version: String = "1.0.0"
) {
    
    /**
     * Check if template matches worker requirements
     */
    fun matches(requirements: WorkerRequirements): Boolean {
        // Check if template has all required capabilities
        val hasRequiredLanguages = requirements.requiredLanguages.all { language ->
            capabilities.languages.contains(language)
        }
        
        val hasRequiredTools = requirements.requiredTools.all { tool ->
            capabilities.tools.contains(tool)
        }
        
        val hasRequiredFeatures = requirements.requiredFeatures.all { feature ->
            capabilities.features.contains(feature)
        }
        
        // Check if template has required labels
        val hasRequiredLabels = requirements.labels.all { (key, value) ->
            labels[key] == value
        }
        
        // Check resource requirements
        val hasResourcesAvailable = resources.canSatisfy(requirements.resources)
        
        return hasRequiredLanguages && hasRequiredTools && hasRequiredFeatures && 
               hasRequiredLabels && hasResourcesAvailable
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
        env = env + additionalEnv,
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
    val storage: String = "",           // Storage (e.g., "10Gi")
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
    val requiredLanguages: Set<String> = emptySet(),
    val requiredTools: Set<String> = emptySet(),
    val requiredFeatures: Set<String> = emptySet(),
    val labels: Map<String, String> = emptyMap(),
    val resources: ResourceRequirements = ResourceRequirements(),
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<WorkerToleration> = emptyList()
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

/**
 * Extended classes for Kubernetes WorkerTemplate support
 */

/**
 * Worker template capabilities (languages, tools, features)
 */
data class WorkerTemplateCapabilities(
    val languages: Set<String> = emptySet(),
    val tools: Set<String> = emptySet(),
    val features: Set<String> = emptySet()
)

/**
 * Kubernetes Toleration (renamed from Toleration to avoid conflict)
 */
data class WorkerToleration(
    val key: String,
    val operator: String = "Equal",
    val value: String? = null,
    val effect: String? = null,
    val tolerationSeconds: Long? = null
)

/**
 * Worker Affinity (renamed from NodeAffinity)
 */
data class WorkerAffinity(
    val nodeAffinity: WorkerNodeAffinity? = null
)

data class WorkerNodeAffinity(
    val required: WorkerNodeSelector? = null,
    val preferred: List<WorkerPreferredSchedulingTerm> = emptyList()
)

data class WorkerNodeSelector(
    val terms: List<WorkerNodeSelectorTerm>
)

data class WorkerNodeSelectorTerm(
    val matchExpressions: List<WorkerNodeSelectorRequirement> = emptyList()
)

data class WorkerNodeSelectorRequirement(
    val key: String,
    val operator: String,
    val values: List<String> = emptyList()
)

data class WorkerPreferredSchedulingTerm(
    val weight: Int,
    val preference: WorkerNodeSelectorTerm
)

/**
 * Pod Security Context
 */
data class PodSecurityContext(
    val runAsUser: Long? = null,
    val runAsGroup: Long? = null,
    val runAsNonRoot: Boolean? = null,
    val fsGroup: Long? = null
)

/**
 * Container Security Context
 */
data class ContainerSecurityContext(
    val runAsUser: Long? = null,
    val runAsGroup: Long? = null,
    val runAsNonRoot: Boolean? = null,
    val readOnlyRootFilesystem: Boolean? = null,
    val allowPrivilegeEscalation: Boolean? = null,
    val capabilities: ContainerCapabilities? = null
)

data class ContainerCapabilities(
    val add: List<String> = emptyList(),
    val drop: List<String> = emptyList()
)

/**
 * Volume Specification
 */
data class VolumeSpec(
    val name: String,
    val type: String, // emptyDir, configMap, secret, persistentVolumeClaim, hostPath
    val source: String, // source identifier (configmap name, secret name, pvc name, host path)
    val sizeLimit: String? = null,
    val defaultMode: Int? = null,
    val readOnly: Boolean? = null,
    val hostPathType: String? = null
)

/**
 * Volume Mount Specification
 */
data class VolumeMountSpec(
    val name: String,
    val mountPath: String,
    val readOnly: Boolean = false,
    val subPath: String? = null
)

/**
 * Container Port
 */
data class ContainerPort(
    val name: String? = null,
    val containerPort: Int,
    val protocol: String? = null
)

/**
 * Init Container
 */
data class InitContainer(
    val name: String,
    val image: String,
    val command: List<String> = emptyList(),
    val args: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

/**
 * Probe Specification (for liveness and readiness probes)
 */
data class ProbeSpec(
    val type: String, // http, tcp, exec
    val path: String? = null,
    val port: Int = 8080,
    val scheme: String? = null,
    val headers: Map<String, String>? = null,
    val command: List<String>? = null,
    val initialDelaySeconds: Int = 0,
    val periodSeconds: Int = 10,
    val timeoutSeconds: Int = 1,
    val failureThreshold: Int = 3,
    val successThreshold: Int = 1
)