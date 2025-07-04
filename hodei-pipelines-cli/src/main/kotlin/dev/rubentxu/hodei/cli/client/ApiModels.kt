package dev.rubentxu.hodei.cli.client

import kotlinx.serialization.Serializable
import kotlinx.datetime.Instant

// =================== AUTHENTICATION ===================

@Serializable
data class LoginRequest(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponse(
    val token: String,
    val user: User? = null,
    val expiresIn: Long? = null
)

@Serializable
data class AuthToken(
    val accessToken: String,
    val tokenType: String = "Bearer",
    val expiresAt: String? = null
)

@Serializable
data class User(
    val id: String,
    val username: String,
    val email: String? = null,
    val roles: List<String> = emptyList()
)

@Serializable
data class UserInfo(
    val username: String,
    val orchestratorUrl: String,
    val context: String
)

// =================== HEALTH & SYSTEM ===================

@Serializable
data class HealthResponse(
    val overall: String,
    val timestamp: String,
    val checks: Map<String, ComponentHealth>
)

@Serializable
data class ComponentHealth(
    val status: String,
    val message: String,
    val timestamp: String
)

@Serializable
data class VersionResponse(
    val applicationName: String,
    val version: String,
    val buildTime: String? = null,
    val gitCommit: String? = null,
    val jvmVersion: String,
    val kotlinVersion: String? = null,
    val timestamp: String,
    val properties: Map<String, String> = emptyMap()
)

// =================== RESOURCE POOLS ===================

@Serializable
data class ResourcePool(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val provider: String,
    val capacity: PoolCapacity? = null,
    val utilization: PoolUtilization? = null,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class CreatePoolRequest(
    val name: String,
    val type: String,
    val provider: String = "docker",
    val maxWorkers: Int = 5,
    val configuration: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class PoolCapacity(
    val totalCpuCores: Int,
    val totalMemoryMB: Long,
    val totalStorageGB: Long? = null,
    val maxInstances: Int,
    val availableInstanceTypes: List<String> = emptyList()
)

@Serializable
data class PoolUtilization(
    val poolId: String,
    val totalCpu: Double,
    val usedCpu: Double,
    val totalMemoryBytes: Long,
    val usedMemoryBytes: Long,
    val totalDiskBytes: Long,
    val usedDiskBytes: Long,
    val runningJobs: Int,
    val queuedJobs: Int,
    val timestamp: String
)

// =================== JOBS ===================

@Serializable
data class Job(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val priority: String,
    val description: String? = null,
    val content: String,
    val resourcePoolId: String? = null,
    val workerId: String? = null,
    val progress: Double? = null,
    val currentStep: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val duration: Long? = null,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class JobSubmissionRequest(
    val name: String,
    val type: String = "pipeline",
    val priority: String = "normal",
    val description: String? = null,
    val content: String,
    val resourcePoolId: String? = null,
    val timeout: Long? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class JobSubmissionResponse(
    val jobId: String,
    val status: String,
    val message: String,
    val estimatedDuration: Long? = null,
    val queuePosition: Int? = null
)

@Serializable
data class JobLogsResponse(
    val jobId: String,
    val logs: List<LogEntry>,
    val totalLines: Int,
    val hasMore: Boolean = false
)

@Serializable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val source: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

// =================== WORKERS ===================

@Serializable
data class Worker(
    val id: String,
    val name: String,
    val status: String,
    val type: String,
    val resourcePoolId: String,
    val currentJobId: String? = null,
    val capabilities: List<String> = emptyList(),
    val lastHeartbeat: String,
    val connectedAt: String,
    val metadata: Map<String, String> = emptyMap()
)

// =================== TEMPLATES ===================

@Serializable
data class Template(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val type: String,
    val status: String,
    val spec: Map<String, String>,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String
)

@Serializable
data class CreateTemplateRequest(
    val name: String,
    val description: String,
    val type: String,
    val spec: Map<String, String>,
    val metadata: Map<String, String> = emptyMap()
)