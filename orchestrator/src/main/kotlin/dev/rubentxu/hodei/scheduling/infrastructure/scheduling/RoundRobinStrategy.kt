package dev.rubentxu.hodei.scheduling.infrastructure.scheduling

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.scheduling.domain.entities.PoolCandidate
import dev.rubentxu.hodei.scheduling.domain.entities.SchedulingStrategy
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Round Robin scheduling strategy.
 * Distributes jobs evenly across all available pools in a circular fashion.
 * Simple and ensures fair distribution of load.
 */
class RoundRobinStrategy : SchedulingStrategy {
    private val logger = LoggerFactory.getLogger(RoundRobinStrategy::class.java)
    private val counter = AtomicInteger(0)
    
    override suspend fun selectPool(
        job: Job,
        candidatePools: List<PoolCandidate>
    ): Either<String, ResourcePool> {
        if (candidatePools.isEmpty()) {
            return "No candidate pools available".left()
        }
        
        // Sort pools by ID to ensure consistent ordering
        val sortedPools = candidatePools.sortedBy { it.pool.id.value }
        
        // Get next index using atomic counter
        val index = counter.getAndIncrement() % sortedPools.size
        val selected = sortedPools[index].pool
        
        logger.info("Round Robin selected pool '${selected.name}' (index: $index) for job ${job.id}")
        return selected.right()
    }
    
    override fun getName(): String = "RoundRobin"
}