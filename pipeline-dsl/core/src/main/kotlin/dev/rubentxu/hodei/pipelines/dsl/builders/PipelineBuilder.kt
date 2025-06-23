package dev.rubentxu.hodei.pipelines.dsl.builders

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.model.*

/**
 * Builder principal para la construcción de pipelines con DSL tipado.
 * 
 * Integra con el sistema de workers existente manteniendo type safety
 * y proporcionando una API fluida para la definición de pipelines.
 */
@PipelineDslMarker
class PipelineBuilder(private val name: String) {
    private var description: String? = null
    private var agent: Agent? = null
    private var environment: MutableMap<String, String> = mutableMapOf()
    private var stages: MutableList<Stage> = mutableListOf()
    private var post: PostActions? = null
    private var triggers: MutableList<Trigger> = mutableListOf()
    private var parameters: MutableMap<String, Parameter> = mutableMapOf()
    private var options: PipelineOptions = PipelineOptions()
    
    /**
     * Establece la descripción del pipeline.
     */
    fun description(description: String) {
        this.description = description
    }
    
    /**
     * Configura el agente de ejecución.
     */
    fun agent(block: AgentBuilder.() -> Unit) {
        val builder = AgentBuilder()
        builder.block()
        this.agent = builder.build()
    }
    
    /**
     * Configura variables de entorno.
     */
    fun environment(block: EnvironmentBuilder.() -> Unit) {
        val builder = EnvironmentBuilder()
        builder.block()
        this.environment.putAll(builder.build())
    }
    
    /**
     * Configura variables de entorno usando map syntax.
     */
    fun environment(env: Map<String, String>) {
        this.environment.putAll(env)
    }
    
    /**
     * Define los stages del pipeline.
     */
    fun stages(block: StagesBuilder.() -> Unit) {
        val builder = StagesBuilder()
        builder.block()
        this.stages.addAll(builder.build())
    }
    
    /**
     * Configura acciones post-ejecución.
     */
    fun post(block: PostActionsBuilder.() -> Unit) {
        val builder = PostActionsBuilder()
        builder.block()
        this.post = builder.build()
    }
    
    /**
     * Configura triggers del pipeline.
     */
    fun triggers(block: TriggersBuilder.() -> Unit) {
        val builder = TriggersBuilder()
        builder.block()
        this.triggers.addAll(builder.build())
    }
    
    /**
     * Configura parámetros del pipeline.
     */
    fun parameters(block: ParametersBuilder.() -> Unit) {
        val builder = ParametersBuilder()
        builder.block()
        this.parameters.putAll(builder.build())
    }
    
    /**
     * Configura opciones del pipeline.
     */
    fun options(block: PipelineOptionsBuilder.() -> Unit) {
        val builder = PipelineOptionsBuilder()
        builder.block()
        this.options = builder.build()
    }
    
    /**
     * Construye el pipeline final.
     */
    internal fun build(): Pipeline {
        return Pipeline(
            name = name,
            description = description,
            agent = agent,
            environment = environment.toMap(),
            stages = stages.toList(),
            post = post,
            triggers = triggers.toList(),
            parameters = parameters.toMap(),
            options = options
        )
    }
}

/**
 * Builder para configuración de agentes.
 */
@PipelineDslMarker
class AgentBuilder {
    private var label: String? = null
    private var docker: DockerAgent? = null
    private var kubernetes: KubernetesAgent? = null
    
    /**
     * Configura agente por label.
     */
    fun label(label: String) {
        this.label = label
    }
    
    /**
     * Configura agente Docker.
     */
    fun docker(image: String, block: DockerAgentBuilder.() -> Unit = {}) {
        val builder = DockerAgentBuilder(image)
        builder.block()
        this.docker = builder.build()
    }
    
    /**
     * Configura agente Kubernetes.
     */
    fun kubernetes(block: KubernetesAgentBuilder.() -> Unit) {
        val builder = KubernetesAgentBuilder()
        builder.block()
        this.kubernetes = builder.build()
    }
    
    internal fun build(): Agent {
        return Agent(
            label = label,
            docker = docker,
            kubernetes = kubernetes
        )
    }
}

/**
 * Builder para agente Docker.
 */
@PipelineDslMarker
class DockerAgentBuilder(private val image: String) {
    private var args: MutableList<String> = mutableListOf()
    private var registryUrl: String? = null
    private var registryCredentialsId: String? = null
    
    fun args(vararg args: String) {
        this.args.addAll(args)
    }
    
    fun args(args: List<String>) {
        this.args.addAll(args)
    }
    
    fun registryUrl(url: String) {
        this.registryUrl = url
    }
    
    fun registryCredentialsId(credentialsId: String) {
        this.registryCredentialsId = credentialsId
    }
    
    internal fun build(): DockerAgent {
        return DockerAgent(
            image = image,
            args = args.toList(),
            registryUrl = registryUrl,
            registryCredentialsId = registryCredentialsId
        )
    }
}

/**
 * Builder para agente Kubernetes.
 */
@PipelineDslMarker
class KubernetesAgentBuilder {
    private var yaml: String? = null
    private var yamlFile: String? = null
    private var namespace: String? = null
    
    fun yaml(yaml: String) {
        this.yaml = yaml
    }
    
    fun yamlFile(yamlFile: String) {
        this.yamlFile = yamlFile
    }
    
    fun namespace(namespace: String) {
        this.namespace = namespace
    }
    
    internal fun build(): KubernetesAgent {
        return KubernetesAgent(
            yaml = yaml,
            yamlFile = yamlFile,
            namespace = namespace
        )
    }
}

/**
 * Builder para variables de entorno.
 */
@PipelineDslMarker
class EnvironmentBuilder {
    private val environment: MutableMap<String, String> = mutableMapOf()
    
    /**
     * Establece una variable de entorno usando syntax infix.
     */
    infix fun String.to(value: String) {
        environment[this] = value
    }
    
    /**
     * Establece múltiples variables de entorno.
     */
    fun putAll(env: Map<String, String>) {
        environment.putAll(env)
    }
    
    internal fun build(): Map<String, String> = environment.toMap()
}