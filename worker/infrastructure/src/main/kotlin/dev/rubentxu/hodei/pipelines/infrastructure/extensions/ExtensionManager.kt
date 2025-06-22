package dev.rubentxu.hodei.pipelines.infrastructure.extensions

import dev.rubentxu.hodei.pipelines.infrastructure.dsl.AdvancedPipelineContext
import mu.KotlinLogging
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Extension management system for pipeline DSL
 */
interface ExtensionManager {
    fun loadExtension(extensionPath: File): PipelineExtension
    fun registerExtension(extension: PipelineExtension)
    fun getExtension(identifier: String): PipelineExtension?
    fun getAllExtensions(): Map<String, PipelineExtension>
    fun getAvailableSteps(): Map<String, StepDefinition>
    fun getGlobalVariables(): Map<String, Any>
    fun unloadExtension(identifier: String)
}

/**
 * Default implementation of ExtensionManager
 */
class DefaultExtensionManager : ExtensionManager {
    private val loadedExtensions = ConcurrentHashMap<String, PipelineExtension>()
    private val extensionClassLoaders = ConcurrentHashMap<String, URLClassLoader>()
    
    override fun loadExtension(extensionPath: File): PipelineExtension {
        logger.info { "Loading extension from: ${extensionPath.absolutePath}" }
        
        if (!extensionPath.exists()) {
            throw ExtensionNotFoundException("Extension file not found: ${extensionPath.absolutePath}")
        }
        
        val classLoader = URLClassLoader(arrayOf(extensionPath.toURI().toURL()), this::class.java.classLoader)
        
        // Use ServiceLoader to find extension implementations
        val serviceLoader = ServiceLoader.load(PipelineExtension::class.java, classLoader)
        val extension = serviceLoader.firstOrNull() 
            ?: throw ExtensionLoadException("No PipelineExtension implementation found in: ${extensionPath.absolutePath}")
        
        // Initialize the extension
        val context = ExtensionContext()
        extension.initialize(context)
        
        // Store the extension and its classloader
        extensionClassLoaders[extension.identifier] = classLoader
        loadedExtensions[extension.identifier] = extension
        
        logger.info { "Successfully loaded extension: ${extension.identifier} v${extension.version}" }
        return extension
    }
    
    override fun registerExtension(extension: PipelineExtension) {
        logger.info { "Registering extension: ${extension.identifier}" }
        
        val context = ExtensionContext()
        extension.initialize(context)
        loadedExtensions[extension.identifier] = extension
    }
    
    override fun getExtension(identifier: String): PipelineExtension? {
        return loadedExtensions[identifier]
    }
    
    override fun getAllExtensions(): Map<String, PipelineExtension> {
        return loadedExtensions.toMap()
    }
    
    override fun getAvailableSteps(): Map<String, StepDefinition> {
        return loadedExtensions.values.flatMap { extension ->
            extension.getSteps().map { (stepName, stepDef) ->
                "${extension.identifier}.$stepName" to stepDef
            }
        }.toMap()
    }
    
    override fun getGlobalVariables(): Map<String, Any> {
        return loadedExtensions.values.flatMap { extension ->
            extension.getGlobalVariables().map { (varName, value) ->
                "${extension.identifier}.$varName" to value
            }
        }.toMap()
    }
    
    override fun unloadExtension(identifier: String) {
        logger.info { "Unloading extension: $identifier" }
        loadedExtensions.remove(identifier)
        extensionClassLoaders.remove(identifier)?.close()
    }
    
    /**
     * Auto-discover and load extensions from directories
     */
    fun autoLoadExtensions(directories: List<String>) {
        directories.forEach { directory ->
            val dir = File(directory)
            if (dir.exists() && dir.isDirectory) {
                logger.info { "Auto-loading extensions from: ${dir.absolutePath}" }
                
                dir.listFiles { file ->
                    file.isFile && file.extension.lowercase() == "jar"
                }?.forEach { jarFile ->
                    try {
                        loadExtension(jarFile)
                    } catch (e: Exception) {
                        logger.warn(e) { "Failed to load extension from: ${jarFile.absolutePath}" }
                    }
                }
            }
        }
    }
}

/**
 * Pipeline extension interface
 */
interface PipelineExtension {
    val identifier: String
    val version: String
    val description: String
    val author: String
    val minimumPipelineVersion: String?
    
    fun initialize(context: ExtensionContext)
    fun getSteps(): Map<String, StepDefinition>
    fun getGlobalVariables(): Map<String, Any>
    fun cleanup()
}

/**
 * Base class for pipeline extensions
 */
abstract class ExtensionBase : PipelineExtension {
    protected lateinit var context: ExtensionContext
    
