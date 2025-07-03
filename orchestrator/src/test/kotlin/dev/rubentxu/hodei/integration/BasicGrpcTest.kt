package dev.rubentxu.hodei.integration

import dev.rubentxu.hodei.pipelines.v1.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class BasicGrpcTest {

    @Test
    fun `should create grpc service`() = runTest {
        // Arrange & Act
        val services = TestServiceFactory.createServices()
        
        // Assert
        assertNotNull(services.grpcService)
        println("✅ gRPC service created successfully")
    }

    @Test
    fun `should handle worker registration`() = runTest {
        // Arrange & Act
        val services = TestServiceFactory.createServices()
        val worker = TestWorkerClient("test-worker-1", this)
        
        // Connect worker and register
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        
        // Send registration
        worker.register()
        
        // Give time for registration to process
        delay(100)
        
        // Assert
        val registeredWorkers = services.workerManager.getAllWorkers()
        assertEquals(1, registeredWorkers.size)
        assertEquals("test-worker-1", registeredWorkers[0].id.value)
        
        println("✅ Worker registration test passed")
        
        worker.close()
    }

    @Test
    fun `should handle status updates from worker`() = runTest {
        // Arrange & Act
        val services = TestServiceFactory.createServices()
        val worker = TestWorkerClient("test-worker-2", this)
        
        // Connect and register worker
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        
        worker.register()
        delay(100)
        
        // Send status update
        worker.sendStatusUpdate(EventType.STAGE_STARTED, "Starting test stage")
        delay(100)
        
        // Assert - Worker should still be registered
        assertTrue(services.grpcService.isWorkerConnected("test-worker-2"))
        
        println("✅ Status update test passed")
        
        worker.close()
    }

    @Test
    fun `should handle execution result from worker`() = runTest {
        // Arrange & Act
        val services = TestServiceFactory.createServices()
        val worker = TestWorkerClient("test-worker-3", this)
        
        // Connect and register worker
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        
        worker.register()
        delay(100)
        
        // Send execution result
        worker.sendExecutionResult(success = true, exitCode = 0, details = "Test completed successfully")
        delay(100)
        
        // Assert - Worker should still be connected
        assertTrue(services.grpcService.isWorkerConnected("test-worker-3"))
        
        println("✅ Execution result test passed")
        
        worker.close()
    }

    @Test
    fun `should send execution assignment to worker`() = runTest {
        // Arrange & Act
        val services = TestServiceFactory.createServices()
        val worker = TestWorkerClient("test-worker-4", this)
        
        // Connect and register worker
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        
        worker.register()
        delay(100)
        
        // Send execution assignment
        val assignment = TestExecutionAssignments.createSimpleShellAssignment(
            executionId = "test-execution-1",
            commands = listOf("echo 'Hello World'")
        )
        
        val success = services.grpcService.sendExecutionAssignment("test-worker-4", assignment)
        assertTrue(success)
        
        // Assert - Worker should receive the assignment
        val receivedAssignment = worker.expectExecutionAssignment()
        assertEquals("test-execution-1", receivedAssignment.executionId)
        assertEquals(1, receivedAssignment.definition.shell.commandsList.size)
        assertEquals("echo 'Hello World'", receivedAssignment.definition.shell.commandsList[0])
        
        println("✅ Execution assignment test passed")
        
        worker.close()
    }

    @Test
    fun `should handle multiple workers`() = runTest {
        // Arrange & Act
        val services = TestServiceFactory.createServices()
        val worker1 = TestWorkerClient("multi-worker-1", this)
        val worker2 = TestWorkerClient("multi-worker-2", this)
        
        // Connect and register both workers
        launch {
            val flow1 = services.grpcService.connect(worker1.outgoingFlow)
            worker1.collectIncomingMessages(flow1)
        }
        
        launch {
            val flow2 = services.grpcService.connect(worker2.outgoingFlow)
            worker2.collectIncomingMessages(flow2)
        }
        
        delay(100)
        
        worker1.register()
        worker2.register()
        
        delay(200)
        
        // Assert
        val connectedWorkers = services.grpcService.getConnectedWorkers()
        assertEquals(2, connectedWorkers.size)
        assertTrue(connectedWorkers.contains("multi-worker-1"))
        assertTrue(connectedWorkers.contains("multi-worker-2"))
        
        println("✅ Multiple workers test passed")
        
        worker1.close()
        worker2.close()
    }
}