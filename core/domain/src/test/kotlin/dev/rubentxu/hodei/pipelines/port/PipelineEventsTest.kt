package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.job.JobId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.time.Instant

class PipelineEventsTest {
    
    private val testJobId = JobId("test-job-123")
    
    @Test
    fun `should create stage started event`() {
        // When
        val event = PipelineEvent.StageStarted(
            jobId = testJobId,
            stageName = "build",
            stageType = StageType.BUILD,
            metadata = mapOf("branch" to "main")
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals("build", event.stageName)
        assertEquals(StageType.BUILD, event.stageType)
        assertEquals("main", event.metadata["branch"])
        assertTrue(event.timestamp.isBefore(Instant.now().plusSeconds(1)))
    }
    
    @Test
    fun `should create stage completed event`() {
        // When
        val event = PipelineEvent.StageCompleted(
            jobId = testJobId,
            stageName = "test",
            duration = 5000L,
            status = StageStatus.SUCCESS,
            output = "All tests passed"
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals("test", event.stageName)
        assertEquals(5000L, event.duration)
        assertEquals(StageStatus.SUCCESS, event.status)
        assertEquals("All tests passed", event.output)
    }
    
    @Test
    fun `should create stage failed event`() {
        // When
        val exception = RuntimeException("Build failed")
        val event = PipelineEvent.StageFailed(
            jobId = testJobId,
            stageName = "build",
            error = "Compilation error",
            cause = exception,
            duration = 2000L
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals("build", event.stageName)
        assertEquals("Compilation error", event.error)
        assertEquals(exception, event.cause)
        assertEquals(2000L, event.duration)
    }
    
    @Test
    fun `should create task started event`() {
        // When
        val event = PipelineEvent.TaskStarted(
            jobId = testJobId,
            taskName = "compile",
            stageName = "build"
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals("compile", event.taskName)
        assertEquals("build", event.stageName)
    }
    
    @Test
    fun `should create progress update event`() {
        // When
        val event = PipelineEvent.ProgressUpdate(
            jobId = testJobId,
            currentStep = 3,
            totalSteps = 10,
            message = "Running tests"
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals(3, event.currentStep)
        assertEquals(10, event.totalSteps)
        assertEquals("Running tests", event.message)
        assertEquals(30.0, event.percentage)
    }
    
    @Test
    fun `should create parallel group events`() {
        // Given
        val stageNames = listOf("unit-tests", "integration-tests", "linting")
        
        // When
        val startedEvent = PipelineEvent.ParallelGroupStarted(
            jobId = testJobId,
            groupName = "test-group",
            parallelStages = stageNames
        )
        
        val completedEvent = PipelineEvent.ParallelGroupCompleted(
            jobId = testJobId,
            groupName = "test-group",
            duration = 8000L,
            successfulStages = listOf("unit-tests", "linting"),
            failedStages = listOf("integration-tests")
        )
        
        // Then
        assertEquals(testJobId, startedEvent.jobId)
        assertEquals("test-group", startedEvent.groupName)
        assertEquals(stageNames, startedEvent.parallelStages)
        
        assertEquals(testJobId, completedEvent.jobId)
        assertEquals("test-group", completedEvent.groupName)
        assertEquals(8000L, completedEvent.duration)
        assertEquals(2, completedEvent.successfulStages.size)
        assertEquals(1, completedEvent.failedStages.size)
    }
    
    @Test
    fun `should create artifact generated event`() {
        // When
        val event = PipelineEvent.ArtifactGenerated(
            jobId = testJobId,
            artifactName = "app.jar",
            artifactPath = "/tmp/artifacts/app.jar",
            artifactSize = 1024000L,
            artifactType = "jar"
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals("app.jar", event.artifactName)
        assertEquals("/tmp/artifacts/app.jar", event.artifactPath)
        assertEquals(1024000L, event.artifactSize)
        assertEquals("jar", event.artifactType)
    }
    
    @Test
    fun `should create custom event`() {
        // When
        val event = PipelineEvent.CustomEvent(
            jobId = testJobId,
            eventType = "deployment",
            eventName = "health_check",
            data = mapOf(
                "endpoint" to "https://api.example.com/health",
                "status" to "healthy",
                "response_time" to 120
            ),
            source = "health-checker"
        )
        
        // Then
        assertEquals(testJobId, event.jobId)
        assertEquals("deployment", event.eventType)
        assertEquals("health_check", event.eventName)
        assertEquals("health-checker", event.source)
        assertEquals(3, event.data.size)
        assertEquals("healthy", event.data["status"])
        assertEquals(120, event.data["response_time"])
    }
    
    @Test
    fun `should create checkpoint and recovery events`() {
        // When
        val checkpointEvent = PipelineEvent.CheckpointCreated(
            jobId = testJobId,
            checkpointId = "checkpoint-001",
            stageName = "deploy",
            state = mapOf("version" to "1.2.3", "replicas" to 3)
        )
        
        val recoveryEvent = PipelineEvent.RecoveryInitiated(
            jobId = testJobId,
            checkpointId = "checkpoint-001",
            reason = "Pipeline failure during deployment"
        )
        
        // Then
        assertEquals(testJobId, checkpointEvent.jobId)
        assertEquals("checkpoint-001", checkpointEvent.checkpointId)
        assertEquals("deploy", checkpointEvent.stageName)
        assertEquals("1.2.3", checkpointEvent.state["version"])
        
        assertEquals(testJobId, recoveryEvent.jobId)
        assertEquals("checkpoint-001", recoveryEvent.checkpointId)
        assertEquals("Pipeline failure during deployment", recoveryEvent.reason)
    }
    
    @Test
    fun `stage types should be comprehensive`() {
        // Verify all expected stage types exist
        val expectedTypes = setOf(
            StageType.BUILD,
            StageType.TEST,
            StageType.DEPLOY,
            StageType.SETUP,
            StageType.CLEANUP,
            StageType.VALIDATION,
            StageType.SECURITY_SCAN,
            StageType.QUALITY_GATE,
            StageType.INTEGRATION,
            StageType.PACKAGING,
            StageType.NOTIFICATION,
            StageType.CUSTOM
        )
        
        assertEquals(expectedTypes, StageType.values().toSet())
    }
    
    @Test
    fun `stage status should be comprehensive`() {
        // Verify all expected stage statuses exist
        val expectedStatuses = setOf(
            StageStatus.SUCCESS,
            StageStatus.FAILURE,
            StageStatus.SKIPPED,
            StageStatus.UNSTABLE,
            StageStatus.ABORTED
        )
        
        assertEquals(expectedStatuses, StageStatus.values().toSet())
    }
    
    @Test
    fun `pipeline execution context should track state`() {
        // When
        val context = PipelineExecutionContext(
            jobId = testJobId,
            workerId = dev.rubentxu.hodei.pipelines.domain.worker.WorkerId("worker-1"),
            environment = mapOf("ENV" to "test"),
            workingDirectory = "/tmp/workspace"
        )
        
        // Then
        assertEquals(testJobId, context.jobId)
        assertEquals("worker-1", context.workerId.value)
        assertEquals("test", context.environment["ENV"])
        assertEquals("/tmp/workspace", context.workingDirectory)
        assertNull(context.currentStage)
        assertTrue(context.executedStages.isEmpty())
        assertTrue(context.artifacts.isEmpty())
        assertTrue(context.checkpoints.isEmpty())
    }
}