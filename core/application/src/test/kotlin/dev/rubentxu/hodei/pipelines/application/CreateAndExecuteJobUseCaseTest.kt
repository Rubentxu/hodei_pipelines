package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.worker.*
import dev.rubentxu.hodei.pipelines.port.*
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class CreateAndExecuteJobUseCaseTest {
    
    private val jobRepository = mockk<JobRepository>()
    private val workerRepository = mockk<WorkerRepository>()
    private val jobExecutor = mockk<JobExecutor>()
    private val eventPublisher = mockk<EventPublisher>()
    
    private val useCase = CreateAndExecuteJobUseCase(
        jobRepository = jobRepository,
        workerRepository = workerRepository,
        jobExecutor = jobExecutor,
        eventPublisher = eventPublisher
    )
    
    @Test
    fun `should create and execute job successfully`() = runTest {
        // Given
        val jobDefinition = JobDefinition(
            name = "Test Job",
            payload = JobPayload.Command(listOf("echo", "hello world")),
            workingDirectory = "/tmp"
        )
        
        val worker = Worker(
            id = WorkerId("worker-1"),
            name = "Test Worker",
            capabilities = WorkerCapabilities.builder()
                .os("linux")
                .arch("x64")
                .maxConcurrentJobs(1)
                .build()
        )
        
        val job = Job(
            id = JobId("job-1"),
            definition = jobDefinition
        )
        
        coEvery { jobRepository.save(any()) } returns Result.success(job.id)
        coEvery { workerRepository.findAvailableWorkers() } returns Result.success(listOf(worker))
        coEvery { workerRepository.save(any()) } returns Result.success(worker.assignJob().id)
        coEvery { jobExecutor.execute(any(), any()) } returns flowOf(
            JobExecutionEvent.Started(job.id, worker.id),
            JobExecutionEvent.Completed(job.id, 0, "hello world")
        )
        coEvery { eventPublisher.publishJobEvent(any()) } just Runs
        
        // When
        val request = CreateAndExecuteJobRequest(jobDefinition)
        val result = useCase.execute(request).toList()
        
        // Then
        assertTrue(result.any { it is JobExecutionResult.JobCreated })
        assertTrue(result.any { it is JobExecutionResult.JobAssigned })
        assertTrue(result.any { it is JobExecutionResult.JobStarted })
        assertTrue(result.any { it is JobExecutionResult.JobCompleted })
        
        coVerify { jobRepository.save(any()) }
        coVerify { workerRepository.findAvailableWorkers() }
        coVerify { jobExecutor.execute(any(), any()) }
        coVerify { eventPublisher.publishJobEvent(any()) }
    }
    
    @Test
    fun `should fail when no workers available`() = runTest {
        // Given
        val jobDefinition = JobDefinition(
            name = "Test Job", 
            payload = JobPayload.Command(listOf("echo", "hello")),
            workingDirectory = "/tmp"
        )
        
        val job = Job(id = JobId("job-1"), definition = jobDefinition)
        
        coEvery { jobRepository.save(any()) } returns Result.success(job.id)
        coEvery { workerRepository.findAvailableWorkers() } returns Result.success(emptyList())
        coEvery { eventPublisher.publishJobEvent(any()) } just Runs
        
        // When
        val request = CreateAndExecuteJobRequest(jobDefinition)
        val result = useCase.execute(request).toList()
        
        // Then
        assertTrue(result.any { it is JobExecutionResult.JobCreated })
        assertTrue(result.any { it is JobExecutionResult.JobFailed })
        
        val failedResult = result.find { it is JobExecutionResult.JobFailed } as JobExecutionResult.JobFailed
        assertTrue(failedResult.error.contains("No available workers"))
    }
}