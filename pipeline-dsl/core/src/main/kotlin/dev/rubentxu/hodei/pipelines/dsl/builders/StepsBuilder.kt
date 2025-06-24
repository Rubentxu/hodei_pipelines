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
    
    /**
     * Archiva artifacts.
     */
    fun archiveArtifacts(
        artifacts: String,
        allowEmptyArchive: Boolean = false,
        caseSensitive: Boolean = true,
        defaultExcludes: Boolean = true,
        excludes: String? = null,
        fingerprint: Boolean = false,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null
    ) {
        steps.add(
            Step.ArchiveArtifacts(
                artifacts = artifacts,
                allowEmptyArchive = allowEmptyArchive,
                caseSensitive = caseSensitive,
                defaultExcludes = defaultExcludes,
                excludes = excludes,
                fingerprint = fingerprint,
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    /**
     * Publica resultados de tests.
     */
    fun publishTestResults(
        testResultsPattern: String,
        allowEmptyResults: Boolean = false,
        checksName: String? = null,
        healthScaleFactor: Double = 1.0,
        keepLongStdio: Boolean = false,
        skipMarkingBuildUnstable: Boolean = false,
        skipPublishingChecks: Boolean = false,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null
    ) {
        steps.add(
            Step.PublishTestResults(
                testResultsPattern = testResultsPattern,
                allowEmptyResults = allowEmptyResults,
                checksName = checksName,
                healthScaleFactor = healthScaleFactor,
                keepLongStdio = keepLongStdio,
                skipMarkingBuildUnstable = skipMarkingBuildUnstable,
                skipPublishingChecks = skipPublishingChecks,
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    /**
     * Checkout de código fuente.
     */
    fun checkout(
        scm: SCMConfig,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null
    ) {
        steps.add(
            Step.Checkout(
                scm = scm,
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    /**
     * Checkout Git con DSL.
     */
    fun git(
        url: String,
        branch: String = "main",
        credentialsId: String? = null,
        shallow: Boolean = false,
        depth: Int? = null,
        submodules: Boolean = false,
        clean: Boolean = false,
        checkoutDir: String? = null,
        lfs: Boolean = false,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null
    ) {
        steps.add(
            Step.Checkout(
                scm = SCMConfig.Git(
                    url = url,
                    branch = branch,
                    credentialsId = credentialsId,
                    shallow = shallow,
                    depth = depth,
                    submodules = submodules,
                    clean = clean,
                    checkoutDir = checkoutDir,
                    lfs = lfs
                ),
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
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
    
    /**
     * Operaciones Docker.
     */
    fun docker(
        image: String,
        block: DockerStepBuilder.() -> Unit = {}
    ) {
        val builder = DockerStepBuilder(image)
        builder.block()
        steps.add(builder.build())
    }
    
    /**
     * Envía notificaciones.
     */
    fun notification(
        message: String,
        block: NotificationStepBuilder.() -> Unit
    ) {
        val builder = NotificationStepBuilder(message)
        builder.block()
        steps.add(builder.build())
    }
    
    /**
     * Ejecuta steps en un directorio específico.
     */
    fun dir(
        path: String,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null,
        block: StepsBuilder.() -> Unit
    ) {
        val builder = StepsBuilder()
        builder.block()
        steps.add(
            Step.Dir(
                path = path,
                steps = builder.build(),
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    /**
     * Ejecuta steps con variables de entorno específicas.
     */
    fun withEnv(
        environment: Map<String, String>,
        name: String? = null,
        continueOnError: Boolean = false,
        timeout: Int? = null,
        block: StepsBuilder.() -> Unit
    ) {
        val builder = StepsBuilder()
        builder.block()
        steps.add(
            Step.WithEnv(
                environment = environment,
                steps = builder.build(),
                name = name,
                continueOnError = continueOnError,
                timeout = timeout
            )
        )
    }
    
    /**
     * Step de timeout compatible con Jenkins.
     */
    fun timeout(time: Int, unit: TimeUnit = TimeUnit.MINUTES, block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        steps.add(
            Step.Timeout(
                time = time,
                unit = unit,
                steps = builder.build()
            )
        )
    }
    
    /**
     * Step de retry compatible con Jenkins.
     */
    fun retry(count: Int, block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        steps.add(
            Step.Retry(
                count = count,
                steps = builder.build()
            )
        )
    }
    
    /**
     * Step de ejecución paralela compatible con Jenkins.
     */
    fun parallel(branches: Map<String, () -> Unit>) {
        val parallelBranches = mutableMapOf<String, List<Step>>()
        
        branches.forEach { (name, block) ->
            val builder = StepsBuilder()
            // Ejecutar el bloque en el contexto del builder
            block.invoke()
            parallelBranches[name] = builder.build()
        }
        
        steps.add(
            Step.Parallel(
                branches = parallelBranches,
                failFast = true
            )
        )
    }
    
    /**
     * Step de ejecución paralela con DSL.
     */
    fun parallel(failFast: Boolean = true, block: ParallelBuilder.() -> Unit) {
        val builder = ParallelBuilder(failFast)
        builder.block()
        steps.add(builder.build())
    }
    
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

/**
 * Builder para steps Docker.
 */
@PipelineDslMarker
class DockerStepBuilder(private val image: String) {
    private var command: String? = null
    private var args: MutableList<String> = mutableListOf()
    private var volumes: MutableList<String> = mutableListOf()
    private var environment: MutableMap<String, String> = mutableMapOf()
    private var workingDirectory: String? = null
    private var user: String? = null
    private var entrypoint: String? = null
    private var name: String? = null
    private var continueOnError: Boolean = false
    private var timeout: Int? = null
    
    fun command(command: String) {
        this.command = command
    }
    
    fun args(vararg args: String) {
        this.args.addAll(args)
    }
    
    fun args(args: List<String>) {
        this.args.addAll(args)
    }
    
    fun volume(hostPath: String, containerPath: String) {
        this.volumes.add("$hostPath:$containerPath")
    }
    
    fun volumes(vararg volumes: String) {
        this.volumes.addAll(volumes)
    }
    
    fun env(key: String, value: String) {
        this.environment[key] = value
    }
    
    fun env(env: Map<String, String>) {
        this.environment.putAll(env)
    }
    
    fun workingDirectory(workingDirectory: String) {
        this.workingDirectory = workingDirectory
    }
    
    fun user(user: String) {
        this.user = user
    }
    
    fun entrypoint(entrypoint: String) {
        this.entrypoint = entrypoint
    }
    
    fun name(name: String) {
        this.name = name
    }
    
    fun continueOnError(continueOnError: Boolean = true) {
        this.continueOnError = continueOnError
    }
    
    fun timeout(timeout: Int) {
        this.timeout = timeout
    }
    
    internal fun build(): Step.Docker {
        return Step.Docker(
            image = image,
            command = command,
            args = args.toList(),
            volumes = volumes.toList(),
            environment = environment.toMap(),
            workingDirectory = workingDirectory,
            user = user,
            entrypoint = entrypoint,
            name = name,
            continueOnError = continueOnError,
            timeout = timeout
        )
    }
}

/**
 * Builder para steps de notificación.
 */
@PipelineDslMarker
class NotificationStepBuilder(private val message: String) {
    private var channels: MutableList<NotificationChannel> = mutableListOf()
    private var onlyOnStateChange: Boolean = false
    private var name: String? = null
    private var continueOnError: Boolean = true
    private var timeout: Int? = null
    
    fun slack(
        channel: String,
        token: String? = null,
        username: String? = null,
        color: String? = null
    ) {
        channels.add(
            NotificationChannel.Slack(
                channel = channel,
                token = token,
                username = username,
                color = color
            )
        )
    }
    
    fun email(
        to: List<String>,
        subject: String? = null,
        mimeType: String = "text/plain"
    ) {
        channels.add(
            NotificationChannel.Email(
                to = to,
                subject = subject,
                mimeType = mimeType
            )
        )
    }
    
    fun email(vararg to: String, subject: String? = null, mimeType: String = "text/plain") {
        email(to.toList(), subject, mimeType)
    }
    
    fun teams(
        webhookUrl: String,
        color: String? = null
    ) {
        channels.add(
            NotificationChannel.Teams(
                webhookUrl = webhookUrl,
                color = color
            )
        )
    }
    
    fun custom(
        type: String,
        config: Map<String, String>
    ) {
        channels.add(
            NotificationChannel.Custom(
                type = type,
                config = config
            )
        )
    }
    
    fun onlyOnStateChange(onlyOnStateChange: Boolean = true) {
        this.onlyOnStateChange = onlyOnStateChange
    }
    
    fun name(name: String) {
        this.name = name
    }
    
    fun continueOnError(continueOnError: Boolean = true) {
        this.continueOnError = continueOnError
    }
    
    fun timeout(timeout: Int) {
        this.timeout = timeout
    }
    
    internal fun build(): Step.Notification {
        return Step.Notification(
            message = message,
            channels = channels.toList(),
            onlyOnStateChange = onlyOnStateChange,
            name = name,
            continueOnError = continueOnError,
            timeout = timeout
        )
    }
}