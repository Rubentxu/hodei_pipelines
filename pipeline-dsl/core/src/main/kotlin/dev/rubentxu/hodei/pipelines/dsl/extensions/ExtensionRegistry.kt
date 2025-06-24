package dev.rubentxu.hodei.pipelines.dsl.extensions

import dev.rubentxu.hodei.pipelines.dsl.builders.StepsBuilder
import dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepExecutorRegistry
import dev.rubentxu.hodei.pipelines.dsl.library.LibraryManager
import mu.KotlinLogging
import java.io.File
import java.net.URLClassLoader
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Registry para gestionar extensiones de steps de terceros.
 * Permite la carga dinámica y registro de nuevos steps.
 */
class ExtensionRegistry(
    private val stepExecutorRegistry: StepExecutorRegistry,
    private val libraryManager: LibraryManager
) {
    private val extensions = ConcurrentHashMap<String, StepExtension>()
    private val loadedJars = mutableSetOf<File>()
    
    /**
     * Registra una extensión manualmente.
     */
    fun registerExtension(extension: StepExtension) {
        logger.info { "Registering extension: ${extension.name} v${extension.version}" }
        
        // Validar dependencias
        validateDependencies(extension)
        
        // Registrar el ejecutor
        stepExecutorRegistry.register(
            stepType = extension.name,
            executor = extension.createExecutor(),
            category = extension.category
        )
        
        extensions[extension.name] = extension
        
        logger.info { "Extension ${extension.name} registered successfully" }
    }
    
    /**
     * Carga extensiones desde un JAR file.
     */
    fun loadExtensionsFromJar(jarFile: File) {
        if (!jarFile.exists() || !jarFile.isFile) {
            throw IllegalArgumentException("JAR file not found: ${jarFile.absolutePath}")
        }
        
        if (loadedJars.contains(jarFile)) {
            logger.info { "JAR already loaded: ${jarFile.name}" }
            return
        }
        
        try {
            logger.info { "Loading extensions from JAR: ${jarFile.name}" }
            
            // Cargar JAR en el classloader
            val classLoader = URLClassLoader(arrayOf(jarFile.toURI().toURL()))
            
            // Usar ServiceLoader para descubrir extensiones
            val serviceLoader = ServiceLoader.load(StepExtension::class.java, classLoader)
            
            var loadedCount = 0
            for (extension in serviceLoader) {
                registerExtension(extension)
                loadedCount++
            }
            
            loadedJars.add(jarFile)
            logger.info { "Loaded $loadedCount extensions from ${jarFile.name}" }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to load extensions from JAR: ${jarFile.name}" }
            throw e
        }
    }
    
    /**
     * Carga extensiones desde un directorio.
     */
    fun loadExtensionsFromDirectory(directory: File) {
        if (!directory.exists() || !directory.isDirectory) {
            logger.warn { "Extensions directory not found: ${directory.absolutePath}" }
            return
        }
        
        logger.info { "Scanning for extensions in: ${directory.absolutePath}" }
        
        directory.listFiles { _, name -> name.endsWith(".jar") }
            ?.forEach { jarFile ->
                try {
                    loadExtensionsFromJar(jarFile)
                } catch (e: Exception) {
                    logger.error(e) { "Failed to load extension from: ${jarFile.name}" }
                }
            }
    }
    
    /**
     * Carga extensiones automáticamente desde ubicaciones estándar.
     */
    fun autoLoadExtensions() {
        val standardLocations = listOf(
            File("extensions"),
            File("plugins"),
            File(System.getProperty("user.home"), ".hodei/extensions"),
            File("/opt/hodei/extensions")
        )
        
        standardLocations.forEach { location ->
            if (location.exists()) {
                loadExtensionsFromDirectory(location)
            }
        }
    }
    
    /**
     * Registra las funciones DSL de todas las extensiones en un StepsBuilder.
     */
    fun registerDslFunctions(builder: StepsBuilder) {
        extensions.values.forEach { extension ->
            try {
                extension.registerDslFunctions(builder)
            } catch (e: Exception) {
                logger.error(e) { "Failed to register DSL functions for extension: ${extension.name}" }
            }
        }
    }
    
    /**
     * Obtiene información de todas las extensiones cargadas.
     */
    fun getLoadedExtensions(): Map<String, ExtensionInfo> {
        return extensions.mapValues { (_, extension) ->
            ExtensionInfo(
                name = extension.name,
                version = extension.version,
                category = extension.category,
                description = extension.description,
                dependencies = extension.dependencies
            )
        }
    }
    
    /**
     * Obtiene una extensión por nombre.
     */
    fun getExtension(name: String): StepExtension? = extensions[name]
    
    /**
     * Valida que las dependencias de una extensión estén disponibles.
     */
    private fun validateDependencies(extension: StepExtension) {
        extension.dependencies.forEach { dependency ->
            // Aquí podrías implementar validación real de dependencias
            // Por ahora solo loggeamos
            logger.debug { "Extension ${extension.name} requires dependency: $dependency" }
        }
    }
    
    /**
     * Descarga e instala una extensión desde un repositorio.
     */
    suspend fun installExtension(
        groupId: String,
        artifactId: String,
        version: String,
        repository: String = "https://repo1.maven.org/maven2"
    ) {
        logger.info { "Installing extension: $groupId:$artifactId:$version" }
        
        try {
            // Descargar JAR usando LibraryManager
            libraryManager.downloadArtifact(groupId, artifactId, version, repository)
            
            // Cargar extensiones del JAR descargado
            val jarFile = File("lib", "$artifactId-$version.jar")
            if (jarFile.exists()) {
                loadExtensionsFromJar(jarFile)
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to install extension: $groupId:$artifactId:$version" }
            throw e
        }
    }
}

/**
 * Información de una extensión cargada.
 */
data class ExtensionInfo(
    val name: String,
    val version: String,
    val category: dev.rubentxu.hodei.pipelines.dsl.execution.steps.StepCategory,
    val description: String,
    val dependencies: List<Dependency>
)