package dev.rubentxu.hodei.templatemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Version
import dev.rubentxu.hodei.shared.domain.primitives.RetryPolicy
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class Template(
    val id: DomainId,
    val name: String,
    val description: String,
    val version: Version,
    val spec: JsonObject,
    val status: TemplateStatus,
    val parentTemplateId: DomainId? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String,
    val statistics: TemplateStatistics = TemplateStatistics()
) {
    init {
        require(name.isNotBlank()) { "Template name cannot be blank" }
        require(description.isNotBlank()) { "Template description cannot be blank" }
        require(spec.isNotEmpty()) { "Template spec cannot be empty" }
    }
    
    fun updateStatus(newStatus: TemplateStatus): Template {
        require(status.canTransitionTo(newStatus)) { 
            "Cannot transition from $status to $newStatus" 
        }
        return copy(status = newStatus, updatedAt = kotlinx.datetime.Clock.System.now())
    }
    
    fun updateStatistics(stats: TemplateStatistics): Template =
        copy(statistics = stats, updatedAt = kotlinx.datetime.Clock.System.now())
    
    fun isUsableForJobs(): Boolean = status.canCreateJobs
    
    val defaultRetryPolicy: RetryPolicy? = null // Will be implemented later
}

@Serializable
data class TemplateStatistics(
    val totalExecutions: Long = 0,
    val successfulExecutions: Long = 0,
    val failedExecutions: Long = 0,
    val averageDurationSeconds: Double = 0.0
) {
    val successRate: Double
        get() = if (totalExecutions > 0) successfulExecutions.toDouble() / totalExecutions else 0.0
}