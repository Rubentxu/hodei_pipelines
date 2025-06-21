package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.infrastructure.worker.PipelineWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull as junitAssertNotNull
import org.mockito.kotlin.mock

/**
 * Basic integration tests to validate the test infrastructure
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BasicIntegrationTest {
    
    private lateinit var server: EmbeddedGrpcServer
    private lateinit var scriptExecutor: PipelineScriptExecutor
    
    @BeforeAll
    fun setupServer() {
        server = EmbeddedGrpcServer().start()
        scriptExecutor = mock<PipelineScriptExecutor>()
    }
    
    @AfterAll
    fun teardownServer() {
        server.close()
    }
    
    @BeforeEach
    fun clearTestData() {
        server.clearTestData()
    }
    
    @Test
    fun `server should start and accept connections`() {
        // Given/When - Server started in setup
        
        // Then
        assertTrue(server.isRunning()) {
            "Server should be running"
        }
        assertTrue(server.port > 0) {
            "Server should have a valid port"
        }
    }
    
    @Test
    fun `should create worker instance without errors`() {
        // Given
        val workerId = "basic-test-worker"
        val workerName = "Basic Test Worker"
        
        // When/Then - Should not throw
        assertDoesNotThrow {
            PipelineWorker(
                workerId = workerId,
                workerName = workerName,
                serverHost = "localhost",
                serverPort = server.port,
                scriptExecutor = scriptExecutor
            ).use { 
                // Worker created successfully
            }
        }
    }
    
    @Test
    fun `mock services should be accessible`() {
        // Given/When
        val jobExecutorService = server.jobExecutorService
        val workerManagementService = server.workerManagementService
        
        // Then
        junitAssertNotNull(jobExecutorService)
        junitAssertNotNull(workerManagementService)
        
        // Test configuration
        server.configureJobExecution(simulateArtifactTransfer = false)
        assertFalse(jobExecutorService.simulateArtifactTransfer)
        
        server.configureWorkerManagement(registrationShouldFail = true)
        assertTrue(workerManagementService.registrationShouldFail)
    }
    
    @Test
    fun `should track test metrics`() = runTest {
        // Given
        val initialMetrics = server.getTestMetrics()
        
        // When
        server.addTestJob(TestJobBuilder.createSimpleJob())
        
        // Then
        val updatedMetrics = server.getTestMetrics()
        assertEquals(initialMetrics.jobsInQueue + 1, updatedMetrics.jobsInQueue)
    }
    
    @Test
    fun `test artifact builders should work`() {
        // Given/When
        val simpleArtifact = TestArtifactBuilder.createSimpleArtifact()
        val compressedArtifact = TestArtifactBuilder.createCompressedArtifact()
        val largeArtifact = TestArtifactBuilder.createLargeArtifact(sizeKB = 10)
        
        // Then
        junitAssertNotNull(simpleArtifact.id)
        assertEquals("test.txt", simpleArtifact.name)
        assertTrue(simpleArtifact.data.isNotEmpty())
        
        junitAssertNotNull(compressedArtifact.id)
        assertEquals(dev.rubentxu.hodei.pipelines.proto.CompressionType.COMPRESSION_GZIP, compressedArtifact.compression)
        
        junitAssertNotNull(largeArtifact.id)
        assertEquals(10 * 1024, largeArtifact.data.size) // 10KB
    }
    
    @Test
    fun `test job builders should work`() {
        // Given/When
        val simpleJob = TestJobBuilder.createSimpleJob()
        val complexJob = TestJobBuilder.createComplexJob()
        
        // Then
        junitAssertNotNull(simpleJob.id)
        assertEquals("Test Job", simpleJob.name)
        assertTrue(simpleJob.artifacts.isEmpty())
        
        junitAssertNotNull(complexJob.id)
        assertEquals("Complex Test Job", complexJob.name)
        assertTrue(complexJob.artifacts.isNotEmpty())
        assertEquals(3, complexJob.artifacts.size) // config-1, config-2, data-1
    }
}