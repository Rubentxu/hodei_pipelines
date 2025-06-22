package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.domain.worker.*
import dev.rubentxu.hodei.pipelines.port.*
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class RegisterWorkerUseCaseTest {
    
    private val workerRepository = mockk<WorkerRepository>()
    private val eventPublisher = mockk<EventPublisher>()
    
    private val useCase = RegisterWorkerUseCase(workerRepository, eventPublisher)
    
    @Test
    fun `should register new worker successfully`() = runTest {
        // Given
        val request = RegisterWorkerRequest(
            name = "Test Worker",
            os = "linux",
            arch = "x64",
            maxConcurrentJobs = 5
        )
        
        val worker = Worker(
            id = WorkerId("generated-id"),
            name = request.name,
            capabilities = WorkerCapabilities.builder()
                .os(request.os)
                .arch(request.arch)
                .maxConcurrentJobs(request.maxConcurrentJobs)
                .build()
        )
        
        coEvery { workerRepository.save(any()) } returns Result.success(worker.id)
        coEvery { eventPublisher.publishWorkerEvent(any()) } just Runs
        
        // When
        val result = useCase.execute(request)
        
        // Then
        assertTrue(result.success)
        assertNotNull(result.workerId)
        assertNotNull(result.sessionToken)
        
        coVerify { workerRepository.save(any()) }
        coVerify { eventPublisher.publishWorkerEvent(any()) }
    }
    
    @Test
    fun `should handle worker registration failure`() = runTest {
        // Given
        val request = RegisterWorkerRequest(
            name = "Test Worker",
            os = "linux", 
            arch = "x64",
            maxConcurrentJobs = 5
        )
        
        coEvery { workerRepository.save(any()) } returns Result.failure(RuntimeException("Database error"))
        
        // When
        val result = useCase.execute(request)
        
        // Then
        assertFalse(result.success)
        assertNull(result.workerId)
        assertNull(result.sessionToken)
        assertTrue(result.errorMessage?.contains("Database error") == true)
    }
}