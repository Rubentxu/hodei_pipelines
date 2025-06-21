package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.script.PipelineScriptExecutor
import dev.rubentxu.hodei.pipelines.infrastructure.worker.PipelineWorker
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.*
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Integration tests for job execution
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobExecutionIntegrationTest {
    
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
    fun `should handle simple job execution`() = runTest {
        // Given
        val workerId = "job-execution-worker"
        
        // Mock script executor to return successful execution
        whenever(scriptExecutor.execute(any(), any())).thenReturn(
            flow {
                emit(JobExecutionEvent.Started(JobId("test-job"), WorkerId(workerId)))
                delay(100)
                emit(JobExecutionEvent.Completed(JobId("test-job"), 0, "Job completed successfully"))
            }
        )
        
        // Add a simple job to server
        val testJob = TestJobBuilder.createSimpleJob(
            id = "test-job",
            name = "Simple Integration Test Job",
            script = "echo 'Hello from integration test'"
        )
        server.addTestJob(testJob)
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Job Execution Test Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for job processing
            delay(2000)
            
            // Then
            val messages = server.jobExecutorService.getReceivedMessages()
            val jobMessages = messages.filter { 
                it.messageType == "JOB_OUTPUT_AND_STATUS" 
            }
            
            assertTrue(jobMessages.isNotEmpty()) {
                "Should have received job execution messages"
            }
        }
    }
    
    @Test
    fun `should handle job with artifacts`() = runTest {
        // Given
        val workerId = "artifact-job-worker"
        
        // Mock script executor
        whenever(scriptExecutor.execute(any(), any())).thenReturn(
            flow {
                emit(JobExecutionEvent.Started(JobId("artifact-job"), WorkerId(workerId)))
                delay(100)
                emit(JobExecutionEvent.Completed(JobId("artifact-job"), 0, "Job with artifacts completed"))
            }
        )
        
        // Create job with artifacts
        val artifacts = listOf(
            TestArtifactBuilder.createSimpleArtifact("config-1", "test.properties"),
            TestArtifactBuilder.createSimpleArtifact("config-2", "app.yaml")
        )
        
        val testJob = TestJobBuilder.createJobWithArtifacts(
            id = "artifact-job",
            name = "Job with Artifacts",
            script = "echo 'Processing artifacts'",
            artifacts = artifacts
        )
        server.addTestJob(testJob)
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Artifact Job Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for artifact transfer and job processing
            delay(3000)
            
            // Then
            val messages = server.jobExecutorService.getReceivedMessages()
            
            // Should receive cache responses
            val cacheMessages = messages.filter { it.messageType == "CACHE_RESPONSE" }
            assertTrue(cacheMessages.isNotEmpty()) {
                "Should have received cache response messages"
            }
            
            // Should receive artifact acknowledgments
            val artifactAcks = messages.filter { it.messageType == "ARTIFACT_ACK" }
            assertTrue(artifactAcks.size >= artifacts.size) {
                "Should have received artifact acknowledgments for all artifacts"
            }
            
            // Should execute job
            val jobMessages = messages.filter { it.messageType == "JOB_OUTPUT_AND_STATUS" }
            assertTrue(jobMessages.isNotEmpty()) {
                "Should have received job execution messages"
            }
        }
    }
    
    @Test
    fun `should handle cache queries correctly`() = runTest {
        // Given
        val workerId = "cache-test-worker"
        
        // Configure server to simulate cache queries
        server.configureJobExecution(
            simulateArtifactTransfer = true,
            simulateCacheQueries = true
        )
        
        // Mock script executor
        whenever(scriptExecutor.execute(any(), any())).thenReturn(
            flow {
                emit(JobExecutionEvent.Started(JobId("cache-job"), WorkerId(workerId)))
                delay(100)
                emit(JobExecutionEvent.Completed(JobId("cache-job"), 0, "Cache test completed"))
            }
        )
        
        // Create job with artifacts to trigger cache queries
        val testJob = TestJobBuilder.createJobWithArtifacts(
            id = "cache-job",
            name = "Cache Test Job",
            script = "echo 'Testing cache'",
            artifacts = listOf(TestArtifactBuilder.createSimpleArtifact())
        )
        server.addTestJob(testJob)
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Cache Test Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for cache interaction
            delay(2000)
            
            // Then
            val messages = server.jobExecutorService.getReceivedMessages()
            val cacheResponses = messages.filter { it.messageType == "CACHE_RESPONSE" }
            
            assertTrue(cacheResponses.isNotEmpty()) {
                "Should have received cache response messages"
            }
        }
    }
    
    @Test
    fun `should handle compressed artifacts`() = runTest {
        // Given
        val workerId = "compression-worker"
        
        // Mock script executor
        whenever(scriptExecutor.execute(any(), any())).thenReturn(
            flow {
                emit(JobExecutionEvent.Started(JobId("compression-job"), WorkerId(workerId)))
                delay(100)
                emit(JobExecutionEvent.Completed(JobId("compression-job"), 0, "Compression test completed"))
            }
        )
        
        // Create job with compressed artifacts
        val compressedArtifact = TestArtifactBuilder.createCompressedArtifact(
            "compressed-config",
            "large-config.yaml"
        )
        
        val testJob = TestJobBuilder.createJobWithArtifacts(
            id = "compression-job",
            name = "Compression Test Job",
            script = "echo 'Testing compression'",
            artifacts = listOf(compressedArtifact)
        )
        server.addTestJob(testJob)
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Compression Test Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for compressed artifact processing
            delay(3000)
            
            // Then
            val messages = server.jobExecutorService.getReceivedMessages()
            val artifactAcks = messages.filter { it.messageType == "ARTIFACT_ACK" }
            
            assertTrue(artifactAcks.isNotEmpty()) {
                "Should have received artifact acknowledgments for compressed artifacts"
            }
        }
    }
    
    @Test
    fun `should track execution metrics`() = runTest {
        // Given
        val workerId = "metrics-worker"
        
        // Mock script executor
        whenever(scriptExecutor.execute(any(), any())).thenReturn(
            flow {
                emit(JobExecutionEvent.Started(JobId("metrics-job"), WorkerId(workerId)))
                delay(50)
                emit(JobExecutionEvent.OutputReceived(
                    JobId("metrics-job"), 
                    JobOutputChunk(
                        data = "Test output".toByteArray(),
                        isError = false,
                        timestamp = java.time.Instant.now()
                    )
                ))
                delay(50)
                emit(JobExecutionEvent.Completed(JobId("metrics-job"), 0, "Metrics test completed"))
            }
        )
        
        val testJob = TestJobBuilder.createSimpleJob(
            id = "metrics-job",
            name = "Metrics Test Job"
        )
        server.addTestJob(testJob)
        
        // When
        val worker = PipelineWorker(
            workerId = workerId,
            workerName = "Metrics Test Worker",
            serverHost = "localhost",
            serverPort = server.port,
            scriptExecutor = scriptExecutor
        )
        
        worker.use { w ->
            w.start()
            
            // Wait for job execution
            delay(2000)
            
            // Then
            val metrics = server.getTestMetrics()
            val messages = server.jobExecutorService.getReceivedMessages()
            
            assertTrue(messages.isNotEmpty()) {
                "Should have collected execution messages for metrics"
            }
            
            val heartbeats = messages.count { it.messageType == "HEARTBEAT" }
            assertTrue(heartbeats > 0) {
                "Should have received heartbeat messages for metrics"
            }
        }
    }
}