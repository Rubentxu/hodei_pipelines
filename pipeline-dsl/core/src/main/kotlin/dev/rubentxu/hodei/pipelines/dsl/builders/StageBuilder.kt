package dev.rubentxu.hodei.pipelines.dsl.builders

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.model.*
import dev.rubentxu.hodei.pipelines.dsl.model.StageType

/**
 * Builder para la colección de stages del pipeline.
 */
@PipelineDslMarker
class StagesBuilder {
    private val stages: MutableList<Stage> = mutableListOf()
    
    /**
     * Define un stage individual.
     */
    fun stage(name: String, block: StageBuilder.() -> Unit) {
        val builder = StageBuilder(name)
        builder.block()
        stages.add(builder.build())
    }
    
    internal fun build(): List<Stage> = stages.toList()
}

/**
 * Builder para un stage individual.
 */
@PipelineDslMarker
class StageBuilder(private val name: String) {
    private var description: String? = null
    private var steps: MutableList<Step> = mutableListOf()
    private var parallel: ParallelStages? = null
    private var agent: Agent? = null
    private var environment: MutableMap<String, String> = mutableMapOf()
    private var whenCondition: WhenCondition? = null
    private var input: InputDirective? = null
    private var options: StageOptions? = null
    private var post: PostActions? = null
    private var tools: MutableList<Tool> = mutableListOf()
    private var requires: MutableList<String> = mutableListOf()
    private var produces: MutableList<String> = mutableListOf()
    private var type: StageType = StageType.CUSTOM
    
    /**
     * Establece la descripción del stage.
     */
    fun description(description: String) {
        this.description = description
    }
    
    /**
     * Define steps secuenciales del stage.
     */
    fun steps(block: StepsBuilder.() -> Unit) {
        val builder = StepsBuilder()
        builder.block()
        this.steps.addAll(builder.build())
    }
    
    /**
     * Define stages paralelos.
     */
    fun parallel(failFast: Boolean = true, block: ParallelStagesBuilder.() -> Unit) {
        val builder = ParallelStagesBuilder(failFast)
        builder.block()
        this.parallel = builder.build()
    }
    
    /**
     * Configura el agente específico del stage.
     */
    fun agent(block: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder()
        builder.block()
        this.agent = builder.build()
    }
    
    /**
     * Configura variables de entorno del stage.
     */
    fun environment(block: EnvironmentBuilder.() -> Unit) {
        val builder = EnvironmentBuilder()
        builder.block()
        this.environment.putAll(builder.build())
    }
    
    /**
     * Configura condiciones para ejecución condicional.
     */
    fun `when`(block: WhenConditionBuilder.() -> Unit) {
        val builder = WhenConditionBuilder()
        builder.block()
        this.whenCondition = builder.build()
    }
    
    /**
     * Configura input directive para stages interactivos.
     */
    fun input(message: String, block: InputDirectiveBuilder.() -> Unit = {}) {
        val builder = InputDirectiveBuilder(message)
        builder.block()
        this.input = builder.build()
    }
    
    /**
     * Configura opciones del stage.
     */
    fun options(block: StageOptionsBuilder.() -> Unit) {
        val builder = StageOptionsBuilder()
        builder.block()
        this.options = builder.build()
    }
    
    /**
     * Configura acciones post-stage.
     */
    fun post(block: PostActionsBuilder.() -> Unit) {
        val builder = PostActionsBuilder()
        builder.block()
        this.post = builder.build()
    }
    
    /**
     * Configura herramientas disponibles.
     */
    fun tools(block: ToolsBuilder.() -> Unit) {
        val builder = ToolsBuilder()
        builder.block()
        this.tools.addAll(builder.build())
    }
    
    /**
     * Define artifacts requeridos por este stage.
     */
    fun requires(vararg artifacts: String) {
        this.requires.addAll(artifacts)
    }
    
