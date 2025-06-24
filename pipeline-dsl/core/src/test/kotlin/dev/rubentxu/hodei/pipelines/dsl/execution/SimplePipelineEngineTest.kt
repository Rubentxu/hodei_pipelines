package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.model.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests simplificados para Pipeline Engine
 */
class SimplePipelineEngineTest {

    private val pipelineEngine = PipelineEngine()

    @Test
    fun `should execute simple echo pipeline`() = runTest {
        // Given
        val pipeline = Pipeline(
            name = "Echo Test",
            stages = listOf(
                Stage(
                    name = "Echo Stage",
                    steps = listOf(
                        Step.Echo(message = "Hello World")
                    )
                )
            )
        )

        val jobId = "echo-test"
        val workerId = "test-worker"
        val outputChannel = Channel<PipelineOutputChunk>(Channel.UNLIMITED)
        val eventChannel = Channel<PipelineExecutionEvent>(Channel.UNLIMITED)

        // When
        val result = pipelineEngine.execute(
            pipeline = pipeline,
            jobId = jobId,
            workerId = workerId,
            outputChannel = outputChannel,
            eventChannel = eventChannel
        )

        // Then
        assertTrue(result.success)
        assertEquals(1, result.stageResults.size)
        assertEquals("Echo Stage", result.stageResults[0].stageName)
        assertTrue(result.stageResults[0].success)

        outputChannel.close()
        eventChannel.close()
    }

    @Test
    fun `should handle multiple stages`() = runTest {
        // Given
        val pipeline = Pipeline(
            name = "Multi Stage",
            stages = listOf(
                Stage(name = "Stage 1", steps = listOf(Step.Echo(message = "1"))),
                Stage(name = "Stage 2", steps = listOf(Step.Echo(message = "2")))
            )
        )

        val jobId = "multi-test"
        val workerId = "test-worker"
        val outputChannel = Channel<PipelineOutputChunk>(Channel.UNLIMITED)
        val eventChannel = Channel<PipelineExecutionEvent>(Channel.UNLIMITED)

        // When
        val result = pipelineEngine.execute(
            pipeline = pipeline,
            jobId = jobId,
            workerId = workerId,
            outputChannel = outputChannel,
            eventChannel = eventChannel
        )

        // Then
        assertTrue(result.success)
        assertEquals(2, result.stageResults.size)

        outputChannel.close()
        eventChannel.close()
    }

    @Test
    fun `should measure execution time`() = runTest {
        // Given
        val pipeline = Pipeline(
            name = "Timing Test",
            stages = listOf(
                Stage(
                    name = "Timed Stage",
                    steps = listOf(Step.Echo(message = "Timing"))
                )
            )
        )

        val jobId = "timing-test"
        val workerId = "test-worker"
        val outputChannel = Channel<PipelineOutputChunk>(Channel.UNLIMITED)
        val eventChannel = Channel<PipelineExecutionEvent>(Channel.UNLIMITED)

        // When
        val result = pipelineEngine.execute(
            pipeline = pipeline,
            jobId = jobId,
            workerId = workerId,
            outputChannel = outputChannel,
            eventChannel = eventChannel
        )

        // Then
        assertTrue(result.success)
        assertTrue(result.duration > 0)
        assertTrue(result.stageResults[0].duration > 0)

        outputChannel.close()
        eventChannel.close()
    }
}