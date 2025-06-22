package dev.rubentxu.hodei.pipelines.domain.worker.model.dsl

/**
 * Step definition for pipeline steps
 */
data class StepDefinition(
    val name: String,
    val description: String = "",
    val parameters: Map<String, ParameterDefinition> = emptyMap(),
    val requiredPermissions: Set<String> = emptySet(),
    val executor: suspend (Map<String, Any>, StepExecutionContext) -> StepResult
)


/**
 * Parameter definition for step parameters
 */
data class ParameterDefinition(
    val name: String,
    val type: ParameterType,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val description: String = "",
    val validation: ((Any) -> Boolean)? = null
)

/**
 * Parameter types
 */
enum class ParameterType {
    STRING,
    TEXT,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    CHOICE,
    PASSWORD,
    FILE,
    DIRECTORY,
    URL,
    JSON,
    YAML
}

/**
 * Step execution context
 */
data class StepExecutionContext(
    val pipeline: PipelineContext,
    val stepName: String,
    val parameters: Map<String, Any>,
    val environment: Map<String, String>
)

/**
 * Step execution result
 */
sealed class StepResult {
    data class Success(val message: String = "", val output: Map<String, Any> = emptyMap()) : StepResult()
    data class Failure(val error: String, val exitCode: Int = 1) : StepResult()
    data class Unstable(val warnings: List<String>) : StepResult()
}

/**
 * Extension context for initialization
 */
class ExtensionContext {
    private val properties = mutableMapOf<String, Any>()

    fun setProperty(key: String, value: Any) {
        properties[key] = value
    }

    fun getProperty(key: String): Any? {
        return properties[key]
    }

    fun <T> getProperty(key: String, type: Class<T>): T? {
        return properties[key]?.let { value ->
            if (type.isInstance(value)) {
                @Suppress("UNCHECKED_CAST")
                value as T
            } else {
                null
            }
        }
    }
}

// Built-in Extensions

/**
 * Extension exceptions
 */
class ExtensionNotFoundException(message: String) : Exception(message)
class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ExtensionInitializationException(message: String, cause: Throwable? = null) : Exception(message, cause)