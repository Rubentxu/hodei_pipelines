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
            
            // Wait for worker registration with timeout
            val registered = server.waitForWorkerConnection(workerId, 3000)
            assertTrue(registered) {
                "Worker should be registered within timeout"
            }
            
            // Additional small delay to ensure state consistency
            delay(100)
            
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
            
            // Wait for worker to connect first
            val connected = server.waitForWorkerConnection(workerId, 3000)
            assertTrue(connected) {
                "Worker should connect within timeout"
            }
            
            // Wait specifically for heartbeat messages
            val heartbeatReceived = server.jobExecutorService.waitForMessageType("HEARTBEAT", 2000)
            assertTrue(heartbeatReceived) {
                "Should receive heartbeat messages within timeout"
            }
            
            // Allow a bit more time for multiple heartbeats
            delay(500)
            
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
        
        // Wait for connection to be established
        val connected = server.waitForWorkerConnection(workerId, 3000)
        assertTrue(connected) {
            "Worker should connect before testing disconnection"
        }
        
        // Verify worker is actually registered
        assertTrue(server.workerManagementService.isWorkerRegistered(workerId)) {
            "Worker should be registered after connection"
        }
        
        // Then disconnect
        worker.close()
        
        // Wait for disconnection
        val disconnected = server.jobExecutorService.waitForWorkerDisconnection(workerId, 3000)
        assertTrue(disconnected) {
            "Worker should disconnect from job execution service within timeout"
        }
        
        // Then - Worker should be disconnected from job execution
        val connectedWorkers = server.jobExecutorService.getConnectedWorkers()
        assertFalse(connectedWorkers.containsKey(workerId)) {
            "Worker should be disconnected from job execution service after close. Connected workers: ${connectedWorkers.keys}"
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
                
                // Wait for both workers to connect
                val worker1Connected = server.waitForWorkerConnection(worker1Id, 3000)
                val worker2Connected = server.waitForWorkerConnection(worker2Id, 3000)
                
                assertTrue(worker1Connected) {
                    "Worker 1 should connect within timeout"
                }
                assertTrue(worker2Connected) {
                    "Worker 2 should connect within timeout"
                }
                
                // Additional delay for state consistency
                delay(200)
                
                // Then
                assertTrue(server.workerManagementService.isWorkerRegistered(worker1Id)) {
                    "Worker 1 should be registered"
                }
                assertTrue(server.workerManagementService.isWorkerRegistered(worker2Id)) {
                    "Worker 2 should be registered"
                }
                
                val registeredWorkers = server.workerManagementService.getRegisteredWorkers()
                assertTrue(registeredWorkers.size >= 2) {
                    "Should have at least both workers registered. Got: ${registeredWorkers.keys}"
                }
                
                junitAssertNotNull(registeredWorkers[worker1Id]) {
                    "Worker 1 should be in registered workers"
                }
                junitAssertNotNull(registeredWorkers[worker2Id]) {
                    "Worker 2 should be in registered workers"
                }
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