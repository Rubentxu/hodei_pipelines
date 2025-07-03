package dev.rubentxu.hodei.templatemanagement.domain.entities

import kotlinx.serialization.Serializable

@Serializable
enum class TemplateStatus {
    DRAFT,
    VALIDATING,
    PUBLISHED,
    DEPRECATED,
    ARCHIVED;
    
    fun canTransitionTo(newStatus: TemplateStatus): Boolean = when (this) {
        DRAFT -> newStatus in setOf(VALIDATING, ARCHIVED)
        VALIDATING -> newStatus in setOf(PUBLISHED, DRAFT, ARCHIVED)
        PUBLISHED -> newStatus in setOf(DEPRECATED, ARCHIVED)
        DEPRECATED -> newStatus in setOf(ARCHIVED, PUBLISHED)
        ARCHIVED -> false
    }
    
    val canCreateJobs: Boolean
        get() = this == PUBLISHED
    
    val isActive: Boolean
        get() = this in setOf(DRAFT, VALIDATING, PUBLISHED, DEPRECATED)
}