package dev.rubentxu.hodei.pipelines.dsl.builders

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.model.*

/**
 * Builder para la colección de steps en un stage.
 * 
 * Proporciona una API tipada para la definición de steps que integra
 * con el sistema de ejecución existente de workers.
 */
@PipelineDslMarker
class StepsBuilder {
    private val steps: MutableList<Step> = mutableListOf()
    
    /**
     * Ejecuta un comando shell.
     * Compatible con Jenkins Pipeline DSL.
     */
    fun sh(script: String) {
        steps.add(
            Step.Shell(
                command = script,
                name = null,
                continueOnError = false,
                timeout = null,
                workingDirectory = null,
                returnStdout = false,
                returnStatus = false,
                encoding = "UTF-8"
            )
        )
    }
    
    /**
     * Ejecuta un comando shell con parámetros.
     * Compatible con Jenkins Pipeline DSL.
     */
    fun sh(
        script: String? = null,
        returnStdout: Boolean = false,
        returnStatus: Boolean = false,
        encoding: String? = null,
        label: String? = null
    ) {
        requireNotNull(script) { "Script parameter is required" }
        steps.add(
            Step.Shell(
                command = script,
                name = label,
                continueOnError = returnStatus,
                timeout = null,
                workingDirectory = null,
                returnStdout = returnStdout,
                returnStatus = returnStatus,
                encoding = encoding ?: "UTF-8"
            )
        )
    }
    
    /**
     * Ejecuta un comando batch (Windows).
     * Compatible con Jenkins Pipeline DSL.
     */
    fun bat(script: String) {
        steps.add(
            Step.Batch(
                command = script,
                name = null,
                continueOnError = false,
                timeout = null,
                workingDirectory = null,
                returnStdout = false,
                returnStatus = false,
                encoding = "UTF-8"
            )
        )
    }
    
    /**
     * Ejecuta un comando batch con parámetros.
     * Compatible con Jenkins Pipeline DSL.
     */
    fun bat(
        script: String? = null,
        returnStdout: Boolean = false,
        returnStatus: Boolean = false,
        encoding: String? = null,
        label: String? = null
    ) {
        requireNotNull(script) { "Script parameter is required" }
        steps.add(
            Step.Batch(
                command = script,
                name = label,
                continueOnError = returnStatus,
                timeout = null,
                workingDirectory = null,
                returnStdout = returnStdout,
                returnStatus = returnStatus,
                encoding = encoding ?: "UTF-8"
            )
        )
    }
    
    /**
     * Muestra un mensaje.
     * Compatible con Jenkins Pipeline DSL.
     */
    fun echo(message: String) {
        steps.add(
            Step.Echo(
                message = message,
                name = null,
                continueOnError = false,
                timeout = null
            )
        )
    }
    
    // NOTE: Specialized steps moved to dedicated extension modules:
    // - archiveArtifacts, publishTestResults -> jenkins-pipeline-steps extension
    // - checkout, git -> scm-steps extension
    // - docker -> docker-steps extension
    // - notification -> notification-steps extension
    // Use custom() method or dedicated extensions for these functionalities
    
    /**
     * Ejecuta script de archivo.
     */
    fun script(
        scriptFile: String,
        parameters: Map<String, String> = emptyMap(),
        interpreter: String? = null,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null
    ) {
        steps.add(
            Step.Script(
                scriptFile = scriptFile,
                parameters = parameters,
                interpreter = interpreter,
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    // NOTE: docker() and notification() methods moved to dedicated extensions
    
    // NOTE: dir, withEnv, timeout, retry, parallel steps are now provided by pipeline-steps-library extension
    // These methods have been removed to avoid duplication and ensure consistency with the extension library
    
    /**
     * Step personalizado.
     */
    fun custom(
        action: String,
        parameters: Map<String, String> = emptyMap(),
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null
    ) {
        steps.add(
            Step.Custom(
                action = action,
                parameters = parameters,
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    internal fun build(): List<Step> = steps.toList()
}

// NOTE: DockerStepBuilder and NotificationStepBuilder classes removed
// These builders are now provided by dedicated extension modules:
// - DockerStepBuilder -> docker-steps extension
// - NotificationStepBuilder -> notification-steps extension