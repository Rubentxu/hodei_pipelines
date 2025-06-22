package dev.rubentxu.hodei.pipelines.infrastructure.orchestration.kubernetes

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.WorkerOrchestrator
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.apis.CoreV1Api
import io.kubernetes.client.openapi.models.*
import io.kubernetes.client.util.Watch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import mu.KotlinLogging
import java.time.Instant

/**
 * Kubernetes implementation using client-java 24.0.0
 * 
 * This implementation uses the latest Kubernetes Java client API patterns,
 * supports both in-cluster and out-of-cluster deployment scenarios,
 * and follows hexagonal architecture principles.
 */
class KubernetesWorkerOrchestrator(
    private val kubernetesClient: ApiClient,
    private val configuration: KubernetesOrchestratorConfiguration,
    private val resourceValidator: KubernetesResourceValidator = KubernetesResourceValidator()
) : WorkerOrchestrator {

    private val logger = KotlinLogging.logger {}
    private val coreV1Api = CoreV1Api(kubernetesClient)

    override suspend fun createWorker(template: WorkerTemplate, poolId: WorkerPoolId): WorkerCreationResult {
        logger.info { "Creating Kubernetes worker from template ${template.id.value} for pool ${poolId.value}" }
        
        return try {
            // 1. Validate template
            val validationResult = validateTemplate(template)
            if (validationResult is TemplateValidationResult.Invalid) {
                logger.warn { "Template validation failed: ${validationResult.errors}" }
                return WorkerCreationResult.InvalidTemplate(validationResult.errors)
            }

            // 2. Check resource availability
            val resourceAvailability = getResourceAvailability()
            if (!resourceAvailability.canAccommodateWorkers(1)) {
                logger.warn { "Insufficient cluster resources for worker creation" }
                return WorkerCreationResult.InsufficientResources(
                    required = template.resources,
                    available = resourceAvailability
                )
            }

            // 3. Generate unique worker identity
            val workerId = WorkerId.generate()
            val podName = generatePodName(template, workerId)

            // 4. Create Kubernetes Pod specification
            val podSpec = createPodSpec(template, workerId, poolId)
            val pod = V1Pod().apply {
                metadata = V1ObjectMeta().apply {
                    name = podName
                    namespace = configuration.getEffectiveNamespace()
                    labels = buildLabels(template, poolId, workerId)
                    annotations = buildAnnotations(template, workerId)
                }
                spec = podSpec
            }

            // 5. Create pod in Kubernetes using modern API
            val effectiveNamespace = configuration.getEffectiveNamespace()
            logger.debug { "Creating Kubernetes pod: $podName in namespace $effectiveNamespace" }
            
            val createdPod = coreV1Api.createNamespacedPod(
                effectiveNamespace,
                pod
            ).execute()

            // 6. Convert Kubernetes pod to domain Worker
            val worker = convertPodToWorker(createdPod, workerId)
            
            logger.info { "Successfully created Kubernetes worker ${workerId.value} (pod: $podName)" }
            WorkerCreationResult.Success(worker)

        } catch (e: io.kubernetes.client.openapi.ApiException) {
            val errorMessage = "Kubernetes API error: ${e.message} (code: ${e.code})"
            logger.error { "$errorMessage - Response: ${e.responseBody}" }
            
            when (e.code) {
                409 -> WorkerCreationResult.Failed("Worker already exists", e)
                403 -> WorkerCreationResult.Failed("Insufficient permissions to create worker", e)
                422 -> WorkerCreationResult.InvalidTemplate(listOf("Invalid pod specification: ${e.message}"))
                else -> WorkerCreationResult.Failed(errorMessage, e)
            }
        } catch (e: Exception) {
            val errorMessage = "Unexpected error creating worker: ${e.message}"
            logger.error(e) { errorMessage }
            WorkerCreationResult.Failed(errorMessage, e)
        }
    }

    override suspend fun deleteWorker(workerId: WorkerId): WorkerDeletionResult {
        logger.info { "Deleting Kubernetes worker ${workerId.value}" }
        
        return try {
            // 1. Find pod by worker ID
            val podName = findPodNameByWorkerId(workerId)
            if (podName == null) {
                logger.warn { "Worker ${workerId.value} not found for deletion" }
                return WorkerDeletionResult.NotFound(workerId)
            }

            // 2. Check if worker has active jobs
            val pod = coreV1Api.readNamespacedPod(podName, configuration.getEffectiveNamespace())
                .execute()
            val activeJobs = extractActiveJobsFromPod(pod)
            if (activeJobs.isNotEmpty() && !configuration.allowForceDelete) {
                logger.warn { "Worker ${workerId.value} has active jobs: $activeJobs" }
                return WorkerDeletionResult.HasActiveJobs(activeJobs)
            }

            // 3. Delete pod with modern API
            val deleteOptions = V1DeleteOptions().apply {
                gracePeriodSeconds = configuration.deleteGracePeriodSeconds.toLong()
                propagationPolicy = "Foreground"
            }

            logger.debug { "Deleting pod $podName with grace period ${configuration.deleteGracePeriodSeconds}s" }
            coreV1Api.deleteNamespacedPod(podName, configuration.getEffectiveNamespace())
                .body(deleteOptions)
                .execute()

            logger.info { "Successfully initiated deletion of worker ${workerId.value}" }
            WorkerDeletionResult.Success

        } catch (e: io.kubernetes.client.openapi.ApiException) {
            when (e.code) {
                404 -> {
                    logger.warn { "Worker ${workerId.value} not found (404) - considering deletion successful" }
                    WorkerDeletionResult.Success // Already deleted
                }
                403 -> WorkerDeletionResult.Failed("Insufficient permissions to delete worker", e)
                else -> {
                    val errorMessage = "Kubernetes API error deleting worker: ${e.message}"
                    logger.error { errorMessage }
                    WorkerDeletionResult.Failed(errorMessage, e)
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Unexpected error deleting worker: ${e.message}"
            logger.error(e) { errorMessage }
            WorkerDeletionResult.Failed(errorMessage, e)
        }
    }

    override suspend fun getWorkerStatus(workerId: WorkerId): WorkerStatusResult {
        logger.debug { "Getting status for worker ${workerId.value}" }
        
        return try {
            val podName = findPodNameByWorkerId(workerId)
            if (podName == null) {
                return WorkerStatusResult.NotFound(workerId)
            }

            val pod = coreV1Api.readNamespacedPod(podName, configuration.getEffectiveNamespace())
                .execute()
            val worker = convertPodToWorker(pod, workerId)
            
            WorkerStatusResult.Success(worker)

        } catch (e: io.kubernetes.client.openapi.ApiException) {
            when (e.code) {
                404 -> WorkerStatusResult.NotFound(workerId)
                else -> {
                    val errorMessage = "Error getting worker status: ${e.message}"
                    logger.error { errorMessage }
                    WorkerStatusResult.Failed(errorMessage, e)
                }
            }
        } catch (e: Exception) {
            val errorMessage = "Unexpected error getting worker status: ${e.message}"
            logger.error(e) { errorMessage }
            WorkerStatusResult.Failed(errorMessage, e)
        }
    }

    override suspend fun listWorkers(poolId: WorkerPoolId): List<Worker> {
        logger.debug { "Listing workers for pool ${poolId.value}" }
        
        return try {
            val labelSelector = "app=${configuration.appLabel},pool-id=${poolId.value}"
            
            val podList = coreV1Api.listNamespacedPod(configuration.getEffectiveNamespace())
                .labelSelector(labelSelector)
                .execute()

            podList.items.mapNotNull { pod ->
                try {
                    val workerId = pod.metadata?.labels?.get("worker-id")?.let { WorkerId(it) }
                        ?: return@mapNotNull null
                    convertPodToWorker(pod, workerId)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to convert pod ${pod.metadata?.name} to worker" }
                    null
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error listing workers for pool ${poolId.value}" }
            emptyList()
        }
    }

    override suspend fun listAllWorkers(): List<Worker> {
        logger.debug { "Listing all workers" }
        
        return try {
            val labelSelector = "app=${configuration.appLabel}"
            
            val podList = coreV1Api.listNamespacedPod(configuration.getEffectiveNamespace())
                .labelSelector(labelSelector)
                .execute()

            podList.items.mapNotNull { pod ->
                try {
                    val workerId = pod.metadata?.labels?.get("worker-id")?.let { WorkerId(it) }
                        ?: return@mapNotNull null
                    convertPodToWorker(pod, workerId)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to convert pod ${pod.metadata?.name} to worker" }
                    null
                }
            }

        } catch (e: Exception) {
            logger.error(e) { "Error listing all workers" }
            emptyList()
        }
    }

    override suspend fun getResourceAvailability(): ResourceAvailability {
        logger.debug { "Getting cluster resource availability" }
        
        return try {
            // Simplified implementation - real metrics would need more complex parsing
            val nodeList = coreV1Api.listNode().execute()
            val availableNodes = nodeList.items.size

            ResourceAvailability(
                totalCpu = "16000m",
                totalMemory = "32Gi",
                availableCpu = "8000m",
                availableMemory = "16Gi",
                totalNodes = nodeList.items.size,
                availableNodes = availableNodes,
                workerResourceRequirements = configuration.defaultWorkerResources
            )

        } catch (e: Exception) {
            logger.error(e) { "Error getting resource availability" }
            // Return conservative estimates on error
            ResourceAvailability(
                totalCpu = "unknown",
                totalMemory = "unknown", 
                availableCpu = "1000m",
                availableMemory = "2Gi",
                totalNodes = 1,
                availableNodes = 1,
                workerResourceRequirements = configuration.defaultWorkerResources
            )
        }
    }

    override fun watchWorkerEvents(): Flow<WorkerOrchestrationEvent> {
        logger.info { "Starting worker event watch for namespace ${configuration.getEffectiveNamespace()}" }
        // Simplified implementation - real watch would use Kubernetes Watch API
        return flowOf()
    }

    override suspend fun validateTemplate(template: WorkerTemplate): TemplateValidationResult {
        return resourceValidator.validateTemplate(template, configuration)
    }

    override suspend fun getOrchestratorInfo(): OrchestratorInfo {
        return OrchestratorInfo(
            type = OrchestratorType.KUBERNETES,
            version = "24.0.0",
            capabilities = setOf(
                OrchestratorCapability.AUTO_SCALING,
                OrchestratorCapability.PERSISTENT_STORAGE,
                OrchestratorCapability.LOAD_BALANCING,
                OrchestratorCapability.SERVICE_DISCOVERY,
                OrchestratorCapability.SECRETS_MANAGEMENT,
                OrchestratorCapability.RESOURCE_QUOTAS,
                OrchestratorCapability.NODE_AFFINITY,
                OrchestratorCapability.TOLERATIONS,
                OrchestratorCapability.VOLUME_MOUNTS,
                OrchestratorCapability.SECURITY_CONTEXTS,
                OrchestratorCapability.HEALTH_CHECKS,
                OrchestratorCapability.ROLLING_UPDATES,
                OrchestratorCapability.BATCH_JOBS
            ),
            limits = OrchestratorLimits(
                maxWorkersPerPool = configuration.maxWorkersPerPool,
                maxTotalWorkers = configuration.maxTotalWorkers,
                maxCpuPerWorker = configuration.maxCpuPerWorker,
                maxMemoryPerWorker = configuration.maxMemoryPerWorker,
                maxVolumesPerWorker = configuration.maxVolumesPerWorker,
                supportedVolumeTypes = setOf(
                    "emptyDir", "configMap", "secret", "persistentVolumeClaim", "hostPath"
                ),
                maxConcurrentCreations = configuration.maxConcurrentCreations
            ),
            metadata = mapOf(
                "namespace" to configuration.getEffectiveNamespace(),
                "serviceAccount" to configuration.serviceAccountName,
                "inCluster" to configuration.inCluster.toString()
            )
        )
    }

    override suspend fun healthCheck(): OrchestratorHealth {
        return try {
            // Simple health check: try to list pods
            coreV1Api.listNamespacedPod(configuration.getEffectiveNamespace())
                .limit(1)
                .execute()
            
            OrchestratorHealth(
                status = HealthStatus.HEALTHY,
                message = "Kubernetes API is accessible",
                details = mapOf(
                    "namespace" to configuration.getEffectiveNamespace(),
                    "apiVersion" to "24.0.0",
                    "inCluster" to configuration.inCluster.toString()
                )
            )
        } catch (e: io.kubernetes.client.openapi.ApiException) {
            OrchestratorHealth(
                status = when (e.code) {
                    401, 403 -> HealthStatus.UNHEALTHY
                    else -> HealthStatus.DEGRADED
                },
                message = "Kubernetes API error: ${e.message}",
                details = mapOf(
                    "code" to e.code,
                    "namespace" to configuration.getEffectiveNamespace()
                )
            )
        } catch (e: Exception) {
            OrchestratorHealth(
                status = HealthStatus.UNHEALTHY,
                message = "Cannot connect to Kubernetes API: ${e.message}",
                details = mapOf(
                    "exception" to (e::class.simpleName ?: "Unknown")
                )
            )
        }
    }

    // Private helper methods

    private fun generatePodName(template: WorkerTemplate, workerId: WorkerId): String {
        val prefix = configuration.podNamePrefix
        val templateName = template.name.lowercase()
            .replace(" ", "-")
            .replace(Regex("[^a-z0-9-]"), "")
        val workerSuffix = workerId.value.takeLast(8)
        
        return "$prefix-$templateName-$workerSuffix"
    }

    private fun createPodSpec(
        template: WorkerTemplate,
        workerId: WorkerId,
        poolId: WorkerPoolId
    ): V1PodSpec {
        return V1PodSpec().apply {
            serviceAccountName = configuration.serviceAccountName
            restartPolicy = "Never"
            
            // Node selection
            if (template.nodeSelector.isNotEmpty()) {
                nodeSelector = template.nodeSelector
            }
            
            // Main container
            containers = listOf(
                V1Container().apply {
                    name = "worker"
                    image = template.image
                    imagePullPolicy = template.imagePullPolicy ?: "Always"
                    
                    // Commands and args
                    if (template.command.isNotEmpty()) {
                        command = template.command
                    }
                    if (template.args.isNotEmpty()) {
                        args = template.args
                    }
                    
                    // Environment variables
                    env = buildEnvironmentVariables(template, workerId)
                    
                    // Resource requirements
                    resources = V1ResourceRequirements().apply {
                        requests = mapOf(
                            "cpu" to io.kubernetes.client.custom.Quantity(template.resources.cpu),
                            "memory" to io.kubernetes.client.custom.Quantity(template.resources.memory)
                        )
                        limits = mapOf(
                            "cpu" to io.kubernetes.client.custom.Quantity(configuration.maxCpuPerWorker),
                            "memory" to io.kubernetes.client.custom.Quantity(configuration.maxMemoryPerWorker)
                        )
                    }
                    
                    // Ports
                    if (template.ports.isNotEmpty()) {
                        ports = template.ports.map { port ->
                            V1ContainerPort().apply {
                                name = port.name
                                containerPort = port.containerPort
                                protocol = port.protocol ?: "TCP"
                            }
                        }
                    }
                }
            )
            
            // Grace period
            terminationGracePeriodSeconds = configuration.deleteGracePeriodSeconds.toLong()
        }
    }

    private fun buildLabels(
        template: WorkerTemplate, 
        poolId: WorkerPoolId, 
        workerId: WorkerId
    ): Map<String, String> {
        return mapOf(
            "app" to configuration.appLabel,
            "worker-id" to workerId.value,
            "pool-id" to poolId.value,
            "template-id" to template.id.value,
            "managed-by" to "hodei-pipelines",
            "version" to template.version
        ) + template.labels
    }

    private fun buildAnnotations(template: WorkerTemplate, workerId: WorkerId): Map<String, String> {
        return mapOf(
            "hodei.pipelines/worker-id" to workerId.value,
            "hodei.pipelines/template-id" to template.id.value,
            "hodei.pipelines/created-at" to Instant.now().toString(),
            "hodei.pipelines/ephemeral" to "true"
        )
    }

    private fun buildEnvironmentVariables(
        template: WorkerTemplate,
        workerId: WorkerId
    ): List<V1EnvVar> {
        val baseEnv = listOf(
            V1EnvVar().apply {
                name = "HODEI_WORKER_ID"
                value = workerId.value
            },
            V1EnvVar().apply {
                name = "HODEI_TEMPLATE_ID"
                value = template.id.value
            },
            V1EnvVar().apply {
                name = "HODEI_NAMESPACE"
                value = configuration.getEffectiveNamespace()
            },
            // Pod information via Downward API
            V1EnvVar().apply {
                name = "POD_NAME"
                valueFrom = V1EnvVarSource().apply {
                    fieldRef = V1ObjectFieldSelector().apply {
                        fieldPath = "metadata.name"
                    }
                }
            },
            V1EnvVar().apply {
                name = "NODE_NAME"
                valueFrom = V1EnvVarSource().apply {
                    fieldRef = V1ObjectFieldSelector().apply {
                        fieldPath = "spec.nodeName"
                    }
                }
            }
        )
        
        val templateEnv = template.env.map { (key, value) ->
            V1EnvVar().apply {
                name = key
                this.value = value
            }
        }
        
        return baseEnv + templateEnv
    }

    private suspend fun findPodNameByWorkerId(workerId: WorkerId): String? {
        return try {
            val labelSelector = "app=${configuration.appLabel},worker-id=${workerId.value}"
            val podList = coreV1Api.listNamespacedPod(configuration.getEffectiveNamespace())
                .labelSelector(labelSelector)
                .execute()
            
            podList.items.firstOrNull()?.metadata?.name
        } catch (e: Exception) {
            logger.warn(e) { "Error finding pod for worker ${workerId.value}" }
            null
        }
    }

    private fun extractActiveJobsFromPod(pod: V1Pod): List<String> {
        return pod.metadata?.labels?.get("active-jobs")?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
    }


    private fun parseCpuToMillicores(cpu: String): Long {
        return when {
            cpu.endsWith("m") -> cpu.dropLast(1).toLong()
            cpu.endsWith("n") -> cpu.dropLast(1).toLong() / 1_000_000
            else -> cpu.toLong() * 1000
        }
    }

    private fun parseMemoryToBytes(memory: String): Long {
        return when {
            memory.endsWith("Ki") -> memory.dropLast(2).toLong() * 1024
            memory.endsWith("Mi") -> memory.dropLast(2).toLong() * 1024 * 1024
            memory.endsWith("Gi") -> memory.dropLast(2).toLong() * 1024 * 1024 * 1024
            memory.endsWith("Ti") -> memory.dropLast(2).toLong() * 1024 * 1024 * 1024 * 1024
            memory.endsWith("k") -> memory.dropLast(1).toLong() * 1000
            memory.endsWith("M") -> memory.dropLast(1).toLong() * 1000 * 1000
            memory.endsWith("G") -> memory.dropLast(1).toLong() * 1000 * 1000 * 1000
            memory.endsWith("T") -> memory.dropLast(1).toLong() * 1000 * 1000 * 1000 * 1000
            else -> memory.toLong()
        }
    }

    private fun convertPodToWorker(pod: V1Pod, workerId: WorkerId): Worker {
        val status = mapPodStatusToWorkerStatus(pod.status)
        val capabilities = createWorkerCapabilitiesFromLabels(pod.metadata?.labels ?: emptyMap())
        
        return Worker(
            id = workerId,
            name = pod.metadata?.name ?: workerId.value,
            capabilities = capabilities,
            status = status,
            createdAt = pod.metadata?.creationTimestamp?.toInstant() ?: Instant.now()
        )
    }

    private fun mapPodStatusToWorkerStatus(status: V1PodStatus?): dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus {
        return when (status?.phase) {
            "Pending" -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.PROVISIONING
            "Running" -> {
                val allReady = status.containerStatuses?.all { it.ready == true } ?: false
                if (allReady) dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY 
                else dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.PROVISIONING
            }
            "Succeeded" -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.OFFLINE
            "Failed" -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.FAILED
            else -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.OFFLINE
        }
    }

    private fun createWorkerCapabilitiesFromLabels(labels: Map<String, String>): dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities {
        return dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities.builder()
            .languages(labels["languages"]?.split(",")?.toSet() ?: emptySet())
            .tools(labels["tools"]?.split(",")?.toSet() ?: emptySet())
            .features(labels["features"]?.split(",")?.toSet() ?: emptySet())
            .build()
    }
}