package dev.rubentxu.hodei.scheduling.application.services

import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.RepositoryError
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
import dev.rubentxu.hodei.scheduling.infrastructure.scheduling.LeastLoadedStrategy
import io.mockk.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SchedulerServiceTest {
    
    private lateinit var resourcePoolRepository: ResourcePoolRepository
    private lateinit var kubernetesMonitor: IResourceMonitor
    private lateinit var dockerMonitor: IResourceMonitor
    private lateinit var schedulerService: SchedulerService
    
    @BeforeEach
    fun setup() {
        resourcePoolRepository = mockk()
        kubernetesMonitor = mockk()
        dockerMonitor = mockk()
        
        val resourceMonitors = mapOf(
            "kubernetes" to kubernetesMonitor,
            "docker" to dockerMonitor
        )
        
        schedulerService = SchedulerService(
            resourcePoolRepository = resourcePoolRepository,
            resourceMonitors = resourceMonitors,
            defaultStrategy = LeastLoadedStrategy()
        )
    }
    
    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }
    
    @Test
    fun `findPlacement should return error when no active pools exist`() = runTest {
        // Given
        val job = createTestJob()
        coEvery { resourcePoolRepository.findActive() } returns emptyList<ResourcePool>().right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isLeft())
        result.fold(
            { error -> assertEquals("No active resource pools available", error) },
            { }
        )
    }
    
    @Test
    fun `findPlacement should return error when repository fails`() = runTest {
        // Given
        val job = createTestJob()
        coEvery { resourcePoolRepository.findActive() } returns RepositoryError.OperationFailed(message = "DB Error").left()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isLeft())
        result.fold(
            { error -> assertEquals("Failed to fetch active resource pools", error) },
            { }
        )
    }
    
    @Test
    fun `findPlacement should use requested pool when specified and available`() = runTest {
        // Given
        val poolId = DomainId.generate()
        val requestedPool = createTestPool(id = poolId, name = "requested-pool")
        val otherPool = createTestPool(name = "other-pool")
        val job = createTestJob(poolId = poolId)
        
        coEvery { resourcePoolRepository.findActive() } returns listOf(requestedPool, otherPool).right()
        coEvery { kubernetesMonitor.getUtilization(poolId) } returns createUtilization(
            totalCpu = 10.0,
            usedCpu = 2.0,
            totalMemoryBytes = 8_000_000_000,
            usedMemoryBytes = 2_000_000_000
        ).right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> 
                assertEquals(requestedPool.id, pool.id)
                assertEquals("requested-pool", pool.name)
            }
        )
    }
    
    @Test
    fun `findPlacement should return error when requested pool has no capacity`() = runTest {
        // Given
        val poolId = DomainId.generate()
        val requestedPool = createTestPool(id = poolId, name = "full-pool")
        val job = createTestJob(
            poolId = poolId,
            resourceRequirements = mapOf(
                "cpu" to "8",
                "memory" to "7Gi"
            )
        )
        
        coEvery { resourcePoolRepository.findActive() } returns listOf(requestedPool).right()
        coEvery { kubernetesMonitor.getUtilization(poolId) } returns createUtilization(
            totalCpu = 10.0,
            usedCpu = 5.0, // Only 5 CPU available, job needs 8
            totalMemoryBytes = 8_000_000_000,
            usedMemoryBytes = 2_000_000_000
        ).right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isLeft())
        result.fold(
            { error -> assertEquals("Requested pool full-pool does not have sufficient capacity", error) },
            { }
        )
    }
    
    @Test
    fun `findPlacement should select pool using default strategy when no pool specified`() = runTest {
        // Given
        val pool1 = createTestPool(name = "pool-1")
        val pool2 = createTestPool(name = "pool-2")
        val pool3 = createTestPool(name = "pool-3")
        val job = createTestJob()
        
        coEvery { resourcePoolRepository.findActive() } returns listOf(pool1, pool2, pool3).right()
        
        // Pool 1: 50% utilized
        coEvery { kubernetesMonitor.getUtilization(pool1.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 5.0,
            totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
        ).right()
        
        // Pool 2: 20% utilized (should be selected by LeastLoaded)
        coEvery { kubernetesMonitor.getUtilization(pool2.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 2.0,
            totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 1_600_000_000
        ).right()
        
        // Pool 3: 80% utilized
        coEvery { kubernetesMonitor.getUtilization(pool3.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 8.0,
            totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 6_400_000_000
        ).right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("pool-2", pool.name) } // Least loaded
        )
    }
    
    @Test
    fun `findPlacement should use specified strategy`() = runTest {
        // Given
        val pool1 = createTestPool(name = "pool-1")
        val pool2 = createTestPool(name = "pool-2")
        val job = createTestJob()
        
        coEvery { resourcePoolRepository.findActive() } returns listOf(pool1, pool2).right()
        
        coEvery { kubernetesMonitor.getUtilization(pool1.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 2.0
        ).right()
        
        coEvery { kubernetesMonitor.getUtilization(pool2.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 4.0
        ).right()
        
        // When - use round robin (should select first pool in sorted order)
        val result = schedulerService.findPlacement(job, "roundrobin")
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertNotNull(pool) } // Round robin will select one deterministically
        )
    }
    
    @Test
    fun `findPlacement should filter out pools with insufficient resources`() = runTest {
        // Given
        val pool1 = createTestPool(name = "small-pool")
        val pool2 = createTestPool(name = "large-pool")
        val job = createTestJob(
            resourceRequirements = mapOf(
                "cpu" to "6",
                "memory" to "6Gi"
            )
        )
        
        coEvery { resourcePoolRepository.findActive() } returns listOf(pool1, pool2).right()
        
        // Pool 1: Not enough resources
        coEvery { kubernetesMonitor.getUtilization(pool1.id) } returns createUtilization(
            totalCpu = 8.0, usedCpu = 4.0, // Only 4 CPU available, need 6
            totalMemoryBytes = 8_000_000_000, usedMemoryBytes = 4_000_000_000
        ).right()
        
        // Pool 2: Enough resources
        coEvery { kubernetesMonitor.getUtilization(pool2.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 2.0, // 8 CPU available
            totalMemoryBytes = 16_000_000_000, usedMemoryBytes = 4_000_000_000
        ).right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("large-pool", pool.name) }
        )
    }
    
    @Test
    fun `findPlacement should respect pool maxJobs limit`() = runTest {
        // Given
        val pool1 = createTestPool(name = "limited-pool", maxJobs = 5)
        val pool2 = createTestPool(name = "unlimited-pool", maxJobs = null)
        val job = createTestJob()
        
        coEvery { resourcePoolRepository.findActive() } returns listOf(pool1, pool2).right()
        
        // Pool 1: At max job capacity
        coEvery { kubernetesMonitor.getUtilization(pool1.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 2.0,
            runningJobs = 5 // At limit
        ).right()
        
        // Pool 2: Has capacity
        coEvery { kubernetesMonitor.getUtilization(pool2.id) } returns createUtilization(
            totalCpu = 10.0, usedCpu = 4.0,
            runningJobs = 10
        ).right()
        
        // When
        val result = schedulerService.findPlacement(job)
        
        // Then
        assertTrue(result.isRight())
        result.fold(
            { },
            { pool -> assertEquals("unlimited-pool", pool.name) }
        )
    }
    
    @Test
    fun `getAvailableStrategies should return all strategies`() {
        // When
        val strategies = schedulerService.getAvailableStrategies()
        
        // Then
        assertTrue(strategies.contains("roundrobin"))
        assertTrue(strategies.contains("greedy"))
        assertTrue(strategies.contains("leastloaded"))
        assertTrue(strategies.contains("binpacking"))
        assertTrue(strategies.contains("default"))
    }
    
    // Helper functions
    private fun createTestJob(
        id: DomainId = DomainId.generate(),
        poolId: DomainId? = null,
        resourceRequirements: Map<String, String> = mapOf(
            "cpu" to "2",
            "memory" to "2Gi"
        )
    ): Job {
        return Job(
            id = id,
            name = "test-job",
            templateId = DomainId("test-template"),
            templateVersion = null,
            poolId = poolId,
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
        id: DomainId = DomainId.generate(),
        name: String = "test-pool",
        type: String = "kubernetes",
        maxJobs: Int? = null
    ): ResourcePool {
        return ResourcePool(
            id = id,
            name = name,
            type = type,
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