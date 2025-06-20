package dev.rubentxu.hodei.pipelines.domain.orchestration

import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerPoolTest {
    
    private fun createTestWorkerPool(): WorkerPool {
        val template = WorkerTemplate(
            id = WorkerTemplateId("test-template"),
            name = "Test Template",
            image = "test:latest",
            resources = ResourceRequirements(cpu = "1000m", memory = "2Gi")
        )
        
        val scalingPolicy = ScalingPolicy(
            id = ScalingPolicyId("test-policy"),
            name = "Test Scaling Policy",
            minWorkers = 1,
            maxWorkers = 5,
            scaleUpThreshold = ScaleThreshold(queueLength = 3),
            scaleDownThreshold = ScaleThreshold(queueLength = 0)
        )
        
        return WorkerPool(
            id = WorkerPoolId("test-pool"),
            name = "Test Pool",
            template = template,
            scalingPolicy = scalingPolicy
        )
    }
    
    private fun createTestWorker(id: String = "worker-1", status: WorkerStatus = WorkerStatus.READY): Worker {
        return Worker(
            id = WorkerId(id),
            name = "Test Worker $id",
            capabilities = dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities.builder()
                .os("linux")
                .arch("x86_64")
                .maxConcurrentJobs(5)
                .build(),
            status = status
        )
    }
    
    @Test
    fun `should create worker pool with valid configuration`() {
        val pool = createTestWorkerPool()
        
        assertEquals("test-pool", pool.id.value)
        assertEquals("Test Pool", pool.name)
        assertEquals(0, pool.currentSize)
        assertEquals(WorkerPoolStatus.INACTIVE, pool.status)
    }
    
    @Test
    fun `should reject invalid worker pool id`() {
        assertThrows<IllegalArgumentException> {
            WorkerPoolId("")
        }
    }
    
    @Test
    fun `should add worker to pool`() {
        val pool = createTestWorkerPool()
        val worker = createTestWorker()
        
        val updatedPool = pool.addWorker(worker)
        
        assertEquals(1, updatedPool.currentSize)
        assertEquals(1, updatedPool.workers.size)
        assertTrue(updatedPool.workers.contains(worker))
    }
    
    @Test
    fun `should remove worker from pool`() {
        val pool = createTestWorkerPool()
        val worker = createTestWorker()
        val poolWithWorker = pool.addWorker(worker)
        
        val updatedPool = poolWithWorker.removeWorker(worker.id)
        
        assertEquals(0, updatedPool.currentSize)
        assertEquals(0, updatedPool.workers.size)
        assertFalse(updatedPool.workers.contains(worker))
    }
    
    @Test
    fun `should get available workers`() {
        val pool = createTestWorkerPool()
        val readyWorker = createTestWorker("worker-1", WorkerStatus.READY)
        val busyWorker = createTestWorker("worker-2", WorkerStatus.BUSY)
        
        val updatedPool = pool
            .addWorker(readyWorker)
            .addWorker(busyWorker)
        
        val availableWorkers = updatedPool.getAvailableWorkers()
        
        assertEquals(1, availableWorkers.size)
        assertEquals(readyWorker, availableWorkers.first())
    }
    
    @Test
    fun `should get busy workers`() {
        val pool = createTestWorkerPool()
        val readyWorker = createTestWorker("worker-1", WorkerStatus.READY)
        val busyWorker = createTestWorker("worker-2", WorkerStatus.BUSY)
        
        val updatedPool = pool
            .addWorker(readyWorker)
            .addWorker(busyWorker)
        
        val busyWorkers = updatedPool.getBusyWorkers()
        
        assertEquals(1, busyWorkers.size)
        assertEquals(busyWorker, busyWorkers.first())
    }
    
    @Test
    fun `should check if pool can scale up`() {
        val pool = createTestWorkerPool() // maxSize = 10
        
        assertTrue(pool.canScaleUp())
        
        // Add workers to reach max size
        var updatedPool = pool
        repeat(10) { i ->  // Use pool.maxSize instead of hardcoded 5
            updatedPool = updatedPool.addWorker(createTestWorker("worker-$i"))
        }
        
        assertTrue(!updatedPool.canScaleUp()) // Should not be able to scale up when at max size
    }
    
    @Test
    fun `should check if pool can scale down`() {
        val pool = createTestWorkerPool() // minWorkers = 1
        
        assertFalse(pool.canScaleDown()) // currentSize = 0, minWorkers = 1
        
        val updatedPool = pool.addWorker(createTestWorker())
        
        assertFalse(updatedPool.canScaleDown()) // currentSize = 1, minWorkers = 1
        
        val poolWithTwoWorkers = updatedPool.addWorker(createTestWorker("worker-2"))
        
        assertTrue(poolWithTwoWorkers.canScaleDown()) // currentSize = 2, minWorkers = 1
    }
    
    @Test
    fun `should calculate desired size based on queue length`() {
        val pool = createTestWorkerPool()
        
        // Queue length exceeds scale up threshold (3)
        val desiredSizeScaleUp = pool.calculateDesiredSize(queueLength = 5, avgWaitTime = Duration.ofMinutes(1))
        assertEquals(1, desiredSizeScaleUp) // Should scale up from 0 to 1
        
        val poolWithWorkers = pool
            .addWorker(createTestWorker("worker-1"))
            .addWorker(createTestWorker("worker-2"))
        
        // Queue length is zero, should scale down
        val desiredSizeScaleDown = poolWithWorkers.calculateDesiredSize(queueLength = 0, avgWaitTime = Duration.ofSeconds(10))
        assertEquals(1, desiredSizeScaleDown) // Should scale down to minWorkers (1)
    }
    
    @Test
    fun `should update worker status in pool`() {
        val pool = createTestWorkerPool()
        val worker = createTestWorker("worker-1", WorkerStatus.READY)
        val poolWithWorker = pool.addWorker(worker)
        
        val updatedPool = poolWithWorker.updateWorkerStatus(worker.id, WorkerStatus.BUSY)
        
        val updatedWorker = updatedPool.workers.first { it.id == worker.id }
        assertEquals(WorkerStatus.BUSY, updatedWorker.status)
    }
    
    @Test
    fun `should not update non-existent worker status`() {
        val pool = createTestWorkerPool()
        val nonExistentWorkerId = WorkerId("non-existent")
        
        val updatedPool = pool.updateWorkerStatus(nonExistentWorkerId, WorkerStatus.BUSY)
        
        // Pool should remain unchanged except for lastModified timestamp
        assertEquals(pool.id, updatedPool.id)
        assertEquals(pool.name, updatedPool.name)
        assertEquals(pool.workers, updatedPool.workers)
        assertEquals(pool.currentSize, updatedPool.currentSize)
    }
}