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

class GreedyBestFitStrategyTest {
    
    private lateinit var strategy: GreedyBestFitStrategy
    
    @BeforeEach
    fun setup() {
        strategy = GreedyBestFitStrategy()
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
    fun `should select pool with lowest utilization`() = runTest {
        // Given
        val job = createTestJob()
        val pool1 = createTestPool("high-util-pool")
        val pool2 = createTestPool("low-util-pool")
        val pool3 = createTestPool("medium-util-pool")
        
        val candidates = listOf(
            PoolCandidate(pool1, createUtilization(
                totalCpu = 10.0, usedCpu = 8.0,  // 80% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 6_400_000_000  // 80% memory
            )),
            PoolCandidate(pool2, createUtilization(
                totalCpu = 10.0, usedCpu = 2.0,  // 20% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 1_600_000_000  // 20% memory
            )),
            PoolCandidate(pool3, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,  // 50% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000  // 50% memory
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("low-util-pool", pool.name) }
        )
    }
    
    @Test
    fun `should handle pools with different CPU and memory utilization`() = runTest {
        // Given
        val job = createTestJob()
        val pool1 = createTestPool("high-cpu-pool")
        val pool2 = createTestPool("high-memory-pool")
        val pool3 = createTestPool("balanced-pool")
        
        val candidates = listOf(
            PoolCandidate(pool1, createUtilization(
                totalCpu = 10.0, usedCpu = 9.0,  // 90% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 800_000_000  // 10% memory
            )),  // Average: 50%
            PoolCandidate(pool2, createUtilization(
                totalCpu = 10.0, usedCpu = 1.0,  // 10% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 7_200_000_000  // 90% memory
            )),  // Average: 50%
            PoolCandidate(pool3, createUtilization(
                totalCpu = 10.0, usedCpu = 3.0,  // 30% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 2_400_000_000  // 30% memory
            ))   // Average: 30% (should be selected)
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("balanced-pool", pool.name) }
        )
    }
    
    @Test
    fun `should handle empty pools correctly`() = runTest {
        // Given
        val job = createTestJob()
        val emptyPool = createTestPool("empty-pool")
        val usedPool = createTestPool("used-pool")
        
        val candidates = listOf(
            PoolCandidate(emptyPool, createUtilization(
                totalCpu = 10.0, usedCpu = 0.0,  // 0% utilization
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 0
            )),
            PoolCandidate(usedPool, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,  // 50% utilization
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("empty-pool", pool.name) }
        )
    }
    
    @Test
    fun `should handle pools with zero total resources`() = runTest {
        // Given
        val job = createTestJob()
        val zeroResourcePool = createTestPool("zero-pool")
        val normalPool = createTestPool("normal-pool")
        
        val candidates = listOf(
            PoolCandidate(zeroResourcePool, createUtilization(
                totalCpu = 0.0, usedCpu = 0.0,  // Division by zero case
                totalMemoryBytes = 0, usedMemoryBytes = 0
            )),
            PoolCandidate(normalPool, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> 
                // Should select zero-pool as it has 0.0 utilization score
                assertEquals("zero-pool", pool.name)
            }
        )
    }
    
    @Test
    fun `should be deterministic for equal utilizations`() = runTest {
        // Given
        val job = createTestJob()
        val pool1 = createTestPool("pool-1")
        val pool2 = createTestPool("pool-2")
        
        val candidates = listOf(
            PoolCandidate(pool1, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
            )),
            PoolCandidate(pool2, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
            ))
        )
        
        // When - Multiple calls should return same result
        val results = (1..5).map { strategy.selectPool(job, candidates) }
        
        // Then
        assertTrue(results.all { it.isRight() })
        val selectedPools = results.map { result ->
            result.fold({ "" }, { it.name })
        }
        assertTrue(selectedPools.all { it == selectedPools.first() })
    }
    
    @Test
    fun `getName should return correct strategy name`() {
        assertEquals("GreedyBestFit", strategy.getName())
    }
    
    // Helper functions
    private fun createTestJob(): Job {
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
            resourceRequirements = mapOf("cpu" to "2", "memory" to "2Gi"),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
    }
    
    private fun createTestPool(name: String): ResourcePool {
        return ResourcePool(
            id = DomainId.generate(),
            name = name,
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxWorkers = 10,
            maxJobs = null,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
    }
    
    private fun createUtilization(
        totalCpu: Double = 10.0,
        usedCpu: Double = 5.0,
        totalMemoryBytes: Long = 8_000_000_000,
        usedMemoryBytes: Long = 4_000_000_000
    ): ResourcePoolUtilization {
        return ResourcePoolUtilization(
            poolId = DomainId.generate(),
            totalCpu = totalCpu,
            usedCpu = usedCpu,
            totalMemoryBytes = totalMemoryBytes,
            usedMemoryBytes = usedMemoryBytes,
            totalDiskBytes = 100_000_000_000,
            usedDiskBytes = 20_000_000_000,
            runningJobs = 2,
            queuedJobs = 0,
            timestamp = Clock.System.now()
        )
    }
}