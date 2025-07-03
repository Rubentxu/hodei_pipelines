package dev.rubentxu.hodei.scheduling.application.services

import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobType
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IResourceMonitor
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceUtilization
import dev.rubentxu.hodei.scheduling.domain.entities.PoolCandidate
import dev.rubentxu.hodei.scheduling.infrastructure.scheduling.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Simple test to verify scheduler functionality without complex dependencies
 */
class SimpleSchedulerTest {
    
    private lateinit var resourcePoolRepository: ResourcePoolRepository
    private lateinit var kubernetesMonitor: IResourceMonitor
    private lateinit var schedulerService: SchedulerService
    
    // Test pools
    private lateinit var pool1: ResourcePool
    private lateinit var pool2: ResourcePool
    
    @BeforeEach
    fun setup() {
        resourcePoolRepository = mockk()
        kubernetesMonitor = mockk()
        
        val resourceMonitors = mapOf("kubernetes" to kubernetesMonitor)
        
        schedulerService = SchedulerService(
            resourcePoolRepository = resourcePoolRepository,
            resourceMonitors = resourceMonitors,
            defaultStrategy = RoundRobinStrategy()
        )
        
        // Initialize test pools
        pool1 = createTestPool("pool-1")
        pool2 = createTestPool("pool-2")
        
        // Setup default behavior
        coEvery { resourcePoolRepository.findActive() } returns 
            listOf(pool1, pool2).right()
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `findPlacement should use round robin strategy when specified`() = runTest {
        // Given
        val job = createTestJob()
        val utilization = createUtilization(totalCpu = 10.0, usedCpu = 1.0)
        
        coEvery { kubernetesMonitor.getUtilization(pool1.id) } returns utilization.right()
        coEvery { kubernetesMonitor.getUtilization(pool2.id) } returns utilization.right()
        
        // When - First job with round robin
        val result1 = schedulerService.findPlacement(job, "roundrobin")
        
        // Then - Should select a pool successfully
        assertTrue(result1.isRight())
        val firstSelectedPool = result1.fold({ null }, { it.name })
        assertTrue(firstSelectedPool in listOf("pool-1", "pool-2"))
        
        // When - Second job with round robin
        val result2 = schedulerService.findPlacement(job, "roundrobin")
        
        // Then - Should select the other pool (round robin behavior)
        assertTrue(result2.isRight())
        val secondSelectedPool = result2.fold({ null }, { it.name })
        assertTrue(secondSelectedPool in listOf("pool-1", "pool-2"))
        
        // Verify round robin behavior - the two selections should be different
        assertTrue(firstSelectedPool != secondSelectedPool)
    }
    
    @Test
    fun `findPlacement should use greedy strategy when specified`() = runTest {
        // Given
        val job = createTestJob()
        
        // Pool1 has higher utilization
        coEvery { kubernetesMonitor.getUtilization(pool1.id) } returns 
            createUtilization(usedCpu = 8.0).right()
            
        // Pool2 has lower utilization
        coEvery { kubernetesMonitor.getUtilization(pool2.id) } returns 
            createUtilization(usedCpu = 2.0).right()
        
        // When
        val result = schedulerService.findPlacement(job, "greedy")
        
        // Then - Should select pool2 (lower utilization)
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("pool-2", pool.name) }
        )
    }
    
    @Test
    fun `findPlacement should return error when no pools available`() = runTest {
        // Given
        val job = createTestJob()
        coEvery { resourcePoolRepository.findActive() } returns emptyList<ResourcePool>().right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isLeft())
        result.fold(
            { error -> assertTrue(error.contains("No active resource pools")) },
            { }
        )
    }
    
    @Test
    fun `getAllStrategies should return all available strategies`() {
        val strategies = schedulerService.getAvailableStrategies()
        
        assertEquals(5, strategies.size)
        assertTrue("roundrobin" in strategies)
        assertTrue("greedy" in strategies)
        assertTrue("leastloaded" in strategies)
        assertTrue("binpacking" in strategies)
        assertTrue("default" in strategies)
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
        usedCpu: Double = 5.0
    ): ResourceUtilization {
        return ResourceUtilization(
            poolId = DomainId.generate(),
            totalCpu = totalCpu,
            usedCpu = usedCpu,
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