    override fun initialize(context: ExtensionContext) {
        this.context = context
        onInitialize()
    }
    
    protected open fun onInitialize() {
        // Override in subclasses
    }
    
    override fun getGlobalVariables(): Map<String, Any> {
        return emptyMap()
    }
    
    override fun cleanup() {
        // Override in subclasses if needed
    }
}

/**
 * Step definition for pipeline steps
 */
data class StepDefinition(
    val name: String,
    val description: String = "",
    val parameters: Map<String, ParameterDefinition> = emptyMap(),
    val requiredPermissions: Set<String> = emptySet(),
    val executor: suspend (Map<String, Any>, StepExecutionContext) -> StepResult
)

/**
 * Parameter definition for step parameters
 */
data class ParameterDefinition(
    val name: String,
    val type: ParameterType,
    val required: Boolean = false,
    val defaultValue: Any? = null,
    val description: String = "",
    val validation: ((Any) -> Boolean)? = null
)

/**
 * Parameter types
 */
enum class ParameterType {
    STRING,
    TEXT,
    BOOLEAN,
    INTEGER,
    DECIMAL,
    CHOICE,
    PASSWORD,
    FILE,
    DIRECTORY,
    URL,
    JSON,
    YAML
}

/**
 * Step execution context
 */
data class StepExecutionContext(
    val pipeline: AdvancedPipelineContext,
    val stepName: String,
    val parameters: Map<String, Any>,
    val environment: Map<String, String>
)

/**
 * Step execution result
 */
sealed class StepResult {
    data class Success(val message: String = "", val output: Map<String, Any> = emptyMap()) : StepResult()
    data class Failure(val error: String, val exitCode: Int = 1) : StepResult()
    data class Unstable(val warnings: List<String>) : StepResult()
}

/**
 * Extension context for initialization
 */
class ExtensionContext {
    private val properties = mutableMapOf<String, Any>()
    
    fun setProperty(key: String, value: Any) {
        properties[key] = value
    }
    
    fun getProperty(key: String): Any? {
        return properties[key]
    }
    
    fun <T> getProperty(key: String, type: Class<T>): T? {
        return properties[key]?.let { value ->
            if (type.isInstance(value)) {
                @Suppress("UNCHECKED_CAST")
                value as T
            } else {
                null
            }
        }
    }
}

// Built-in Extensions

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
                "url" to ParameterDefinition("url", ParameterType.URL, required = true, description = "Git repository URL"),
                "branch" to ParameterDefinition("branch", ParameterType.STRING, defaultValue = "main", description = "Branch to checkout"),
                "credentials" to ParameterDefinition("credentials", ParameterType.STRING, description = "Credentials ID"),
                "depth" to ParameterDefinition("depth", ParameterType.INTEGER, defaultValue = 0, description = "Clone depth")
            ),
            executor = { params, context -> executeCheckout(params, context) }
        ),
        
        "tag" to StepDefinition(
            name = "tag",
            description = "Create a Git tag",
            parameters = mapOf(
                "name" to ParameterDefinition("name", ParameterType.STRING, required = true, description = "Tag name"),
                "message" to ParameterDefinition("message", ParameterType.STRING, description = "Tag message"),
                "push" to ParameterDefinition("push", ParameterType.BOOLEAN, defaultValue = false, description = "Push tag to remote")
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
                "dockerfile" to ParameterDefinition("dockerfile", ParameterType.FILE, defaultValue = "Dockerfile", description = "Dockerfile path"),
                "context" to ParameterDefinition("context", ParameterType.DIRECTORY, defaultValue = ".", description = "Build context"),
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
                "channel" to ParameterDefinition("channel", ParameterType.STRING, required = true, description = "Slack channel"),
                "message" to ParameterDefinition("message", ParameterType.TEXT, required = true, description = "Message to send"),
                "webhook" to ParameterDefinition("webhook", ParameterType.URL, description = "Slack webhook URL"),
                "color" to ParameterDefinition("color", ParameterType.CHOICE, defaultValue = "good", description = "Message color")
            ),
            executor = { params, context -> executeSlack(params, context) }
        ),
        
        "email" to StepDefinition(
            name = "email",
            description = "Send email notification",
            parameters = mapOf(
                "to" to ParameterDefinition("to", ParameterType.STRING, required = true, description = "Recipients"),
                "subject" to ParameterDefinition("subject", ParameterType.STRING, required = true, description = "Email subject"),
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

/**
 * Extension exceptions
 */
class ExtensionNotFoundException(message: String) : Exception(message)
class ExtensionLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ExtensionInitializationException(message: String, cause: Throwable? = null) : Exception(message, cause)