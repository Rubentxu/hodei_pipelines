package dev.rubentxu.hodei.pipelines.dsl.model

// Standalone Pipeline DSL - no worker dependencies
import kotlinx.serialization.Serializable
import dev.rubentxu.hodei.pipelines.dsl.model.TimeUnit

/**
 * Definición de un Step en el Pipeline DSL.
 * 
 * Los steps son las unidades atómicas de ejecución que integran con
 * el sistema de workers existente y soportan diferentes tipos de acciones.
 */
@Serializable
sealed class Step {
    abstract val name: String?
    abstract val continueOnError: Boolean
    abstract val timeout: Int? // en segundos
    
    /**
     * Tipo de step para identificar el ejecutor apropiado.
     */
    val stepType: String
        get() = when (this) {
            is Shell -> "sh"
            is Batch -> "bat"
            is Echo -> "echo"
            is Script -> "script"
            // dir, withEnv, timeout, retry, parallel steps handled by extensions
            is Custom -> action
        }
    
    /**
     * Step para ejecutar comandos shell.
     */
    @Serializable
    data class Shell(
        val command: String,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null,
        val workingDirectory: String? = null,
        val returnStdout: Boolean = false,
        val returnStatus: Boolean = false,
        val encoding: String = "UTF-8"
    ) : Step()
    
    /**
     * Step para ejecutar comandos batch (Windows).
     */
    @Serializable
    data class Batch(
        val command: String,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null,
        val workingDirectory: String? = null,
        val returnStdout: Boolean = false,
        val returnStatus: Boolean = false,
        val encoding: String = "UTF-8"
    ) : Step()
    
    /**
     * Step para mostrar mensajes.
     */
    @Serializable
    data class Echo(
        val message: String,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    // NOTE: ArchiveArtifacts, PublishTestResults, Checkout step classes moved to dedicated extensions
    // - jenkins-pipeline-steps extension
    // - scm-steps extension
    
    /**
     * Step para ejecutar scripts de archivos.
     */
    @Serializable
    data class Script(
        val scriptFile: String,
        val parameters: Map<String, String> = emptyMap(),
        val interpreter: String? = null,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    // NOTE: Docker and Notification step classes moved to dedicated extensions
    // - docker-steps extension
    // - notification-steps extension
    
    // NOTE: Dir, WithEnv, Timeout, Retry, Parallel step classes removed
    // These are now handled by the pipeline-steps-library extension
    // Use ExtensionStep with appropriate extension name instead
    
    /**
     * Step personalizado que puede ser extendido.
     */
    @Serializable
    data class Custom(
        val action: String,
        val parameters: Map<String, String> = emptyMap(),
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    /**
     * ID único del step para tracking.
     */
    val id: String
        get() = name ?: "${stepType}-${hashCode()}"
    
    /**
     * Indica si el step debe ignorar errores (alias de continueOnError).
     */
    val ignoreErrors: Boolean
        get() = continueOnError
}

// NOTE: SCMConfig and NotificationChannel classes moved to dedicated extensions
// - SCMConfig -> scm-steps extension
// - NotificationChannel -> notification-steps extension

