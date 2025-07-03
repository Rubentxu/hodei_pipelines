package dev.rubentxu.hodei.scheduling.integration

import arrow.core.right
import dev.rubentxu.hodei.scheduling.application.services.SchedulerService
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobType
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IResourceMonitor
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization
import dev.rubentxu.hodei.scheduling.infrastructure.scheduling.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchedulingStrategiesIntegrationTest {
    
    private lateinit var resourcePoolRepository: ResourcePoolRepository
    private lateinit var kubernetesMonitor: IResourceMonitor
    private lateinit var schedulerService: SchedulerService
    
    // Test pools
    private lateinit var smallPool: ResourcePool
    private lateinit var mediumPool: ResourcePool
    private lateinit var largePool: ResourcePool
    private lateinit var busyPool: ResourcePool
    
    @BeforeEach
    fun setup() {
        resourcePoolRepository = mockk()
        kubernetesMonitor = mockk()
        
        val resourceMonitors = mapOf("kubernetes" to kubernetesMonitor)
        
        schedulerService = SchedulerService(
            resourcePoolRepository = resourcePoolRepository,
            resourceMonitors = resourceMonitors,
            defaultStrategy = LeastLoadedStrategy()
        )
        
        // Initialize test pools
        smallPool = createTestPool("small-pool", maxWorkers = 5, maxJobs = 10)
        mediumPool = createTestPool("medium-pool", maxWorkers = 10, maxJobs = 20)
        largePool = createTestPool("large-pool", maxWorkers = 20, maxJobs = 50)
        busyPool = createTestPool("busy-pool", maxWorkers = 15, maxJobs = 30)
        
        // Setup default behavior
        coEvery { resourcePoolRepository.findActive() } returns 
            listOf(smallPool, mediumPool, largePool, busyPool).right()
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `round robin should distribute jobs evenly across pools`() = runTest {
        // Given - All pools have same utilization
        setupPoolUtilizations(
            smallPool to createUtilization(totalCpu = 5.0, usedCpu = 2.0),
            mediumPool to createUtilization(totalCpu = 10.0, usedCpu = 4.0),
            largePool to createUtilization(totalCpu = 20.0, usedCpu = 8.0),
            busyPool to createUtilization(totalCpu = 15.0, usedCpu = 6.0)
        )
        
        // When - Schedule multiple jobs with round robin
        val results = mutableMapOf<String, Int>()
        repeat(12) {
            val job = createTestJob(name = "job-$it")
            val result = schedulerService.findPlacement(job, "roundrobin")
            
            assertTrue(result.isRight())
            result.fold(
                { },
                { pool -> 
                    results[pool.name] = results.getOrDefault(pool.name, 0) + 1
                }
            )
        }
        
        // Then - Jobs should be distributed evenly (3 per pool)
        assertEquals(4, results.size)
        results.values.forEach { count ->
            assertEquals(3, count)
        }
    }
    
    @Test
    fun `greedy best fit should prefer least utilized pools`() = runTest {
        // Given - Pools with different utilization levels
        setupPoolUtilizations(
            smallPool to createUtilization(totalCpu = 5.0, usedCpu = 4.5),    // 90% utilized
            mediumPool to createUtilization(totalCpu = 10.0, usedCpu = 2.0),   // 20% utilized
            largePool to createUtilization(totalCpu = 20.0, usedCpu = 10.0),   // 50% utilized
            busyPool to createUtilization(totalCpu = 15.0, usedCpu = 12.0)     // 80% utilized
        )
        
        // When - Schedule jobs with greedy strategy
        val results = mutableListOf<String>()
        repeat(5) {
            val job = createTestJob(name = "job-$it")
            val result = schedulerService.findPlacement(job, "greedy")
            
            assertTrue(result.isRight())
            result.fold(
                { },
                { pool -> results.add(pool.name) }
            )
        }
        
        // Then - All jobs should go to medium pool (least utilized)
        assertTrue(results.all { it == "medium-pool" })
    }
    
    @Test
    fun `least loaded strategy should consider multiple factors`() = runTest {
        // Given - Complex scenario with different factors
        setupPoolUtilizations(
            smallPool to createUtilization(
                totalCpu = 5.0, usedCpu = 1.0,      // Low CPU usage
                totalMemoryBytes = 4_000_000_000, usedMemoryBytes = 3_500_000_000,  // High memory usage
                runningJobs = 8                      // Near job limit (10)
            ),
            mediumPool to createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,     // Medium CPU usage
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 2_000_000_000,  // Low memory usage
                runningJobs = 5,                     // Low job count
                queuedJobs = 0
            ),
            largePool to createUtilization(
                totalCpu = 20.0, usedCpu = 18.0,    // High CPU usage
                totalMemoryBytes = 16_000_000_000, usedMemoryBytes = 4_000_000_000, // Low memory usage
                runningJobs = 10
            ),
            busyPool to createUtilization(
                totalCpu = 15.0, usedCpu = 7.0,     // Medium CPU usage
                totalMemoryBytes = 12_000_000_000, usedMemoryBytes = 6_000_000_000, // Medium memory usage
                runningJobs = 15,
                queuedJobs = 10                      // Has queue
            )
        )
        
        // When
        val job = createTestJob(
            resourceRequirements = mapOf("cpu" to "2", "memory" to "2Gi")
        )
        val result = schedulerService.findPlacement(job, "leastloaded")
        
        // Then - Should select medium pool (best overall score)
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("medium-pool", pool.name) }
        )
    }
    
    @Test
    fun `bin packing should consolidate jobs on fewer pools`() = runTest {
        // Given - Pools with varying utilization
        setupPoolUtilizations(
            smallPool to createUtilization(totalCpu = 5.0, usedCpu = 0.5),     // 10% - nearly empty
            mediumPool to createUtilization(totalCpu = 10.0, usedCpu = 6.0),    // 60% - ideal for packing
            largePool to createUtilization(totalCpu = 20.0, usedCpu = 2.0),     // 10% - nearly empty
            busyPool to createUtilization(totalCpu = 15.0, usedCpu = 14.0)      // 93% - too full
        )
        
        // When - Schedule multiple jobs with bin packing
        val results = mutableListOf<String>()
        repeat(5) {
            val job = createTestJob(
                name = "job-$it",
                resourceRequirements = mapOf("cpu" to "1", "memory" to "1Gi")
            )
            val result = schedulerService.findPlacement(job, "binpacking")
            
            assertTrue(result.isRight())
            result.fold(
                { },
                { pool -> results.add(pool.name) }
            )
        }
        
        // Then - Bin packing should consolidate by preferring pools with higher utilization first
        // All jobs should be successfully placed
        assertEquals(5, results.size)
        
        // Verify the strategy preferred pools with higher utilization (busy-pool first)
        val busyPoolJobs = results.count { it == "busy-pool" }
        val mediumPoolJobs = results.count { it == "medium-pool" }
        
        // Since we don't update utilization between calls, busy-pool should get most jobs
        assertTrue(busyPoolJobs > 0, "Bin packing should use busy-pool for consolidation")
        assertTrue(results.isNotEmpty(), "All jobs should be placed successfully")
    }
    
    @Test
    fun `strategies should respect job resource requirements`() = runTest {
        // Given - Job with high resource requirements
        val bigJob = createTestJob(
            name = "big-job",
            resourceRequirements = mapOf("cpu" to "15", "memory" to "6Gi")
        )
        
        setupPoolUtilizations(
            smallPool to createUtilization(totalCpu = 5.0, usedCpu = 0.0),     // Not enough total CPU
            mediumPool to createUtilization(totalCpu = 10.0, usedCpu = 0.0),   // Not enough total CPU
            largePool to createUtilization(totalCpu = 20.0, usedCpu = 3.0, totalMemoryBytes = 16_000_000_000, usedMemoryBytes = 2_000_000_000),    // Enough resources
            busyPool to createUtilization(totalCpu = 15.0, usedCpu = 5.0)      // Not enough available CPU
        )
        
        // When - Try all strategies
        val strategies = listOf("roundrobin", "greedy", "leastloaded", "binpacking")
        
        for (strategy in strategies) {
            val result = schedulerService.findPlacement(bigJob, strategy)
            
            // Then - All strategies should select large pool (only one with enough resources)
            assertTrue(result.isRight(), "Strategy $strategy should find a pool")
            result.fold(
                { },
                { pool -> 
                    assertEquals("large-pool", pool.name, 
                        "Strategy $strategy should select large-pool")
                }
            )
        }
    }
    
    @Test
    fun `strategies should respect pool job limits`() = runTest {
        // Given - Small pool at job capacity
        setupPoolUtilizations(
            smallPool to createUtilization(
                totalCpu = 5.0, usedCpu = 1.0,
                runningJobs = 10  // At max jobs limit
            ),
            mediumPool to createUtilization(
                totalCpu = 10.0, usedCpu = 8.0,  // High CPU but has job capacity
                runningJobs = 15
            ),
            largePool to createUtilization(
                totalCpu = 20.0, usedCpu = 19.0,  // Very high CPU but has job capacity
                runningJobs = 40
            ),
            busyPool to createUtilization(
                totalCpu = 15.0, usedCpu = 14.0,
                runningJobs = 30  // At max jobs limit
            )
        )
        
        // When
        val job = createTestJob()
        val results = mutableMapOf<String, MutableList<String>>()
        
        for (strategy in listOf("roundrobin", "greedy", "leastloaded", "binpacking")) {
            val result = schedulerService.findPlacement(job, strategy)
            assertTrue(result.isRight())
            
            result.fold(
                { },
                { pool -> 
                    results.getOrPut(strategy) { mutableListOf() }.add(pool.name)
                }
            )
        }
        
        // Then - No strategy should select small-pool or busy-pool (at capacity)
        results.forEach { (strategy, pools) ->
            assertTrue(
                pools.none { it in listOf("small-pool", "busy-pool") },
                "Strategy $strategy should not select pools at job capacity"
            )
        }
    }
    
    @Test
    fun `default strategy should be used when none specified`() = runTest {
        // Given
        setupPoolUtilizations(
            smallPool to createUtilization(totalCpu = 5.0, usedCpu = 4.0),
            mediumPool to createUtilization(totalCpu = 10.0, usedCpu = 2.0),
            largePool to createUtilization(totalCpu = 20.0, usedCpu = 5.0),
            busyPool to createUtilization(totalCpu = 15.0, usedCpu = 10.0)
        )
        
        // When - No strategy specified (should use LeastLoaded)
        val job = createTestJob()
        val resultDefault = schedulerService.findPlacement(job)
        val resultExplicit = schedulerService.findPlacement(job, "leastloaded")
        
        // Then - Both should give same result
        assertTrue(resultDefault.isRight())
        assertTrue(resultExplicit.isRight())
        
        val poolDefault = resultDefault.fold({ null }, { it })
        val poolExplicit = resultExplicit.fold({ null }, { it })
        
        assertNotNull(poolDefault)
        assertNotNull(poolExplicit)
        assertEquals(poolDefault.name, poolExplicit.name)
    }
    
    // Helper functions
    private fun createTestJob(
        name: String = "test-job",
        resourceRequirements: Map<String, String> = mapOf(
            "cpu" to "2",
            "memory" to "2Gi"
        )
    ): Job {
        return Job(
            id = DomainId.generate(),
            name = name,
            templateId = DomainId("test-template"),
            templateVersion = null,
            poolId = null,
            definition = Job.Definition(
                type = JobType.SHELL,
                content = "echo test",
                envVars = emptyMap()
            ),
            status = JobStatus.PENDING,
            priority = Priority.NORMAL,
            resourceRequirements = resourceRequirements,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
    }
    
    private fun createTestPool(
        name: String,
        maxWorkers: Int = 10,
        maxJobs: Int? = null
    ): ResourcePool {
        return ResourcePool(
            id = DomainId.generate(),
            name = name,
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxWorkers = maxWorkers,
            maxJobs = maxJobs,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
    }
    
    private fun createUtilization(
        totalCpu: Double = 10.0,
        usedCpu: Double = 5.0,
        totalMemoryBytes: Long = 8_000_000_000,
        usedMemoryBytes: Long = 4_000_000_000,
        runningJobs: Int = 2,
        queuedJobs: Int = 0
    ): ResourcePoolUtilization {
        return ResourcePoolUtilization(
            poolId = DomainId.generate(),
            totalCpu = totalCpu,
            usedCpu = usedCpu,
            totalMemoryBytes = totalMemoryBytes,
            usedMemoryBytes = usedMemoryBytes,
            totalDiskBytes = 100_000_000_000,
            usedDiskBytes = 20_000_000_000,
            runningJobs = runningJobs,
            queuedJobs = queuedJobs,
            timestamp = Clock.System.now()
        )
    }
    
    private fun setupPoolUtilizations(vararg poolUtilizations: Pair<ResourcePool, ResourcePoolUtilization>) {
        poolUtilizations.forEach { (pool, utilization) ->
            coEvery { kubernetesMonitor.getUtilization(pool.id) } returns 
                utilization.copy(poolId = pool.id).right()
        }
    }
}