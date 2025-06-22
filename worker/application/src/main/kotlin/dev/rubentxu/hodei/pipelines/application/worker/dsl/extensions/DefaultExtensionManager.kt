package dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ExtensionContext
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ExtensionLoadException
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ExtensionNotFoundException
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.ports.ExtensionManager
import dev.rubentxu.hodei.pipelines.domain.worker.ports.PipelineExtension
import mu.KotlinLogging
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}
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