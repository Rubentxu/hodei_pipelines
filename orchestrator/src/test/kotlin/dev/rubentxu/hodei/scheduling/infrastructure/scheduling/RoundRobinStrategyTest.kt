package dev.rubentxu.hodei.scheduling.infrastructure.scheduling

import arrow.core.left
import arrow.core.right
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

class RoundRobinStrategyTest {
    
    private lateinit var strategy: RoundRobinStrategy
    
    @BeforeEach
    fun setup() {
        strategy = RoundRobinStrategy()
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
    fun `should select pools in round robin order`() = runTest {
        // Given
        val job = createTestJob()
        val pool1 = createTestPool("pool-1", DomainId("1"))
        val pool2 = createTestPool("pool-2", DomainId("2"))
        val pool3 = createTestPool("pool-3", DomainId("3"))
        
        val candidates = listOf(
            PoolCandidate(pool1, createUtilization()),
            PoolCandidate(pool2, createUtilization()),
            PoolCandidate(pool3, createUtilization())
        )
        
        // When - First call
        val result1 = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result1.isRight())
        result1.fold(
            { },
            { pool -> assertEquals("pool-1", pool.name) }
        )
        
        // When - Second call
        val result2 = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result2.isRight())
        result2.fold(
            { },
            { pool -> assertEquals("pool-2", pool.name) }
        )
        
        // When - Third call
        val result3 = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result3.isRight())
        result3.fold(
            { },
            { pool -> assertEquals("pool-3", pool.name) }
        )
        
        // When - Fourth call (should wrap around)
        val result4 = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result4.isRight())
        result4.fold(
            { },
            { pool -> assertEquals("pool-1", pool.name) }
        )
    }
    
    @Test
    fun `should handle single pool`() = runTest {
        // Given
        val job = createTestJob()
        val pool = createTestPool("single-pool")
        val candidates = listOf(PoolCandidate(pool, createUtilization()))
        
        // When - Multiple calls should always return same pool
        repeat(5) {
            val result = strategy.selectPool(job, candidates)
            
            // Then
            assertTrue(result.isRight())
            result.fold(
                { },
                { selectedPool -> assertEquals("single-pool", selectedPool.name) }
            )
        }
    }
    
    @Test
    fun `should maintain consistent ordering based on pool id`() = runTest {
        // Given
        val job = createTestJob()
        val poolB = createTestPool("pool-b", DomainId("b"))
        val poolA = createTestPool("pool-a", DomainId("a"))
        val poolC = createTestPool("pool-c", DomainId("c"))
        
        // Candidates in different order
        val candidates = listOf(
            PoolCandidate(poolB, createUtilization()),
            PoolCandidate(poolA, createUtilization()),
            PoolCandidate(poolC, createUtilization())
        )
        
        // When - Should select based on sorted ID order (A, B, C)
        val result1 = strategy.selectPool(job, candidates)
        val result2 = strategy.selectPool(job, candidates)
        val result3 = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result1.isRight())
        assertTrue(result2.isRight())
        assertTrue(result3.isRight())
        
        result1.fold({ }, { pool -> assertEquals("pool-a", pool.name) })
        result2.fold({ }, { pool -> assertEquals("pool-b", pool.name) })
        result3.fold({ }, { pool -> assertEquals("pool-c", pool.name) })
    }
    
    @Test
    fun `getName should return correct strategy name`() {
        assertEquals("RoundRobin", strategy.getName())
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
    
    private fun createTestPool(
        name: String,
        id: DomainId = DomainId.generate()
    ): ResourcePool {
        return ResourcePool(
            id = id,
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
    
    private fun createUtilization(): ResourcePoolUtilization {
        return ResourcePoolUtilization(
            poolId = DomainId.generate(),
            totalCpu = 10.0,
            usedCpu = 5.0,
            totalMemoryBytes = 8_000_000_000,
            usedMemoryBytes = 4_000_000_000,
            totalDiskBytes = 100_000_000_000,
            usedDiskBytes = 20_000_000_000,
            runningJobs = 2,
            queuedJobs = 0,
            timestamp = Clock.System.now()
        )
    }
}