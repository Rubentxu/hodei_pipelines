package dev.rubentxu.hodei.domain.worker

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
enum class WorkerStatus {
    PROVISIONING,
    IDLE,
    BUSY,
    DRAINING,
    MAINTENANCE,
    TERMINATING,
    TERMINATED,
    ERROR;
    
    fun canTransitionTo(newStatus: WorkerStatus): Boolean = when (this) {
        PROVISIONING -> newStatus in setOf(IDLE, ERROR, TERMINATED)
        IDLE -> newStatus in setOf(BUSY, DRAINING, MAINTENANCE, TERMINATING, ERROR)
        BUSY -> newStatus in setOf(IDLE, DRAINING, TERMINATING, ERROR)
        DRAINING -> newStatus in setOf(TERMINATING, ERROR)
        MAINTENANCE -> newStatus in setOf(IDLE, TERMINATING, ERROR)
        TERMINATING -> newStatus in setOf(TERMINATED, ERROR)
        TERMINATED -> false
        ERROR -> newStatus in setOf(TERMINATING, TERMINATED)
    }
    
    val isTerminal: Boolean
        get() = this in setOf(TERMINATED, ERROR)
    
    val isActive: Boolean
        get() = this in setOf(IDLE, BUSY, DRAINING, MAINTENANCE)
}

@Serializable
data class WorkerCapabilities(
    val cpu: String,
    val memory: String,
    val storage: String,
    val supportedPlatforms: List<String> = emptyList(),
    val supportedProviders: Set<String> = emptySet(),
    val supportedRuntimes: Set<String> = setOf("docker"),
    val maxConcurrentJobs: Int = 1,
    val features: Set<String> = emptySet(),
    val customCapabilities: Map<String, String> = emptyMap()
) {
    init {
        require(cpu.isNotBlank()) { "CPU specification cannot be blank" }
        require(memory.isNotBlank()) { "Memory specification cannot be blank" }
        require(storage.isNotBlank()) { "Storage specification cannot be blank" }
    }

    fun supports(platform: String): Boolean {
        return supportedPlatforms.contains(platform)
    }

    fun hasCapability(capability: String): Boolean {
        return customCapabilities.containsKey(capability)
    }

    fun getCapabilityValue(capability: String): String? {
        return customCapabilities[capability]
    }
}

@Serializable
data class WorkerResources(
    val cpuCores: Double,
    val memoryGB: Double,
    val diskGB: Double = 0.0,
    val networkMbps: Double = 0.0
) {
    init {
        require(cpuCores > 0) { "CPU cores must be positive" }
        require(memoryGB > 0) { "Memory must be positive" }
        require(diskGB >= 0) { "Disk cannot be negative" }
        require(networkMbps >= 0) { "Network speed cannot be negative" }
    }
}

@Serializable
data class Worker(
    val id: DomainId,
    val poolId: DomainId,
    val executionId: DomainId? = null,
    val status: WorkerStatus = WorkerStatus.IDLE,
    val nodeId: String? = null,
    val ipAddress: String? = null,
    val capabilities: WorkerCapabilities,
    val resourceAllocation: WorkerResources,
    val lastHeartbeat: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val terminatedAt: Instant? = null
) {
    fun updateStatus(newStatus: WorkerStatus): Worker {
        require(status.canTransitionTo(newStatus)) { 
            "Cannot transition from $status to $newStatus" 
        }
        return copy(
            status = newStatus,
            updatedAt = kotlinx.datetime.Clock.System.now(),
            terminatedAt = if (newStatus.isTerminal) kotlinx.datetime.Clock.System.now() else terminatedAt
        )
    }
    
    fun assignExecution(executionId: DomainId): Worker {
        require(status == WorkerStatus.IDLE) { "Worker must be idle to accept execution" }
        return copy(
            executionId = executionId,
            status = WorkerStatus.BUSY,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }
    
    fun releaseExecution(): Worker =
        copy(
            executionId = null,
            status = WorkerStatus.IDLE,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun heartbeat(): Worker =
        copy(
            lastHeartbeat = kotlinx.datetime.Clock.System.now(),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
        
    fun drain(): Worker =
        copy(
            status = WorkerStatus.DRAINING,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    
    fun isHealthy(heartbeatTimeoutSeconds: Long = 300): Boolean {
        val now = kotlinx.datetime.Clock.System.now()
        return lastHeartbeat?.let { 
            (now.epochSeconds - it.epochSeconds) < heartbeatTimeoutSeconds 
        } ?: false
    }
    
    val isAvailable: Boolean
        get() = status == WorkerStatus.IDLE
    
    val isBusy: Boolean
        get() = status == WorkerStatus.BUSY
}

@Serializable
data class ResourceUsage(
    val cpuUsagePercent: Double = 0.0,
    val memoryUsageBytes: Long = 0L,
    val storageUsageBytes: Long = 0L,
    val networkRxBytes: Long = 0L,
    val networkTxBytes: Long = 0L,
    val customMetrics: Map<String, Double> = emptyMap()
) {
    init {
        require(cpuUsagePercent >= 0.0 && cpuUsagePercent <= 100.0) { 
            "CPU usage must be between 0 and 100 percent" 
        }
        require(memoryUsageBytes >= 0) { "Memory usage cannot be negative" }
        require(storageUsageBytes >= 0) { "Storage usage cannot be negative" }
        require(networkRxBytes >= 0) { "Network RX bytes cannot be negative" }
        require(networkTxBytes >= 0) { "Network TX bytes cannot be negative" }
    }

    val isHighCpuUsage: Boolean
        get() = cpuUsagePercent > 80.0

    val isHighMemoryUsage: Boolean
        get() = memoryUsageBytes > 0

    fun getCustomMetric(metricName: String): Double? {
        return customMetrics[metricName]
    }

    fun withCustomMetric(metricName: String, value: Double): ResourceUsage {
        return copy(customMetrics = customMetrics + (metricName to value))
    }
}