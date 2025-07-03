package dev.rubentxu.hodei.infrastructure.api.dto

import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

// Health Check DTOs
@Serializable
data class HealthCheckResponse(
    val overall: String, // "healthy", "unhealthy", "degraded"
    val timestamp: Instant,
    val checks: Map<String, ComponentHealth>
)

@Serializable
data class ComponentHealth(
    val status: String, // "healthy", "unhealthy"
    val message: String,
    val timestamp: Instant,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ReadinessCheckResponse(
    val ready: Boolean,
    val timestamp: Instant,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

@Serializable
data class LivenessCheckResponse(
    val alive: Boolean,
    val timestamp: Instant,
    val message: String,
    val details: Map<String, String> = emptyMap()
)

// Metrics DTOs
@Serializable
data class SystemMetricsResponse(
    val timestamp: Instant,
    val jobs: JobMetrics,
    val executions: ExecutionMetrics,
    val workers: WorkerMetrics,
    val system: SystemResourceMetrics? = null
)

@Serializable
data class JobMetrics(
    val totalJobs: Int,
    val pendingJobs: Int,
    val runningJobs: Int,
    val completedJobs: Int,
    val failedJobs: Int
)

@Serializable
data class ExecutionMetrics(
    val activeExecutions: Int,
    val totalExecutionsToday: Int,
    val avgExecutionTime: Double // in seconds
)

@Serializable
data class WorkerMetrics(
    val connectedWorkers: Int,
    val activeWorkers: Int,
    val idleWorkers: Int
)

@Serializable
data class SystemResourceMetrics(
    val cpuUsage: Double, // percentage
    val memoryUsage: Double, // percentage  
    val diskUsage: Double, // percentage
    val networkIO: NetworkIOMetrics? = null
)

@Serializable
data class NetworkIOMetrics(
    val bytesIn: Long,
    val bytesOut: Long,
    val packetsIn: Long,
    val packetsOut: Long
)

// Admin Status DTOs
@Serializable
data class AdminStatusResponse(
    val version: String,
    val uptime: String,
    val timestamp: Instant,
    val environment: String,
    val features: List<String>,
    val limits: Map<String, String>
)

@Serializable
data class SystemInfoResponse(
    val applicationName: String,
    val version: String,
    val buildTime: String,
    val gitCommit: String,
    val jvmVersion: String,
    val kotlinVersion: String,
    val timestamp: Instant,
    val properties: Map<String, String>
)

// Prometheus-style metrics (for future implementation)
@Serializable
data class PrometheusMetricsResponse(
    val metrics: List<PrometheusMetric>
)

@Serializable
data class PrometheusMetric(
    val name: String,
    val type: String, // "counter", "gauge", "histogram", "summary"
    val help: String,
    val samples: List<PrometheusMetricSample>
)

@Serializable
data class PrometheusMetricSample(
    val name: String,
    val labels: Map<String, String> = emptyMap(),
    val value: Double,
    val timestamp: Instant? = null
)