package dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterType
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepExecutionContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepResult

/**
 * Notification Extension
 */
class NotificationExtension : ExtensionBase() {
    override val identifier = "notification"
    override val version = "1.0.0"
    override val description = "Notification services"
    override val author = "Hodei Pipelines"
    override val minimumPipelineVersion = "1.0.0"

    override fun getSteps(): Map<String, StepDefinition> = mapOf(
        "slack" to StepDefinition(
            name = "slack",
            description = "Send Slack notification",
            parameters = mapOf(
                "channel" to ParameterDefinition(
                    "channel",
                    ParameterType.STRING,
                    required = true,
                    description = "Slack channel"
                ),
                "message" to ParameterDefinition(
                    "message",
                    ParameterType.TEXT,
                    required = true,
                    description = "Message to send"
                ),
                "webhook" to ParameterDefinition("webhook", ParameterType.URL, description = "Slack webhook URL"),
                "color" to ParameterDefinition(
                    "color",
                    ParameterType.CHOICE,
                    defaultValue = "good",
                    description = "Message color"
                )
            ),
            executor = { params, context -> executeSlack(params, context) }
        ),

        "email" to StepDefinition(
            name = "email",
            description = "Send email notification",
            parameters = mapOf(
                "to" to ParameterDefinition("to", ParameterType.STRING, required = true, description = "Recipients"),
                "subject" to ParameterDefinition(
                    "subject",
                    ParameterType.STRING,
                    required = true,
                    description = "Email subject"
                ),
                "body" to ParameterDefinition("body", ParameterType.TEXT, required = true, description = "Email body")
            ),
            executor = { params, context -> executeEmail(params, context) }
        )
    )

    private suspend fun executeSlack(params: Map<String, Any>, context: StepExecutionContext): StepResult {
        return try {
            val channel = params["channel"] as String
            val message = params["message"] as String

            // Implement Slack notification logic here
            context.pipeline.println("📢 Slack notification sent to $channel: $message")
            StepResult.Success("Slack notification sent")
        } catch (e: Exception) {
            StepResult.Failure("Slack notification failed: ${e.message}")
        }
    }

    private suspend fun executeEmail(params: Map<String, Any>, context: StepExecutionContext): StepResult {
        return try {
            val to = params["to"] as String
            val subject = params["subject"] as String
            val body = params["body"] as String

            // Implement email notification logic here
            context.pipeline.println("📧 Email sent to $to: $subject")
            StepResult.Success("Email notification sent")
        } catch (e: Exception) {
            StepResult.Failure("Email notification failed: ${e.message}")
        }
    }
}