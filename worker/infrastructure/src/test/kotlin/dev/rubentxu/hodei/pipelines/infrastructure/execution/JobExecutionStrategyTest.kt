package dev.rubentxu.hodei.pipelines.infrastructure.execution

import dev.rubentxu.hodei.pipelines.domain.job.*
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies.KotlinScriptingStrategy
import dev.rubentxu.hodei.pipelines.infrastructure.execution.strategies.SystemCommandStrategy
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class JobExecutionStrategyTest {
    
    private lateinit var strategyManager: ExecutionStrategyManager
    private val workerId = WorkerId("test-worker")
    private val outputChunks = mutableListOf<JobOutputChunk>()
    
    @BeforeEach
    fun setup() {
        strategyManager = DefaultExecutionStrategyManager()
        outputChunks.clear()
    }
    
    @Test
    fun `should register and retrieve strategies`() {
        // Given
        val scriptStrategy = KotlinScriptingStrategy()
        val commandStrategy = SystemCommandStrategy()
        
        // When
        strategyManager.registerStrategy(JobType.SCRIPT, scriptStrategy)
        strategyManager.registerStrategy(JobType.COMMAND, commandStrategy)
        
        // Then
        assertEquals(scriptStrategy, strategyManager.getStrategy(JobType.SCRIPT))
        assertEquals(commandStrategy, strategyManager.getStrategy(JobType.COMMAND))
        assertEquals(setOf(JobType.SCRIPT, JobType.COMMAND), strategyManager.getSupportedJobTypes())
    }
    
    @Test
    fun `should throw exception for unsupported job type`() {
        // When & Then
        assertThrows(IllegalArgumentException::class.java) {
            strategyManager.getStrategy(JobType.DOCKER)
        }
    }
    
    @Test
    fun `system command strategy should execute simple command`() = runBlocking {
        // Given
        val strategy = SystemCommandStrategy()
        val job = createTestJob(
            payload = JobPayload.Command(listOf("true")) // Simple command that always succeeds
        )
        
        // When
        val result = strategy.execute(job, workerId) { chunk ->
            outputChunks.add(chunk)
        }
        
        // Then
        assertEquals(JobStatus.COMPLETED, result.status)
        assertEquals(0, result.exitCode)
    }
    
    @Test
    fun `system command strategy should handle command failure`() = runBlocking {
        // Given
        val strategy = SystemCommandStrategy()
        val job = createTestJob(
            payload = JobPayload.Command(listOf("false")) // Command that always fails
        )
        
        // When
        val result = strategy.execute(job, workerId) { chunk ->
            outputChunks.add(chunk)
        }
        
        // Then
        assertEquals(JobStatus.FAILED, result.status)
        assertNotEquals(0, result.exitCode)
        assertNotNull(result.errorMessage)
    }
    
    @Test
    fun `kotlin scripting strategy should execute simple script`() = runBlocking {
        // Given
        val strategy = KotlinScriptingStrategy()
        val job = createTestJob(
            payload = JobPayload.Script("println(\"Hello from Kotlin!\")")
        )
        
        // When
        val result = strategy.execute(job, workerId) { chunk ->
            outputChunks.add(chunk)
        }
        
        // Then
        assertEquals(JobStatus.COMPLETED, result.status)
        assertEquals(0, result.exitCode)
        assertTrue(outputChunks.any { String(it.data).contains("Hello from Kotlin!") })
    }
    
    @Test
    fun `kotlin scripting strategy should handle script compilation error`() = runBlocking {
        // Given
        val strategy = KotlinScriptingStrategy()
        val job = createTestJob(
            payload = JobPayload.Script("invalid kotlin syntax !!!")
        )
        
        // When
        val result = strategy.execute(job, workerId) { chunk ->
            outputChunks.add(chunk)
        }
        
        // Then
        assertEquals(JobStatus.FAILED, result.status)
        assertNotNull(result.errorMessage)
        assertTrue(result.errorMessage!!.contains("compilation failed"))
    }
    
    @Test
    fun `strategy should support correct job types`() {
        // Given
        val scriptStrategy = KotlinScriptingStrategy()
        val commandStrategy = SystemCommandStrategy()
        
        // Then
        assertTrue(scriptStrategy.canHandle(JobType.SCRIPT))
        assertFalse(scriptStrategy.canHandle(JobType.COMMAND))
        
        assertTrue(commandStrategy.canHandle(JobType.COMMAND))
        assertFalse(commandStrategy.canHandle(JobType.SCRIPT))
    }
    
    private fun createTestJob(
        payload: JobPayload,
        environment: Map<String, String> = emptyMap()
    ): Job {
        return Job(
            id = JobId.generate(),
            definition = JobDefinition(
                name = "test-job",
                payload = payload,
                environment = environment,
                workingDirectory = System.getProperty("java.io.tmpdir")
            )
        )
    }
}