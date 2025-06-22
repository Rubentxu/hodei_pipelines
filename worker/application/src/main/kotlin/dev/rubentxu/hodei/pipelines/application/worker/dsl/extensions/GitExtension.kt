package dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterType
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepExecutionContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepResult

/**
 * Git Extension
 */
class GitExtension : ExtensionBase() {
    override val identifier = "git"
    override val version = "1.0.0"
    override val description = "Git SCM operations"
    override val author = "Hodei Pipelines"
    override val minimumPipelineVersion = "1.0.0"

    override fun getSteps(): Map<String, StepDefinition> = mapOf(
        "checkout" to StepDefinition(
            name = "checkout",
            description = "Checkout source code from Git repository",
            parameters = mapOf(
                "url" to ParameterDefinition(
                    "url",
                    ParameterType.URL,
                    required = true,
                    description = "Git repository URL"
                ),
                "branch" to ParameterDefinition(
                    "branch",
                    ParameterType.STRING,
                    defaultValue = "main",
                    description = "Branch to checkout"
                ),
                "credentials" to ParameterDefinition(
                    "credentials",
                    ParameterType.STRING,
                    description = "Credentials ID"
                ),
                "depth" to ParameterDefinition(
                    "depth",
                    ParameterType.INTEGER,
                    defaultValue = 0,
                    description = "Clone depth"
                )
            ),
            executor = { params, context -> executeCheckout(params, context) }
        ),

        "tag" to StepDefinition(
            name = "tag",
            description = "Create a Git tag",
            parameters = mapOf(
                "name" to ParameterDefinition("name", ParameterType.STRING, required = true, description = "Tag name"),
                "message" to ParameterDefinition("message", ParameterType.STRING, description = "Tag message"),
                "push" to ParameterDefinition(
                    "push",
                    ParameterType.BOOLEAN,
                    defaultValue = false,
                    description = "Push tag to remote"
                )
            ),
            executor = { params, context -> executeTag(params, context) }
        )
    )

    private suspend fun executeCheckout(params: Map<String, Any>, context: StepExecutionContext): StepResult {
        return try {
            val url = params["url"] as String
            val branch = params["branch"] as? String ?: "main"
            val depth = params["depth"] as? Int ?: 0

            val command = buildString {
                append("git clone")
                if (depth > 0) append(" --depth $depth")
                append(" -b $branch")
                append(" $url .")
            }

            context.pipeline.sh(command)
            StepResult.Success("Repository checked out successfully")
        } catch (e: Exception) {
            StepResult.Failure("Checkout failed: ${e.message}")
        }
    }

    private suspend fun executeTag(params: Map<String, Any>, context: StepExecutionContext): StepResult {
        return try {
            val tagName = params["name"] as String
            val message = params["message"] as? String
            val push = params["push"] as? Boolean ?: false

            val command = if (message != null) {
                "git tag -a $tagName -m \"$message\""
            } else {
                "git tag $tagName"
            }

            context.pipeline.sh(command)

            if (push) {
                context.pipeline.sh("git push origin $tagName")
            }

            StepResult.Success("Tag '$tagName' created successfully")
        } catch (e: Exception) {
            StepResult.Failure("Tag creation failed: ${e.message}")
        }
    }
}