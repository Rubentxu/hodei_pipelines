package dev.rubentxu.hodei.templatemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json

/**
 * Worker template specification for different runtime environments.
 * 
 * Defines the complete specification for creating worker instances,
 * including Docker images, resource requirements, and configuration.
 */
@Serializable
data class WorkerTemplateSpec(
    val type: WorkerType,
    val runtime: RuntimeSpec,
    val resources: ResourceSpec,
    val networking: NetworkSpec? = null,
    val environment: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val securityContext: SecurityContextSpec? = null,
    val healthCheck: HealthCheckSpec? = null,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Types of workers supported
 */
@Serializable
enum class WorkerType {
    DOCKER,
    KUBERNETES_POD,
    KUBERNETES_DEPLOYMENT,
    VM,
    SERVERLESS
}

/**
 * Runtime specification (Docker, Kubernetes, etc.)
 */
@Serializable
data class RuntimeSpec(
    val image: String,
    val tag: String = "latest",
    val pullPolicy: ImagePullPolicy = ImagePullPolicy.IF_NOT_PRESENT,
    val command: List<String>? = null,
    val args: List<String>? = null,
    val workingDir: String? = null,
    val entrypoint: String? = null
) {
    val fullImageName: String
        get() = if (tag.isNotBlank()) "$image:$tag" else image
}

/**
 * Image pull policies
 */
@Serializable
enum class ImagePullPolicy {
    ALWAYS,
    IF_NOT_PRESENT,
    NEVER
}

/**
 * Resource requirements specification
 */
@Serializable
data class ResourceSpec(
    val requests: ResourceRequirements,
    val limits: ResourceRequirements? = null,
    val nodeSelector: Map<String, String> = emptyMap(),
    val tolerations: List<Toleration> = emptyList(),
    val affinity: AffinitySpec? = null
)

/**
 * Resource requirements (CPU, memory, etc.)
 */
@Serializable
data class ResourceRequirements(
    val cpu: String, // e.g., "100m", "1", "2.5"
    val memory: String, // e.g., "128Mi", "1Gi", "2048Mi"
    val storage: String? = null, // e.g., "10Gi", "100Mi"
    val gpu: Int = 0,
    val customResources: Map<String, String> = emptyMap()
)

/**
 * Toleration for node scheduling
 */
@Serializable
data class Toleration(
    val key: String,
    val operator: TolerationOperator = TolerationOperator.EQUAL,
    val value: String? = null,
    val effect: TaintEffect? = null,
    val tolerationSeconds: Long? = null
)

@Serializable
enum class TolerationOperator {
    EQUAL,
    EXISTS
}

@Serializable
enum class TaintEffect {
    NO_SCHEDULE,
    PREFER_NO_SCHEDULE,
    NO_EXECUTE
}

/**
 * Affinity specification for advanced scheduling
 */
@Serializable
data class AffinitySpec(
    val nodeAffinity: NodeAffinitySpec? = null,
    val podAffinity: PodAffinitySpec? = null,
    val podAntiAffinity: PodAffinitySpec? = null
)

@Serializable
data class NodeAffinitySpec(
    val requiredDuringSchedulingIgnoredDuringExecution: NodeSelectorSpec? = null,
    val preferredDuringSchedulingIgnoredDuringExecution: List<PreferredSchedulingTerm> = emptyList()
)

@Serializable
data class PodAffinitySpec(
    val requiredDuringSchedulingIgnoredDuringExecution: List<PodAffinityTerm> = emptyList(),
    val preferredDuringSchedulingIgnoredDuringExecution: List<WeightedPodAffinityTerm> = emptyList()
)

@Serializable
data class NodeSelectorSpec(
    val nodeSelectorTerms: List<NodeSelectorTerm>
)

@Serializable
data class NodeSelectorTerm(
    val matchExpressions: List<NodeSelectorRequirement> = emptyList(),
    val matchFields: List<NodeSelectorRequirement> = emptyList()
)

@Serializable
data class NodeSelectorRequirement(
    val key: String,
    val operator: NodeSelectorOperator,
    val values: List<String> = emptyList()
)

@Serializable
enum class NodeSelectorOperator {
    IN,
    NOT_IN,
    EXISTS,
    DOES_NOT_EXIST,
    GT,
    LT
}

@Serializable
data class PreferredSchedulingTerm(
    val weight: Int,
    val preference: NodeSelectorTerm
)

@Serializable
data class PodAffinityTerm(
    val labelSelector: LabelSelector? = null,
    val namespaces: List<String> = emptyList(),
    val topologyKey: String
)

@Serializable
data class WeightedPodAffinityTerm(
    val weight: Int,
    val podAffinityTerm: PodAffinityTerm
)

@Serializable
data class LabelSelector(
    val matchLabels: Map<String, String> = emptyMap(),
    val matchExpressions: List<LabelSelectorRequirement> = emptyList()
)

@Serializable
data class LabelSelectorRequirement(
    val key: String,
    val operator: LabelSelectorOperator,
    val values: List<String> = emptyList()
)

@Serializable
enum class LabelSelectorOperator {
    IN,
    NOT_IN,
    EXISTS,
    DOES_NOT_EXIST
}

/**
 * Network configuration for worker
 */
@Serializable
data class NetworkSpec(
    val hostNetwork: Boolean = false,
    val dnsPolicy: DNSPolicy = DNSPolicy.CLUSTER_FIRST,
    val hostname: String? = null,
    val subdomain: String? = null,
    val ports: List<ContainerPort> = emptyList()
)

@Serializable
enum class DNSPolicy {
    CLUSTER_FIRST,
    CLUSTER_FIRST_WITH_HOST_NET,
    DEFAULT,
    NONE
}

@Serializable
data class ContainerPort(
    val name: String? = null,
    val containerPort: Int,
    val protocol: Protocol = Protocol.TCP,
    val hostIP: String? = null,
    val hostPort: Int? = null
)

@Serializable
enum class Protocol {
    TCP,
    UDP,
    SCTP
}

/**
 * Security context specification
 */
@Serializable
data class SecurityContextSpec(
    val runAsUser: Long? = null,
    val runAsGroup: Long? = null,
    val runAsNonRoot: Boolean? = null,
    val readOnlyRootFilesystem: Boolean? = null,
    val allowPrivilegeEscalation: Boolean? = null,
    val capabilities: CapabilitiesSpec? = null,
    val seLinuxOptions: SELinuxOptionsSpec? = null,
    val seccompProfile: SeccompProfileSpec? = null
)

@Serializable
data class CapabilitiesSpec(
    val add: List<String> = emptyList(),
    val drop: List<String> = emptyList()
)

@Serializable
data class SELinuxOptionsSpec(
    val level: String? = null,
    val role: String? = null,
    val type: String? = null,
    val user: String? = null
)

@Serializable
data class SeccompProfileSpec(
    val type: SeccompProfileType,
    val localhostProfile: String? = null
)

@Serializable
enum class SeccompProfileType {
    UNCONFINED,
    RUNTIME_DEFAULT,
    LOCALHOST
}

/**
 * Health check specification
 */
@Serializable
data class HealthCheckSpec(
    val livenessProbe: ProbeSpec? = null,
    val readinessProbe: ProbeSpec? = null,
    val startupProbe: ProbeSpec? = null
)

@Serializable
data class ProbeSpec(
    val httpGet: HTTPGetActionSpec? = null,
    val exec: ExecActionSpec? = null,
    val tcpSocket: TCPSocketActionSpec? = null,
    val grpc: GRPCActionSpec? = null,
    val initialDelaySeconds: Int = 0,
    val periodSeconds: Int = 10,
    val timeoutSeconds: Int = 1,
    val successThreshold: Int = 1,
    val failureThreshold: Int = 3
)

@Serializable
data class HTTPGetActionSpec(
    val path: String? = null,
    val port: Int,
    val host: String? = null,
    val scheme: HTTPScheme = HTTPScheme.HTTP,
    val httpHeaders: List<HTTPHeaderSpec> = emptyList()
)

@Serializable
enum class HTTPScheme {
    HTTP,
    HTTPS
}

@Serializable
data class HTTPHeaderSpec(
    val name: String,
    val value: String
)

@Serializable
data class ExecActionSpec(
    val command: List<String>
)

@Serializable
data class TCPSocketActionSpec(
    val port: Int,
    val host: String? = null
)

@Serializable
data class GRPCActionSpec(
    val port: Int,
    val service: String? = null
)

/**
 * Helper functions for creating common worker templates
 */
object WorkerTemplateBuilder {
    
    /**
     * Create a basic Docker worker template
     */
    fun createDockerTemplate(
        name: String,
        description: String,
        image: String,
        tag: String = "latest",
        cpuRequest: String = "100m",
        memoryRequest: String = "256Mi",
        environment: Map<String, String> = emptyMap(),
        createdBy: String = "system"
    ): Template {
        val spec = WorkerTemplateSpec(
            type = WorkerType.DOCKER,
            runtime = RuntimeSpec(
                image = image,
                tag = tag,
                pullPolicy = ImagePullPolicy.IF_NOT_PRESENT,
                command = listOf("worker"),
                args = listOf("--orchestrator-url", "\${HODEI_ORCHESTRATOR_URL}")
            ),
            resources = ResourceSpec(
                requests = ResourceRequirements(
                    cpu = cpuRequest,
                    memory = memoryRequest
                )
            ),
            environment = environment + mapOf(
                "HODEI_WORKER_TYPE" to "docker",
                "HODEI_WORKER_ID" to "\${WORKER_ID}",
                "HODEI_ORCHESTRATOR_URL" to "\${HODEI_ORCHESTRATOR_URL}"
            ),
            labels = mapOf(
                "hodei.worker" to "true",
                "hodei.worker.type" to "docker",
                "hodei.template" to name
            )
        )
        
        val now = kotlinx.datetime.Clock.System.now()
        return Template(
            id = DomainId.generate(),
            name = name,
            description = description,
            version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
            spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
            status = TemplateStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )
    }
    
    /**
     * Create a Kubernetes pod worker template
     */
    fun createKubernetesTemplate(
        name: String,
        description: String,
        image: String,
        tag: String = "latest",
        namespace: String = "default",
        cpuRequest: String = "100m",
        memoryRequest: String = "256Mi",
        environment: Map<String, String> = emptyMap(),
        createdBy: String = "system"
    ): Template {
        val spec = WorkerTemplateSpec(
            type = WorkerType.KUBERNETES_POD,
            runtime = RuntimeSpec(
                image = image,
                tag = tag,
                pullPolicy = ImagePullPolicy.IF_NOT_PRESENT
            ),
            resources = ResourceSpec(
                requests = ResourceRequirements(
                    cpu = cpuRequest,
                    memory = memoryRequest
                )
            ),
            environment = environment + mapOf(
                "HODEI_WORKER_TYPE" to "kubernetes",
                "HODEI_WORKER_NAMESPACE" to namespace
            ),
            labels = mapOf(
                "hodei.worker" to "true",
                "hodei.worker.type" to "kubernetes",
                "hodei.template" to name
            )
        )
        
        val now = kotlinx.datetime.Clock.System.now()
        return Template(
            id = DomainId.generate(),
            name = name,
            description = description,
            version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
            spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
            status = TemplateStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )
    }
    
    /**
     * Create a Docker template optimized for CI/CD pipeline execution
     */
    fun createDockerCIPipelineTemplate(
        name: String = "docker-ci-pipeline-worker",
        description: String = "Docker worker template optimized for CI/CD pipeline execution",
        image: String = "hodei/worker-ci",
        tag: String = "latest",
        createdBy: String = "system"
    ): Template {
        val spec = WorkerTemplateSpec(
            type = WorkerType.DOCKER,
            runtime = RuntimeSpec(
                image = image,
                tag = tag,
                command = listOf("hodei-worker"),
                args = listOf("--mode=ci-pipeline"),
                workingDir = "/workspace"
            ),
            resources = ResourceSpec(
                requests = ResourceRequirements(
                    cpu = "500m",
                    memory = "1Gi",
                    storage = "10Gi"
                ),
                limits = ResourceRequirements(
                    cpu = "2000m",
                    memory = "4Gi",
                    storage = "20Gi"
                )
            ),
            environment = mapOf(
                "HODEI_WORKER_TYPE" to "ci-pipeline",
                "HODEI_LOG_LEVEL" to "INFO",
                "HODEI_WORKER_POOL" to "ci-pipeline",
                "HODEI_WORKSPACE" to "/workspace",
                "CI" to "true"
            ),
            labels = mapOf(
                "hodei.worker" to "true",
                "hodei.worker.type" to "ci-pipeline",
                "hodei.template" to name
            ),
            networking = NetworkSpec(
                hostNetwork = false,
                dnsPolicy = DNSPolicy.CLUSTER_FIRST
            ),
            healthCheck = HealthCheckSpec(
                livenessProbe = ProbeSpec(
                    httpGet = HTTPGetActionSpec(path = "/health", port = 8080),
                    initialDelaySeconds = 30,
                    periodSeconds = 10
                ),
                readinessProbe = ProbeSpec(
                    httpGet = HTTPGetActionSpec(path = "/ready", port = 8080),
                    initialDelaySeconds = 5,
                    periodSeconds = 5
                )
            )
        )
        
        val now = kotlinx.datetime.Clock.System.now()
        return Template(
            id = DomainId.generate(),
            name = name,
            description = description,
            version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
            spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
            status = TemplateStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )
    }
    
    /**
     * Create a lightweight Docker template for quick job execution
     */
    fun createDockerLightweightTemplate(
        name: String = "docker-lightweight-worker",
        description: String = "Lightweight Docker worker template for fast job execution",
        image: String = "hodei/worker-alpine",
        tag: String = "latest",
        createdBy: String = "system"
    ): Template {
        val spec = WorkerTemplateSpec(
            type = WorkerType.DOCKER,
            runtime = RuntimeSpec(
                image = image,
                tag = tag,
                command = listOf("hodei-worker"),
                args = listOf("--mode=lightweight"),
                workingDir = "/app"
            ),
            resources = ResourceSpec(
                requests = ResourceRequirements(
                    cpu = "50m",
                    memory = "128Mi"
                ),
                limits = ResourceRequirements(
                    cpu = "500m",
                    memory = "512Mi"
                )
            ),
            environment = mapOf(
                "HODEI_WORKER_TYPE" to "lightweight",
                "HODEI_LOG_LEVEL" to "WARN",
                "HODEI_WORKER_POOL" to "lightweight"
            ),
            labels = mapOf(
                "hodei.worker" to "true",
                "hodei.worker.type" to "lightweight",
                "hodei.template" to name
            ),
            networking = NetworkSpec(
                hostNetwork = false,
                dnsPolicy = DNSPolicy.DEFAULT
            ),
            healthCheck = HealthCheckSpec(
                livenessProbe = ProbeSpec(
                    exec = ExecActionSpec(command = listOf("pgrep", "hodei-worker")),
                    initialDelaySeconds = 10,
                    periodSeconds = 30
                ),
                readinessProbe = ProbeSpec(
                    exec = ExecActionSpec(command = listOf("pgrep", "hodei-worker")),
                    initialDelaySeconds = 2,
                    periodSeconds = 10
                )
            )
        )
        
        val now = kotlinx.datetime.Clock.System.now()
        return Template(
            id = DomainId.generate(),
            name = name,
            description = description,
            version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
            spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
            status = TemplateStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )
    }
    
    /**
     * Create a Docker template with persistent storage for data processing jobs
     */
    fun createDockerPersistentStorageTemplate(
        name: String = "docker-storage-worker",
        description: String = "Docker worker template with persistent storage for data processing",
        image: String = "hodei/worker-data",
        tag: String = "latest",
        createdBy: String = "system"
    ): Template {
        val spec = WorkerTemplateSpec(
            type = WorkerType.DOCKER,
            runtime = RuntimeSpec(
                image = image,
                tag = tag,
                command = listOf("hodei-worker"),
                args = listOf("--mode=data-processing"),
                workingDir = "/data"
            ),
            resources = ResourceSpec(
                requests = ResourceRequirements(
                    cpu = "1000m",
                    memory = "2Gi",
                    storage = "50Gi"
                ),
                limits = ResourceRequirements(
                    cpu = "4000m",
                    memory = "8Gi",
                    storage = "100Gi"
                )
            ),
            environment = mapOf(
                "HODEI_WORKER_TYPE" to "data-processing",
                "HODEI_LOG_LEVEL" to "INFO",
                "HODEI_WORKER_POOL" to "data-processing",
                "HODEI_DATA_DIR" to "/data",
                "HODEI_TEMP_DIR" to "/tmp/hodei"
            ),
            labels = mapOf(
                "hodei.worker" to "true",
                "hodei.worker.type" to "data-processing",
                "hodei.template" to name,
                "hodei.storage" to "persistent"
            ),
            networking = NetworkSpec(
                hostNetwork = false,
                dnsPolicy = DNSPolicy.CLUSTER_FIRST
            ),
            healthCheck = HealthCheckSpec(
                livenessProbe = ProbeSpec(
                    httpGet = HTTPGetActionSpec(path = "/health", port = 8080),
                    initialDelaySeconds = 60,
                    periodSeconds = 30,
                    timeoutSeconds = 10
                ),
                readinessProbe = ProbeSpec(
                    httpGet = HTTPGetActionSpec(path = "/ready", port = 8080),
                    initialDelaySeconds = 30,
                    periodSeconds = 15
                )
            )
        )
        
        val now = kotlinx.datetime.Clock.System.now()
        return Template(
            id = DomainId.generate(),
            name = name,
            description = description,
            version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
            spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
            status = TemplateStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )
    }
    
    /**
     * Create a Docker template for GPU-accelerated workloads
     */
    fun createDockerGpuTemplate(
        name: String = "docker-gpu-worker",
        description: String = "Docker worker template with GPU support for ML/AI workloads",
        image: String = "hodei/worker-gpu",
        tag: String = "latest",
        createdBy: String = "system"
    ): Template {
        val spec = WorkerTemplateSpec(
            type = WorkerType.DOCKER,
            runtime = RuntimeSpec(
                image = image,
                tag = tag,
                command = listOf("hodei-worker"),
                args = listOf("--mode=gpu-accelerated"),
                workingDir = "/workspace"
            ),
            resources = ResourceSpec(
                requests = ResourceRequirements(
                    cpu = "2000m",
                    memory = "4Gi",
                    gpu = 1
                ),
                limits = ResourceRequirements(
                    cpu = "8000m",
                    memory = "16Gi",
                    gpu = 1
                )
            ),
            environment = mapOf(
                "HODEI_WORKER_TYPE" to "gpu-accelerated",
                "HODEI_LOG_LEVEL" to "INFO",
                "HODEI_WORKER_POOL" to "gpu",
                "NVIDIA_VISIBLE_DEVICES" to "all",
                "CUDA_VISIBLE_DEVICES" to "all"
            ),
            labels = mapOf(
                "hodei.worker" to "true",
                "hodei.worker.type" to "gpu-accelerated",
                "hodei.template" to name,
                "hodei.gpu" to "nvidia"
            ),
            networking = NetworkSpec(
                hostNetwork = false,
                dnsPolicy = DNSPolicy.CLUSTER_FIRST
            ),
            healthCheck = HealthCheckSpec(
                livenessProbe = ProbeSpec(
                    httpGet = HTTPGetActionSpec(path = "/health", port = 8080),
                    initialDelaySeconds = 120,
                    periodSeconds = 30
                ),
                readinessProbe = ProbeSpec(
                    exec = ExecActionSpec(command = listOf("nvidia-smi", "-q")),
                    initialDelaySeconds = 60,
                    periodSeconds = 20
                )
            )
        )
        
        val now = kotlinx.datetime.Clock.System.now()
        return Template(
            id = DomainId.generate(),
            name = name,
            description = description,
            version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
            spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
            status = TemplateStatus.PUBLISHED,
            createdAt = now,
            updatedAt = now,
            createdBy = createdBy
        )
    }
}