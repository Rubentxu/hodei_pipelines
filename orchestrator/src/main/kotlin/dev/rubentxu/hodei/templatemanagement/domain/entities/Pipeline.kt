package dev.rubentxu.hodei.domain.template

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class Pipeline(
    val stages: List<Stage>,
    val environment: Map<String, String> = emptyMap(),
    val resources: ResourceRequirements? = null
)

@Serializable
data class Stage(
    val name: String,
    val steps: List<Step>,
    val dependsOn: List<String> = emptyList(),
    val condition: String? = null,
    val retry: RetryConfig? = null
)

@Serializable
data class Step(
    val name: String,
    val type: String, // script, container, plugin
    val config: JsonObject,
    val timeout: Duration? = 300.seconds,
    val retry: RetryConfig? = null
)

@Serializable
data class RetryConfig(
    val attempts: Int,
    val backoff: Duration = 30.seconds
)

@Serializable
data class ResourceRequirements(
    val cpu: String? = null,    // e.g., "500m" or "2"
    val memory: String? = null, // e.g., "512Mi" or "1Gi"
    val storage: String? = null, // e.g., "1Gi"
    val gpu: Int? = null
)