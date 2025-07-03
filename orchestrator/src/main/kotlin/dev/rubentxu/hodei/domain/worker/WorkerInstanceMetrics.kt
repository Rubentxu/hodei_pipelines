package dev.rubentxu.hodei.domain.worker

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Worker Instance Metrics for monitoring and analysis
 * Provides insight into worker performance and resource utilization
 */
@Serializable
data class WorkerInstanceMetrics(
    val workerId: DomainId,
    val poolId: DomainId,
    val timestamp: Instant,
    val cpuUsagePercent: Double,
    val memoryUsagePercent: Double,
    val diskUsagePercent: Double = 0.0,
    val networkBytesPerSecond: Long = 0L,
    val activeExecutions: Int = 0,
    val completedExecutions: Int = 0,
    val failedExecutions: Int = 0,
    val averageExecutionTimeMs: Long = 0L,
    val customMetrics: Map<String, Double> = emptyMap()
) {
    init {
        require(cpuUsagePercent in 0.0..100.0) { "CPU usage must be between 0 and 100" }
        require(memoryUsagePercent in 0.0..100.0) { "Memory usage must be between 0 and 100" }
        require(diskUsagePercent in 0.0..100.0) { "Disk usage must be between 0 and 100" }
        require(networkBytesPerSecond >= 0) { "Network usage cannot be negative" }
        require(activeExecutions >= 0) { "Active executions cannot be negative" }
        require(completedExecutions >= 0) { "Completed executions cannot be negative" }
        require(failedExecutions >= 0) { "Failed executions cannot be negative" }
    }

    val successRate: Double
        get() = if (completedExecutions + failedExecutions > 0) {
            completedExecutions.toDouble() / (completedExecutions + failedExecutions)
        } else {
            1.0
        }

    val isResourceConstrained: Boolean
        get() = cpuUsagePercent > 90.0 || memoryUsagePercent > 90.0 || diskUsagePercent > 90.0

    val isPerformant: Boolean
        get() = averageExecutionTimeMs < 30000 && successRate > 0.95

    fun withCustomMetric(name: String, value: Double): WorkerInstanceMetrics {
        return copy(customMetrics = customMetrics + (name to value))
    }
}

/**
 * Worker Pool Health Status
 */
@Serializable
data class WorkerPoolHealth(
    val poolId: DomainId,
    val timestamp: Instant,
    val totalWorkers: Int,
    val healthyWorkers: Int,
    val unhealthyWorkers: Int,
    val availableWorkers: Int,
    val busyWorkers: Int,
    val averageCpuUsage: Double,
    val averageMemoryUsage: Double,
    val totalExecutions: Int,
    val totalFailures: Int,
    val avgExecutionTime: Long
) {
    val healthScore: Double
        get() = if (totalWorkers > 0) {
            healthyWorkers.toDouble() / totalWorkers * 100.0
        } else {
            0.0
        }

    val capacity: Double
        get() = if (totalWorkers > 0) {
            availableWorkers.toDouble() / totalWorkers * 100.0
        } else {
            0.0
        }

    val successRate: Double
        get() = if (totalExecutions > 0) {
            (totalExecutions - totalFailures).toDouble() / totalExecutions
        } else {
            1.0
        }

    val isHealthy: Boolean
        get() = healthScore >= 80.0 && successRate >= 0.95
}

/**
 * Worker instance management utilities
 */
object WorkerInstanceUtils {
    
    fun isWorkerHealthy(worker: Worker, heartbeatThresholdSeconds: Long = 300): Boolean {
        val now = kotlinx.datetime.Clock.System.now()
        val lastHeartbeat = worker.lastHeartbeat ?: return false
        return (now.epochSeconds - lastHeartbeat.epochSeconds) < heartbeatThresholdSeconds
    }

    fun calculateWorkerScore(worker: Worker, metrics: WorkerInstanceMetrics? = null): Double {
        // Error or terminated workers get minimum score immediately
        if (worker.status == WorkerStatus.ERROR || worker.status == WorkerStatus.TERMINATED) {
            return 0.0
        }
        
        var score = 50.0 // Base score

        // Status scoring
        score += when (worker.status) {
            WorkerStatus.IDLE -> 30.0
            WorkerStatus.BUSY -> 10.0
            WorkerStatus.DRAINING -> -10.0
            WorkerStatus.MAINTENANCE -> -20.0
            else -> 0.0
        }

        // Health scoring
        if (isWorkerHealthy(worker)) {
            score += 10.0
        } else {
            score -= 20.0
        }

        // Resource capacity scoring
        metrics?.let { m ->
            val avgResourceUsage = (m.cpuUsagePercent + m.memoryUsagePercent) / 2.0
            score += when {
                avgResourceUsage < 50.0 -> 10.0
                avgResourceUsage < 80.0 -> 5.0
                avgResourceUsage > 95.0 -> -15.0
                else -> 0.0
            }

            // Performance scoring
            if (m.isPerformant) {
                score += 15.0
            } else if (m.successRate < 0.9) {
                score -= 10.0
            }
        }

        return score.coerceIn(0.0, 100.0)
    }

    fun selectBestWorker(workers: List<Worker>, metrics: Map<DomainId, WorkerInstanceMetrics> = emptyMap()): Worker? {
        return workers
            .filter { it.isAvailable }
            .maxByOrNull { worker ->
                calculateWorkerScore(worker, metrics[worker.id])
            }
    }

    fun calculatePoolCapacity(workers: List<Worker>): PoolCapacity {
        val totalCpu = workers.sumOf { parseResourceSpec(it.capabilities.cpu) }
        val totalMemory = workers.sumOf { parseResourceSpec(it.capabilities.memory) }
        val availableWorkers = workers.count { it.isAvailable }
        val totalWorkers = workers.size

        return PoolCapacity(
            totalCpuUnits = totalCpu.toInt(),
            totalMemoryGb = totalMemory.toInt(),
            availableWorkers = availableWorkers,
            totalWorkers = totalWorkers,
            utilizationPercent = if (totalWorkers > 0) {
                ((totalWorkers - availableWorkers).toDouble() / totalWorkers) * 100.0
            } else {
                0.0
            }
        )
    }

    private fun parseResourceSpec(spec: String): Double {
        // Simple parsing - extract numbers from resource specs like "4", "8Gi", "16GB"
        return spec.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: 0.0
    }
}

@Serializable
data class PoolCapacity(
    val totalCpuUnits: Int,
    val totalMemoryGb: Int,
    val availableWorkers: Int,
    val totalWorkers: Int,
    val utilizationPercent: Double
)