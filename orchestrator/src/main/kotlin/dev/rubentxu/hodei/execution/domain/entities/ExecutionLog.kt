package dev.rubentxu.hodei.execution.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

@Serializable
data class ExecutionLog(
    val id: DomainId,
    val executionId: DomainId,
    val timestamp: Instant,
    val stream: LogStream,
    val level: LogLevel,
    val message: String,
    val stageName: String? = null,
    val stepName: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    init {
        require(message.isNotBlank()) { "Log message cannot be blank" }
    }
}

@Serializable
enum class LogStream {
    STDOUT,
    STDERR,
    SYSTEM
}

@Serializable
enum class LogLevel {
    TRACE,
    DEBUG,
    INFO,
    WARN,
    ERROR,
    FATAL
}