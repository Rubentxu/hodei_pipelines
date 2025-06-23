package dev.rubentxu.hodei.pipelines.dsl.builders

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.model.*

/**
 * Builder para triggers del pipeline.
 */
@PipelineDslMarker
class TriggersBuilder {
    private val triggers: MutableList<Trigger> = mutableListOf()
    
    /**
     * Trigger basado en cron.
     */
    fun cron(expression: String) {
        triggers.add(Trigger.Cron(expression))
    }
    
    /**
     * Trigger basado en polling SCM.
     */
    fun pollSCM(pollSCM: String) {
        triggers.add(Trigger.SCM(pollSCM))
    }
    
    /**
     * Trigger basado en upstream projects.
     */
    fun upstream(projects: List<String>, threshold: String = "SUCCESS") {
        triggers.add(Trigger.Upstream(projects, threshold))
    }
    
    /**
     * Trigger basado en upstream projects (variadic).
     */
    fun upstream(vararg projects: String, threshold: String = "SUCCESS") {
        upstream(projects.toList(), threshold)
    }
    
    internal fun build(): List<Trigger> = triggers.toList()
}

/**
 * Builder para parámetros del pipeline.
 */
@PipelineDslMarker
class ParametersBuilder {
    private val parameters: MutableMap<String, Parameter> = mutableMapOf()
    
    /**
     * Parámetro de tipo string.
     */
    fun string(
        name: String,
        defaultValue: String? = null,
        description: String? = null
    ) {
        parameters[name] = Parameter(
            name = name,
            type = ParameterType.STRING,
            defaultValue = defaultValue,
            description = description
        )
    }
    
    /**
     * Parámetro de tipo text (multilínea).
     */
    fun text(
        name: String,
        defaultValue: String? = null,
        description: String? = null
    ) {
        parameters[name] = Parameter(
            name = name,
            type = ParameterType.TEXT,
            defaultValue = defaultValue,
            description = description
        )
    }
    
    /**
     * Parámetro de tipo boolean.
     */
    fun boolean(
        name: String,
        defaultValue: String? = null,
        description: String? = null
    ) {
        parameters[name] = Parameter(
            name = name,
            type = ParameterType.BOOLEAN,
            defaultValue = defaultValue,
            description = description
        )
    }
    
    /**
     * Parámetro de tipo choice.
     */
    fun choice(
        name: String,
        choices: List<String>,
        defaultValue: String? = null,
        description: String? = null
    ) {
        parameters[name] = Parameter(
            name = name,
            type = ParameterType.CHOICE,
            defaultValue = defaultValue,
            description = description,
            choices = choices
        )
    }
    
    /**
     * Parámetro de tipo choice (variadic).
     */
    fun choice(
        name: String,
        vararg choices: String,
        defaultValue: String? = null,
        description: String? = null
    ) {
        choice(name, choices.toList(), defaultValue, description)
    }
    
    /**
     * Parámetro de tipo password.
     */
    fun password(
        name: String,
        defaultValue: String? = null,
        description: String? = null
    ) {
        parameters[name] = Parameter(
            name = name,
            type = ParameterType.PASSWORD,
            defaultValue = defaultValue,
            description = description
        )
    }
    
    /**
     * Parámetro de tipo file.
     */
    fun file(
        name: String,
        description: String? = null
    ) {
        parameters[name] = Parameter(
            name = name,
            type = ParameterType.FILE,
            description = description
        )
    }
    
    internal fun build(): Map<String, Parameter> = parameters.toMap()
}

/**
 * Builder para acciones post-ejecución.
 */
@PipelineDslMarker
class PostActionsBuilder {
    private var always: MutableList<Step> = mutableListOf()
    private var success: MutableList<Step> = mutableListOf()
    private var failure: MutableList<Step> = mutableListOf()
    private var unstable: MutableList<Step> = mutableListOf()
    private var aborted: MutableList<Step> = mutableListOf()
    private var changed: MutableList<Step> = mutableListOf()
    private var fixed: MutableList<Step> = mutableListOf()
    private var regression: MutableList<Step> = mutableListOf()
    
