package dev.rubentxu.hodei.pipelines.steps.dsl

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.extensions.ExtensionStep
import dev.rubentxu.hodei.pipelines.dsl.model.TimeUnit

/**
 * DSL Extensions para workflow-basic-steps compatible con Jenkins.
 * Proporciona sintaxis natural para todos los steps básicos.
 */

/**
 * Build another job.
 * Equivalent to Jenkins build step.
 */
@PipelineDslMarker
fun StepsBuilder.build(
    job: String,
    parameters: Map<String, String> = emptyMap(),
    propagate: Boolean = true,
    wait: Boolean = true
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "build",
            parameters = mapOf(
                "job" to job,
                "parameters" to parameters,
                "propagate" to propagate,
                "wait" to wait
            )
        )
    )
}

/**
 * Build with parameter block DSL.
 */
@PipelineDslMarker
inline fun StepsBuilder.build(
    job: String,
    propagate: Boolean = true,
    wait: Boolean = true,
    block: BuildParametersBuilder.() -> Unit = {}
) {
    val builder = BuildParametersBuilder()
    builder.block()
    
    build(
        job = job,
        parameters = builder.build(),
        propagate = propagate,
        wait = wait
    )
}

@PipelineDslMarker
class BuildParametersBuilder {
    private val parameters = mutableMapOf<String, String>()
    
    fun string(name: String, value: String) {
        parameters[name] = value
    }
    
    fun booleanParam(name: String, value: Boolean) {
        parameters[name] = value.toString()
    }
    
    fun choice(name: String, value: String) {
        parameters[name] = value
    }
    
    internal fun build(): Map<String, String> = parameters.toMap()
}

/**
 * Catch errors and optionally set build result.
 */
@PipelineDslMarker
inline fun StepsBuilder.catchError(
    buildResult: String = "FAILURE",
    message: String? = null,
    stageResult: String? = null,
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "catchError",
            parameters = mapOf(
                "buildResult" to buildResult,
                "message" to (message ?: ""),
                "stageResult" to (stageResult ?: "")
            )
        )
    )
    
    // Execute nested steps
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Delete workspace directory.
 */
@PipelineDslMarker
fun StepsBuilder.deleteDir() {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "deleteDir",
            parameters = emptyMap()
        )
    )
}

/**
 * Execute steps in a different directory.
 * Already exists in core DSL, but adding for completeness.
 */
@PipelineDslMarker
inline fun StepsBuilder.dir(
    path: String,
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "dir",
            parameters = mapOf("path" to path)
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Fail the build with an error message.
 */
@PipelineDslMarker
fun StepsBuilder.error(message: String) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "error",
            parameters = mapOf("message" to message)
        )
    )
}

/**
 * Check if file exists.
 */
@PipelineDslMarker
fun StepsBuilder.fileExists(file: String): String {
    val stepName = "fileExists-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "fileExists",
            parameters = mapOf("file" to file),
            name = stepName
        )
    )
    return stepName
}

/**
 * Check if running on Unix-like system.
 */
@PipelineDslMarker
fun StepsBuilder.isUnix(): String {
    val stepName = "isUnix-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "isUnix",
            parameters = emptyMap(),
            name = stepName
        )
    )
    return stepName
}

/**
 * Send email notification.
 */
@PipelineDslMarker
fun StepsBuilder.mail(
    to: String,
    subject: String,
    body: String = "",
    from: String? = null,
    cc: String? = null,
    bcc: String? = null,
    attachLog: Boolean = false
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "mail",
            parameters = mapOf(
                "to" to to,
                "subject" to subject,
                "body" to body,
                "from" to (from ?: ""),
                "cc" to (cc ?: ""),
                "bcc" to (bcc ?: ""),
                "attachLog" to attachLog
            )
        )
    )
}

/**
 * Mail with DSL configuration.
 */
@PipelineDslMarker
inline fun StepsBuilder.mail(
    to: String,
    subject: String,
    block: MailBuilder.() -> Unit
) {
    val builder = MailBuilder(to, subject)
    builder.block()
    builder.addToSteps(this)
}

@PipelineDslMarker
class MailBuilder(
    private val to: String,
    private val subject: String
) {
    var body: String = ""
    var from: String? = null
    var cc: String? = null
    var bcc: String? = null
    var attachLog: Boolean = false
    
    internal fun addToSteps(stepsBuilder: StepsBuilder) {
        stepsBuilder.mail(
            to = to,
            subject = subject,
            body = body,
            from = from,
            cc = cc,
            bcc = bcc,
            attachLog = attachLog
        )
    }
}

/**
 * Set a milestone.
 */
@PipelineDslMarker
fun StepsBuilder.milestone(ordinal: Int? = null, label: String? = null) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "milestone",
            parameters = mapOf(
                "ordinal" to (ordinal ?: 0),
                "label" to (label ?: "")
            )
        )
    )
}

/**
 * Allocate a node.
 */
@PipelineDslMarker
inline fun StepsBuilder.node(
    label: String = "any",
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "node",
            parameters = mapOf("label" to label)
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Get current working directory.
 */
@PipelineDslMarker
fun StepsBuilder.pwd(tmp: Boolean = false): String {
    val stepName = "pwd-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "pwd",
            parameters = mapOf("tmp" to tmp),
            name = stepName
        )
    )
    return stepName
}

