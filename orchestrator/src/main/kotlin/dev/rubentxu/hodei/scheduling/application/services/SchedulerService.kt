package dev.rubentxu.hodei.scheduling.application.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IResourceMonitor
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization
import dev.rubentxu.hodei.scheduling.domain.entities.PoolCandidate
import dev.rubentxu.hodei.scheduling.domain.entities.SchedulingStrategy
import dev.rubentxu.hodei.scheduling.infrastructure.scheduling.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

/**
 * Pure scheduling service responsible only for placement decisions.
 * This service is stateless and focuses solely on WHERE to run jobs,
 * not WHEN to run them.
 * 
 * Supports multiple scheduling strategies through the Strategy pattern.
 */
class SchedulerService(
    private val resourcePoolRepository: ResourcePoolRepository,
    private val resourceMonitors: Map<String, IResourceMonitor>,
    private val defaultStrategy: SchedulingStrategy = LeastLoadedStrategy()
) {
    private val logger = LoggerFactory.getLogger(SchedulerService::class.java)
    
    // Available scheduling strategies
    private val strategies = mapOf(
        "roundrobin" to RoundRobinStrategy(),
        "greedy" to GreedyBestFitStrategy(),
        "leastloaded" to LeastLoadedStrategy(),
        "binpacking" to BinPackingFirstFitStrategy(),
        "default" to defaultStrategy
    )

    /**
     * Finds the optimal resource pool for job execution based on current resource availability
     * and job requirements, using the specified scheduling strategy.
     * 
     * @param job The job to find placement for
     * @param strategyName Optional strategy name. If not specified, uses default strategy
     * @return Either an error message or the selected ResourcePool
     */
    suspend fun findPlacement(
        job: Job, 
        strategyName: String? = null
    ): Either<String, ResourcePool> {
        logger.debug("Finding placement for job ${job.id} using strategy: ${strategyName ?: "default"}")

        // Get all active resource pools
        val activePools = resourcePoolRepository.findActive().fold(
            { return "Failed to fetch active resource pools".left() },
            { pools -> 
                if (pools.isEmpty()) {
                    logger.warn("No active resource pools available")
                    return "No active resource pools available".left()
                }
                pools
            }
        )

        // If job specifies a pool, validate it's active and available
        job.poolId?.let { requestedPoolId ->
            val requestedPool = activePools.find { it.id == requestedPoolId }
            if (requestedPool == null) {
                return "Requested pool ${requestedPoolId.value} is not active or does not exist".left()
            }
            
            // Check if requested pool has capacity
            val hasCapacity = checkPoolCapacity(requestedPool, job).fold(
                { false },
                { it }
            )
            
            return if (hasCapacity) {
                logger.info("Using requested pool ${requestedPool.name} for job ${job.id}")
                requestedPool.right()
            } else {
                "Requested pool ${requestedPool.name} does not have sufficient capacity".left()
            }
        }

        // Select scheduling strategy
        val strategy = strategyName?.let { strategies[it.lowercase()] } ?: defaultStrategy
        logger.debug("Using ${strategy.getName()} scheduling strategy")

        // Find best pool using selected strategy
        return findOptimalPool(activePools, job, strategy)
    }

    private suspend fun findOptimalPool(
        pools: List<ResourcePool>,
        job: Job,
        strategy: SchedulingStrategy
    ): Either<String, ResourcePool> = coroutineScope {
        logger.debug("Evaluating ${pools.size} pools for job ${job.id}")

        // Collect utilization data for all pools in parallel
        val poolUtilizations = pools.map { pool ->
            async {
                val utilizationResult = getPoolUtilization(pool)
                Triple(pool, utilizationResult, utilizationResult.fold({ null }, { it }))
            }
        }.map { it.await() }

        // Filter pools with sufficient capacity and create candidates
        val candidatePools = poolUtilizations.mapNotNull { (pool, utilizationResult, utilization) ->
            utilizationResult.fold(
                { 
                    logger.warn("Failed to get utilization for pool ${pool.name}: $it")
                    null
                },
                { util ->
                    if (hasEnoughResources(pool, util, job)) {
                        PoolCandidate(pool, util)
                    } else {
                        logger.debug("Pool ${pool.name} does not have enough resources")
                        null
                    }
                }
            )
        }

        if (candidatePools.isEmpty()) {
            return@coroutineScope "No resource pool has sufficient capacity for job ${job.id}".left()
        }

        // Use strategy to select the best pool
        strategy.selectPool(job, candidatePools)
    }

    private suspend fun checkPoolCapacity(
        pool: ResourcePool,
        job: Job
    ): Either<String, Boolean> {
        val utilization = getPoolUtilization(pool).fold(
            { return it.left() },
            { it }
        )
        
        return hasEnoughResources(pool, utilization, job).right()
    }

    private suspend fun getPoolUtilization(pool: ResourcePool): Either<String, ResourcePoolUtilization> {
        val monitor = resourceMonitors[pool.type] 
            ?: return "No resource monitor available for pool type ${pool.type}".left()

        return try {
            monitor.getUtilization(pool.id).fold(
                { "Failed to get utilization: $it".left() },
                { it.right() }
            )
        } catch (e: Exception) {
            logger.error("Error getting utilization for pool ${pool.id}", e)
            "Error getting pool utilization: ${e.message}".left()
        }
    }

    private fun hasEnoughResources(
        pool: ResourcePool,
        utilization: ResourcePoolUtilization,
        job: Job
    ): Boolean {
        val jobRequirements = job.resourceRequirements
        
        // Check CPU requirements
        val cpuRequired = jobRequirements["cpu"]?.toDoubleOrNull() ?: 0.0
        val cpuAvailable = utilization.totalCpu - utilization.usedCpu
        if (cpuRequired > cpuAvailable) {
            logger.debug("Insufficient CPU in pool ${pool.name}: required=$cpuRequired, available=$cpuAvailable")
            return false
        }

        // Check memory requirements
        val memoryRequired = parseMemoryString(jobRequirements["memory"] ?: "0")
        val memoryAvailable = utilization.totalMemoryBytes - utilization.usedMemoryBytes
        if (memoryRequired > memoryAvailable) {
            logger.debug("Insufficient memory in pool ${pool.name}: required=$memoryRequired, available=$memoryAvailable")
            return false
        }

        // Check if pool has capacity for another job
        if (pool.maxJobs != null && utilization.runningJobs >= pool.maxJobs!!) {
            logger.debug("Pool ${pool.name} at maximum job capacity: ${utilization.runningJobs}/${pool.maxJobs}")
            return false
        }

        return true
    }

    private fun parseMemoryString(memory: String): Long {
        return try {
            when {
                memory.endsWith("Gi") -> memory.removeSuffix("Gi").toLong() * 1024 * 1024 * 1024
                memory.endsWith("Mi") -> memory.removeSuffix("Mi").toLong() * 1024 * 1024
                memory.endsWith("Ki") -> memory.removeSuffix("Ki").toLong() * 1024
                memory.endsWith("G") -> memory.removeSuffix("G").toLong() * 1000 * 1000 * 1000
                memory.endsWith("M") -> memory.removeSuffix("M").toLong() * 1000 * 1000
                memory.endsWith("K") -> memory.removeSuffix("K").toLong() * 1000
                else -> memory.toLong()
            }
        } catch (e: NumberFormatException) {
            logger.warn("Failed to parse memory string: $memory, defaulting to 0")
            0L
        }
    }
    
    /**
     * Get list of available scheduling strategies
     */
    fun getAvailableStrategies(): List<String> {
        return strategies.keys.toList()
    }
}