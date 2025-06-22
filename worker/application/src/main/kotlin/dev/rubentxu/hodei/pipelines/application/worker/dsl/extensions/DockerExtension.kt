package dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterType
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepExecutionContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepResult

/**
 * Docker Extension
 */
class DockerExtension : ExtensionBase() {
    override val identifier = "docker"
    override val version = "1.0.0"
    override val description = "Docker container operations"
    override val author = "Hodei Pipelines"
    override val minimumPipelineVersion = "1.0.0"

    override fun getSteps(): Map<String, StepDefinition> = mapOf(
        "build" to StepDefinition(
            name = "build",
            description = "Build Docker image",
            parameters = mapOf(
                "tag" to ParameterDefinition("tag", ParameterType.STRING, required = true, description = "Image tag"),
                "dockerfile" to ParameterDefinition(
                    "dockerfile",
                    ParameterType.FILE,
                    defaultValue = "Dockerfile",
                    description = "Dockerfile path"
                ),
                "context" to ParameterDefinition(
                    "context",
                    ParameterType.DIRECTORY,
                    defaultValue = ".",
                    description = "Build context"
                ),
                "args" to ParameterDefinition("args", ParameterType.JSON, description = "Build arguments")
            ),
            executor = { params, context -> executeBuild(params, context) }
        ),

        "push" to StepDefinition(
            name = "push",
            description = "Push Docker image",
            parameters = mapOf(
                "tag" to ParameterDefinition("tag", ParameterType.STRING, required = true, description = "Image tag"),
                "registry" to ParameterDefinition("registry", ParameterType.STRING, description = "Registry URL")
            ),
            executor = { params, context -> executePush(params, context) }
        )
    )

    private suspend fun executeBuild(params: Map<String, Any>, context: StepExecutionContext): StepResult {
        return try {
            val tag = params["tag"] as String
            val dockerfile = params["dockerfile"] as? String ?: "Dockerfile"
            val buildContext = params["context"] as? String ?: "."

            val command = "docker build -t $tag -f $dockerfile $buildContext"
            context.pipeline.sh(command)

            StepResult.Success("Docker image built: $tag")
        } catch (e: Exception) {
            StepResult.Failure("Docker build failed: ${e.message}")
        }
    }

    private suspend fun executePush(params: Map<String, Any>, context: StepExecutionContext): StepResult {
        return try {
            val tag = params["tag"] as String

            context.pipeline.sh("docker push $tag")
            StepResult.Success("Docker image pushed: $tag")
        } catch (e: Exception) {
            StepResult.Failure("Docker push failed: ${e.message}")
        }
    }
}