package dev.rubentxu.hodei.pipelines.steps.e2e

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.execution.CommandExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.context.StreamingOutputContext
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepExecutorRegistry
import dev.rubentxu.hodei.pipelines.dsl.extensions.ExtensionRegistry
import dev.rubentxu.hodei.pipelines.dsl.library.PipelineLibraryManager
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import dev.rubentxu.hodei.pipelines.dsl.security.PipelineSandbox
import dev.rubentxu.hodei.pipelines.dsl.security.SecurityPolicy
import dev.rubentxu.hodei.pipelines.steps.basic.WorkflowBasicStepsExtension
import dev.rubentxu.hodei.pipelines.steps.utility.PipelineUtilityStepsExtension
import dev.rubentxu.hodei.pipelines.steps.registration.StepsLibraryExtension
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Base class para tests E2E de la librería de steps.
 * Configura un entorno completo de ejecución con todas las extensiones.
 */
abstract class E2ETestBase {
    
    @TempDir
    lateinit var tempDir: Path
    
    protected lateinit var workingDirectory: File
    protected lateinit var outputChannel: Channel<PipelineOutputChunk>
    protected lateinit var eventChannel: Channel<PipelineExecutionEvent>
    protected lateinit var stepExecutorRegistry: StepExecutorRegistry
    protected lateinit var extensionRegistry: ExtensionRegistry
    protected lateinit var pipelineContext: PipelineContext
    
    // Collectors para verificar output y eventos
    protected val outputCollector = mutableListOf<PipelineOutputChunk>()
    protected val eventCollector = mutableListOf<PipelineExecutionEvent>()
    protected val outputMutex = Mutex()
    protected val eventMutex = Mutex()
    
    @BeforeEach
    fun setUp() {
        workingDirectory = tempDir.toFile()
        
        // Crear channels para output y eventos
        outputChannel = Channel(UNLIMITED)
        eventChannel = Channel(UNLIMITED)
        
        // Configurar registry de ejecutores
        stepExecutorRegistry = StepExecutorRegistry()
        
        // Configurar registry de extensiones
        val libraryManager = PipelineLibraryManager()
        extensionRegistry = ExtensionRegistry(stepExecutorRegistry, libraryManager)
        
        // Registrar extensiones de la librería
        extensionRegistry.registerExtension(WorkflowBasicStepsExtension())
        extensionRegistry.registerExtension(PipelineUtilityStepsExtension())
        extensionRegistry.registerExtension(StepsLibraryExtension())
        
        // Crear contexto del pipeline
        pipelineContext = PipelineContext(
            jobId = "test-job-${System.currentTimeMillis()}",
            workerId = "test-worker",
            workingDirectory = workingDirectory,
            environment = mutableMapOf(
                "TEST_MODE" to "true",
                "WORKSPACE" to workingDirectory.absolutePath,
                "BUILD_NUMBER" to "1",
                "JOB_NAME" to "test-pipeline"
            ),
            outputChannel = outputChannel,
            eventChannel = eventChannel,
            libraryManager = libraryManager,
            securityManager = PipelineSandbox(SecurityPolicy.permissive()), // Permissive para tests
            commandExecutor = CommandExecutor(
                outputChannel = outputChannel,
                eventChannel = eventChannel,
                securityManager = PipelineSandbox(SecurityPolicy.permissive())
            )
        )
        
        // Iniciar collectors en background
        startCollectors()
    }
    
    @AfterEach
    fun tearDown() {
        runBlocking {
            // Cerrar channels
            outputChannel.close()
            eventChannel.close()
            
            // Cleanup del workspace
            try {
                workingDirectory.deleteRecursively()
            } catch (e: Exception) {
                // Ignore cleanup errors in tests
            }
        }
    }
    
    /**
     * Inicia los collectors para capturar output y eventos en background.
     */
    private fun startCollectors() {
        // Output collector
        kotlinx.coroutines.GlobalScope.launch {
            for (chunk in outputChannel) {
                outputMutex.withLock {
                    outputCollector.add(chunk)
                }
            }
        }
        
        // Event collector
        kotlinx.coroutines.GlobalScope.launch {
            for (event in eventChannel) {
                eventMutex.withLock {
                    eventCollector.add(event)
                }
            }
        }
    }
    
