package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/**
 * Clase base para tests end-to-end del CLI Pipeline DSL.
 */
abstract class BaseE2ETest {
    
    @TempDir
    lateinit var tempDir: Path
    
    protected lateinit var testPipelinesDir: File
    protected lateinit var cliJar: File
    
    private val originalOut = System.out
    private val originalErr = System.err
    protected lateinit var capturedOut: ByteArrayOutputStream
    protected lateinit var capturedErr: ByteArrayOutputStream
    
    @BeforeEach
    fun setup() {
        // Configurar directorios de test
        testPipelinesDir = File(this::class.java.classLoader.getResource("pipelines")!!.toURI())
        
        // Buscar el JAR del CLI (debe estar compilado)
        cliJar = findCliJar()
        
        // Configurar captura de output
        capturedOut = ByteArrayOutputStream()
        capturedErr = ByteArrayOutputStream()
    }
    
    private fun findCliJar(): File {
        // Buscar el JAR en el directorio de build
        val buildDir = File("build/libs")
        if (buildDir.exists()) {
            val jarFiles = buildDir.listFiles { file -> 
                file.name.endsWith(".jar") && !file.name.contains("plain")
            }
            if (jarFiles != null && jarFiles.isNotEmpty()) {
                return jarFiles.first()
            }
        }
        
        // Fallback: buscar en el directorio del proyecto padre
        val parentBuildDir = File("../cli/build/libs")
        if (parentBuildDir.exists()) {
            val jarFiles = parentBuildDir.listFiles { file -> 
                file.name.endsWith(".jar") && !file.name.contains("plain")
            }
            if (jarFiles != null && jarFiles.isNotEmpty()) {
                return jarFiles.first()
            }
        }
        
        throw IllegalStateException("CLI JAR not found. Please run 'gradle :pipeline-dsl:cli:build' first")
    }
    
    /**
     * Ejecuta el CLI con los argumentos dados y captura la salida.
     */
    protected fun runCli(vararg args: String, timeoutSeconds: Long = 30): CliResult {
        val command = listOf("java", "-jar", cliJar.absolutePath) + args.toList()
        
        val processBuilder = ProcessBuilder(command)
            .directory(tempDir.toFile())
            .redirectErrorStream(false)
        
        val process = processBuilder.start()
        
        val outputReader = process.inputStream.bufferedReader()
        val errorReader = process.errorStream.bufferedReader()
        
        val output = StringBuilder()
        val error = StringBuilder()
        
        // Leer output en hilos separados
        val outputThread = Thread {
            outputReader.use { reader ->
                reader.lineSequence().forEach { line ->
                    output.appendLine(line)
                }
            }
        }
        
        val errorThread = Thread {
            errorReader.use { reader ->
                reader.lineSequence().forEach { line ->
                    error.appendLine(line)
                }
            }
        }
        
        outputThread.start()
        errorThread.start()
        
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        
        if (!finished) {
            process.destroyForcibly()
            throw RuntimeException("Command timed out after $timeoutSeconds seconds")
        }
        
        outputThread.join(1000)
        errorThread.join(1000)
        
        return CliResult(
            exitCode = process.exitValue(),
            stdout = output.toString().trim(),
            stderr = error.toString().trim(),
            command = command.joinToString(" ")
        )
    }
    
    /**
     * Obtiene el archivo de pipeline de test por nombre.
     */
    protected fun getPipelineFile(name: String): File {
        return File(testPipelinesDir, name)
    }
    
    /**
     * Crea un archivo de pipeline temporal con el contenido dado.
     */
    protected fun createTempPipeline(name: String, content: String): File {
        val file = tempDir.resolve(name).toFile()
        file.writeText(content)
        return file
    }
    
    /**
     * Verifica que un archivo de pipeline existe.
     */
    protected fun assertPipelineExists(name: String) {
        val file = getPipelineFile(name)
        assert(file.exists()) { "Pipeline file $name not found at ${file.absolutePath}" }
        assert(file.canRead()) { "Pipeline file $name is not readable" }
    }
}

/**
 * Resultado de la ejecución del CLI.
 */
data class CliResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val command: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val isFailure: Boolean get() = exitCode != 0
    
    fun assertSuccess(message: String = "CLI command should succeed") {
        assert(isSuccess) { 
            "$message\nCommand: $command\nExit code: $exitCode\nStdout: $stdout\nStderr: $stderr" 
        }
    }
    
    fun assertFailure(message: String = "CLI command should fail") {
        assert(isFailure) { 
            "$message\nCommand: $command\nExit code: $exitCode\nStdout: $stdout\nStderr: $stderr" 
        }
    }
    
    fun assertContains(text: String, message: String = "Output should contain '$text'") {
        val fullOutput = "$stdout\n$stderr"
        assert(fullOutput.contains(text)) { 
            "$message\nFull output: $fullOutput" 
        }
    }
    
    fun assertNotContains(text: String, message: String = "Output should not contain '$text'") {
        val fullOutput = "$stdout\n$stderr"
        assert(!fullOutput.contains(text)) { 
            "$message\nFull output: $fullOutput" 
        }
    }
    
    fun assertStdoutContains(text: String, message: String = "Stdout should contain '$text'") {
        assert(stdout.contains(text)) { 
            "$message\nStdout: $stdout" 
        }
    }
    
    fun assertStderrContains(text: String, message: String = "Stderr should contain '$text'") {
        assert(stderr.contains(text)) { 
            "$message\nStderr: $stderr" 
        }
    }
}