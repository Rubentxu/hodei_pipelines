package dev.rubentxu.hodei.pipelines.dsl.model

import dev.rubentxu.hodei.pipelines.port.StageType
import kotlinx.serialization.Serializable

/**
 * Definición de un Stage en el Pipeline DSL.
 * 
 * Los stages representan fases lógicas del pipeline y pueden ejecutarse
 * secuencialmente o en paralelo, con dependencies de artifacts.
 */
@Serializable
data class Stage(
    val name: String,
    val description: String? = null,
    val steps: List<Step> = emptyList(),
    val parallel: ParallelStages? = null,
    val agent: Agent? = null,
    val environment: Map<String, String> = emptyMap(),
    val whenCondition: WhenCondition? = null,
    val input: InputDirective? = null,
    val options: StageOptions? = null,
    val post: PostActions? = null,
    val tools: List<Tool> = emptyList(),
    val requires: List<String> = emptyList(), // Artifact dependencies
    val produces: List<String> = emptyList(), // Artifacts produced
    val type: StageType = StageType.CUSTOM
) {
    
    /**
     * Verifica si este stage puede ejecutarse dado el contexto actual.
     */
    fun canExecute(context: Map<String, Any>): Boolean {
        return whenCondition?.evaluate(context) ?: true
    }
    
    /**
     * Obtiene el número total de steps incluyendo parallel stages.
     */
    fun getTotalStepCount(): Int {
        return steps.size + (parallel?.stages?.sumOf { it.steps.size } ?: 0)
    }
}

/**
 * Stages paralelos dentro de un stage.
 */
@Serializable
data class ParallelStages(
    val stages: List<Stage>,
    val failFast: Boolean = true
)

/**
 * Condiciones para ejecución condicional de stages.
 */
@Serializable
sealed class WhenCondition {
    abstract fun evaluate(context: Map<String, Any>): Boolean
    
    @Serializable
    data class Branch(val pattern: String) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            val currentBranch = context["BRANCH_NAME"] as? String ?: return false
            return matchesPattern(currentBranch, pattern)
        }
    }
    
    @Serializable
    data class Tag(val pattern: String) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            val currentTag = context["TAG_NAME"] as? String ?: return false
            return matchesPattern(currentTag, pattern)
        }
    }
    
    @Serializable
    data class ChangeRequest(val target: String? = null) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            val isChangeRequest = context["CHANGE_ID"] != null
            return if (target != null) {
                isChangeRequest && context["CHANGE_TARGET"] == target
            } else {
                isChangeRequest
            }
        }
    }
    
    @Serializable
    data class Environment(val name: String, val value: String) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            return context[name] == value
        }
    }
    
    @Serializable
    data class Expression(val expression: String) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            // Simple expression evaluation - in production would use a proper expression engine
            return when {
                expression.startsWith("env.") -> {
                    val varName = expression.substring(4)
                    context[varName] != null
                }
                expression.startsWith("params.") -> {
                    val paramName = expression.substring(7)
                    context[paramName] != null
                }
                expression == "true" -> true
                expression == "false" -> false
                else -> false
            }
        }
    }
    
    @Serializable
    data class AllOf(val conditions: List<WhenCondition>) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            return conditions.all { it.evaluate(context) }
        }
    }
    
    @Serializable
    data class AnyOf(val conditions: List<WhenCondition>) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            return conditions.any { it.evaluate(context) }
        }
    }
    
    @Serializable
    data class Not(val condition: WhenCondition) : WhenCondition() {
        override fun evaluate(context: Map<String, Any>): Boolean {
            return !condition.evaluate(context)
        }
    }
    
    protected fun matchesPattern(value: String, pattern: String): Boolean {
        // Simple glob pattern matching
        val regex = pattern
            .replace(".", "\\\\.")
            .replace("*", ".*")
            .replace("?", ".")
        return value.matches(Regex(regex))
    }
}

/**
 * Directiva de input para stages interactivos.
 */
@Serializable
data class InputDirective(
    val message: String,
    val id: String? = null,
    val ok: String = "Proceed",
    val submitter: String? = null,
    val submitterParameter: String? = null,
    val parameters: List<Parameter> = emptyList()
)

/**
 * Opciones específicas del stage.
 */
@Serializable
data class StageOptions(
    val skipDefaultCheckout: Boolean = false,
    val timeout: TimeoutOption? = null,
    val retry: RetryOption? = null,
    val timestamps: Boolean = false
)

/**
 * Herramientas disponibles en el stage.
 */
@Serializable
sealed class Tool {
    @Serializable
    data class Maven(val version: String = "3.8.6") : Tool()
    
    @Serializable
    data class Gradle(val version: String = "7.6") : Tool()
    
    @Serializable
    data class Node(val version: String = "18") : Tool()
    
    @Serializable
    data class JDK(val version: String = "17") : Tool()
    
    @Serializable
    data class Docker(val version: String = "latest") : Tool()
    
    @Serializable
    data class Custom(val name: String, val version: String) : Tool()
}