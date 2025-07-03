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
 * Least Loaded scheduling strategy.
 * Selects the pool with the most available resources, considering multiple metrics:
 * - CPU availability
 * - Memory availability  
 * - Number of running jobs
 * - Job queue depth (if applicable)
 * 
 * This strategy aims to maximize resource availability for future jobs.
 */
class LeastLoadedStrategy : SchedulingStrategy {
    private val logger = LoggerFactory.getLogger(LeastLoadedStrategy::class.java)
    
    override suspend fun selectPool(
        job: Job,
        candidatePools: List<PoolCandidate>
    ): Either<String, ResourcePool> {
        if (candidatePools.isEmpty()) {
            return "No candidate pools available".left()
        }
        
        // Calculate comprehensive load score for each pool
        val poolsWithScores = candidatePools.map { candidate ->
            val score = calculateLoadScore(candidate, job)
            candidate.copy(score = score)
        }
        
        // Select pool with highest score (most available resources)
        val selected = poolsWithScores.maxByOrNull { it.score }?.pool
            ?: return "Failed to select pool using least loaded strategy".left()
        
        val selectedScore = poolsWithScores.find { it.pool.id == selected.id }?.score ?: 0.0
        logger.info("Least Loaded selected pool '${selected.name}' for job ${job.id} " +
                   "(availability score: ${"%.2f".format(selectedScore)})")
        
        return selected.right()
    }
    
    private fun calculateLoadScore(candidate: PoolCandidate, job: Job): Double {
        val utilization = candidate.utilization
        val pool = candidate.pool
        
        // Calculate available resources as percentages
        val cpuAvailability = if (utilization.totalCpu > 0) {
            (utilization.totalCpu - utilization.usedCpu) / utilization.totalCpu
        } else 0.0
        
        val memoryAvailability = if (utilization.totalMemoryBytes > 0) {
            (utilization.totalMemoryBytes - utilization.usedMemoryBytes).toDouble() / utilization.totalMemoryBytes
        } else 0.0
        
        // Consider job capacity
        val jobCapacityScore = if (pool.maxJobs != null && pool.maxJobs!! > 0) {
            1.0 - (utilization.runningJobs.toDouble() / pool.maxJobs!!)
        } else if (utilization.runningJobs > 0) {
            // If no max jobs limit, use a diminishing returns function
            1.0 / (1.0 + utilization.runningJobs * 0.1)
        } else {
            1.0
        }
        
        // Consider queue depth if available
        val queueScore = if (utilization.queuedJobs > 0) {
            1.0 / (1.0 + utilization.queuedJobs * 0.2)  // Penalize queued jobs
        } else {
            1.0
        }
        
        // Check if job requirements fit comfortably
        val jobRequirements = job.resourceRequirements
        val cpuRequired = jobRequirements["cpu"]?.toDoubleOrNull() ?: 0.0
        val memoryRequired = parseMemoryToGiB(jobRequirements["memory"] ?: "0")
        
        val cpuFitScore = if (utilization.totalCpu > 0 && cpuRequired > 0) {
            val availableCpu = utilization.totalCpu - utilization.usedCpu
            minOf(availableCpu / cpuRequired, 1.0)  // How many times the job would fit
        } else {
            1.0
        }
        
        val memoryFitScore = if (utilization.totalMemoryBytes > 0 && memoryRequired > 0) {
            val availableMemoryGiB = (utilization.totalMemoryBytes - utilization.usedMemoryBytes) / (1024.0 * 1024.0 * 1024.0)
            minOf(availableMemoryGiB / memoryRequired, 1.0)
        } else {
            1.0
        }
        
        // Weighted combination of all factors
        return (cpuAvailability * 0.25 +           // 25% weight on CPU availability
                memoryAvailability * 0.25 +         // 25% weight on memory availability
                jobCapacityScore * 0.20 +           // 20% weight on job capacity
                queueScore * 0.10 +                 // 10% weight on queue depth
                cpuFitScore * 0.10 +                // 10% weight on CPU fit
                memoryFitScore * 0.10)              // 10% weight on memory fit
    }
    
    private fun parseMemoryToGiB(memory: String): Double {
        return try {
            when {
                memory.endsWith("Gi") -> memory.removeSuffix("Gi").toDouble()
                memory.endsWith("Mi") -> memory.removeSuffix("Mi").toDouble() / 1024
                memory.endsWith("Ki") -> memory.removeSuffix("Ki").toDouble() / (1024 * 1024)
                memory.endsWith("G") -> memory.removeSuffix("G").toDouble() * 1000 / (1024 * 1024 * 1024)
                memory.endsWith("M") -> memory.removeSuffix("M").toDouble() * 1000 / (1024 * 1024 * 1024 * 1000)
                memory.endsWith("K") -> memory.removeSuffix("K").toDouble() * 1000 / (1024 * 1024 * 1024 * 1000 * 1000)
                else -> memory.toDouble() / (1024 * 1024 * 1024)  // Assume bytes
            }
        } catch (e: NumberFormatException) {
            0.0
        }
    }
    
    override fun getName(): String = "LeastLoaded"
}