    /**
     * Define artifacts producidos por este stage.
     */
    fun produces(vararg artifacts: String) {
        this.produces.addAll(artifacts)
    }
    
    /**
     * Define el tipo del stage.
     */
    fun type(type: StageType) {
        this.type = type
    }
    
    internal fun build(): Stage {
        return Stage(
            name = name,
            description = description,
            steps = steps.toList(),
            parallel = parallel,
            agent = agent,
            environment = environment.toMap(),
            whenCondition = whenCondition,
            input = input,
            options = options,
            post = post,
            tools = tools.toList(),
            requires = requires.toList(),
            produces = produces.toList(),
            type = type
        )
    }
}

/**
 * Builder para stages paralelos.
 */
@PipelineDslMarker
class ParallelStagesBuilder(private val failFast: Boolean) {
    private val stages: MutableList<Stage> = mutableListOf()
    
    /**
     * Define un stage dentro del bloque paralelo.
     */
    fun stage(name: String, block: StageBuilder.() -> Unit) {
        val builder = StageBuilder(name)
        builder.block()
        stages.add(builder.build())
    }
    
    internal fun build(): ParallelStages {
        return ParallelStages(
            stages = stages.toList(),
            failFast = failFast
        )
    }
}

/**
 * Builder para condiciones when.
 */
@PipelineDslMarker
class WhenConditionBuilder {
    private var condition: WhenCondition? = null
    
    /**
     * Condición basada en branch.
     */
    fun branch(pattern: String) {
        condition = WhenCondition.Branch(pattern)
    }
    
    /**
     * Condición basada en múltiples branches.
     */
    fun branch(vararg patterns: String) {
        val conditions = patterns.map { WhenCondition.Branch(it) }
        condition = WhenCondition.AnyOf(conditions)
    }
    
    /**
     * Condición basada en tag.
     */
    fun tag(pattern: String) {
        condition = WhenCondition.Tag(pattern)
    }
    
    /**
     * Condición basada en change request.
     */
    fun changeRequest(target: String? = null) {
        condition = WhenCondition.ChangeRequest(target)
    }
    
    /**
     * Condición basada en variable de entorno.
     */
    fun environment(name: String, value: String) {
        condition = WhenCondition.Environment(name, value)
    }
    
    /**
     * Condición basada en expresión.
     */
    fun expression(expression: String) {
        condition = WhenCondition.Expression(expression)
    }
    
    /**
     * Combina condiciones con AND lógico.
     */
    fun allOf(block: AllOfBuilder.() -> Unit) {
        val builder = AllOfBuilder()
        builder.block()
        condition = builder.build()
    }
    
    /**
     * Combina condiciones con OR lógico.
     */
    fun anyOf(block: AnyOfBuilder.() -> Unit) {
        val builder = AnyOfBuilder()
        builder.block()
        condition = builder.build()
    }
    
    /**
     * Niega una condición.
     */
    fun not(block: WhenConditionBuilder.() -> Unit) {
        val builder = WhenConditionBuilder()
        builder.block()
        val innerCondition = builder.build()
        if (innerCondition != null) {
            condition = WhenCondition.Not(innerCondition)
        }
    }
    
    internal fun build(): WhenCondition? = condition
}

/**
 * Builder para condiciones AllOf.
 */
@PipelineDslMarker
class AllOfBuilder {
    private val conditions: MutableList<WhenCondition> = mutableListOf()
    
    fun branch(pattern: String) {
        conditions.add(WhenCondition.Branch(pattern))
    }
    
    fun tag(pattern: String) {
        conditions.add(WhenCondition.Tag(pattern))
    }
    
    fun changeRequest(target: String? = null) {
        conditions.add(WhenCondition.ChangeRequest(target))
    }
    
    fun environment(name: String, value: String) {
        conditions.add(WhenCondition.Environment(name, value))
    }
    
    fun expression(expression: String) {
        conditions.add(WhenCondition.Expression(expression))
    }
    
