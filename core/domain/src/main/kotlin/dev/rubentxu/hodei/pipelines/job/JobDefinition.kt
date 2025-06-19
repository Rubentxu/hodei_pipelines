package dev.rubentxu.hodei.pipelines.job

import java.time.Instant

// Identificador único para un Job.
@JvmInline
value class JobIdentifier(val value: String)

// Representa una entrada en el log de salida de un job.
data class LogEntry(
    val data: ByteArray,
    val isStdErr: Boolean,
    val timestamp: Instant
) {
    // Sobrescribimos equals y hashCode para manejar el array de bytes.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as LogEntry

        if (!data.contentEquals(other.data)) return false
        if (isStdErr != other.isStdErr) return false
        if (timestamp != other.timestamp) return false

        return true
    }

    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isStdErr.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}


sealed interface ParameterValue {
    data class StringValue(val value: String) : ParameterValue
    data class TextValue(val value: String) : ParameterValue
    data class BooleanValue(val value: Boolean) : ParameterValue
    data class ChoiceValue(val options: List<String>, val selected: String) : ParameterValue
    data class PasswordValue(val value: String) : ParameterValue
    data class FileValue(val fileName: String, val content: ByteArray, val contentType: String) : ParameterValue
    data class JsonValue(val value: String) : ParameterValue // JSON como string en el dominio
}

// Enumeración que representa el estado de un Job en el dominio.
enum class JobStatus {
    QUEUED,
    RUNNING,
    SUCCESS,
    FAILED,
    CANCELLED
}

data class JobParameter(
    val name: String,
    val description: String,
    val required: Boolean,
    val value: ParameterValue
)



// Raíz del agregado Job. Modela el ciclo de vida y la definición de un trabajo.
class JobDefinition(
    val id: JobIdentifier,
    val name: String,
    val command: List<String>,
    val environment: Map<String, String>,
    val workingDirectory: String,
    val parameters: List<JobParameter>
)