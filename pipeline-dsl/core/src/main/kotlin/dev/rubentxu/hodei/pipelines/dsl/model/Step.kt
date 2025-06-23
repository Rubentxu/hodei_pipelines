package dev.rubentxu.hodei.pipelines.dsl.model

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterType
import kotlinx.serialization.Serializable

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
    
    /**
     * Step para archivar artifacts.
     */
    @Serializable
    data class ArchiveArtifacts(
        val artifacts: String,
        val allowEmptyArchive: Boolean = false,
        val caseSensitive: Boolean = true,
        val defaultExcludes: Boolean = true,
        val excludes: String? = null,
        val fingerprint: Boolean = false,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    /**
     * Step para publicar resultados de tests.
     */
    @Serializable
    data class PublishTestResults(
        val testResultsPattern: String,
        val allowEmptyResults: Boolean = false,
        val checksName: String? = null,
        val healthScaleFactor: Double = 1.0,
        val keepLongStdio: Boolean = false,
        val skipMarkingBuildUnstable: Boolean = false,
        val skipPublishingChecks: Boolean = false,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    /**
     * Step para checkout de código fuente.
     */
    @Serializable
    data class Checkout(
        val scm: SCMConfig,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
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
    
    /**
     * Step para operaciones Docker.
     */
    @Serializable
    data class Docker(
        val image: String,
        val command: String? = null,
        val args: List<String> = emptyList(),
        val volumes: List<String> = emptyList(),
        val environment: Map<String, String> = emptyMap(),
        val workingDirectory: String? = null,
        val user: String? = null,
        val entrypoint: String? = null,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    /**
     * Step para enviar notificaciones.
     */
    @Serializable
    data class Notification(
        val message: String,
        val channels: List<NotificationChannel>,
        val onlyOnStateChange: Boolean = false,
        override val name: String? = null,
        override val continueOnError: Boolean = true,
        override val timeout: Int? = null
    ) : Step()
    
    /**
     * Step para operaciones de directorio.
     */
    @Serializable
    data class Dir(
        val path: String,
        val steps: List<Step>,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
    /**
     * Step para configurar entorno.
     */
    @Serializable
    data class WithEnv(
        val environment: Map<String, String>,
        val steps: List<Step>,
        override val name: String? = null,
        override val continueOnError: Boolean = false,
        override val timeout: Int? = null
    ) : Step()
    
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
}

/**
 * Configuración de SCM (Source Code Management).
 */
@Serializable
sealed class SCMConfig {
    @Serializable
    data class Git(
        val url: String,
        val branch: String = "main",
        val credentialsId: String? = null,
        val shallow: Boolean = false,
        val depth: Int? = null,
        val submodules: Boolean = false,
        val clean: Boolean = false,
        val checkoutDir: String? = null,
        val lfs: Boolean = false
    ) : SCMConfig()
    
    @Serializable
    data class Svn(
        val url: String,
        val credentialsId: String? = null,
        val checkoutDir: String? = null,
        val depth: String = "infinity"
    ) : SCMConfig()
    
    @Serializable
    data class Mercurial(
        val url: String,
        val branch: String = "default",
        val credentialsId: String? = null,
        val clean: Boolean = false
    ) : SCMConfig()
}

/**
 * Canales de notificación.
 */
@Serializable
sealed class NotificationChannel {
    @Serializable
    data class Slack(
        val channel: String,
        val token: String? = null,
        val username: String? = null,
        val color: String? = null
    ) : NotificationChannel()
    
    @Serializable
    data class Email(
        val to: List<String>,
        val subject: String? = null,
        val mimeType: String = "text/plain"
    ) : NotificationChannel()
    
    @Serializable
    data class Teams(
        val webhookUrl: String,
        val color: String? = null
    ) : NotificationChannel()
    
    @Serializable
    data class Custom(
        val type: String,
        val config: Map<String, String>
    ) : NotificationChannel()
}

/**
 * Extensión para obtener el tipo de step como string.
 */
val Step.stepType: String
    get() = when (this) {
        is Step.Shell -> "sh"
        is Step.Batch -> "bat"
        is Step.Echo -> "echo"
        is Step.ArchiveArtifacts -> "archiveArtifacts"
        is Step.PublishTestResults -> "publishTestResults"
        is Step.Checkout -> "checkout"
        is Step.Script -> "script"
        is Step.Docker -> "docker"
        is Step.Notification -> "notification"
        is Step.Dir -> "dir"
        is Step.WithEnv -> "withEnv"
        is Step.Custom -> action
    }