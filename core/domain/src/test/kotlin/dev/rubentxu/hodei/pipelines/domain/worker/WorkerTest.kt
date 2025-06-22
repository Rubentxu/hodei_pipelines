package dev.rubentxu.hodei.pipelines.domain.worker

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class WorkerTest {
    
    @Test
    fun `should create worker with required fields`() {
        val id = WorkerId("worker-123")
        val name = "Test Worker"
        val capabilities = WorkerCapabilities.builder()
            .os("linux")
            .arch("x64")
            .maxConcurrentJobs(5)
            .build()
        
        val worker = Worker(
            id = id,
            name = name,
            capabilities = capabilities
        )
        
        assertEquals(id, worker.id)
        assertEquals(name, worker.name)
        assertEquals(capabilities, worker.capabilities)
        assertEquals(WorkerStatus.IDLE, worker.status)
        assertEquals(0, worker.activeJobs)
        assertTrue(worker.createdAt.isBefore(Instant.now().plusSeconds(1)))
    }
    
    @Test
    fun `should update heartbeat timestamp`() {
        val worker = createTestWorker()
        val originalHeartbeat = worker.lastHeartbeat
        
        Thread.sleep(10) // Ensure time difference
        val updatedWorker = worker.updateHeartbeat()
        
        assertTrue(updatedWorker.lastHeartbeat.isAfter(originalHeartbeat))
    }
    
    @Test
    fun `should transition to busy status when assigned job`() {
        val worker = createTestWorker()
        
        val busyWorker = worker.assignJob()
        
        assertEquals(WorkerStatus.BUSY, busyWorker.status)
        assertEquals(1, busyWorker.activeJobs)
    }
    
    @Test
    fun `should return to idle when no active jobs`() {
        val worker = createTestWorker().assignJob()
        
        val idleWorker = worker.completeJob()
        
        assertEquals(WorkerStatus.IDLE, idleWorker.status)
        assertEquals(0, idleWorker.activeJobs)
    }
    
    @Test
    fun `should handle multiple concurrent jobs`() {
        val worker = createTestWorker()
            .assignJob()
            .assignJob()
            .assignJob()
        
        assertEquals(WorkerStatus.BUSY, worker.status)
        assertEquals(3, worker.activeJobs)
        
        val workerAfterCompletion = worker.completeJob()
        assertEquals(WorkerStatus.BUSY, workerAfterCompletion.status)
        assertEquals(2, workerAfterCompletion.activeJobs)
    }
    
    @Test
    fun `should respect max concurrent jobs limit`() {
        val capabilities = WorkerCapabilities.builder()
            .os("linux")
            .arch("x64")
            .maxConcurrentJobs(2)
            .build()
        val worker = Worker(
            id = WorkerId("test"),
            name = "Test",
            capabilities = capabilities
        ).assignJob().assignJob()
        
        assertThrows(IllegalStateException::class.java) {
            worker.assignJob()
        }
    }
    
    @Test
    fun `should check if worker can accept job based on capabilities`() {
        val worker = createTestWorker()
        
        assertTrue(worker.canAcceptJob("linux", "x64"))
        assertFalse(worker.canAcceptJob("windows", "x64"))
        assertFalse(worker.canAcceptJob("linux", "arm64"))
    }
    
    @Test
    fun `should transition to offline status`() {
        val worker = createTestWorker()
        
        val offlineWorker = worker.goOffline()
        
        assertEquals(WorkerStatus.OFFLINE, offlineWorker.status)
    }
    
    @Test
    fun `should validate status transitions`() {
        val offlineWorker = createTestWorker().goOffline()
        
        // Cannot assign job to offline worker
        assertThrows(IllegalStateException::class.java) {
            offlineWorker.assignJob()
        }
    }
    
    private fun createTestWorker(): Worker {
        return Worker(
            id = WorkerId("test-worker"),
            name = "Test Worker",
            capabilities = WorkerCapabilities.builder()
                .os("linux")
                .arch("x64")
                .maxConcurrentJobs(5)
                .build()
        )
    }
}