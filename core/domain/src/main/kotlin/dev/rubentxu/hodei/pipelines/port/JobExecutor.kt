package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.job.JobDefinition
import dev.rubentxu.hodei.pipelines.job.JobIdentifier
import dev.rubentxu.hodei.pipelines.job.JobOutputAndStatus
import kotlinx.coroutines.flow.Flow


// Puerto de salida para delegar la ejecución de un Job a un sistema externo (un Worker).
// La implementación de este puerto será el cliente gRPC en la capa de infraestructura.
interface JobExecutor {
    // Inicia la ejecución y gestiona la comunicación bidireccional.
    suspend fun execute(job: JobDefinition): Flow<JobOutputAndStatus>

    // Envía una señal de control a un job en ejecución.
    suspend fun sendSignal(jobId: JobIdentifier, signal: ControlSignal)
}

enum class ControlSignal {
    CANCEL
}


// Puerto para la persistencia de Jobs.
interface JobRepository {
    fun findById(id: JobIdentifier): JobDefinition?
    fun save(job: JobDefinition)
    fun nextIdentifier(): JobIdentifier
}