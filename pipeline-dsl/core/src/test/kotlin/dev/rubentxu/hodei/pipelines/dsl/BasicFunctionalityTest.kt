package dev.rubentxu.hodei.pipelines.dsl

import dev.rubentxu.hodei.pipelines.domain.job.*
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineEngine
import dev.rubentxu.hodei.pipelines.dsl.integration.PipelineDslStrategy
import dev.rubentxu.hodei.pipelines.dsl.model.*
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests básicos de funcionalidad para verificar que el Pipeline DSL funciona correctamente.
 */
class BasicFunctionalityTest {

    @Test
    fun `should create simple pipeline with DSL`() {
        // Given/When
        val pipeline = pipeline("Test Pipeline") {
            stages {
                stage("Test Stage") {
                    steps {
                        echo("Hello World")
                    }
                }
            }
        }

        // Then
        assertEquals("Test Pipeline", pipeline.name)
        assertEquals(1, pipeline.stages.size)
        assertEquals("Test Stage", pipeline.stages[0].name)
        assertEquals(1, pipeline.stages[0].steps.size)
        assertTrue(pipeline.stages[0].steps[0] is Step.Echo)
    }

    @Test
    fun `should execute simple pipeline`() = runTest {
        // Given
        val pipeline = Pipeline(
            name = "Simple Test",
            stages = listOf(
                Stage(
                    name = "Test Stage",
                    steps = listOf(Step.Echo(message = "Test message"))
                )
            )
        )

        val engine = PipelineEngine()
        val jobId = JobId("test-job")
        val workerId = WorkerId("test-worker")
        val outputChannel = Channel<JobOutputChunk>(Channel.UNLIMITED)
        val eventChannel = Channel<JobExecutionEvent>(Channel.UNLIMITED)

        // When
        val result = engine.execute(
            pipeline = pipeline,
            jobId = jobId,
            workerId = workerId,
            outputChannel = outputChannel,
            eventChannel = eventChannel
        )

        // Then
        assertTrue(result.success)
        assertEquals(1, result.stageResults.size)
        assertTrue(result.duration > 0)
        
        outputChannel.close()
        eventChannel.close()
    }

    @Test
    fun `should convert script job to pipeline DSL`() = runTest {
        // Given
        val job = Job(
            id = JobId("script-test"),
            definition = JobDefinition(
                name = "Script Test Job",
                payload = JobPayload.Script("println('Test script')"),
                environment = emptyMap(),
                workingDirectory = "/tmp"
            )
        )
        val workerId = WorkerId("test-worker")
        val strategy = PipelineDslStrategy()

        // When
        val result = strategy.execute(job, workerId) { }

        // Then
        assertEquals(JobStatus.COMPLETED, result.status)
        assertEquals(0, result.exitCode)
        assertNotNull(result.output)
    }

    @Test
    fun `should convert command job to pipeline DSL`() = runTest {
        // Given
        val job = Job(
            id = JobId("command-test"),
            definition = JobDefinition(
                name = "Command Test Job",
                payload = JobPayload.Command(listOf("echo", "Test command")),
                environment = emptyMap(),
                workingDirectory = "/tmp"
            )
        )
        val workerId = WorkerId("test-worker")
        val strategy = PipelineDslStrategy()

        // When
        val result = strategy.execute(job, workerId) { }

        // Then
        assertEquals(JobStatus.COMPLETED, result.status)
        assertEquals(0, result.exitCode)
        assertNotNull(result.output)
    }

    @Test
    fun `should handle job types correctly`() {
        // Given
        val strategy = PipelineDslStrategy()

        // When/Then
        assertTrue(strategy.canHandle(JobType.SCRIPT))
        assertTrue(strategy.canHandle(JobType.COMMAND))
        assertTrue(strategy.canHandle(JobType.COMPILED_SCRIPT))

        val supportedTypes = strategy.getSupportedJobTypes()
        assertEquals(3, supportedTypes.size)
        assertTrue(supportedTypes.contains(JobType.SCRIPT))
        assertTrue(supportedTypes.contains(JobType.COMMAND))
        assertTrue(supportedTypes.contains(JobType.COMPILED_SCRIPT))
    }

    @Test
    fun `should provide migration information`() {
        // Given/When
        val migrationInfo = dev.rubentxu.hodei.pipelines.dsl.integration.WorkerStrategyFactory.getMigrationInfo()

        // Then
        assertNotNull(migrationInfo)
        assertTrue(migrationInfo.containsKey("replacedStrategies"))
        assertTrue(migrationInfo.containsKey("newUnifiedStrategy"))
        assertTrue(migrationInfo.containsKey("benefits"))
        
        assertEquals("PipelineDslStrategy", migrationInfo["newUnifiedStrategy"])
    }
}