package dev.rubentxu.hodei.pipelines.dsl.execution

import dev.rubentxu.hodei.pipelines.dsl.model.PipelineOutputChunk
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import mu.KotlinLogging
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Gestor optimizado para streaming de salida de procesos.
 * Maneja stdout/stderr de forma concurrente y eficiente.
 */
class OutputStreamHandler(
    private val outputChannel: SendChannel<PipelineOutputChunk>
) {
    companion object {
        private const val BUFFER_SIZE = 8192
        private const val CHUNK_TIMEOUT_MS = 50L
    }

    /**
     * Captura y envía la salida de un proceso de forma streaming.
     * Optimizado para rendimiento con buffers y coroutines.
     */
    suspend fun streamProcessOutput(
        process: Process,
        jobId: String,
        stepId: String
    ) = coroutineScope {
        // Lanzar lectores concurrentes para stdout y stderr
        val stdoutJob = launch {
            streamOutput(
                process.inputStream,
                isError = false,
                jobId = jobId,
                stepId = stepId
            )
        }
        
        val stderrJob = launch {
            streamOutput(
                process.errorStream,
                isError = true,
                jobId = jobId,
                stepId = stepId
            )
        }
        
        // Esperar a que ambos streams terminen
        stdoutJob.join()
        stderrJob.join()
    }

    /**
     * Stream de salida como Flow para casos donde se necesite procesar localmente.
     */
    fun streamAsFlow(
        inputStream: InputStream,
        isError: Boolean = false
    ): Flow<PipelineOutputChunk> = flow {
        val buffer = ByteArray(BUFFER_SIZE)
        var bytesRead: Int
        
        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
            if (bytesRead > 0) {
                val chunk = PipelineOutputChunk(
                    data = buffer.copyOf(bytesRead),
                    isError = isError,
                    timestamp = System.currentTimeMillis()
                )
                emit(chunk)
            }
        }
    }

    /**
     * Stream optimizado de salida a través del channel.
     */
    private suspend fun streamOutput(
        inputStream: InputStream,
        isError: Boolean,
        jobId: String,
        stepId: String
    ) {
        try {
            inputStream.buffered(BUFFER_SIZE).use { bufferedStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                
                while (bufferedStream.read(buffer).also { bytesRead = it } != -1) {
                    if (bytesRead > 0) {
                        val chunk = PipelineOutputChunk(
                            data = buffer.copyOf(bytesRead),
                            isError = isError,
                            timestamp = System.currentTimeMillis()
                        )
                        
                        // Enviar chunk sin bloquear
                        outputChannel.send(chunk)
                        
                        // Pequeño yield para no saturar el channel
                        if (bytesRead == BUFFER_SIZE) {
                            yield()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(e) { "Error streaming output for job=$jobId, step=$stepId" }
            // Enviar error como chunk
            val errorChunk = PipelineOutputChunk(
                data = "Stream error: ${e.message}\n".toByteArray(),
                isError = true,
                timestamp = System.currentTimeMillis()
            )
            outputChannel.send(errorChunk)
        }
    }

    /**
     * Versión alternativa que lee por líneas para casos donde se necesite.
     */
    suspend fun streamLinesOutput(
        process: Process,
        lineProcessor: suspend (String, Boolean) -> Unit
    ) = coroutineScope {
        val stdoutJob = launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    runBlocking { lineProcessor(line, false) }
                }
            }
        }
        
        val stderrJob = launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    runBlocking { lineProcessor(line, true) }
                }
            }
        }
        
        stdoutJob.join()
        stderrJob.join()
    }
}