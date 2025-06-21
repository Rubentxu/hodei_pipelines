package dev.rubentxu.hodei.pipelines.infrastructure.script


import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobPayload
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PipelineScriptExecutorTest {

    private lateinit var scriptExecutor: PipelineScriptExecutor

    @BeforeEach
    fun setUp() {
        scriptExecutor = PipelineScriptExecutor()
    }

    private fun createJob(script: String, jobId: String = "test-job-1"): Job {
        return Job(
            id = JobId(jobId),
            definition = JobDefinition(
                name = "Test Job",
                payload = JobPayload.Script(script),
                workingDirectory = "/tmp"
            )
        )
    }

    @Test
    fun `should execute a simple script successfully`() = runTest {
        // Given
        val script = """
            println("Hello from script!")
        """.trimIndent()
        val job = createJob(script)

        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()

        // Then
        val completedEvent = events.last() as? JobExecutionEvent.Completed
        assertNotNull(completedEvent)
        assertTrue(completedEvent!!.output.contains("Hello from script!"))
    }

    @Test
    fun `should handle script compilation failure`() = runTest {
        // Given
        val script = """
            println("This will not compile")
            val x: String = 123
        """.trimIndent()
        val job = createJob(script)

        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()

        // Then
        val failedEvent = events.last() as? JobExecutionEvent.Failed
        assertNotNull(failedEvent)
        assertTrue(failedEvent!!.error.contains("Initializer type mismatch: expected 'String', actual 'Int'."))
    }

    @Test
    fun `should handle script runtime exception`() = runTest {
        // Given
        val script = """
            println("About to throw an exception")
            throw RuntimeException("BOOM!")
        """.trimIndent()
        val job = createJob(script)

        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()

        // Then
        val failedEvent = events.last() as? JobExecutionEvent.Failed
        assertNotNull(failedEvent)
        assertTrue(failedEvent!!.error.contains("BOOM!"))
    }

    @Test
    fun `should execute sh command successfully`() = runTest {
        // Given
        val script = """
            val output = sh("echo 'Hello from sh'")
            println("sh command output: " + output)
        """.trimIndent()
        val job = createJob(script)

        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()

        // Then
        val completedEvent = events.last() as? JobExecutionEvent.Completed
        assertNotNull(completedEvent)
        assertTrue(completedEvent!!.output.contains("Hello from sh"))
        assertTrue(completedEvent.output.contains("sh command output: Hello from sh"))
    }

    @Test
    fun `should handle failing sh command`() = runTest {
        // Given
        val script = """
            sh("exit 1")
        """.trimIndent()
        val job = createJob(script)

        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()

        // Then
        val failedEvent = events.last() as? JobExecutionEvent.Failed
        assertNotNull(failedEvent)
        assertTrue(failedEvent!!.error.contains("failed with exit code 1"))
    }

    @Test
    fun `should execute tasks with dependencies correctly`() = runTest {
        // Given
        val script = """
            tasks.register("A") {
                doLast { println("Task A executed") }
            }
            tasks.register("B") {
                dependsOn("A")
                doLast { println("Task B executed") }
            }
            tasks.getByName("B").execute()
        """.trimIndent()
        val job = createJob(script)

        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()

        // Then
        val completedEvent = events.last() as? JobExecutionEvent.Completed
        assertNotNull(completedEvent)
        val output = completedEvent!!.output
        assertTrue(output.indexOf("Task A executed") < output.indexOf("Task B executed"))
    }
}