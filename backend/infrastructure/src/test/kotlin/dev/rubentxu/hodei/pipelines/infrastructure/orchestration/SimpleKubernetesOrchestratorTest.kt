package dev.rubentxu.hodei.pipelines.infrastructure.orchestration

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SimpleKubernetesOrchestratorTest {
    
    private lateinit var orchestrator: SimpleKubernetesOrchestrator
    
    @BeforeEach
    fun setUp() {
        orchestrator = SimpleKubernetesOrchestrator("test-namespace")
    }
    
    private fun createTestTemplate(): WorkerTemplate {
        return WorkerTemplate(
            id = WorkerTemplateId("test-template"),
            name = "Test Template",
            image = "hodei/worker:latest",
            resources = ResourceRequirements(cpu = "500m", memory = "1Gi"),
            capabilities = mapOf("build" to "true"),
            labels = mapOf("env" to "test")
        )
    }
    
    @Test
    fun `should create worker successfully`() = runTest {
        // Given
        val template = createTestTemplate()
        val poolId = WorkerPoolId("test-pool")
        
        // When
        val result = orchestrator.createWorker(template, poolId)
        
        // Then
        assertTrue(result is WorkerCreationResult.Success)
        val worker = (result as WorkerCreationResult.Success).worker
        assertTrue(worker.name.contains("test-template"))
        assertEquals(dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY, worker.status)
    }
    
    @Test
    fun `should reject invalid template`() = runTest {
        // Given invalid template (blank image)
        val invalidTemplate = createTestTemplate().copy(image = "")
        val poolId = WorkerPoolId("test-pool")
        
        // When
        val result = orchestrator.createWorker(invalidTemplate, poolId)
        
        // Then
        assertTrue(result is WorkerCreationResult.InvalidTemplate)
        val errors = (result as WorkerCreationResult.InvalidTemplate).errors
        assertTrue(errors.any { it.contains("image cannot be blank") })
    }
    
    @Test
    fun `should delete worker successfully`() = runTest {
        // Given - create a worker first
        val template = createTestTemplate()
        val poolId = WorkerPoolId("test-pool")
        val createResult = orchestrator.createWorker(template, poolId)
        
        assertTrue(createResult is WorkerCreationResult.Success)
        val workerId = (createResult as WorkerCreationResult.Success).worker.id
        
        // When
        val deleteResult = orchestrator.deleteWorker(workerId)
        
        // Then
        assertTrue(deleteResult is WorkerDeletionResult.Success)
    }
    
    @Test
    fun `should return not found when deleting non-existent worker`() = runTest {
        // Given
        val nonExistentWorkerId = WorkerId("non-existent")
        
        // When
        val result = orchestrator.deleteWorker(nonExistentWorkerId)
        
        // Then
        assertTrue(result is WorkerDeletionResult.NotFound)
        assertEquals(nonExistentWorkerId, (result as WorkerDeletionResult.NotFound).workerId)
    }
    
    @Test
    fun `should list workers in pool`() = runTest {
        // Given - create multiple workers
        val template = createTestTemplate()
        val poolId = WorkerPoolId("test-pool")
        
        orchestrator.createWorker(template, poolId)
        orchestrator.createWorker(template, poolId)
        
        // When
        val workers = orchestrator.listWorkers(poolId)
        
        // Then
        assertEquals(2, workers.size)
        workers.forEach { worker ->
            assertTrue(worker.name.contains("test-template"))
            assertEquals(dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY, worker.status)
        }
    }
    
    @Test
    fun `should get worker status`() = runTest {
        // Given - create a worker
        val template = createTestTemplate()
        val poolId = WorkerPoolId("test-pool")
        val createResult = orchestrator.createWorker(template, poolId)
        
        assertTrue(createResult is WorkerCreationResult.Success)
        val workerId = (createResult as WorkerCreationResult.Success).worker.id
        
        // When
        val statusResult = orchestrator.getWorkerStatus(workerId)
        
        // Then
        assertTrue(statusResult is WorkerStatusResult.Success)
        val worker = (statusResult as WorkerStatusResult.Success).worker
        assertEquals(workerId, worker.id)
    }
    
    @Test
    fun `should validate template correctly`() = runTest {
        // Given valid template
        val validTemplate = createTestTemplate()
        
        // When
        val result = orchestrator.validateTemplate(validTemplate)
        
        // Then
        assertTrue(result is TemplateValidationResult.Valid)
    }
    
    @Test
    fun `should return orchestrator info`() = runTest {
        // When
        val info = orchestrator.getOrchestratorInfo()
        
        // Then
        assertEquals(OrchestratorType.KUBERNETES, info.type)
        assertEquals("v1.28.0", info.version)
        assertTrue(info.capabilities.contains(OrchestratorCapability.AUTO_SCALING))
        assertTrue(info.capabilities.contains(OrchestratorCapability.PERSISTENT_STORAGE))
        assertEquals("test-namespace", info.metadata["namespace"])
        assertEquals("simulated", info.metadata["implementation"])
    }
    
    @Test
    fun `should perform health check successfully`() = runTest {
        // When
        val health = orchestrator.healthCheck()
        
        // Then
        assertEquals(HealthStatus.HEALTHY, health.status)
        assertEquals("Simulated Kubernetes cluster is healthy", health.message)
    }
    
    @Test
    fun `should get resource availability`() = runTest {
        // When
        val availability = orchestrator.getResourceAvailability()
        
        // Then
        assertTrue(availability.totalNodes > 0)
        assertTrue(availability.availableNodes > 0)
        assertTrue(availability.totalCpu.isNotEmpty())
        assertTrue(availability.totalMemory.isNotEmpty())
        assertTrue(availability.canAccommodateWorkers(1))
    }
    
    @Test
    fun `should list all workers across pools`() = runTest {
        // Given - create workers in different pools
        val template = createTestTemplate()
        val pool1 = WorkerPoolId("pool-1")
        val pool2 = WorkerPoolId("pool-2")
        
        orchestrator.createWorker(template, pool1)
        orchestrator.createWorker(template, pool2)
        
        // When
        val allWorkers = orchestrator.listAllWorkers()
        
        // Then
        assertEquals(2, allWorkers.size)
    }
}