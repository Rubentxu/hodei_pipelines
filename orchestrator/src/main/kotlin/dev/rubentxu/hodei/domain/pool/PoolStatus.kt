package dev.rubentxu.hodei.domain.pool

import kotlinx.serialization.Serializable

@Serializable
enum class PoolStatus {
    PROVISIONING,
    ACTIVE,
    DRAINING,
    MAINTENANCE,
    ERROR;
    
    fun canTransitionTo(newStatus: PoolStatus): Boolean = when (this) {
        PROVISIONING -> newStatus in setOf(ACTIVE, ERROR)
        ACTIVE -> newStatus in setOf(DRAINING, MAINTENANCE, ERROR)
        DRAINING -> newStatus in setOf(ACTIVE, MAINTENANCE)
        MAINTENANCE -> newStatus == ACTIVE
        ERROR -> newStatus == ACTIVE
    }
    
    val canAcceptJobs: Boolean
        get() = this == ACTIVE
    
    val isHealthy: Boolean
        get() = this in setOf(ACTIVE, DRAINING)
}