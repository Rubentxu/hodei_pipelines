package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.infrastructure.worker.PipelineWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull as junitAssertNotNull
import org.mockito.kotlin.mock

/**
 * Simplified integration tests for worker registration
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleWorkerRegistrationTest {
    
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
    fun `should register worker successfully`() = runTest {
        // Given
        val workerId = "simple-test-worker"
        val workerName = "Simple Test Worker"
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = workerName,
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for worker connection using server utility method
            val connected = server.waitForWorkerConnection(workerId, 5000)
            assertTrue(connected) {
                "Worker should connect within timeout"
            }
            
            // Then
            assertTrue(server.workerManagementService.isWorkerRegistered(workerId)) {
                "Worker should be registered"
            }
            
            val registeredWorkers = server.workerManagementService.getRegisteredWorkers()
            val workerInfo = registeredWorkers[workerId]
            junitAssertNotNull(workerInfo)
            assertEquals(workerName, workerInfo!!.workerName)
        }
    }
    
    @Test
    fun `should handle registration failure`() = runTest {
        // Given
        server.configureWorkerManagement(registrationShouldFail = true)
        val workerId = "failed-worker"
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Failed Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait a bit for registration attempt
            delay(1000)
            
            // Then
            assertFalse(server.workerManagementService.isWorkerRegistered(workerId)) {
                "Worker should not be registered due to failure"
            }
        }
    }
    
    @Test
    fun `should send heartbeats after registration`() = runTest {
        // Given
        server.configureWorkerManagement(heartbeatInterval = 1) // Fast heartbeats
        val workerId = "heartbeat-worker"
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Heartbeat Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for worker connection
            val connected = server.waitForWorkerConnection(workerId, 5000)
            assertTrue(connected) {
                "Worker should connect within timeout"
            }
            
            // Wait specifically for heartbeat messages
            val heartbeatReceived = server.jobExecutorService.waitForMessageType("HEARTBEAT", 3000)
            assertTrue(heartbeatReceived) {
                "Should receive heartbeat messages within timeout"
            }
            
            // Then
            assertTrue(server.workerManagementService.isWorkerRegistered(workerId))
            
            val messages = server.jobExecutorService.getReceivedMessages()
            val heartbeats = messages.filter { it.messageType == "HEARTBEAT" }
            assertTrue(heartbeats.isNotEmpty()) {
                "Should have received heartbeat messages. Received: ${messages.map { it.messageType }}"
            }
        }
    }
    
    @Test 
    fun `should handle worker disconnection`() = runTest {
        // Given
        val workerId = "disconnect-worker"
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Disconnect Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        // When
        worker.start()
        
        // Wait for worker connection
        val connected = server.waitForWorkerConnection(workerId, 5000)
        assertTrue(connected) {
            "Worker should connect within timeout"
        }
        
        assertTrue(server.workerManagementService.isWorkerRegistered(workerId))
        
        // Close worker
        worker.close()
        
        // Wait for disconnection
        val disconnected = server.jobExecutorService.waitForWorkerDisconnection(workerId, 3000)
        assertTrue(disconnected) {
            "Worker should disconnect from job execution service within timeout"
        }
        
        // Then - Note: Worker stays registered but disconnects from job execution
        val connectedWorkers = server.jobExecutorService.getConnectedWorkers()
        assertFalse(connectedWorkers.containsKey(workerId)) {
            "Worker should be disconnected from job execution service. Connected: ${connectedWorkers.keys}"
        }
    }
}