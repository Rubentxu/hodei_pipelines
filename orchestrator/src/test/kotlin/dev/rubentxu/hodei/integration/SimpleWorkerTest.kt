package dev.rubentxu.hodei.integration

import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Simple test to verify worker registration works
 */
class SimpleWorkerTest {

    @Test
    fun `worker registration works`() = runTest {
        println("=== SIMPLE WORKER TEST ===")
        
        val workerManager = WorkerManagerService()
        
        // Test direct registration
        println("1. Registering worker directly...")
        workerManager.registerWorker("test-worker-direct")
        
        // Check workers
        val workers = workerManager.getAllWorkers()
        println("2. Found ${workers.size} workers")
        workers.forEach { worker ->
            println("   - Worker: ${worker.id.value}, Status: ${worker.status}")
        }
        
        assertEquals(1, workers.size)
        assertEquals("test-worker-direct", workers[0].id.value)
        
        println("✅ Direct worker registration test passed!")
    }

    @Test
    fun `grpc worker registration works`() = runTest {
        println("=== GRPC WORKER TEST ===")
        
        val services = TestServiceFactory.createServices()
        val workerManager = services.workerManager
        val grpcService = services.grpcService
        
        // Create test worker
        val worker = TestWorkerClient("grpc-test-worker", this)
        
        // Start connection
        val connectionJob = launch {
            val orchestratorFlow = grpcService.connect(worker.outgoingFlow)
            worker.collectIncomingMessages(orchestratorFlow)
        }
        
        delay(100) // Connection setup
        
        // Register worker
        println("1. Registering worker via gRPC...")
        worker.register()
        
        // Wait for registration
        delay(1000) // Give more time
        
        // Check workers
        val workers = workerManager.getAllWorkers()
        println("2. Found ${workers.size} workers after gRPC registration")
        workers.forEach { w ->
            println("   - Worker: ${w.id.value}, Status: ${w.status}")
        }
        
        assertTrue(workers.isNotEmpty(), "Should have at least one worker registered via gRPC")
        assertEquals("grpc-test-worker", workers[0].id.value)
        
        worker.close()
        connectionJob.cancel()
        
        println("✅ gRPC worker registration test passed!")
    }
}