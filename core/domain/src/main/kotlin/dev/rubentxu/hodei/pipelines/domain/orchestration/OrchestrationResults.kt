package dev.rubentxu.hodei.pipelines.domain.orchestration

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId

/**
 * Result types for orchestration operations following functional programming principles
 * These represent the outcome of orchestration operations without throwing exceptions
 */

/**
 * Result of worker creation operations
 */
sealed class WorkerCreationResult {
    data class Success(val worker: Worker) : WorkerCreationResult()
    data class Failed(val reason: String, val cause: Throwable? = null) : WorkerCreationResult()
    data class InsufficientResources(
        val required: ResourceRequirements,
        val available: ResourceAvailability
    ) : WorkerCreationResult()
    data class InvalidTemplate(val errors: List<String>) : WorkerCreationResult()
    data class NoSuitableTemplate(val requirements: WorkerRequirements) : WorkerCreationResult()
    data class Timeout(val timeoutSeconds: Int) : WorkerCreationResult()
    data class QuotaExceeded(val currentCount: Int, val maxAllowed: Int) : WorkerCreationResult()
}

/**
 * Result of worker deletion operations
 */
sealed class WorkerDeletionResult {
    object Success : WorkerDeletionResult()
    data class Failed(val reason: String, val cause: Throwable? = null) : WorkerDeletionResult()
    data class NotFound(val workerId: WorkerId) : WorkerDeletionResult()
    data class HasActiveJobs(val activeJobIds: List<String>) : WorkerDeletionResult()
    data class GracePeriodExpired(val gracePeriodSeconds: Int) : WorkerDeletionResult()
}

/**
 * Result of worker status queries
 */
sealed class WorkerStatusResult {
    data class Success(val worker: Worker) : WorkerStatusResult()
    data class Failed(val reason: String, val cause: Throwable? = null) : WorkerStatusResult()
    data class NotFound(val workerId: WorkerId) : WorkerStatusResult()
}

/**
 * Result of template validation
 */
sealed class TemplateValidationResult {
    object Valid : TemplateValidationResult()
    data class Invalid(val errors: List<String>) : TemplateValidationResult()
}

/**
 * Resource availability in the cluster
 */
data class ResourceAvailability(
    val totalCpu: String,
    val totalMemory: String,
    val availableCpu: String,
    val availableMemory: String,
    val totalNodes: Int,
    val availableNodes: Int,
    val allocatableResources: Map<String, String> = emptyMap(),
    val workerResourceRequirements: ResourceRequirements
) {
    /**
     * Check if cluster can accommodate N workers
     */
    fun canAccommodateWorkers(count: Int): Boolean {
        val requiredCpu = parseCpuMillicores(workerResourceRequirements.cpu) * count
        val requiredMemory = parseMemoryBytes(workerResourceRequirements.memory) * count
        
        val availableCpuMillicores = parseCpuMillicores(availableCpu)
        val availableMemoryBytes = parseMemoryBytes(availableMemory)
        
        return requiredCpu <= availableCpuMillicores && requiredMemory <= availableMemoryBytes
    }
    
    /**
     * Calculate maximum possible workers based on available resources
     */
    fun getMaxPossibleWorkers(): Int {
        val cpuLimit = calculateMaxWorkersByCpu()
        val memoryLimit = calculateMaxWorkersByMemory()
        
        return minOf(cpuLimit, memoryLimit, availableNodes * 5) // Max 5 workers per node
    }
    
    fun calculateMaxWorkersByCpu(): Int {
        val availableCpuMillicores = parseCpuMillicores(availableCpu)
        val requiredCpuMillicores = parseCpuMillicores(workerResourceRequirements.cpu)
        
        return if (requiredCpuMillicores > 0) {
            (availableCpuMillicores / requiredCpuMillicores).toInt()
        } else {
            0
        }
    }
    
    fun calculateMaxWorkersByMemory(): Int {
        val availableMemoryBytes = parseMemoryBytes(availableMemory)
        val requiredMemoryBytes = parseMemoryBytes(workerResourceRequirements.memory)
        
        return if (requiredMemoryBytes > 0) {
            (availableMemoryBytes / requiredMemoryBytes).toInt()
        } else {
            0
        }
    }
    
    /**
     * Get resource utilization metrics
     */
    fun getResourceUtilization(): ResourceUtilization {
        val totalCpuMillicores = parseCpuMillicores(totalCpu)
        val availableCpuMillicores = parseCpuMillicores(availableCpu)
        val cpuUtilization = if (totalCpuMillicores > 0) {
            1.0 - (availableCpuMillicores.toDouble() / totalCpuMillicores)
        } else 0.0
        
        val totalMemoryBytes = parseMemoryBytes(totalMemory)
        val availableMemoryBytes = parseMemoryBytes(availableMemory)
        val memoryUtilization = if (totalMemoryBytes > 0) {
            1.0 - (availableMemoryBytes.toDouble() / totalMemoryBytes)
        } else 0.0
        
        return ResourceUtilization(
            cpu = cpuUtilization,
            memory = memoryUtilization,
            nodes = if (totalNodes > 0) 1.0 - (availableNodes.toDouble() / totalNodes) else 0.0
        )
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
 * Resource Utilization metrics
 */
data class ResourceUtilization(
    val cpu: Double,    // 0.0 - 1.0
    val memory: Double, // 0.0 - 1.0
    val nodes: Double   // 0.0 - 1.0
) {
    val average: Double get() = (cpu + memory + nodes) / 3.0
    
    fun isHighUtilization(threshold: Double = 0.8): Boolean = average > threshold
    fun isLowUtilization(threshold: Double = 0.3): Boolean = average < threshold
}

/**
 * Orchestrator health status
 */
data class OrchestratorHealth(
    val status: HealthStatus,
    val message: String,
    val details: Map<String, Any> = emptyMap(),
    val lastChecked: java.time.Instant = java.time.Instant.now()
)

enum class HealthStatus {
    HEALTHY, DEGRADED, UNHEALTHY, UNKNOWN
}

/**
 * Orchestrator information and capabilities
 */
data class OrchestratorInfo(
    val type: OrchestratorType,
    val version: String,
    val capabilities: Set<OrchestratorCapability>,
    val limits: OrchestratorLimits,
    val metadata: Map<String, String> = emptyMap()
)

enum class OrchestratorType {
    KUBERNETES, DOCKER, NOMAD, MESOS, LOCAL
}

enum class OrchestratorCapability {
    AUTO_SCALING,
    PERSISTENT_STORAGE,
    LOAD_BALANCING,
    SERVICE_DISCOVERY,
    SECRETS_MANAGEMENT,
    RESOURCE_QUOTAS,
    NODE_AFFINITY,
    TOLERATIONS,
    VOLUME_MOUNTS,
    SECURITY_CONTEXTS,
    HEALTH_CHECKS,
    ROLLING_UPDATES,
    BATCH_JOBS,
    CUSTOM_RESOURCES,
    NETWORKING_POLICIES,
    POD_DISRUPTION_BUDGETS
}

/**
 * Orchestrator limits and constraints
 */
data class OrchestratorLimits(
    val maxWorkersPerPool: Int,
    val maxTotalWorkers: Int,
    val maxCpuPerWorker: String,
    val maxMemoryPerWorker: String,
    val maxVolumesPerWorker: Int,
    val supportedVolumeTypes: Set<String>,
    val maxConcurrentCreations: Int,
    val defaultTimeoutSeconds: Int = 300
)