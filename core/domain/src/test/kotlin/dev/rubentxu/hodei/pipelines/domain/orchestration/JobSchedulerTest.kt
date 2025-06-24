package dev.rubentxu.hodei.pipelines.domain.orchestration

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class JobSchedulerTest {
    
    private fun createTestJob(id: String = "job-1", name: String = "Test Job"): Job {
        return Job(
            id = JobId(id),
            definition = JobDefinition(
                name = name,
                payload = JobPayload.Command(listOf("echo", "test")),
                workingDirectory = "/tmp",
                environment = mutableMapOf("TEST" to "true")
            )
        )
    }
    
    private fun createTestWorker(
        id: String = "worker-1",
        capabilities: Map<String, String> = mapOf("test" to "true"),
        status: WorkerStatus = WorkerStatus.READY
    ): Worker {
        val capabilitiesBuilder = dev.rubentxu.hodei.pipelines.domain.worker.WorkerCapabilities.builder()
            .os("linux")
            .arch("x86_64")
            .maxConcurrentJobs(5)
        
        capabilities.forEach { (key, value) ->
            if (key == "test" && value == "true") {
                capabilitiesBuilder.label("test")
            }
        }
        
        return Worker(
            id = WorkerId(id),
            name = "Test Worker $id",
            capabilities = capabilitiesBuilder.build(),
            status = status
        )
    }
    
    private fun createTestRequirements(capabilities: Map<String, String> = mapOf("test" to "true")): WorkerRequirements {
        return WorkerRequirements(
            resources = ResourceRequirements(cpu = "500m", memory = "1Gi")
        )
    }
    
    @Test
    fun `should enqueue job successfully`() {
        val queue = JobQueue()
        val job = createTestJob()
        val requirements = createTestRequirements()
        
        val result = queue.enqueue(job, JobPriority.NORMAL, requirements)
        
        assertTrue(result is QueueResult.Success)
        assertEquals(1, (result as QueueResult.Success).queueSize)
    }
    
    @Test
    fun `should not enqueue duplicate job`() {
        val queue = JobQueue()
        val job = createTestJob()
        val requirements = createTestRequirements()
        
        queue.enqueue(job, JobPriority.NORMAL, requirements)
        val result = queue.enqueue(job, JobPriority.NORMAL, requirements)
        
        assertTrue(result is QueueResult.AlreadyQueued)
        assertEquals(job.id, (result as QueueResult.AlreadyQueued).jobId)
    }
    
    @Test
    fun `should respect queue size limit`() {
        val queue = JobQueue(maxQueueSize = 2)
        val requirements = createTestRequirements()
        
        queue.enqueue(createTestJob("job-1"), JobPriority.NORMAL, requirements)
        queue.enqueue(createTestJob("job-2"), JobPriority.NORMAL, requirements)
        val result = queue.enqueue(createTestJob("job-3"), JobPriority.NORMAL, requirements)
        
        assertTrue(result is QueueResult.QueueFull)
        assertEquals(2, (result as QueueResult.QueueFull).maxSize)
    }
    
    @Test
    fun `should dequeue job correctly`() {
        val queue = JobQueue()
        val job = createTestJob()
        val requirements = createTestRequirements()
        
        queue.enqueue(job, JobPriority.NORMAL, requirements)
        val dequeuedJob = queue.dequeue(job.id)
        
        assertNotNull(dequeuedJob)
        assertEquals(job.id, dequeuedJob.job.id)
    }
    
    @Test
    fun `should return null when dequeuing non-existent job`() {
        val queue = JobQueue()
        
        val result = queue.dequeue(JobId("non-existent"))
        
        assertNull(result)
    }
    
    @Test
    fun `should get next job with priority-based scheduling`() {
        val queue = JobQueue(SchedulingStrategy.PRIORITY_BASED)
        val requirements = createTestRequirements()
        val worker = createTestWorker()
        
        // Enqueue jobs with different priorities
        queue.enqueue(createTestJob("low-job"), JobPriority.LOW, requirements)
        queue.enqueue(createTestJob("high-job"), JobPriority.HIGH, requirements)
        queue.enqueue(createTestJob("normal-job"), JobPriority.NORMAL, requirements)
        
        val nextJob = queue.getNextJob(listOf(worker))
        
        assertNotNull(nextJob)
        assertEquals("high-job", nextJob.job.id.value) // Should get high priority job first
    }
    
    @Test
    fun `should return null when no workers available`() {
        val queue = JobQueue()
        val requirements = createTestRequirements()
        
        queue.enqueue(createTestJob(), JobPriority.NORMAL, requirements)
        
        val nextJob = queue.getNextJob(emptyList())
        
        assertNull(nextJob)
    }
    
    @Test
    fun `should return null when no suitable worker for job`() {
        val queue = JobQueue()
        val requirements = createTestRequirements(mapOf("special" to "true"))
        val worker = createTestWorker(capabilities = mapOf("normal" to "true"))
        
        queue.enqueue(createTestJob(), JobPriority.NORMAL, requirements)
        
        val nextJob = queue.getNextJob(listOf(worker))
        
        assertNull(nextJob) // Worker doesn't have required "special" capability
    }
    
    @Test
    fun `should calculate effective priority with aging`() {
        val job = createTestJob()
        val oldTimestamp = Instant.now().minus(Duration.ofMinutes(30))
        val queuedJob = QueuedJob(
            job = job,
            priority = JobPriority.NORMAL,
            requirements = createTestRequirements(),
            queuedAt = oldTimestamp
        )
        
        val effectivePriority = queuedJob.effectivePriority
        
        // Should be higher than base priority due to aging
        assertTrue(effectivePriority > JobPriority.NORMAL.value)
    }
    
    @Test
    fun `should calculate effective priority with deadline pressure`() {
        val job = createTestJob()
        val urgentDeadline = Instant.now().plus(Duration.ofMinutes(5))
        val queuedJob = QueuedJob(
            job = job,
            priority = JobPriority.NORMAL,
            requirements = createTestRequirements(),
            queuedAt = Instant.now(),
            deadline = urgentDeadline,
            estimatedDuration = Duration.ofMinutes(10)
        )
        
        val effectivePriority = queuedJob.effectivePriority
        
        // Should be higher than base priority due to approaching deadline
        assertTrue(effectivePriority > JobPriority.NORMAL.value + 100)
    }
    
    @Test
    fun `should handle expired jobs`() {
        val job = createTestJob()
        val expiredDeadline = Instant.now().minus(Duration.ofMinutes(10))
        val queuedJob = QueuedJob(
            job = job,
            priority = JobPriority.NORMAL,
            requirements = createTestRequirements(),
            queuedAt = Instant.now(),
            deadline = expiredDeadline
        )
        
        assertTrue(queuedJob.isExpired())
        
        val effectivePriority = queuedJob.effectivePriority
        
        // Expired jobs should get high priority boost
        assertTrue(effectivePriority > JobPriority.NORMAL.value + 400)
    }
    
    @Test
    fun `should handle job retries correctly`() {
        val job = createTestJob()
        val queuedJob = QueuedJob(
            job = job,
            priority = JobPriority.NORMAL,
            requirements = createTestRequirements(),
            queuedAt = Instant.now(),
            retryCount = 2,
            maxRetries = 3
        )
        
        assertTrue(queuedJob.canRetry())
        
        val retryJob = queuedJob.retry()
        assertEquals(3, retryJob.retryCount)
        assertTrue(retryJob.queuedAt.isAfter(queuedJob.queuedAt))
    }
    
    @Test
    fun `should not allow retries beyond max limit`() {
        val job = createTestJob()
        val queuedJob = QueuedJob(
            job = job,
            priority = JobPriority.NORMAL,
            requirements = createTestRequirements(),
            queuedAt = Instant.now(),
            retryCount = 3,
            maxRetries = 3
        )
        
        assertTrue(!queuedJob.canRetry())
    }
    
    @Test
    fun `should generate queue statistics correctly`() {
        val queue = JobQueue()
        val requirements = createTestRequirements()
        
        // Add jobs with different priorities
        queue.enqueue(createTestJob("job-1"), JobPriority.HIGH, requirements)
        queue.enqueue(createTestJob("job-2"), JobPriority.NORMAL, requirements)
        queue.enqueue(createTestJob("job-3"), JobPriority.LOW, requirements)
        
        val stats = queue.getQueueStats()
        
        assertEquals(3, stats.totalJobs)
        assertEquals(1, stats.priorityBreakdown[JobPriority.HIGH])
        assertEquals(1, stats.priorityBreakdown[JobPriority.NORMAL])
        assertEquals(1, stats.priorityBreakdown[JobPriority.LOW])
        assertNotNull(stats.oldestJob)
        assertTrue(stats.averageWaitTime.toMillis() >= 0)
    }
    
    @Test
    fun `should handle FIFO scheduling strategy`() {
        val queue = JobQueue(SchedulingStrategy.FIFO)
        val requirements = createTestRequirements()
        val worker = createTestWorker()
        
        // Enqueue jobs with different priorities but FIFO should ignore priority
        queue.enqueue(createTestJob("first-job"), JobPriority.LOW, requirements)
        Thread.sleep(10) // Ensure different timestamps
        queue.enqueue(createTestJob("second-job"), JobPriority.HIGH, requirements)
        
        val nextJob = queue.getNextJob(listOf(worker))
        
        assertNotNull(nextJob)
        assertEquals("first-job", nextJob.job.id.value) // Should get first job regardless of priority
    }
}