package dev.rubentxu.hodei.scheduling.infrastructure.scheduling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.scheduling.domain.entities.PoolCandidate
import dev.rubentxu.hodei.scheduling.domain.entities.SchedulingStrategy
import org.slf4j.LoggerFactory

/**
 * Greedy Best Fit scheduling strategy.
 * Selects the pool with the lowest resource utilization that can accommodate the job.
 * This tends to balance load across pools while keeping resources consolidated.
 */
class GreedyBestFitStrategy : SchedulingStrategy {
    private val logger = LoggerFactory.getLogger(GreedyBestFitStrategy::class.java)
    
    override suspend fun selectPool(
        job: Job,
        candidatePools: List<PoolCandidate>
    ): Either<String, ResourcePool> {
        if (candidatePools.isEmpty()) {
            return "No candidate pools available".left()
        }
        
        // Calculate utilization score for each pool
        val poolsWithScores = candidatePools.map { candidate ->
            val score = calculateUtilizationScore(candidate.utilization)
            candidate.copy(score = score)
        }
        
        // Select pool with lowest utilization (best fit)
        val selected = poolsWithScores.minByOrNull { it.score }?.pool
            ?: return "Failed to select pool using greedy best fit".left()
        
        logger.info("Greedy Best Fit selected pool '${selected.name}' for job ${job.id} " +
                   "(utilization: ${poolsWithScores.find { it.pool.id == selected.id }?.score?.let { "%.2f%%".format(it * 100) }})")
        
        return selected.right()
    }
    
    private fun calculateUtilizationScore(utilization: dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceUtilization): Double {
        val cpuUtilization = if (utilization.totalCpu > 0) {
            utilization.usedCpu / utilization.totalCpu
        } else 0.0
        
        val memoryUtilization = if (utilization.totalMemoryBytes > 0) {
            utilization.usedMemoryBytes.toDouble() / utilization.totalMemoryBytes
        } else 0.0
        
        // Average of CPU and memory utilization (0.0 = empty, 1.0 = full)
        return (cpuUtilization + memoryUtilization) / 2.0
    }
    
    override fun getName(): String = "GreedyBestFit"
}