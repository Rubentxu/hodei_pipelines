package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.execution.context.*
import dev.rubentxu.hodei.pipelines.dsl.library.LibraryManager
import dev.rubentxu.hodei.pipelines.dsl.library.PipelineLibraryManager
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import dev.rubentxu.hodei.pipelines.dsl.security.*
import kotlinx.coroutines.channels.SendChannel
import java.io.File

/**
 * Contexto del Pipeline DSL que compone diferentes responsabilidades.
 * Aplica el principio de responsabilidad única mediante composición.
 */
class PipelineContext(
    jobId: String,
    workerId: String,
    workingDirectory: File = File(System.getProperty("user.dir")),
    environment: MutableMap<String, String> = mutableMapOf(),
    outputChannel: SendChannel<PipelineOutputChunk>,
    val eventChannel: SendChannel<PipelineExecutionEvent>,
    val libraryManager: LibraryManager = PipelineLibraryManager(),
    val securityManager: PipelineSecurityManager = PipelineSandbox(SecurityPolicy.cicd()),
    val commandExecutor: CommandExecutor = CommandExecutor(
        outputChannel = outputChannel,
        eventChannel = eventChannel,
        securityManager = securityManager
    )
) : ExecutionContext(jobId, workerId, workingDirectory, environment) {
    
    // Contexto de salida delegado
    private val outputContext: OutputContext = StreamingOutputContext(outputChannel)
    
    /**
     * Imprime un mensaje al output del pipeline.
     */
    suspend fun println(message: String) = outputContext.println(message)
    
    /**
     * Imprime un mensaje de error al output del pipeline.
     */
    suspend fun printError(message: String) = outputContext.printError(message)
    
    /**
     * Escribe datos binarios al output.
     */
    suspend fun write(data: ByteArray, isError: Boolean = false) = 
        outputContext.write(data, isError)
    
    /**
     * Redirige los streams estándar a los channels del pipeline.
     */
    fun redirectStandardStreams() = outputContext.redirectStandardStreams()
    
    /**
     * Ejecuta un comando del sistema.
     * Delega al CommandExecutor para separar responsabilidades.
     */
    suspend fun executeCommand(
        command: List<String>,
        timeout: kotlin.time.Duration = kotlin.time.Duration.parse("PT30M")
    ): Int = commandExecutor.executeCommand(
        command = command,
        workingDirectory = workingDirectory,
        environment = environment,
        timeout = timeout,
        jobId = jobId,
        stepId = "${jobId}-command"
    )
    
    /**
     * Ejecuta un comando shell con verificación de seguridad.
     * Delega al CommandExecutor especializado.
     */
    suspend fun sh(
        command: String,
        timeout: kotlin.time.Duration = kotlin.time.Duration.parse("PT30M")
    ): Int = commandExecutor.executeShell(
        command = command,
        workingDirectory = workingDirectory,
        environment = environment,
        timeout = timeout,
        jobId = jobId,
        stepId = "${jobId}-sh"
    )
    
    /**
     * Ejecuta un comando batch (Windows) con verificación de seguridad.
     * Delega al CommandExecutor especializado.
     */
    suspend fun bat(
        command: String,
        timeout: kotlin.time.Duration = kotlin.time.Duration.parse("PT30M")
    ): Int = commandExecutor.executeBatch(
        command = command,
        workingDirectory = workingDirectory,
        environment = environment,
        timeout = timeout,
        jobId = jobId,
        stepId = "${jobId}-bat"
    )
    
    /**
     * Cambia el directorio de trabajo con verificación de seguridad.
     */
    fun changeDirectory(path: String): File {
        // Verificar acceso de seguridad al directorio
        val securityCheck = checkFileAccess(path, FileOperation.READ)
        if (securityCheck is SecurityCheckResult.Denied) {
            val violationMessages = securityCheck.violations.joinToString(", ") { it.message }
            throw SecurityException("Directory access blocked by security policy: $violationMessages")
        }
        
        val newDir = if (File(path).isAbsolute) {
            File(path)
        } else {
            File(workingDirectory, path)
        }
        
        require(newDir.exists() && newDir.isDirectory) {
            "Directory does not exist: ${newDir.absolutePath}"
        }
        
        return newDir
    }
    
    /**
     * DSL para contexto de ejecución con variables.
     */
    inline fun <T> withVariable(key: String, value: T, block: () -> Unit) {
        val previousValue = getVariable<T>(key)
        try {
            setVariable(key, value as Any)
            block()
        } finally {
            if (previousValue != null) {
                setVariable(key, previousValue)
            }
        }
    }
    
    /**
     * DSL para contexto de ejecución con múltiples variables.
     */
    inline fun withVariables(vararg pairs: Pair<String, Any>, block: () -> Unit) {
        val previousValues = pairs.map { it.first to getVariable<Any>(it.first) }
        try {
            pairs.forEach { setVariable(it.first, it.second) }
            block()
        } finally {
            previousValues.forEach { (key, value) ->
                if (value != null) setVariable(key, value)
            }
        }
    }
    
    /**
     * Publica un evento del pipeline.
     */
    suspend fun publishEvent(event: PipelineExecutionEvent) {
        eventChannel.send(event)
    }
    
    /**
     * Carga librerías desde artefactos.
     */
    suspend fun loadLibrariesFromArtifacts(artifacts: List<File>) {
        libraryManager.loadLibrariesFromArtifacts(artifacts)
    }
    
    /**
     * Añade una librería desde un artefacto.
     */
    fun addLibraryArtifact(identifier: String, jarFile: File) {
        libraryManager.addArtifactLibrary(identifier, jarFile)
    }
    
    /**
     * Obtiene una librería cargada.
     */
    fun getLibrary(identifier: String) = libraryManager.getLoadedLibrary(identifier)
    
    /**
     * Lista todas las librerías cargadas.
     */
    fun getAllLibraries() = libraryManager.getAllLoadedLibraries()
    
    /**
     * Verifica acceso a archivos antes de operaciones.
     */
    fun checkFileAccess(path: String, operation: FileOperation): SecurityCheckResult {
        return securityManager.checkFileAccess(path, operation)
    }
    
    /**
     * Verifica que una librería sea segura antes de cargarla.
     */
    fun checkLibrarySecurity(libraryId: String): SecurityCheckResult {
        return securityManager.checkLibraryAccess(libraryId)
    }
    
    /**
     * Obtiene la política de seguridad actual.
     */
    fun getSecurityPolicy(): SecurityPolicy = securityManager.securityPolicy
    
    /**
     * Verifica si el sandbox está habilitado.
     */
    fun isSandboxEnabled(): Boolean = securityManager.securityPolicy.sandboxEnabled
}