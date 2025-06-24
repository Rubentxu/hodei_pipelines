package dev.rubentxu.hodei.pipelines.domain.worker

import java.time.Instant
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@JvmInline
value class WorkerId(val value: String) {
    init {
        require(value.isNotBlank()) { "Worker id cannot be empty" }
    }

    companion object {
        fun fromString(value: String): WorkerId {
            return WorkerId(value.trim())
        }

        @OptIn(ExperimentalUuidApi::class)
        fun generate(): WorkerId {
            return WorkerId(Uuid.random().toString())
        }
    }
}

enum class WorkerStatus {
    PROVISIONING,
    READY,
    TERMINATING,
    FAILED,
    IDLE,
    BUSY,
    OFFLINE;

    fun isActive(): Boolean {
        return this == IDLE || this == BUSY || this == READY || this == PROVISIONING
    }

    fun isOffline(): Boolean {
        return this == OFFLINE || this == TERMINATING || this == FAILED
    }
}

data class WorkerCapabilities(
    private val capabilities: Map<String, String>,
) {
    fun matches(key: String, value: String): Boolean {
        return capabilities[key] == value
    }

    fun hasLabel(label: String): Boolean {
        return capabilities["labels"]?.split(",")?.contains(label) ?: false
    }

    fun getOperatingSystem(): String? = capabilities["os"]
    fun getArchitecture(): String? = capabilities["arch"]
    fun getLabels(): List<String> = capabilities["labels"]?.split(",") ?: emptyList()
    
    // New capabilities support
    val languages: Set<String> get() = capabilities["languages"]?.split(",")?.toSet() ?: emptySet()
    val tools: Set<String> get() = capabilities["tools"]?.split(",")?.toSet() ?: emptySet()
    val features: Set<String> get() = capabilities["features"]?.split(",")?.toSet() ?: emptySet()

    fun toMap(): Map<String, String> {
        return capabilities
    }

    companion object {
        fun builder(): CapabilitiesBuilder {
            return CapabilitiesBuilder()
        }
    }
}

class CapabilitiesBuilder {
    private val capabilities = mutableMapOf<String, String>()

    fun os(os: String): CapabilitiesBuilder {
        capabilities["os"] = os
        return this
    }

    fun arch(arch: String): CapabilitiesBuilder {
        capabilities["arch"] = arch
        return this
    }

    fun label(label: String): CapabilitiesBuilder {
        val labels = capabilities.getOrDefault("labels", "")
        capabilities["labels"] = if (labels.isEmpty()) label else "$labels,$label"
        return this
    }

    fun maxConcurrentJobs(maxJobs: Int): CapabilitiesBuilder {
        capabilities["maxConcurrentJobs"] = maxJobs.toString()
        return this
    }
    
    fun languages(languages: Set<String>): CapabilitiesBuilder {
        capabilities["languages"] = languages.joinToString(",")
        return this
    }
    
    fun tools(tools: Set<String>): CapabilitiesBuilder {
        capabilities["tools"] = tools.joinToString(",")
        return this
    }
    
    fun features(features: Set<String>): CapabilitiesBuilder {
        capabilities["features"] = features.joinToString(",")
        return this
    }
    
    fun custom(key: String, value: String): CapabilitiesBuilder {
        capabilities[key] = value
        return this
    }

    fun build(): WorkerCapabilities {
        return WorkerCapabilities(capabilities)
    }
}



data class Worker(
    val id: WorkerId,
    val name: String,
    val capabilities: WorkerCapabilities,
    val status: WorkerStatus = WorkerStatus.IDLE,
    val sessionToken: String? = null,
    val activeJobs: Int = 0,
    val createdAt: Instant = Instant.now(),
    val lastHeartbeat: Instant = Instant.now()
) {

    fun assignJob(): Worker {
        validateCanAcceptJob()
        val newActiveJobs = activeJobs + 1
        return copy(
            activeJobs = newActiveJobs,
            status = WorkerStatus.BUSY
        )
    }

    fun completeJob(): Worker {
        require(activeJobs > 0) { "No active jobs to complete" }
        val newActiveJobs = activeJobs - 1
        return copy(
            activeJobs = newActiveJobs,
            status = if (newActiveJobs == 0) WorkerStatus.IDLE else WorkerStatus.BUSY
        )
    }

    fun canAcceptJob(requiredOS: String, requiredArch: String): Boolean {
        return capabilities.getOperatingSystem() == requiredOS &&
               capabilities.getArchitecture() == requiredArch &&
               status.isActive() &&
               activeJobs < capabilities.toMap()["maxConcurrentJobs"]?.toIntOrNull() ?: Int.MAX_VALUE
    }

    fun goOffline(): Worker {
        return copy(status = WorkerStatus.OFFLINE)
    }

    fun updateHeartbeat(): Worker  = copy(lastHeartbeat = Instant.now())

    private fun validateCanAcceptJob() {
        when {
            status == WorkerStatus.OFFLINE ->
                throw IllegalStateException("Cannot assign job to offline worker")

            activeJobs >= capabilities.toMap()["maxConcurrentJobs"]?.toIntOrNull() ?: Int.MAX_VALUE ->
                throw IllegalStateException("Worker cannot accept more jobs, max concurrent jobs limit reached")
        }
    }
}