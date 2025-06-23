package dev.rubentxu.hodei.pipelines.dsl.model

import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.PipelineContext
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.port.JobOutputChunk
import kotlinx.coroutines.channels.Channel
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Modelo central del Pipeline que integra con el sistema de workers existente.
 * 
 * Esta clase une el DSL tipado con la funcionalidad avanzada de ejecución
 * del sistema de workers, manteniendo la compatibilidad con eventos y output streams.
 */
@Serializable
data class Pipeline(
    val name: String,
    val description: String? = null,
    val agent: Agent? = null,
    val environment: Map<String, String> = emptyMap(),
    val stages: List<Stage> = emptyList(),
    val post: PostActions? = null,
    val triggers: List<Trigger> = emptyList(),
    val parameters: Map<String, Parameter> = emptyMap(),
    val options: PipelineOptions = PipelineOptions()
) {
    
    /**
     * Crea un contexto de ejecución integrado con el sistema de workers existente.
     */
    fun createExecutionContext(
        jobId: JobId,
        workerId: WorkerId,
        outputChannel: Channel<JobOutputChunk>,
        eventChannel: Channel<JobExecutionEvent>,
        runtimeEnvironment: Map<String, String> = emptyMap()
    ): PipelineContext {
        val mergedEnvironment = environment + runtimeEnvironment
        
        return PipelineContext(
            jobId = jobId,
            workerId = workerId,
            environment = mergedEnvironment,
            outputChannel = outputChannel,
            eventChannel = eventChannel
        )
    }
    
    /**
     * Valida dependencias de artifacts entre stages.
     */
    fun validateArtifactDependencies(): List<String> {
        val producedArtifacts = getAllProducedArtifacts()
        val requiredArtifacts = getAllRequiredArtifacts()
        
        return (requiredArtifacts - producedArtifacts).toList()
    }
    
    /**
     * Obtiene todos los artifacts producidos por el pipeline.
     */
    fun getAllProducedArtifacts(): Set<String> {
        return stages.flatMap { it.produces }.toSet()
    }
    
    /**
     * Obtiene todos los artifacts requeridos por el pipeline.
     */
    fun getAllRequiredArtifacts(): Set<String> {
        return stages.flatMap { it.requires }.toSet()
    }
    
    /**
     * Obtiene el número total de steps en todo el pipeline.
     */
    fun getTotalStepCount(): Int {
        return stages.sumOf { stage ->
            stage.steps.size + (stage.parallel?.stages?.sumOf { it.steps.size } ?: 0)
        }
    }
}

/**
 * Configuración del agente de ejecución.
 */
@Serializable
data class Agent(
    val label: String? = null,
    val docker: DockerAgent? = null,
    val kubernetes: KubernetesAgent? = null
)

@Serializable
data class DockerAgent(
    val image: String,
    val args: List<String> = emptyList(),
    val registryUrl: String? = null,
    val registryCredentialsId: String? = null
)

@Serializable
data class KubernetesAgent(
    val yaml: String? = null,
    val yamlFile: String? = null,
    val namespace: String? = null
)

/**
 * Opciones del pipeline.
 */
@Serializable
data class PipelineOptions(
    val buildDiscarder: BuildDiscarder? = null,
    val timeout: TimeoutOption? = null,
    val retry: RetryOption? = null,
    val skipDefaultCheckout: Boolean = false,
    val parallelsAlwaysFailFast: Boolean = false
)

@Serializable
data class BuildDiscarder(
    val numToKeep: Int? = null,
    val daysToKeep: Int? = null,
    val artifactNumToKeep: Int? = null,
    val artifactDaysToKeep: Int? = null
)

@Serializable
data class TimeoutOption(
    val time: Int,
    val unit: TimeUnit = TimeUnit.MINUTES
)

@Serializable
data class RetryOption(
    val count: Int,
    val conditions: List<RetryCondition> = emptyList()
)

@Serializable
enum class TimeUnit {
    SECONDS, MINUTES, HOURS, DAYS
}

@Serializable
enum class RetryCondition {
    ALWAYS, FAILURE, UNSTABLE, CHANGED
}

/**
 * Parámetros del pipeline.
 */
@Serializable
data class Parameter(
    val name: String,
    val type: ParameterType,
    val defaultValue: String? = null,
    val description: String? = null,
    val choices: List<String> = emptyList()
)

@Serializable
enum class ParameterType {
    STRING, TEXT, BOOLEAN, CHOICE, PASSWORD, FILE
}

/**
 * Triggers del pipeline.
 */
@Serializable
sealed class Trigger {
    @Serializable
    data class Cron(val expression: String) : Trigger()
    
    @Serializable
    data class SCM(val pollSCM: String) : Trigger()
    
    @Serializable
    data class Upstream(
        val projects: List<String>, 
        val threshold: String = "SUCCESS"
    ) : Trigger()
}

/**
 * Acciones post-ejecución.
 */
@Serializable
data class PostActions(
    val always: List<Step> = emptyList(),
    val success: List<Step> = emptyList(),
    val failure: List<Step> = emptyList(),
    val unstable: List<Step> = emptyList(),
    val aborted: List<Step> = emptyList(),
    val changed: List<Step> = emptyList(),
    val fixed: List<Step> = emptyList(),
    val regression: List<Step> = emptyList()
)