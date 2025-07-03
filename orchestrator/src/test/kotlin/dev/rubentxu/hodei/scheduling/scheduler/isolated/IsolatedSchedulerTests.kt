package dev.rubentxu.hodei.scheduling.scheduler.isolated

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertNotNull

/**
 * Tests aislados para verificar la funcionalidad del scheduler sin dependencias externas
 */
class IsolatedSchedulerTests {
    
    // Implementaciones simplificadas para tests
    interface ISchedulingStrategy {
        fun selectPool(pools: List<PoolInfo>): PoolInfo?
        fun getName(): String
    }
    
    data class PoolInfo(
        val id: String,
        val name: String,
        val cpuUsage: Double,
        val memoryUsage: Double,
        val jobCount: Int,
        val queueDepth: Int = 0
    )
    
    // Estrategia Round Robin
    class RoundRobinStrategy : ISchedulingStrategy {
        private var counter = 0
        
        override fun selectPool(pools: List<PoolInfo>): PoolInfo? {
            if (pools.isEmpty()) return null
            val selected = pools[counter % pools.size]
            counter++
            return selected
        }
        
        override fun getName() = "RoundRobin"
    }
    
    // Estrategia Greedy
    class GreedyStrategy : ISchedulingStrategy {
        override fun selectPool(pools: List<PoolInfo>): PoolInfo? {
            return pools.minByOrNull { 
                (it.cpuUsage + it.memoryUsage) / 2.0 
            }
        }
        
        override fun getName() = "Greedy"
    }
    
    // Estrategia Least Loaded
    class LeastLoadedStrategy : ISchedulingStrategy {
        override fun selectPool(pools: List<PoolInfo>): PoolInfo? {
            return pools.minByOrNull { pool ->
                val cpuScore = pool.cpuUsage * 0.3
                val memScore = pool.memoryUsage * 0.3
                val jobScore = (pool.jobCount / 20.0) * 0.2
                val queueScore = (pool.queueDepth / 10.0) * 0.2
                cpuScore + memScore + jobScore + queueScore
            }
        }
        
        override fun getName() = "LeastLoaded"
    }
    
    // Estrategia Bin Packing
    class BinPackingStrategy : ISchedulingStrategy {
        override fun selectPool(pools: List<PoolInfo>): PoolInfo? {
            return pools
                .filter { (it.cpuUsage + it.memoryUsage) / 2.0 < 0.85 }
                .maxByOrNull { pool ->
                    val utilization = (pool.cpuUsage + pool.memoryUsage) / 2.0
                    when {
                        utilization < 0.1 -> 0.0
                        utilization < 0.4 -> utilization * 2
                        utilization < 0.7 -> 1.0
                        else -> 1.0 - (utilization - 0.7) * 2
                    }
                } ?: pools.minByOrNull { (it.cpuUsage + it.memoryUsage) / 2.0 }
        }
        
        override fun getName() = "BinPacking"
    }
    
    // Scheduler simplificado
    class SimpleScheduler(
        private val defaultStrategy: ISchedulingStrategy = LeastLoadedStrategy()
    ) {
        private val strategies = mutableMapOf<String, ISchedulingStrategy>()
        
        init {
            registerStrategy(RoundRobinStrategy())
            registerStrategy(GreedyStrategy())
            registerStrategy(LeastLoadedStrategy())
            registerStrategy(BinPackingStrategy())
        }
        
        fun registerStrategy(strategy: ISchedulingStrategy) {
            strategies[strategy.getName().lowercase()] = strategy
        }
        
        fun selectPool(pools: List<PoolInfo>, strategyName: String? = null): PoolInfo? {
            val strategy = strategyName?.let { strategies[it.lowercase()] } ?: defaultStrategy
            return strategy.selectPool(pools)
        }
        
        fun getAvailableStrategies(): Set<String> = strategies.keys
    }
    
    // Tests
    private lateinit var scheduler: SimpleScheduler
    private lateinit var testPools: List<PoolInfo>
    
    @BeforeEach
    fun setup() {
        scheduler = SimpleScheduler()
        testPools = listOf(
            PoolInfo("1", "pool-1", 0.2, 0.3, 2),
            PoolInfo("2", "pool-2", 0.7, 0.8, 8),
            PoolInfo("3", "pool-3", 0.4, 0.5, 4)
        )
    }
    
    @Test
    fun `round robin strategy should distribute evenly`() = runTest {
        val strategy = RoundRobinStrategy()
        val pools = listOf(
            PoolInfo("1", "pool-1", 0.5, 0.5, 5),
            PoolInfo("2", "pool-2", 0.5, 0.5, 5),
            PoolInfo("3", "pool-3", 0.5, 0.5, 5)
        )
        
        // Primera ronda
        assertEquals("pool-1", strategy.selectPool(pools)?.name)
        assertEquals("pool-2", strategy.selectPool(pools)?.name)
        assertEquals("pool-3", strategy.selectPool(pools)?.name)
        
        // Segunda ronda
        assertEquals("pool-1", strategy.selectPool(pools)?.name)
        assertEquals("pool-2", strategy.selectPool(pools)?.name)
        assertEquals("pool-3", strategy.selectPool(pools)?.name)
    }
    
