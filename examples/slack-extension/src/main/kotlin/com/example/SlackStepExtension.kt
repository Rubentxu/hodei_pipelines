package com.example

import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineContext
import dev.rubentxu.hodei.pipelines.dsl.execution.StepExecutor
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepCategory
import dev.rubentxu.hodei.pipelines.dsl.extensions.*
import dev.rubentxu.hodei.pipelines.dsl.model.Step
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

/**
 * EJEMPLO: Extensión para enviar mensajes a Slack.
 * 
 * Así es como un developer crearía una extensión de terceros.
 */
class SlackStepExtension : BaseStepExtension() {
    
    override val name: String = "slack"
    override val version: String = "1.0.0"
    override val category: StepCategory = StepCategory.NOTIFICATION
    override val description: String = "Send messages to Slack channels"
    
    override val dependencies: List<Dependency> = listOf(
        Dependency("com.slack.api", "slack-api-client", "1.29.2"),
        Dependency("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.7.3")
    )
    
    override fun createExecutor(): StepExecutor = SlackStepExecutor()
    
    override fun registerDslFunctions(builder: StepsBuilder) {
        // Registro usando reflection y extension functions
        builder.apply {
            // Función simple para enviar mensaje
            registerSlackStep()
        }
    }
    
    override fun validate(step: Step): List<String> {
        if (step !is ExtensionStep || step.extensionName != name) {
            return listOf("Invalid step type for Slack extension")
        }
        
        val errors = mutableListOf<String>()
        val params = step.parameters
        
        if (params["message"] == null || params["message"].toString().isBlank()) {
            errors.add("Message is required")
        }
        
        if (params["channel"] == null || params["channel"].toString().isBlank()) {
            errors.add("Channel is required")
        }
        
        return errors
    }
}

/**
 * Configuración para el step de Slack.
 */
@Serializable
data class SlackConfig(
    val message: String,
    val channel: String,
    val token: String? = null,
    val username: String? = null,
    val iconEmoji: String? = null,
    val color: String? = null,
    val attachments: List<SlackAttachment> = emptyList()
)

@Serializable
data class SlackAttachment(
    val title: String? = null,
    val text: String? = null,
    val color: String? = null,
    val fields: List<SlackField> = emptyList()
)

@Serializable
data class SlackField(
    val title: String,
    val value: String,
    val short: Boolean = false
)

/**
 * Ejecutor del step de Slack.
 */
class SlackStepExecutor : StepExecutor {
    override suspend fun execute(step: Step, context: PipelineContext) {
        require(step is ExtensionStep && step.extensionName == "slack") {
            "Expected Slack extension step"
        }
        
        context.println("📱 Sending Slack message...")
        
        try {
            val config = parseSlackConfig(step.parameters)
            
            // Simular envío a Slack (aquí usarías la API real de Slack)
            sendSlackMessage(config, context)
            
            context.println("✅ Slack message sent successfully to ${config.channel}")
            
        } catch (e: Exception) {
            context.printError("❌ Failed to send Slack message: ${e.message}")
            if (!step.continueOnError) {
                throw e
            }
        }
    }
    
    private fun parseSlackConfig(parameters: Map<String, Any>): SlackConfig {
        return SlackConfig(
            message = parameters["message"]?.toString() ?: "",
            channel = parameters["channel"]?.toString() ?: "",
            token = parameters["token"]?.toString(),
            username = parameters["username"]?.toString(),
            iconEmoji = parameters["iconEmoji"]?.toString(),
            color = parameters["color"]?.toString()
        )
    }
    
    private suspend fun sendSlackMessage(config: SlackConfig, context: PipelineContext) {
        // Aquí implementarías la llamada real a la API de Slack
        // Por ahora simulamos con delay
        
        context.println("Channel: ${config.channel}")
        context.println("Message: ${config.message}")
        config.username?.let { context.println("Username: $it") }
        config.iconEmoji?.let { context.println("Icon: $it") }
        
        // Simular llamada HTTP
        delay(1000)
        
        // Ejemplo de cómo usarías la librería real de Slack:
        /*
        val slack = Slack.getInstance()
        val token = config.token ?: context.getEnv("SLACK_TOKEN")
        
        val methods = slack.methods(token)
        val response = methods.chatPostMessage { req ->
            req.channel(config.channel)
                .text(config.message)
                .username(config.username)
                .iconEmoji(config.iconEmoji)
        }
        
        if (!response.isOk) {
            throw RuntimeException("Slack API error: ${response.error}")
        }
        */
    }
}

/**
 * Extension functions para el DSL.
 */
private fun StepsBuilder.registerSlackStep() {
    // Esto se haría mediante bytecode manipulation o reflection
    // Por simplicidad, aquí muestro la API que el usuario vería:
}

/**
 * DSL functions que el usuario tendría disponible:
 * 
 * pipeline("example") {
 *     stages {
 *         stage("notify") {
 *             steps {
 *                 // Función simple
 *                 slack("Hello from pipeline!", "#general")
 *                 
 *                 // Función con configuración avanzada
 *                 slack {
 *                     message = "Build completed!"
 *                     channel = "#ci-cd"
 *                     username = "Jenkins Bot"
 *                     iconEmoji = ":robot_face:"
 *                     color = "good"
 *                     
 *                     attachment {
 *                         title = "Build Details"
 *                         field("Status", "Success")
 *                         field("Branch", env.BRANCH_NAME)
 *                         field("Commit", env.GIT_COMMIT)
 *                     }
 *                 }
 *             }
 *         }
 *     }
 * }
 */