    /**
     * Ejecuta un step y espera a que complete.
     */
    protected suspend fun executeStep(step: dev.rubentxu.hodei.pipelines.dsl.model.Step) {
        val executor = stepExecutorRegistry.getExecutor(step.stepType)
            ?: throw IllegalStateException("No executor found for step: ${step.stepType}")
        
        executor.execute(step, pipelineContext)
    }
    
    /**
     * Obtiene todo el output capturado como texto.
     */
    protected suspend fun getOutputText(): String {
        return outputMutex.withLock {
            outputCollector
                .filter { !it.isError }
                .joinToString("") { String(it.data) }
        }
    }
    
    /**
     * Obtiene todo el error output capturado como texto.
     */
    protected suspend fun getErrorText(): String {
        return outputMutex.withLock {
            outputCollector
                .filter { it.isError }
                .joinToString("") { String(it.data) }
        }
    }
    
    /**
     * Obtiene todos los eventos capturados.
     */
    protected suspend fun getEvents(): List<PipelineExecutionEvent> {
        return eventMutex.withLock {
            eventCollector.toList()
        }
    }
    
    /**
     * Verifica que el output contiene el texto esperado.
     */
    protected suspend fun assertOutputContains(expected: String) {
        val output = getOutputText()
        assertTrue(
            output.contains(expected),
            "Expected output to contain '$expected', but was: '$output'"
        )
    }
    
    /**
     * Verifica que no hay errores en el output.
     */
    protected suspend fun assertNoErrors() {
        val errorText = getErrorText()
        assertTrue(
            errorText.isEmpty(),
            "Expected no errors, but found: '$errorText'"
        )
    }
    
    /**
     * Verifica que un archivo existe en el workspace.
     */
    protected fun assertFileExists(relativePath: String) {
        val file = File(workingDirectory, relativePath)
        assertTrue(file.exists(), "File should exist: $relativePath")
    }
    
    /**
     * Verifica el contenido de un archivo.
     */
    protected fun assertFileContent(relativePath: String, expectedContent: String) {
        val file = File(workingDirectory, relativePath)
        assertTrue(file.exists(), "File should exist: $relativePath")
        assertEquals(expectedContent, file.readText().trim())
    }
    
    /**
     * Verifica el contenido de un archivo usando un matcher.
     */
    protected fun assertFileContent(relativePath: String, matcher: (String) -> Boolean) {
        val file = File(workingDirectory, relativePath)
        assertTrue(file.exists(), "File should exist: $relativePath")
        val content = file.readText()
        assertTrue(matcher(content), "File content doesn't match predicate: $content")
    }
    
    /**
     * Crea un archivo de prueba con contenido.
     */
    protected fun createTestFile(relativePath: String, content: String): File {
        val file = File(workingDirectory, relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return file
    }
    
    /**
     * Crea múltiples archivos de prueba.
     */
    protected fun createTestFiles(files: Map<String, String>) {
        files.forEach { (path, content) ->
            createTestFile(path, content)
        }
    }
    
    /**
     * Obtiene una variable del contexto del pipeline.
     */
    protected fun <T> getVariable(key: String): T? {
        return pipelineContext.getVariable(key)
    }
    
    /**
     * Establece una variable en el contexto del pipeline.
     */
    protected fun setVariable(key: String, value: Any) {
        pipelineContext.setVariable(key, value)
    }
    
    /**
     * Verifica que una variable tiene el valor esperado.
     */
    protected fun <T> assertVariable(key: String, expected: T) {
        val actual = getVariable<T>(key)
        assertEquals(expected, actual, "Variable $key should have value $expected")
    }
    
    /**
     * Espera un poco para que las operaciones asíncronas completen.
     */
    protected suspend fun waitForCompletion(millis: Long = 100) {
        kotlinx.coroutines.delay(millis)
    }
    
    /**
     * Ejecuta un bloque de código y captura cualquier excepción.
     */
    protected suspend fun <T> captureException(block: suspend () -> T): Exception? {
        return try {
            block()
            null
        } catch (e: Exception) {
            e
        }
    }
    
    /**
     * Verifica que se lanza una excepción con un mensaje específico.
     */
    protected suspend fun assertThrows(expectedMessage: String, block: suspend () -> Unit) {
        val exception = captureException(block)
        assertTrue(exception != null, "Expected exception to be thrown")
        assertTrue(
            exception!!.message?.contains(expectedMessage) == true,
            "Expected exception message to contain '$expectedMessage', but was: '${exception.message}'"
        )
    }
}