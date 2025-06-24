package dev.rubentxu.hodei.pipelines.dsl.extensions

import dev.rubentxu.hodei.pipelines.dsl.PipelineDslMarker
import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepCategory
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import kotlinx.serialization.Serializable
import kotlinx.serialization.Contextual
import kotlin.reflect.KClass

/**
 * Interfaz para crear extensiones de steps de terceros.
 * Los developers pueden implementar esta interfaz para agregar nuevos steps.
 */
interface StepExtension {
    /**
     * Nombre único de la extensión.
     */
    val name: String
    
    /**
     * Versión de la extensión.
     */
    val version: String
    
    /**
     * Categoría del step.
     */
    val category: StepCategory
    
    /**
     * Descripción de la funcionalidad.
     */
    val description: String
    
    /**
     * Crea el ejecutor para este step.
     */
    fun createExecutor(): StepExecutor
    
    /**
     * Registra las funciones DSL en el StepsBuilder.
     */
    fun registerDslFunctions(builder: StepsBuilder)
    
    /**
     * Valida la configuración del step antes de la ejecución.
     */
    fun validate(step: Step): List<String> = emptyList()
    
    /**
     * Dependencias requeridas (librerías externas).
     */
    val dependencies: List<Dependency> get() = emptyList()
}

/**
 * Dependencia externa requerida por una extensión.
 */
@Serializable
data class Dependency(
    val groupId: String,
    val artifactId: String,
    val version: String,
    val scope: String = "runtime"
) {
    override fun toString(): String = "$groupId:$artifactId:$version"
}

/**
 * Step personalizado para extensiones de terceros.
 */
@Serializable
data class ExtensionStep(
    val extensionName: String,
    val action: String,
    val parameters: Map<String, @Contextual Any> = emptyMap(),
    val name: String? = null,
    val continueOnError: Boolean = false,
    val timeout: Int? = null
) {
    // ExtensionStep is not a Step subclass but contains similar properties
    val stepType: String get() = extensionName
    val id: String get() = name ?: "$extensionName-$action-${hashCode()}"
    val ignoreErrors: Boolean get() = continueOnError
}

/**
 * Clase base abstracta que simplifica la creación de extensiones.
 */
abstract class BaseStepExtension : StepExtension {
    
    /**
     * Registra una función DSL simple.
     */
    protected fun StepsBuilder.registerSimpleStep(
        stepName: String,
        action: String,
        parameterBuilder: ParameterBuilder.() -> Map<String, Any> = { emptyMap() }
    ) {
        // Agregar función al StepsBuilder usando extension
        addExtensionStep(
            ExtensionStep(
                extensionName = name,
                action = action,
                parameters = parameterBuilder.invoke(ParameterBuilder())
            )
        )
    }
    
    /**
     * Registra una función DSL con bloque de configuración.
     */
    protected fun StepsBuilder.registerConfigurableStep(
        stepName: String,
        action: String,
        configClass: KClass<*>,
        configBuilder: () -> Any
    ) {
        val config = configBuilder.invoke()
        addExtensionStep(
            ExtensionStep(
                extensionName = name,
                action = action,
                parameters = mapOf("config" to config)
            )
        )
    }
}

/**
 * Builder para parámetros de steps.
 */
class ParameterBuilder {
    private val params = mutableMapOf<String, Any>()
    
    infix fun String.to(value: Any) {
        params[this] = value
    }
    
    fun build(): Map<String, Any> = params.toMap()
}

/**
 * Extension function para agregar steps de extensión.
 */
private fun StepsBuilder.addExtensionStep(step: ExtensionStep) {
    // Usar reflexión para agregar el step a la lista interna
    val stepsField = this::class.java.getDeclaredField("steps")
    stepsField.isAccessible = true
    @Suppress("UNCHECKED_CAST")
    val steps = stepsField.get(this) as MutableList<Step>
    // Convert ExtensionStep to Step.Custom
    val customStep = Step.Custom(
        action = step.extensionName,
        parameters = step.parameters.mapValues { it.value.toString() },
        name = step.name,
        continueOnError = step.continueOnError,
        timeout = step.timeout
    )
    steps.add(customStep)
}