package dev.rubentxu.hodei.pipelines.dsl.execution.steps

import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Registro de ejecutores de steps con categorización.
 * Aplica el principio Open/Closed permitiendo extensión sin modificación.
 */
class StepExecutorRegistry {
    private val executors = ConcurrentHashMap<String, StepExecutor>()
    private val categories = ConcurrentHashMap<String, StepCategory>()
    private val stepTypeMapping = ConcurrentHashMap<KClass<out Step>, String>()
    
    init {
        // Registrar mapeo de tipos
        registerStepTypeMapping()
    }
    
    /**
     * Registra un ejecutor con su categoría.
     */
    fun register(
        stepType: String,
        executor: StepExecutor,
        category: StepCategory = StepCategory.CUSTOM
    ) {
        executors[stepType] = executor
        categories[stepType] = category
    }
    
    /**
     * Obtiene un ejecutor por tipo de step.
     */
    fun getExecutor(stepType: String): StepExecutor? = executors[stepType]
    
    /**
     * Obtiene un ejecutor por clase de step.
     */
    fun getExecutor(step: Step): StepExecutor? {
        val stepType = stepTypeMapping[step::class] ?: step.stepType
        return executors[stepType]
    }
    
    /**
     * Obtiene la categoría de un step.
     */
    fun getCategory(stepType: String): StepCategory = 
        categories[stepType] ?: StepCategory.CUSTOM
    
    /**
     * Obtiene todos los steps de una categoría.
     */
    fun getStepsByCategory(category: StepCategory): List<String> =
        categories.filterValues { it == category }.keys.toList()
    
    /**
     * Verifica si un tipo de step está registrado.
     */
    fun isRegistered(stepType: String): Boolean = executors.containsKey(stepType)
    
    /**
     * Obtiene todos los tipos de steps registrados.
     */
    fun getAllStepTypes(): Set<String> = executors.keys.toSet()
    
    /**
     * Registra el mapeo de clases de step a tipos.
     */
    private fun registerStepTypeMapping() {
        stepTypeMapping[Step.Shell::class] = "sh"
        stepTypeMapping[Step.Batch::class] = "bat"
        stepTypeMapping[Step.Echo::class] = "echo"
        stepTypeMapping[Step.Script::class] = "script"
        // NOTE: Specialized step mappings moved to dedicated extensions:
        // - ArchiveArtifacts, PublishTestResults, Checkout -> jenkins-pipeline-steps, scm-steps
        // - Docker -> docker-steps extension
        // - Notification -> notification-steps extension
        // NOTE: Dir, WithEnv, Timeout, Retry, Parallel step mappings removed
        // These are now handled by pipeline-steps-library extension
        stepTypeMapping[Step.Custom::class] = "custom"
    }
}

/**
 * DSL para configurar el registro de ejecutores.
 */
class StepExecutorRegistryBuilder {
    private val registry = StepExecutorRegistry()
    
    /**
     * Registra ejecutores básicos.
     */
    fun basic(block: BasicExecutorsBuilder.() -> Unit) {
        val builder = BasicExecutorsBuilder(registry)
        builder.block()
    }
    
    /**
     * Registra ejecutores de control de flujo.
     */
    fun flowControl(block: FlowControlExecutorsBuilder.() -> Unit) {
        val builder = FlowControlExecutorsBuilder(registry)
        builder.block()
    }
    
    /**
     * Registra ejecutores de SCM.
     */
    fun scm(block: ScmExecutorsBuilder.() -> Unit) {
        val builder = ScmExecutorsBuilder(registry)
        builder.block()
    }
    
    /**
     * Registra ejecutores de build.
     */
    fun build(block: BuildExecutorsBuilder.() -> Unit) {
        val builder = BuildExecutorsBuilder(registry)
        builder.block()
    }
    
    /**
     * Registra ejecutores personalizados.
     */
    fun custom(stepType: String, executor: StepExecutor) {
        registry.register(stepType, executor, StepCategory.CUSTOM)
    }
    
    fun build(): StepExecutorRegistry = registry
}

/**
 * Builder para ejecutores básicos.
 */
class BasicExecutorsBuilder(private val registry: StepExecutorRegistry) {
    fun sh(executor: StepExecutor) {
        registry.register("sh", executor, StepCategory.BASIC)
    }
    
    fun bat(executor: StepExecutor) {
        registry.register("bat", executor, StepCategory.BASIC)
    }
    
    fun echo(executor: StepExecutor) {
        registry.register("echo", executor, StepCategory.BASIC)
    }
    
    fun script(executor: StepExecutor) {
        registry.register("script", executor, StepCategory.BASIC)
    }
}

/**
 * Builder para ejecutores de control de flujo.
 */
class FlowControlExecutorsBuilder(private val registry: StepExecutorRegistry) {
    fun dir(executor: StepExecutor) {
        registry.register("dir", executor, StepCategory.FLOW_CONTROL)
    }
    
    fun withEnv(executor: StepExecutor) {
        registry.register("withEnv", executor, StepCategory.FLOW_CONTROL)
    }
}

/**
 * Builder para ejecutores de SCM.
 */
class ScmExecutorsBuilder(private val registry: StepExecutorRegistry) {
    fun checkout(executor: StepExecutor) {
        registry.register("checkout", executor, StepCategory.SCM)
    }
    
    fun git(executor: StepExecutor) {
        registry.register("git", executor, StepCategory.SCM)
    }
}

/**
 * Builder para ejecutores de build.
 */
class BuildExecutorsBuilder(private val registry: StepExecutorRegistry) {
    fun docker(executor: StepExecutor) {
        registry.register("docker", executor, StepCategory.BUILD)
    }
    
    fun gradle(executor: StepExecutor) {
        registry.register("gradle", executor, StepCategory.BUILD)
    }
    
    fun maven(executor: StepExecutor) {
        registry.register("maven", executor, StepCategory.BUILD)
    }
}

/**
 * DSL para crear un registro de ejecutores.
 */
fun stepExecutorRegistry(block: StepExecutorRegistryBuilder.() -> Unit): StepExecutorRegistry {
    val builder = StepExecutorRegistryBuilder()
    builder.block()
    return builder.build()
}