    internal fun build(): WhenCondition.AllOf {
        return WhenCondition.AllOf(conditions.toList())
    }
}

/**
 * Builder para condiciones AnyOf.
 */
@PipelineDslMarker
class AnyOfBuilder {
    private val conditions: MutableList<WhenCondition> = mutableListOf()
    
    fun branch(pattern: String) {
        conditions.add(WhenCondition.Branch(pattern))
    }
    
    fun tag(pattern: String) {
        conditions.add(WhenCondition.Tag(pattern))
    }
    
    fun changeRequest(target: String? = null) {
        conditions.add(WhenCondition.ChangeRequest(target))
    }
    
    fun environment(name: String, value: String) {
        conditions.add(WhenCondition.Environment(name, value))
    }
    
    fun expression(expression: String) {
        conditions.add(WhenCondition.Expression(expression))
    }
    
    internal fun build(): WhenCondition.AnyOf {
        return WhenCondition.AnyOf(conditions.toList())
    }
}

/**
 * Builder para input directive.
 */
@PipelineDslMarker
class InputDirectiveBuilder(private val message: String) {
    private var id: String? = null
    private var ok: String = "Proceed"
    private var submitter: String? = null
    private var submitterParameter: String? = null
    private var parameters: MutableList<Parameter> = mutableListOf()
    
    fun id(id: String) {
        this.id = id
    }
    
    fun ok(ok: String) {
        this.ok = ok
    }
    
    fun submitter(submitter: String) {
        this.submitter = submitter
    }
    
    fun submitterParameter(submitterParameter: String) {
        this.submitterParameter = submitterParameter
    }
    
    fun parameters(block: ParametersBuilder.() -> Unit) {
        val builder = ParametersBuilder()
        builder.block()
        this.parameters.addAll(builder.build().values)
    }
    
    internal fun build(): InputDirective {
        return InputDirective(
            message = message,
            id = id,
            ok = ok,
            submitter = submitter,
            submitterParameter = submitterParameter,
            parameters = parameters.toList()
        )
    }
}

/**
 * Builder para opciones de stage.
 */
@PipelineDslMarker
class StageOptionsBuilder {
    private var skipDefaultCheckout: Boolean = false
    private var timeout: TimeoutOption? = null
    private var retry: RetryOption? = null
    private var timestamps: Boolean = false
    
    fun skipDefaultCheckout(skip: Boolean = true) {
        this.skipDefaultCheckout = skip
    }
    
    fun timeout(time: Int, unit: TimeUnit = TimeUnit.MINUTES) {
        this.timeout = TimeoutOption(time, unit)
    }
    
    fun retry(count: Int, vararg conditions: RetryCondition) {
        this.retry = RetryOption(count, conditions.toList())
    }
    
    fun timestamps(enable: Boolean = true) {
        this.timestamps = enable
    }
    
    internal fun build(): StageOptions {
        return StageOptions(
            skipDefaultCheckout = skipDefaultCheckout,
            timeout = timeout,
            retry = retry,
            timestamps = timestamps
        )
    }
}

/**
 * Builder para herramientas.
 */
@PipelineDslMarker
class ToolsBuilder {
    private val tools: MutableList<Tool> = mutableListOf()
    
    fun maven(version: String = "3.8.6") {
        tools.add(Tool.Maven(version))
    }
    
    fun gradle(version: String = "7.6") {
        tools.add(Tool.Gradle(version))
    }
    
    fun node(version: String = "18") {
        tools.add(Tool.Node(version))
    }
    
    fun jdk(version: String = "17") {
        tools.add(Tool.JDK(version))
    }
    
    fun docker(version: String = "latest") {
        tools.add(Tool.Docker(version))
    }
    
    fun custom(name: String, version: String) {
        tools.add(Tool.Custom(name, version))
    }
    
    internal fun build(): List<Tool> = tools.toList()
}