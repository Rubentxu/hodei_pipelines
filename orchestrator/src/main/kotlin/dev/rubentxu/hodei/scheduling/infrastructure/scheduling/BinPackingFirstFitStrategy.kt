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
 * Bin Packing First Fit scheduling strategy.
 * Selects the first pool that has enough resources, prioritizing pools that are already
 * partially filled to maximize resource consolidation and minimize fragmentation.
 * 
 * This strategy is ideal for:
 * - Reducing the number of active pools
 * - Consolidating workloads
 * - Minimizing resource fragmentation
 * - Cost optimization in cloud environments
 */
class BinPackingFirstFitStrategy : SchedulingStrategy {
    private val logger = LoggerFactory.getLogger(BinPackingFirstFitStrategy::class.java)
    
    override suspend fun selectPool(
        job: Job,
        candidatePools: List<PoolCandidate>
    ): Either<String, ResourcePool> {
        if (candidatePools.isEmpty()) {
            return "No candidate pools available".left()
        }
        
        // Sort pools by utilization (descending) to prefer already-used pools
        val sortedPools = candidatePools.sortedByDescending { candidate ->
            calculatePackingScore(candidate)
        }
        
        // Select first pool that fits (with highest packing score)
        val selected = sortedPools.firstOrNull()?.pool
            ?: return "Failed to select pool using bin packing first fit".left()
        
        val selectedCandidate = sortedPools.first()
        logger.info("Bin Packing First Fit selected pool '${selected.name}' for job ${job.id} " +
                   "(packing score: ${"%.2f".format(calculatePackingScore(selectedCandidate))})")
        
        return selected.right()
    }
    
    private fun calculatePackingScore(candidate: PoolCandidate): Double {
        val utilization = candidate.utilization
        
        // Calculate current utilization
        val cpuUtilization = if (utilization.totalCpu > 0) {
            utilization.usedCpu / utilization.totalCpu
        } else 0.0
        
        val memoryUtilization = if (utilization.totalMemoryBytes > 0) {
            utilization.usedMemoryBytes.toDouble() / utilization.totalMemoryBytes
        } else 0.0
        
        // Prefer pools that are already partially filled but not too full
        // This encourages consolidation while avoiding overloaded pools
        val avgUtilization = (cpuUtilization + memoryUtilization) / 2.0
        
        return when {
            avgUtilization < 0.1 -> avgUtilization * 0.5      // Penalize nearly empty pools
            avgUtilization < 0.7 -> avgUtilization            // Ideal range for packing
            avgUtilization < 0.9 -> avgUtilization * 0.8      // Start to penalize fuller pools
            else -> avgUtilization * 0.5                       // Heavily penalize very full pools
        }
    }
    
    override fun getName(): String = "BinPackingFirstFit"
}