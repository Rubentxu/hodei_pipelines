package dev.rubentxu.hodei.scheduling.scheduler

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Standalone test for scheduler functionality without any external dependencies
 */
class StandaloneSchedulerTest {
    
    @Test
    fun `round robin strategy should distribute evenly`() = runTest {
        // Given
        val strategy = SimpleRoundRobinStrategy()
        val pools = listOf("pool-1", "pool-2", "pool-3")
        
        // When - Schedule 9 jobs
        val assignments = (1..9).map { jobNum ->
            strategy.selectPool(pools)
        }
        
        // Then - Each pool should get 3 jobs
        val counts = assignments.groupingBy { it }.eachCount()
        assertEquals(3, counts["pool-1"])
        assertEquals(3, counts["pool-2"])
        assertEquals(3, counts["pool-3"])
    }
    
    @Test
    fun `greedy strategy should select least loaded pool`() = runTest {
        // Given
        val strategy = SimpleGreedyStrategy()
        val pools = mapOf(
            "pool-1" to 0.8,  // 80% loaded
            "pool-2" to 0.2,  // 20% loaded
            "pool-3" to 0.5   // 50% loaded
        )
        
        // When
        val selected = strategy.selectPool(pools)
        
        // Then
        assertEquals("pool-2", selected)
    }
    
    @Test
    fun `least loaded strategy should consider multiple factors`() = runTest {
        // Given
        val strategy = SimpleLeastLoadedStrategy()
        val pools = listOf(
            PoolScore("pool-1", cpuUtil = 0.2, memUtil = 0.9, jobCount = 10),
            PoolScore("pool-2", cpuUtil = 0.5, memUtil = 0.5, jobCount = 5),
            PoolScore("pool-3", cpuUtil = 0.9, memUtil = 0.1, jobCount = 2)
        )
        
        // When
        val selected = strategy.selectPool(pools)
        
        // Then - pool-3 should be selected (lowest combined score)
        assertEquals("pool-3", selected)
    }
    
    @Test
    fun `bin packing strategy should prefer partially filled pools`() = runTest {
        // Given
        val strategy = SimpleBinPackingStrategy()
        val pools = mapOf(
            "pool-1" to 0.0,   // Empty
            "pool-2" to 0.5,   // Half full - ideal for packing
            "pool-3" to 0.9    // Nearly full
        )
        
        // When
        val selected = strategy.selectPool(pools)
        
        // Then
        assertEquals("pool-2", selected)
    }
}

// Simple strategy implementations for testing
class SimpleRoundRobinStrategy {
    private var counter = 0
    
    fun selectPool(pools: List<String>): String {
        val selected = pools[counter % pools.size]
        counter++
        return selected
    }
}

class SimpleGreedyStrategy {
    fun selectPool(poolUtilizations: Map<String, Double>): String {
        return poolUtilizations.minByOrNull { it.value }?.key 
            ?: throw IllegalArgumentException("No pools available")
    }
}

data class PoolScore(
    val name: String,
    val cpuUtil: Double,
    val memUtil: Double,
    val jobCount: Int
)

class SimpleLeastLoadedStrategy {
    fun selectPool(pools: List<PoolScore>): String {
        return pools.minByOrNull { pool ->
            // Simple scoring: average of CPU, memory, and normalized job count
            val cpuScore = pool.cpuUtil
            val memScore = pool.memUtil
            val jobScore = pool.jobCount / 20.0  // Normalize to 0-1 assuming max 20 jobs
            (cpuScore + memScore + jobScore) / 3.0
        }?.name ?: throw IllegalArgumentException("No pools available")
    }
}

class SimpleBinPackingStrategy {
    fun selectPool(poolUtilizations: Map<String, Double>): String {
        return poolUtilizations
            .filter { it.value < 0.8 }  // Skip nearly full pools
            .maxByOrNull { entry ->
                // Prefer pools that are partially filled (40-70% is ideal)
                when {
                    entry.value < 0.1 -> 0.0   // Penalize empty pools
                    entry.value < 0.4 -> entry.value * 2
                    entry.value < 0.7 -> 1.0   // Ideal range
                    else -> 1.0 - (entry.value - 0.7) * 2
                }
            }?.key ?: poolUtilizations.minByOrNull { it.value }?.key
            ?: throw IllegalArgumentException("No pools available")
    }
}