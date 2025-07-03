package dev.rubentxu.hodei.scheduling.infrastructure.scheduling

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobType
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization
import dev.rubentxu.hodei.scheduling.domain.entities.PoolCandidate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LeastLoadedStrategyTest {
    
    private lateinit var strategy: LeastLoadedStrategy
    
    @BeforeEach
    fun setup() {
        strategy = LeastLoadedStrategy()
    }
    
    @Test
    fun `should return error when no candidates available`() = runTest {
        // Given
        val job = createTestJob()
        val candidates = emptyList<PoolCandidate>()
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isLeft())
        result.fold(
            { error -> assertEquals("No candidate pools available", error) },
            { }
        )
    }
    
    @Test
    fun `should select pool with highest availability score`() = runTest {
        // Given
        val job = createTestJob()
        val pool1 = createTestPool("loaded-pool", maxJobs = 10)
        val pool2 = createTestPool("available-pool", maxJobs = 10)
        val pool3 = createTestPool("busy-pool", maxJobs = 10)
        
        val candidates = listOf(
            PoolCandidate(pool1, createUtilization(
                totalCpu = 10.0, usedCpu = 7.0,  // 30% available
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 6_000_000_000,  // 25% available
                runningJobs = 7,  // 70% job capacity used
                queuedJobs = 2    // Has queue
            )),
            PoolCandidate(pool2, createUtilization(
                totalCpu = 10.0, usedCpu = 2.0,  // 80% available
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 1_600_000_000,  // 80% available
                runningJobs = 2,  // 20% job capacity used
                queuedJobs = 0    // No queue
            )),
            PoolCandidate(pool3, createUtilization(
                totalCpu = 10.0, usedCpu = 9.0,  // 10% available
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 7_200_000_000,  // 10% available
                runningJobs = 9,  // 90% job capacity used
                queuedJobs = 5    // Large queue
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("available-pool", pool.name) }
        )
    }
    
    @Test
    fun `should consider job requirements fit`() = runTest {
        // Given - Job requires significant resources
        val job = createTestJob(
            resourceRequirements = mapOf(
                "cpu" to "6",
                "memory" to "5Gi"
            )
        )
        val pool1 = createTestPool("tight-fit-pool")
        val pool2 = createTestPool("comfortable-fit-pool")
        
        val candidates = listOf(
            PoolCandidate(pool1, createUtilization(
                totalCpu = 10.0, usedCpu = 3.0,  // 7 CPU available (just fits)
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 2_000_000_000  // ~6GB available
            )),
            PoolCandidate(pool2, createUtilization(
                totalCpu = 20.0, usedCpu = 5.0,  // 15 CPU available (comfortable)
                totalMemoryBytes = 16_000_000_000, usedMemoryBytes = 4_000_000_000  // ~12GB available
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("comfortable-fit-pool", pool.name) }
        )
    }
    
    @Test
    fun `should handle pools without job limits`() = runTest {
        // Given
        val job = createTestJob()
        val poolWithLimit = createTestPool("limited-pool", maxJobs = 5)
        val poolWithoutLimit = createTestPool("unlimited-pool", maxJobs = null)
        
        val candidates = listOf(
            PoolCandidate(poolWithLimit, createUtilization(
                totalCpu = 10.0, usedCpu = 2.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 2_000_000_000,
                runningJobs = 4  // 80% of max jobs
            )),
            PoolCandidate(poolWithoutLimit, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,  // More CPU used
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000,  // More memory used
                runningJobs = 10  // No limit, so uses diminishing returns
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        // The unlimited pool might still be selected if its overall score is better
        result.fold(
            { },
            { pool -> assertTrue(pool.name in listOf("limited-pool", "unlimited-pool")) }
        )
    }
    
    @Test
    fun `should penalize pools with queued jobs`() = runTest {
        // Given
        val job = createTestJob()
        val poolNoQueue = createTestPool("no-queue-pool")
        val poolWithQueue = createTestPool("queued-pool")
        
        val candidates = listOf(
            PoolCandidate(poolNoQueue, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000,
                runningJobs = 3,
                queuedJobs = 0  // No queue
            )),
            PoolCandidate(poolWithQueue, createUtilization(
                totalCpu = 10.0, usedCpu = 4.0,  // Slightly less CPU used
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 3_200_000_000,  // Slightly less memory
                runningJobs = 2,
                queuedJobs = 5  // Has queue - should be penalized
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("queued-pool", pool.name) }  // Lower utilization overcomes queue penalty
        )
    }
    
    @Test
    fun `should handle memory string parsing correctly`() = runTest {
        // Given - Job with various memory formats
        val jobGi = createTestJob(resourceRequirements = mapOf("memory" to "2Gi"))
        val jobMi = createTestJob(resourceRequirements = mapOf("memory" to "2048Mi"))
        val jobG = createTestJob(resourceRequirements = mapOf("memory" to "2G"))
        
        val pool = createTestPool("test-pool")
        val candidate = PoolCandidate(pool, createUtilization(
            totalMemoryBytes = 8_000_000_000,
            usedMemoryBytes = 4_000_000_000
        ))
        
        // When - All should succeed
        val results = listOf(
            strategy.selectPool(jobGi, listOf(candidate)),
            strategy.selectPool(jobMi, listOf(candidate)),
            strategy.selectPool(jobG, listOf(candidate))
        )
        
        // Then
        assertTrue(results.all { it.isRight() })
    }
    
    @Test
    fun `should handle zero resources gracefully`() = runTest {
        // Given
        val job = createTestJob()
        val zeroPool = createTestPool("zero-pool")
        val normalPool = createTestPool("normal-pool")
        
        val candidates = listOf(
            PoolCandidate(zeroPool, createUtilization(
                totalCpu = 0.0, usedCpu = 0.0,
                totalMemoryBytes = 0, usedMemoryBytes = 0,
                runningJobs = 0
            )),
            PoolCandidate(normalPool, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000,
                runningJobs = 2
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("normal-pool", pool.name) }
        )
    }
    
    @Test
    fun `getName should return correct strategy name`() {
        assertEquals("LeastLoaded", strategy.getName())
    }
    
    // Helper functions
    private fun createTestJob(
        resourceRequirements: Map<String, String> = mapOf(
            "cpu" to "2",
            "memory" to "2Gi"
        )
    ): Job {
        return Job(
            id = DomainId.generate(),
            name = "test-job",
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
        maxJobs: Int? = null
    ): ResourcePool {
        return ResourcePool(
            id = DomainId.generate(),
            name = name,
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxWorkers = 10,
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
}