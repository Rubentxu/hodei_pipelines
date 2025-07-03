package dev.rubentxu.hodei.resourcemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Resource Pool domain model - similar to Kubernetes namespaces
 * Provides isolation and resource management for workers and jobs
 */
@Serializable
data class ResourcePool(
    val id: DomainId,
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val status: PoolStatus = PoolStatus.ACTIVE,
    val type: String = "kubernetes", // kubernetes, docker, etc.
    val maxWorkers: Int = 10,
    val maxJobs: Int? = null,
    val resourceQuotas: ResourceQuotas = ResourceQuotas.unlimited(),
    val labels: Map<String, String> = emptyMap(),
    val annotations: Map<String, String> = emptyMap(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String
) {
    init {
        require(name.isNotBlank()) { "Resource pool name cannot be blank" }
        require(name.matches(Regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"))) { 
            "Resource pool name must be DNS-1123 compliant (lowercase, alphanumeric, hyphens)" 
        }
        require(name.length <= 63) { "Resource pool name must be 63 characters or less" }
    }

    companion object {
        const val DEFAULT_POOL_NAME = "default"
        
        fun createDefault(createdBy: String): ResourcePool {
            val now = kotlinx.datetime.Clock.System.now()
            return ResourcePool(
                id = DomainId.generate(),
                name = DEFAULT_POOL_NAME,
                displayName = "Default Pool",
                description = "Default resource pool for workers and jobs",
                resourceQuotas = ResourceQuotas.unlimited(),
                createdAt = now,
                updatedAt = now,
                createdBy = createdBy
            )
        }
    }

    fun updateQuotas(newQuotas: ResourceQuotas): ResourcePool {
        return copy(
            resourceQuotas = newQuotas,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun updateStatus(newStatus: PoolStatus): ResourcePool {
        return copy(
            status = newStatus,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun addLabel(key: String, value: String): ResourcePool {
        return copy(
            labels = labels + (key to value),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun removeLabel(key: String): ResourcePool {
        return copy(
            labels = labels - key,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun addAnnotation(key: String, value: String): ResourcePool {
        return copy(
            annotations = annotations + (key to value),
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    fun isActive(): Boolean = status == PoolStatus.ACTIVE
    fun isTerminating(): Boolean = status == PoolStatus.TERMINATING
}

@Serializable
enum class PoolStatus {
    ACTIVE,
    TERMINATING,
    TERMINATED
}

/**
 * Resource quotas and limits for a pool - similar to Kubernetes ResourceQuota
 */
@Serializable
data class ResourceQuotas(
    val cpu: ResourceLimit?,
    val memory: ResourceLimit?,
    val storage: ResourceLimit?,
    val maxWorkers: Int?,
    val maxJobs: Int?,
    val maxConcurrentJobs: Int?,
    val customLimits: Map<String, String> = emptyMap()
) {
    companion object {
        fun unlimited(): ResourceQuotas {
            return ResourceQuotas(
                cpu = null,
                memory = null, 
                storage = null,
                maxWorkers = null,
                maxJobs = null,
                maxConcurrentJobs = null
            )
        }
        
        fun basic(): ResourceQuotas {
            return ResourceQuotas(
                cpu = ResourceLimit("100", "1000"),
                memory = ResourceLimit("1Gi", "10Gi"),
                storage = ResourceLimit("10Gi", "100Gi"),
                maxWorkers = 10,
                maxJobs = 100,
                maxConcurrentJobs = 10
            )
        }
    }

    fun hasLimits(): Boolean {
        return cpu != null || memory != null || storage != null ||
                maxWorkers != null || maxJobs != null || maxConcurrentJobs != null ||
                customLimits.isNotEmpty()
    }

    fun exceedsWorkerLimit(currentWorkers: Int): Boolean {
        return maxWorkers != null && currentWorkers > maxWorkers
    }

    fun exceedsConcurrentJobLimit(currentJobs: Int): Boolean {
        return maxConcurrentJobs != null && currentJobs > maxConcurrentJobs
    }
}

/**
 * Resource limit with requests and limits (Kubernetes style)
 */
@Serializable
data class ResourceLimit(
    val requests: String, // Minimum guaranteed resources
    val limits: String    // Maximum allowed resources
) {
    init {
        require(requests.isNotBlank()) { "Resource requests cannot be blank" }
        require(limits.isNotBlank()) { "Resource limits cannot be blank" }
    }
}