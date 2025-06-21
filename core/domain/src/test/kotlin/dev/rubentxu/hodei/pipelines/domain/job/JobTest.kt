package dev.rubentxu.hodei.pipelines.domain.job

import dev.rubentxu.hodei.pipelines.domain.job.JobPayload

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class JobTest {
    
    @Test
    fun `should create job with required fields`() {
        val id = JobId("job-123")
        val definition = JobDefinition(
            name = "Test Job",
            payload = JobPayload.Command(listOf("echo", "hello world")),
            workingDirectory = "/tmp"
        )
        
        val job = Job(
            id = id,
            definition = definition
        )
        
        assertEquals(id, job.id)
        assertEquals(definition, job.definition)
        assertEquals(JobStatus.QUEUED, job.status)
        assertTrue(job.createdAt.isBefore(Instant.now().plusSeconds(1)))
    }
    
    @Test
    fun `should start job execution`() {
        val job = createTestJob()
        
        val startedJob = job.start()
        
        assertEquals(JobStatus.RUNNING, startedJob.status)
        assertTrue(startedJob.startedAt != null)
        assertTrue(startedJob.updatedAt.isAfter(job.updatedAt))
    }
    
    @Test
    fun `should complete job successfully`() {
        val job = createTestJob().start()
        val exitCode = 0
        val output = "Job completed successfully"
        
        val completedJob = job.complete(exitCode, output)
        
        assertEquals(JobStatus.COMPLETED, completedJob.status)
        assertEquals(exitCode, completedJob.exitCode)
        assertEquals(output, completedJob.output)
        assertTrue(completedJob.completedAt != null)
        assertTrue(completedJob.updatedAt.isAfter(job.updatedAt))
    }
    
    @Test
    fun `should fail job with error`() {
        val job = createTestJob().start()
        val errorMessage = "Job execution failed"
        val exitCode = 1
        
        val failedJob = job.fail(errorMessage, exitCode)
        
        assertEquals(JobStatus.FAILED, failedJob.status)
        assertEquals(errorMessage, failedJob.errorMessage)
        assertEquals(exitCode, failedJob.exitCode)
        assertTrue(failedJob.completedAt != null)
    }
    
    @Test
    fun `should cancel job`() {
        val job = createTestJob()
        
        val cancelledJob = job.cancel()
        
        assertEquals(JobStatus.CANCELLED, cancelledJob.status)
        assertTrue(cancelledJob.updatedAt.isAfter(job.updatedAt))
    }
    
    @Test
    fun `should not allow invalid state transitions`() {
        val completedJob = createTestJob().start().complete(0, "done")
        
        assertThrows(IllegalStateException::class.java) {
            completedJob.start()
        }
        
        assertThrows(IllegalStateException::class.java) {
            completedJob.cancel()
        }
    }
    
    private fun createTestJob(): Job {
        return Job(
            id = JobId("test-job"),
            definition = JobDefinition(
                name = "Test Job",
                payload = JobPayload.Command(listOf("echo", "test")),
                workingDirectory = "/tmp"
            )
        )
    }
}