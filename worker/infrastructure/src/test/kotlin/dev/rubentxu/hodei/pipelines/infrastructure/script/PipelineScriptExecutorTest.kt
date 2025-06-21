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

    /**
     * Función de ayuda para extraer todo el texto de los eventos OutputReceived
     */
    private fun extractOutputText(events: List<JobExecutionEvent>): String {
        return events
            .filterIsInstance<JobExecutionEvent.OutputReceived>()
            .joinToString("\n") { String(it.chunk.data) }
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
        // Verificamos que hay al menos un evento OutputReceived
        val outputEvents = events.filterIsInstance<JobExecutionEvent.OutputReceived>()
        assertFalse(outputEvents.isEmpty(), "Debería haber eventos OutputReceived")

        // Verificamos el contenido combinado de todos los eventos OutputReceived
        val outputText = extractOutputText(events)
        assertTrue(outputText.contains("Hello from script!"),
            "La salida debería contener el texto del println: $outputText")

        // Verificamos que el último evento es Completed con exitCode 0
        val completedEvent = events.last()
        assertTrue(completedEvent is JobExecutionEvent.Completed,
            "El último evento debería ser Completed, pero fue ${completedEvent::class.simpleName}")
        assertEquals(0, (completedEvent as JobExecutionEvent.Completed).exitCode)
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
        // Verificamos que el último evento es Failed
        val failedEvent = events.last()
        assertTrue(failedEvent is JobExecutionEvent.Failed,
            "El último evento debería ser Failed, pero fue ${failedEvent::class.simpleName}")

        // Verificamos que el mensaje de error contiene la información del error de compilación
        assertTrue((failedEvent as JobExecutionEvent.Failed).error.contains("Initializer type mismatch"),
            "El mensaje de error debería contener información sobre el error de tipo")
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
        // Verificamos que hay al menos un evento OutputReceived
        val outputEvents = events.filterIsInstance<JobExecutionEvent.OutputReceived>()
        assertFalse(outputEvents.isEmpty(), "Debería haber eventos OutputReceived")

        // Verificamos que el mensaje "About to throw..." aparece en la salida
        val outputText = extractOutputText(events)
        assertTrue(outputText.contains("About to throw an exception"),
            "La salida debería contener el texto antes de la excepción")

        // Verificamos que el último evento es Failed con el mensaje de error
        val failedEvent = events.last()
        assertTrue(failedEvent is JobExecutionEvent.Failed)
        assertTrue((failedEvent as JobExecutionEvent.Failed).error.contains("BOOM!"),
            "El mensaje de error debería contener el texto de la excepción")
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
        // Verificamos la salida de los eventos OutputReceived
        val outputText = extractOutputText(events)
        assertTrue(outputText.contains("Hello from sh"),
            "La salida debería contener el texto del comando sh")
        assertTrue(outputText.contains("sh command output:"),
            "La salida debería contener el texto de confirmación")

        // Verificamos que el último evento es Completed
        val completedEvent = events.last()
        assertTrue(completedEvent is JobExecutionEvent.Completed)
        assertEquals(0, (completedEvent as JobExecutionEvent.Completed).exitCode)
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
        // Buscamos eventos de error en OutputReceived
        val errorEvents = events.filterIsInstance<JobExecutionEvent.OutputReceived>()
            .filter { it.chunk.isError }

        // Verificamos que hay al menos un evento de error
        assertFalse(errorEvents.isEmpty(), "Debería haber al menos un evento OutputReceived marcado como error")

        // Verificamos el mensaje de error en los eventos
        val errorText = errorEvents
            .joinToString("\n") { String(it.chunk.data) }
        assertTrue(errorText.contains("failed with exit code 1"),
            "La salida de error debería contener información sobre el código de salida")

        // Verificamos que el último evento es Failed
        val failedEvent = events.last()
        assertTrue(failedEvent is JobExecutionEvent.Failed)
        assertTrue((failedEvent as JobExecutionEvent.Failed).error.contains("failed with exit code 1"))
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
        // Extraemos toda la salida para verificar el orden de ejecución
        val outputText = extractOutputText(events)

        // Verificamos que ambas tareas se ejecutaron
        assertTrue(outputText.contains("Task A executed"),
            "La salida debería mostrar que la tarea A se ejecutó")
        assertTrue(outputText.contains("Task B executed"),
            "La salida debería mostrar que la tarea B se ejecutó")

        // Verificamos el orden de ejecución (A antes que B)
        val indexA = outputText.indexOf("Task A executed")
        val indexB = outputText.indexOf("Task B executed")
        assertTrue(indexA < indexB,
            "La tarea A debería ejecutarse antes que la tarea B")

        // Verificamos que el último evento es Completed
        val completedEvent = events.last()
        assertTrue(completedEvent is JobExecutionEvent.Completed)
        assertEquals(0, (completedEvent as JobExecutionEvent.Completed).exitCode)
    }
}