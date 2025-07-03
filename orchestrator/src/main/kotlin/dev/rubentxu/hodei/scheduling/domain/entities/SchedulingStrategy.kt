package dev.rubentxu.hodei.scheduling.domain.entities

import arrow.core.Either
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization

/**
 * Strategy interface for job placement algorithms.
 * Different implementations provide various approaches to selecting
 * the optimal resource pool for job execution.
 */
interface SchedulingStrategy {
    /**
     * Select the optimal resource pool for the given job.
     * 
     * @param job The job to be scheduled
     * @param candidatePools List of pools with their current utilization that have sufficient capacity
     * @return Either an error message or the selected ResourcePool
     */
    suspend fun selectPool(
        job: Job,
        candidatePools: List<PoolCandidate>
    ): Either<String, ResourcePool>
    
    /**
     * Get the name of this scheduling strategy
     */
    fun getName(): String
}

/**
 * Represents a candidate pool with its current utilization metrics
 */
data class PoolCandidate(
    val pool: ResourcePool,
    val utilization: ResourcePoolUtilization,
    val score: Double = 0.0  // Optional score for ranking
)