package dev.rubentxu.hodei.debug

import dev.rubentxu.hodei.integration.TestWorkerClient
import dev.rubentxu.hodei.integration.TestServiceFactory
import dev.rubentxu.hodei.pipelines.v1.*
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Simple debug tests to verify basic functionality with the new protocol
 */
class SimpleDebugTest {

    @Test
    fun `debug - worker lifecycle`() = runTest {
        println("=== DEBUG: Worker Lifecycle Test ===")
        
        // Setup
        val services = TestServiceFactory.createServices()
        
        // Create worker
        val worker = TestWorkerClient("debug-worker-1", this)
        
        // Connect
        println("1. Connecting worker...")
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        
        // Register
        println("2. Registering worker...")
        worker.register()
        delay(100)
        
        // Verify registration
        val workers = services.workerManager.getAllWorkers()
        println("3. Registered workers: ${workers.size}")
        assertEquals(1, workers.size)
        assertEquals("debug-worker-1", workers[0].id.value)
        
        // Send status updates
        println("4. Sending status updates...")
        worker.sendStatusUpdate(EventType.STAGE_STARTED, "Debug stage started")
        delay(100)
        
        worker.sendStatusUpdate(EventType.STEP_STARTED, "Debug step 1")
        delay(100)
        
        worker.sendStatusUpdate(EventType.STEP_COMPLETED, "Debug step 1 completed")
        delay(100)
        
        // Send execution result
        println("5. Sending execution result...")
        worker.sendExecutionResult(success = true, exitCode = 0, details = "Debug test completed")
        delay(100)
        
        // Verify worker is still connected
        assertTrue(services.grpcService.isWorkerConnected("debug-worker-1"))
        
        println("✅ Debug worker lifecycle test passed")
        
        worker.close()
    }

    @Test
    fun `debug - execution flow`() = runTest {
        println("\n=== DEBUG: Execution Flow Test ===")
        
        // Setup
        val services = TestServiceFactory.createServices()
        
        val worker = TestWorkerClient("debug-worker-2", this)
        
        // Connect and register
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        worker.register()
        delay(100)
        
        println("1. Worker registered")
        
        // Send execution assignment
        println("2. Sending execution assignment...")
        val assignment = ExecutionAssignment.newBuilder()
            .setExecutionId("debug-exec-1")
            .setDefinition(
                ExecutionDefinition.newBuilder()
                    .setShell(
                        ShellTask.newBuilder()
                            .addCommands("echo 'Debug test'")
                            .addCommands("echo 'Step 2'")
                            .build()
                    )
                    .putEnvVars("DEBUG", "true")
                    .build()
            )
            .build()
        
        services.grpcService.sendExecutionAssignment("debug-worker-2", assignment)
        
        // Wait for assignment
        println("3. Waiting for assignment...")
        val received = worker.expectExecutionAssignment()
        assertEquals("debug-exec-1", received.executionId)
        assertEquals(2, received.definition.shell.commandsList.size)
        
        println("4. Assignment received, simulating execution...")
        
        // Simulate execution
        worker.sendStatusUpdate(EventType.STAGE_STARTED, "Execution started")
        delay(50)
        
        worker.sendLogChunk(LogStream.STDOUT, "Debug test\n")
        delay(50)
        
        worker.sendLogChunk(LogStream.STDOUT, "Step 2\n")
        delay(50)
        
        worker.sendExecutionResult(success = true, exitCode = 0)
        delay(50)
        
        println("✅ Debug execution flow test passed")
        
        worker.close()
    }

    @Test
    fun `debug - error handling`() = runTest {
        println("\n=== DEBUG: Error Handling Test ===")
        
        // Setup
        val services = TestServiceFactory.createServices()
        
        val worker = TestWorkerClient("debug-worker-3", this)
        
        // Connect and register
        val orchestratorFlow = services.grpcService.connect(worker.outgoingFlow)
        worker.collectIncomingMessages(orchestratorFlow)
        worker.register()
        delay(100)
        
        println("1. Worker registered")
        
        // Send assignment
        val assignment = ExecutionAssignment.newBuilder()
            .setExecutionId("debug-exec-error")
            .setDefinition(
                ExecutionDefinition.newBuilder()
                    .setShell(
                        ShellTask.newBuilder()
                            .addCommands("exit 1")
                            .build()
                    )
                    .build()
            )
            .build()
        
        services.grpcService.sendExecutionAssignment("debug-worker-3", assignment)
        
        // Simulate failed execution
        println("2. Simulating failed execution...")
        worker.sendStatusUpdate(EventType.STAGE_STARTED, "Starting failing command")
        delay(50)
        
        worker.sendLogChunk(LogStream.STDERR, "Error: Command failed\n")
        delay(50)
        
        worker.sendExecutionResult(success = false, exitCode = 1, details = "Command exited with code 1")
        delay(50)
        
        // Worker should still be connected
        assertTrue(services.grpcService.isWorkerConnected("debug-worker-3"))
        
        println("✅ Debug error handling test passed")
        
        worker.close()
    }
}