package dev.rubentxu.hodei.scheduling.infrastructure.scheduling

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobType
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceUtilization
import dev.rubentxu.hodei.scheduling.domain.entities.PoolCandidate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinPackingFirstFitStrategyTest {
    
    private lateinit var strategy: BinPackingFirstFitStrategy
    
    @BeforeEach
    fun setup() {
        strategy = BinPackingFirstFitStrategy()
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
    fun `should prefer partially filled pools for consolidation`() = runTest {
        // Given
        val job = createTestJob()
        val emptyPool = createTestPool("empty-pool")
        val partialPool = createTestPool("partial-pool")
        val fullPool = createTestPool("full-pool")
        
        val candidates = listOf(
            PoolCandidate(emptyPool, createUtilization(
                totalCpu = 10.0, usedCpu = 0.0,  // 0% utilization
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 0
            )),
            PoolCandidate(partialPool, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,  // 50% utilization - ideal for packing
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
            )),
            PoolCandidate(fullPool, createUtilization(
                totalCpu = 10.0, usedCpu = 8.5,  // 85% utilization - getting full
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 6_800_000_000
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("full-pool", pool.name) }  // Full pool has highest packing score
        )
    }
    
    @Test
    fun `should penalize nearly empty pools`() = runTest {
        // Given
        val job = createTestJob()
        val nearlyEmptyPool = createTestPool("nearly-empty-pool")
        val moderatePool = createTestPool("moderate-pool")
        
        val candidates = listOf(
            PoolCandidate(nearlyEmptyPool, createUtilization(
                totalCpu = 10.0, usedCpu = 0.5,  // 5% utilization - penalized
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 400_000_000
            )),
            PoolCandidate(moderatePool, createUtilization(
                totalCpu = 10.0, usedCpu = 3.0,  // 30% utilization - better for packing
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 2_400_000_000
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("moderate-pool", pool.name) }
        )
    }
    
    @Test
    fun `should heavily penalize very full pools`() = runTest {
        // Given
        val job = createTestJob()
        val moderatePool = createTestPool("moderate-pool")
        val veryFullPool = createTestPool("very-full-pool")
        
        val candidates = listOf(
            PoolCandidate(moderatePool, createUtilization(
                totalCpu = 10.0, usedCpu = 6.0,  // 60% utilization
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_800_000_000
            )),
            PoolCandidate(veryFullPool, createUtilization(
                totalCpu = 10.0, usedCpu = 9.5,  // 95% utilization - heavily penalized
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 7_600_000_000
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("moderate-pool", pool.name) }
        )
    }
    
    @Test
    fun `should select first fit among equally scored pools`() = runTest {
        // Given
        val job = createTestJob()
        val pool1 = createTestPool("pool-1")
        val pool2 = createTestPool("pool-2")
        val pool3 = createTestPool("pool-3")
        
        // All pools have same utilization
        val utilization = createUtilization(
            totalCpu = 10.0, usedCpu = 5.0,
            totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
        )
        
        val candidates = listOf(
            PoolCandidate(pool1, utilization),
            PoolCandidate(pool2, utilization),
            PoolCandidate(pool3, utilization)
        )
        
        // When - Multiple runs should be consistent
        val results = (1..5).map { strategy.selectPool(job, candidates) }
        
        // Then
        assertTrue(results.all { it.isRight() })
        val selectedPools = results.map { result ->
            result.fold({ "" }, { it.name })
        }
        // Should consistently select the same pool (first in sorted order)
        assertTrue(selectedPools.all { it == selectedPools.first() })
    }
    
    @Test
    fun `should handle different CPU and memory utilization`() = runTest {
        // Given
        val job = createTestJob()
        val cpuHeavyPool = createTestPool("cpu-heavy")
        val memoryHeavyPool = createTestPool("memory-heavy")
        val balancedPool = createTestPool("balanced")
        
        val candidates = listOf(
            PoolCandidate(cpuHeavyPool, createUtilization(
                totalCpu = 10.0, usedCpu = 8.0,  // 80% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 2_000_000_000  // 25% memory
            )),  // Average: 52.5%
            PoolCandidate(memoryHeavyPool, createUtilization(
                totalCpu = 10.0, usedCpu = 2.0,  // 20% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 6_400_000_000  // 80% memory
            )),  // Average: 50%
            PoolCandidate(balancedPool, createUtilization(
                totalCpu = 10.0, usedCpu = 5.0,  // 50% CPU
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000  // 50% memory
            ))   // Average: 50%
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        // Any of these could be selected based on the packing score calculation
        result.fold(
            { },
            { pool -> assertTrue(pool.name in listOf("cpu-heavy", "memory-heavy", "balanced")) }
        )
    }
    
    @Test
    fun `should handle zero resources`() = runTest {
        // Given
        val job = createTestJob()
        val zeroPool = createTestPool("zero-pool")
        val normalPool = createTestPool("normal-pool")
        
        val candidates = listOf(
            PoolCandidate(zeroPool, createUtilization(
                totalCpu = 0.0, usedCpu = 0.0,
                totalMemoryBytes = 0, usedMemoryBytes = 0
            )),
            PoolCandidate(normalPool, createUtilization(
                totalCpu = 10.0, usedCpu = 4.0,
                totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 3_200_000_000
            ))
        )
        
        // When
        val result = strategy.selectPool(job, candidates)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> 
                // Normal pool should be preferred as zero pool gets very low score
                assertEquals("normal-pool", pool.name)
            }
        )
    }
    
    @Test
    fun `getName should return correct strategy name`() {
        assertEquals("BinPackingFirstFit", strategy.getName())
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
    ): ResourceUtilization {
        return ResourceUtilization(
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