package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.library.LibraryManager
import dev.rubentxu.hodei.pipelines.dsl.library.PipelineLibraryManager
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.channels.SendChannel
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Contexto standalone del Pipeline DSL para la ejecución de steps.
 * Independiente del sistema worker.
 */
class PipelineContext(
    val jobId: String,
    val workerId: String,
    val workingDirectory: File = File(System.getProperty("user.dir")),
    val environment: MutableMap<String, String> = mutableMapOf(),
    val outputChannel: SendChannel<JobOutputChunk>,
    val eventChannel: SendChannel<JobExecutionEvent>,
    val variables: MutableMap<String, Any> = ConcurrentHashMap(),
    val libraryManager: LibraryManager = PipelineLibraryManager()
) {
    
    /**
     * Imprime un mensaje al output del pipeline.
     */
    suspend fun println(message: String) {
        val chunk = JobOutputChunk(
            jobId = jobId,
            data = "$message\n".toByteArray(),
            isError = false,
            timestamp = System.currentTimeMillis()
        )
        outputChannel.send(chunk)
    }
    
    /**
     * Imprime un mensaje de error al output del pipeline.
     */
    suspend fun printError(message: String) {
        val chunk = JobOutputChunk(
            jobId = jobId,
            data = "$message\n".toByteArray(),
            isError = true,
            timestamp = System.currentTimeMillis()
        )
        outputChannel.send(chunk)
    }
    
    /**
     * Ejecuta un comando del sistema.
     */
    suspend fun executeCommand(command: List<String>): Int {
        val processBuilder = ProcessBuilder(command)
            .directory(workingDirectory)
            .redirectErrorStream(false)
        
        // Configurar environment
        processBuilder.environment().putAll(environment)
        
        val process = processBuilder.start()
        
        // Capturar output en tiempo real
        val outputReader = process.inputStream.bufferedReader()
        val errorReader = process.errorStream.bufferedReader()
        
        // Leer output estándar
        outputReader.useLines { lines ->
            lines.forEach { line ->
                println(line)
            }
        }
        
        // Leer error estándar
        errorReader.useLines { lines ->
            lines.forEach { line ->
                printError(line)
            }
        }
        
        return process.waitFor()
    }
    
    /**
     * Ejecuta un comando shell simple.
     */
    suspend fun sh(command: String): Int {
        return if (System.getProperty("os.name").lowercase().contains("windows")) {
            executeCommand(listOf("cmd", "/c", command))
        } else {
            executeCommand(listOf("sh", "-c", command))
        }
    }
    
    /**
     * Ejecuta un comando batch (Windows).
     */
    suspend fun bat(command: String): Int {
        return executeCommand(listOf("cmd", "/c", command))
    }
    
    /**
     * Cambia el directorio de trabajo.
     */
    fun changeDirectory(path: String): File {
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
     * Establece una variable de entorno.
     */
    fun setEnv(key: String, value: String) {
        environment[key] = value
    }
    
    /**
     * Obtiene una variable de entorno.
     */
    fun getEnv(key: String): String? = environment[key]
    
    /**
     * Establece una variable del pipeline.
     */
    fun setVariable(key: String, value: Any) {
        variables[key] = value
    }
    
    /**
     * Obtiene una variable del pipeline.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getVariable(key: String): T? = variables[key] as? T
    
    /**
     * Publica un evento del pipeline.
     */
    suspend fun publishEvent(event: JobExecutionEvent) {
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
}