    /**
     * Steps que siempre se ejecutan.
     */
    fun always(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.always.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan solo si el pipeline es exitoso.
     */
    fun success(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.success.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan solo si el pipeline falla.
     */
    fun failure(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.failure.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan si el pipeline es inestable.
     */
    fun unstable(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.unstable.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan si el pipeline es abortado.
     */
    fun aborted(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.aborted.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan si el estado del pipeline cambió.
     */
    fun changed(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.changed.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan si el pipeline se arregló.
     */
    fun fixed(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.fixed.addAll(builder.build())
    }
    
    /**
     * Steps que se ejecutan si el pipeline regresó.
     */
    fun regression(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.regression.addAll(builder.build())
    }
    
    internal fun build(): PostActions {
        return PostActions(
            always = always.toList(),
            success = success.toList(),
            failure = failure.toList(),
            unstable = unstable.toList(),
            aborted = aborted.toList(),
            changed = changed.toList(),
            fixed = fixed.toList(),
            regression = regression.toList()
        )
    }
}

/**
 * Builder para opciones del pipeline.
 */
@PipelineDslMarker
class PipelineOptionsBuilder {
    private var buildDiscarder: BuildDiscarder? = null
    private var timeout: TimeoutOption? = null
    private var retry: RetryOption? = null
    private var skipDefaultCheckout: Boolean = false
    private var parallelsAlwaysFailFast: Boolean = false
    
    /**
     * Configura build discarder.
     */
    fun buildDiscarder(block: BuildDiscarderBuilder.() -> Unit) {
        val builder = BuildDiscarderBuilder()
        builder.block()
        this.buildDiscarder = builder.build()
    }
    
    /**
     * Configura timeout global.
     */
    fun timeout(time: Int, unit: TimeUnit = TimeUnit.MINUTES) {
        this.timeout = TimeoutOption(time, unit)
    }
    
    /**
     * Configura retry global.
     */
    fun retry(count: Int, vararg conditions: RetryCondition) {
        this.retry = RetryOption(count, conditions.toList())
    }
    
    /**
     * Configura skip default checkout.
     */
    fun skipDefaultCheckout(skip: Boolean = true) {
        this.skipDefaultCheckout = skip
    }
    
    /**
     * Configura parallels always fail fast.
     */
    fun parallelsAlwaysFailFast(failFast: Boolean = true) {
        this.parallelsAlwaysFailFast = failFast
    }
    
    internal fun build(): PipelineOptions {
        return PipelineOptions(
            buildDiscarder = buildDiscarder,
            timeout = timeout,
            retry = retry,
            skipDefaultCheckout = skipDefaultCheckout,
            parallelsAlwaysFailFast = parallelsAlwaysFailFast
        )
    }
}

/**
 * Builder para build discarder.
 */
@PipelineDslMarker
class BuildDiscarderBuilder {
    private var numToKeep: Int? = null
    private var daysToKeep: Int? = null
    private var artifactNumToKeep: Int? = null
    private var artifactDaysToKeep: Int? = null
    
    fun numToKeep(numToKeep: Int) {
        this.numToKeep = numToKeep
    }
    
    fun daysToKeep(daysToKeep: Int) {
        this.daysToKeep = daysToKeep
    }
    
    fun artifactNumToKeep(artifactNumToKeep: Int) {
        this.artifactNumToKeep = artifactNumToKeep
    }
    
    fun artifactDaysToKeep(artifactDaysToKeep: Int) {
        this.artifactDaysToKeep = artifactDaysToKeep
    }
    
    internal fun build(): BuildDiscarder {
        return BuildDiscarder(
            numToKeep = numToKeep,
            daysToKeep = daysToKeep,
            artifactNumToKeep = artifactNumToKeep,
            artifactDaysToKeep = artifactDaysToKeep
        )
    }
}