package dev.rubentxu.hodei.pipelines.dsl.execution.context

import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.*
import java.io.OutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets

/**
 * Contexto especializado para manejo de salida.
 * Aplica el principio de responsabilidad única para la gestión de output.
 */
interface OutputContext {
    suspend fun println(message: String)
    suspend fun printError(message: String)
    suspend fun write(data: ByteArray, isError: Boolean = false)
    fun redirectStandardStreams(): StreamRedirection
}

/**
 * Implementación del contexto de salida con streaming optimizado.
 */
class StreamingOutputContext(
    private val outputChannel: SendChannel<PipelineOutputChunk>
) : OutputContext {
    
    /**
     * Imprime un mensaje al output del pipeline.
     */
    override suspend fun println(message: String) {
        write("$message\n".toByteArray(StandardCharsets.UTF_8), false)
    }
    
    /**
     * Imprime un mensaje de error al output del pipeline.
     */
    override suspend fun printError(message: String) {
        write("$message\n".toByteArray(StandardCharsets.UTF_8), true)
    }
    
    /**
     * Escribe datos binarios al output.
     */
    override suspend fun write(data: ByteArray, isError: Boolean) {
        val chunk = PipelineOutputChunk(
            data = data,
            isError = isError,
            timestamp = System.currentTimeMillis()
        )
        outputChannel.send(chunk)
    }
    
    /**
     * Redirige stdout y stderr a los channels del pipeline.
     */
    override fun redirectStandardStreams(): StreamRedirection {
        val originalOut = System.out
        val originalErr = System.err
        
        val redirectedOut = ChannelPrintStream(outputChannel, false)
        val redirectedErr = ChannelPrintStream(outputChannel, true)
        
        System.setOut(redirectedOut)
        System.setErr(redirectedErr)
        
        return StreamRedirection(originalOut, originalErr)
    }
}

/**
 * PrintStream que redirige a un SendChannel.
 */
class ChannelPrintStream(
    private val channel: SendChannel<PipelineOutputChunk>,
    private val isError: Boolean
) : PrintStream(ChannelOutputStream(channel, isError))

/**
 * OutputStream que envía datos a un SendChannel.
 */
class ChannelOutputStream(
    private val channel: SendChannel<PipelineOutputChunk>,
    private val isError: Boolean
) : OutputStream() {
    private val buffer = mutableListOf<Byte>()
    private val lock = Any()
    
    override fun write(b: Int) {
        synchronized(lock) {
            buffer.add(b.toByte())
            if (b == '\n'.code) {
                flush()
            }
        }
    }
    
    override fun write(b: ByteArray, off: Int, len: Int) {
        synchronized(lock) {
            for (i in off until off + len) {
                buffer.add(b[i])
            }
            // Flush si hay salto de línea
            if (b.slice(off until off + len).contains('\n'.code.toByte())) {
                flush()
            }
        }
    }
    
    override fun flush() {
        synchronized(lock) {
            if (buffer.isNotEmpty()) {
                val chunk = PipelineOutputChunk(
                    data = buffer.toByteArray(),
                    isError = isError,
                    timestamp = System.currentTimeMillis()
                )
                // Usar tryOffer para no bloquear
                channel.tryOffer(chunk)
                buffer.clear()
            }
        }
    }
}

/**
 * Gestiona la restauración de streams originales.
 */
class StreamRedirection(
    private val originalOut: PrintStream,
    private val originalErr: PrintStream
) : AutoCloseable {
    
    override fun close() {
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
}