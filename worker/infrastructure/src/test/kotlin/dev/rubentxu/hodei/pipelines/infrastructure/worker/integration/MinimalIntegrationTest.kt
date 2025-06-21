package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.infrastructure.worker.PipelineWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assertions.assertNotNull as junitAssertNotNull
import org.mockito.kotlin.mock

/**
 * Minimal integration tests that focus on core infrastructure without complex mocking
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinimalIntegrationTest {
    
    private lateinit var server: EmbeddedGrpcServer
    
    @BeforeAll
    fun setupServer() {
        server = EmbeddedGrpcServer().start()
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
    fun `should start embedded server successfully`() {
        assertTrue(server.isRunning()) {
            "Embedded server should be running"
        }
        assertTrue(server.port > 0) {
            "Server should have a valid port number"
        }
    }
    
    @Test
    fun `should connect and register worker with server`() = runTest {
        // Given
        val workerId = "minimal-test-worker"
        val scriptExecutor = mock<PipelineScriptExecutor>()
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Minimal Test Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            delay(1000) // Give time for registration
            
            // Then
            assertTrue(server.workerManagementService.isWorkerRegistered(workerId)) {
                "Worker should be registered with the server"
            }
            
            val registeredWorkers = server.workerManagementService.getRegisteredWorkers()
            junitAssertNotNull(registeredWorkers[workerId]) {
                "Worker should be in the registered workers list"
            }
            assertEquals("Minimal Test Worker", registeredWorkers[workerId]?.workerName) {
                "Worker name should match"
            }
        }
    }
    
    @Test
    fun `should receive heartbeat messages from worker`() = runTest {
        // Given
        val workerId = "heartbeat-test-worker"
        val scriptExecutor = mock<PipelineScriptExecutor>()
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Heartbeat Test Worker", 
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            delay(1500) // Allow multiple heartbeats
            
            // Then
            val messages = server.jobExecutorService.getReceivedMessages()
            val heartbeats = messages.filter { it.messageType == "HEARTBEAT" }
            
            assertTrue(heartbeats.isNotEmpty()) {
                "Should receive heartbeat messages from worker. Received message types: ${messages.map { it.messageType }}"
            }
            
            assertTrue(heartbeats.size >= 1) {
                "Should receive at least one heartbeat message. Got ${heartbeats.size} heartbeats"
            }
        }
    }
    
    @Test
    fun `should handle worker disconnection properly`() = runTest {
        // Given
        val workerId = "disconnect-test-worker"
        val scriptExecutor = mock<PipelineScriptExecutor>()
        
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Disconnect Test Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        // When - Connect first
        worker.start()
        delay(500)
        assertTrue(server.workerManagementService.isWorkerRegistered(workerId))
        
        // Then disconnect
        worker.close()
        delay(500) // Allow cleanup
        
        // Then - Worker should be disconnected from job execution
        val connectedWorkers = server.jobExecutorService.getConnectedWorkers()
        assertFalse(connectedWorkers.containsKey(workerId)) {
            "Worker should be disconnected from job execution service after close"
        }
    }
    
    @Test
    fun `should track multiple workers independently`() = runTest {
        // Given
        val worker1Id = "multi-worker-1"
        val worker2Id = "multi-worker-2"
        val scriptExecutor1 = mock<PipelineScriptExecutor>()
        val scriptExecutor2 = mock<PipelineScriptExecutor>()
        
        val worker1 = PipelineWorker(
            workerId = worker1Id,
            workerName = "Multi Worker 1",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor1
        )
        
        val worker2 = PipelineWorker(
            workerId = worker2Id,
            workerName = "Multi Worker 2",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor2
        )
        
        // When
        worker1.use { w1 ->
            worker2.use { w2 ->
                w1.start()
                w2.start()
                delay(1000)
                
                // Then
                assertTrue(server.workerManagementService.isWorkerRegistered(worker1Id))
                assertTrue(server.workerManagementService.isWorkerRegistered(worker2Id))
                
                val registeredWorkers = server.workerManagementService.getRegisteredWorkers()
                assertEquals(2, registeredWorkers.size) {
                    "Should have both workers registered"
                }
                
                junitAssertNotNull(registeredWorkers[worker1Id])
                junitAssertNotNull(registeredWorkers[worker2Id])
            }
        }
    }
    
    @Test
    fun `should handle test artifacts and jobs creation`() {
        // Given/When
        val artifact = TestArtifactBuilder.createSimpleArtifact("test-id", "test.txt")
        val job = TestJobBuilder.createSimpleJob("job-id", "Test Job")
        val complexJob = TestJobBuilder.createJobWithArtifacts(
            "complex-job",
            "Complex Job",
            "echo test",
            listOf(artifact)
        )
        
        // Then
        junitAssertNotNull(artifact.id)
        assertEquals("test.txt", artifact.name)
        assertTrue(artifact.data.isNotEmpty())
        
        junitAssertNotNull(job.id)
        assertEquals("Test Job", job.name)
        assertTrue(job.artifacts.isEmpty())
        
        junitAssertNotNull(complexJob.id)
        assertEquals("Complex Job", complexJob.name)
        assertEquals(1, complexJob.artifacts.size)
        assertEquals(artifact.id, complexJob.artifacts.first().id)
    }
    
    @Test
    fun `should track server metrics and test configuration`() = runTest {
        // Given
        val initialMetrics = server.getTestMetrics()
        
        // When
        server.addTestJob(TestJobBuilder.createSimpleJob())
        server.configureJobExecution(simulateArtifactTransfer = true)
        server.configureWorkerManagement(registrationShouldFail = false)
        
        // Then
        val updatedMetrics = server.getTestMetrics()
        assertEquals(initialMetrics.jobsInQueue + 1, updatedMetrics.jobsInQueue)
        
        assertTrue(server.jobExecutorService.simulateArtifactTransfer)
        assertFalse(server.workerManagementService.registrationShouldFail)
    }
}