/**
 * Read file contents.
 */
@PipelineDslMarker
fun StepsBuilder.readFile(
    file: String,
    encoding: String = "UTF-8"
): String {
    val stepName = "readFile-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "readFile",
            parameters = mapOf(
                "file" to file,
                "encoding" to encoding
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Retry execution.
 * Already exists in core DSL, adding extended version.
 */
@PipelineDslMarker
inline fun StepsBuilder.retry(
    count: Int = 3,
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "retry",
            parameters = mapOf("count" to count)
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Execute Groovy script.
 */
@PipelineDslMarker
fun StepsBuilder.script(script: String) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "script",
            parameters = mapOf("script" to script)
        )
    )
}

/**
 * Sleep for specified time.
 */
@PipelineDslMarker
fun StepsBuilder.sleep(
    time: Long,
    unit: TimeUnit = TimeUnit.SECONDS
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "sleep",
            parameters = mapOf(
                "time" to time,
                "unit" to unit.name
            )
        )
    )
}

/**
 * Sleep with different time units.
 */
@PipelineDslMarker
fun StepsBuilder.sleep(seconds: Long) = sleep(seconds, TimeUnit.SECONDS)

@PipelineDslMarker
fun StepsBuilder.sleepMinutes(minutes: Long) = sleep(minutes, TimeUnit.MINUTES)

@PipelineDslMarker
fun StepsBuilder.sleepHours(hours: Long) = sleep(hours, TimeUnit.HOURS)

/**
 * Define a stage (nested stages).
 */
@PipelineDslMarker
inline fun StepsBuilder.stage(
    name: String,
    concurrency: Int? = null,
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "stage",
            parameters = mapOf(
                "name" to name,
                "concurrency" to (concurrency ?: 0)
            )
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Set timeout for execution.
 * Enhanced version of core timeout.
 */
@PipelineDslMarker
inline fun StepsBuilder.timeout(
    time: Long,
    unit: TimeUnit = TimeUnit.MINUTES,
    activity: Boolean = false,
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "timeout",
            parameters = mapOf(
                "time" to time,
                "unit" to unit.name,
                "activity" to activity
            )
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Load a tool.
 */
@PipelineDslMarker
fun StepsBuilder.tool(
    name: String,
    type: String? = null
): String {
    val stepName = "tool-${System.currentTimeMillis()}"
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "tool",
            parameters = mapOf(
                "name" to name,
                "type" to (type ?: "")
            ),
            name = stepName
        )
    )
    return stepName
}

/**
 * Mark build as unstable.
 */
@PipelineDslMarker
fun StepsBuilder.unstable(message: String = "Build marked as unstable") {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "unstable",
            parameters = mapOf("message" to message)
        )
    )
}

/**
 * Wait until condition is met.
 */
@PipelineDslMarker
fun StepsBuilder.waitUntil(
    condition: String,
    initialRecurrencePeriod: Long = 250L,
    quiet: Boolean = false
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "waitUntil",
            parameters = mapOf(
                "condition" to condition,
                "initialRecurrencePeriod" to initialRecurrencePeriod,
                "quiet" to quiet
            )
        )
    )
}

/**
 * Wait until with DSL block.
 */
@PipelineDslMarker
inline fun StepsBuilder.waitUntil(
    initialRecurrencePeriod: Long = 250L,
    quiet: Boolean = false,
    crossinline conditionBlock: () -> Boolean
) {
    // En un entorno real, esto evaluaría el bloque periódicamente
    waitUntil(
        condition = "custom_condition",
        initialRecurrencePeriod = initialRecurrencePeriod,
        quiet = quiet
    )
}

/**
 * Warn on error but continue.
 */
@PipelineDslMarker
inline fun StepsBuilder.warnError(
    message: String = "Warning: error occurred",
    catchInterruptions: Boolean = true,
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "warnError",
            parameters = mapOf(
                "message" to message,
                "catchInterruptions" to catchInterruptions
            )
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Execute with environment variables.
 * Enhanced version of core withEnv.
 */
@PipelineDslMarker
inline fun StepsBuilder.withEnv(
    vararg env: String,
    overrides: Map<String, String> = emptyMap(),
    crossinline block: StepsBuilder.() -> Unit
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "withEnv",
            parameters = mapOf(
                "env" to env.toList(),
                "overrides" to overrides
            )
        )
    )
    
    val nestedBuilder = StepsBuilder()
    nestedBuilder.block()
}

/**
 * Write file contents.
 */
@PipelineDslMarker
fun StepsBuilder.writeFile(
    file: String,
    text: String,
    encoding: String = "UTF-8"
) {
    addExtensionStep(
        ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "writeFile",
            parameters = mapOf(
                "file" to file,
                "text" to text,
                "encoding" to encoding
            )
        )
    )
}

// Helper function para agregar extension steps
private fun StepsBuilder.addExtensionStep(step: ExtensionStep) {
    // Usar reflexión para agregar el step a la lista interna
    val stepsField = this::class.java.getDeclaredField("steps")
    stepsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val steps = stepsField.get(this) as MutableList<dev.rubentxu.hodei.pipelines.dsl.model.Step>
    steps.add(step)
}