    @Test
    fun `greedy strategy should select least utilized pool`() = runTest {
        val selected = scheduler.selectPool(testPools, "greedy")
        assertNotNull(selected)
        assertEquals("pool-1", selected.name) // Pool 1 tiene menor utilización (0.2 + 0.3) / 2 = 0.25
    }
    
    @Test
    fun `least loaded strategy should consider multiple factors`() = runTest {
        val pools = listOf(
            PoolInfo("1", "pool-1", 0.1, 0.9, 15, 5),  // Alta memoria y jobs
            PoolInfo("2", "pool-2", 0.5, 0.5, 5, 0),   // Balanceado
            PoolInfo("3", "pool-3", 0.9, 0.1, 2, 10)   // Alto CPU y queue
        )
        
        val selected = scheduler.selectPool(pools, "leastloaded")
        assertNotNull(selected)
        assertEquals("pool-2", selected.name) // Pool 2 está más balanceado
    }
    
    @Test
    fun `bin packing strategy should prefer partially filled pools`() = runTest {
        val pools = listOf(
            PoolInfo("1", "empty", 0.05, 0.05, 0),      // Casi vacío
            PoolInfo("2", "partial", 0.4, 0.5, 5),      // Parcialmente lleno
            PoolInfo("3", "almost-full", 0.85, 0.9, 18) // Casi lleno
        )
        
        val selected = scheduler.selectPool(pools, "binpacking")
        assertNotNull(selected)
        assertEquals("partial", selected.name) // Prefiere el parcialmente lleno
    }
    
    @Test
    fun `scheduler should use default strategy when none specified`() = runTest {
        val selected1 = scheduler.selectPool(testPools)
        val selected2 = scheduler.selectPool(testPools, "leastloaded")
        
        assertNotNull(selected1)
        assertNotNull(selected2)
        assertEquals(selected1.name, selected2.name) // Ambos deben usar LeastLoaded
    }
    
    @Test
    fun `scheduler should handle empty pool list`() = runTest {
        val selected = scheduler.selectPool(emptyList())
        assertEquals(null, selected)
    }
    
    @Test
    fun `all strategies should handle single pool`() = runTest {
        val singlePool = listOf(PoolInfo("1", "only-pool", 0.5, 0.5, 5))
        
        for (strategy in scheduler.getAvailableStrategies()) {
            val selected = scheduler.selectPool(singlePool, strategy)
            assertNotNull(selected, "Strategy $strategy should select the single pool")
            assertEquals("only-pool", selected.name)
        }
    }
    
    @Test
    fun `strategies should respect high utilization thresholds`() = runTest {
        val pools = listOf(
            PoolInfo("1", "overloaded-1", 0.95, 0.98, 50),
            PoolInfo("2", "overloaded-2", 0.92, 0.94, 45),
            PoolInfo("3", "slightly-better", 0.88, 0.90, 40)
        )
        
        // Todas las estrategias deberían elegir el menos cargado
        val strategies = listOf("greedy", "leastloaded", "binpacking")
        for (strategy in strategies) {
            val selected = scheduler.selectPool(pools, strategy)
            assertNotNull(selected, "Strategy $strategy should select a pool")
            assertEquals("slightly-better", selected.name, 
                "Strategy $strategy should select the least loaded pool")
        }
    }
    
    @Test
    fun `round robin should maintain state across calls`() = runTest {
        val pools = listOf(
            PoolInfo("1", "A", 0.5, 0.5, 5),
            PoolInfo("2", "B", 0.5, 0.5, 5)
        )
        
        val selections = mutableListOf<String>()
        repeat(6) {
            scheduler.selectPool(pools, "roundrobin")?.let { 
                selections.add(it.name) 
            }
        }
        
        assertEquals(listOf("A", "B", "A", "B", "A", "B"), selections)
    }
    
    @Test
    fun `strategies should be case insensitive`() = runTest {
        val pool1 = scheduler.selectPool(testPools, "ROUNDROBIN")
        val pool2 = scheduler.selectPool(testPools, "roundrobin")
        val pool3 = scheduler.selectPool(testPools, "RoundRobin")
        
        assertNotNull(pool1)
        assertNotNull(pool2)
        assertNotNull(pool3)
    }
}

/**
 * Test de rendimiento para las estrategias
 */
class SchedulerPerformanceTest {
    
    @Test
    fun `strategies should handle large pool lists efficiently`() = runTest {
        val largePools = (1..100).map { i ->
            IsolatedSchedulerTests.PoolInfo(
                id = i.toString(),
                name = "pool-$i",
                cpuUsage = (i % 10) / 10.0,
                memoryUsage = ((i + 5) % 10) / 10.0,
                jobCount = i % 20,
                queueDepth = i % 5
            )
        }
        
        val scheduler = IsolatedSchedulerTests.SimpleScheduler()
        val strategies = listOf("roundrobin", "greedy", "leastloaded", "binpacking")
        
        for (strategy in strategies) {
            val startTime = System.currentTimeMillis()
            repeat(1000) {
                val selected = scheduler.selectPool(largePools, strategy)
                assertNotNull(selected, "Strategy $strategy should select a pool")
            }
            val duration = System.currentTimeMillis() - startTime
            assertTrue(duration < 1000, "Strategy $strategy took too long: ${duration}ms")
        }
    }
}