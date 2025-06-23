package dev.rubentxu.hodei.pipelines.dsl.library

import mu.KotlinLogging
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

/**
 * Gestor de librerías standalone para Pipeline DSL.
 * 
 * Maneja la carga dinámica de librerías desde artefactos locales 
 * enviados a través del protocolo gRPC del worker.
 */
interface LibraryManager {
    suspend fun loadLibraryFromArtifact(artifactPath: File): LibraryReference?
    suspend fun loadLibrariesFromArtifacts(artifacts: List<File>): Map<String, LibraryReference>
    fun getLoadedLibrary(identifier: String): LibraryReference?
    fun unloadLibrary(identifier: String)
    fun getAllLoadedLibraries(): List<LibraryReference>
    fun addArtifactLibrary(identifier: String, jarFile: File)
}

/**
 * Implementación del gestor de librerías para Pipeline DSL.
 * Maneja artefactos locales enviados por el worker.
 */
class PipelineLibraryManager : LibraryManager {
    
    private val loadedLibraries = ConcurrentHashMap<String, LibraryReference>()
    private val artifactLibraries = ConcurrentHashMap<String, File>()
    
    override suspend fun loadLibraryFromArtifact(artifactPath: File): LibraryReference? {
        if (!artifactPath.exists() || !artifactPath.name.endsWith(".jar")) {
            logger.warn { "Invalid library artifact: ${artifactPath.absolutePath}" }
            return null
        }
        
        val identifier = artifactPath.nameWithoutExtension
        
        // Verificar si ya está cargada
        loadedLibraries[identifier]?.let { return it }
        
        return try {
            logger.info { "Loading library from artifact: ${artifactPath.absolutePath}" }
            
            // Crear definición básica desde el artefacto
            val definition = LibraryDefinition(
                identifier = identifier,
                version = "artifact",
                jarFiles = listOf(artifactPath),
                dependencies = emptyList(),
                metadata = LibraryMetadata(
                    name = identifier,
                    description = "Library loaded from artifact: ${artifactPath.name}",
                    author = "Unknown"
                )
            )
            
            // Crear URLClassLoader con el JAR file
            val urls = arrayOf(artifactPath.toURI().toURL())
            val classLoader = URLClassLoader(urls, this::class.java.classLoader)
            
            // Crear referencia y cachear
            val reference = LibraryReference(
                identifier = identifier,
                definition = definition,
                classLoader = classLoader,
                loadedAt = Instant.now()
            )
            
            loadedLibraries[identifier] = reference
            logger.info { "Successfully loaded library from artifact: $identifier" }
            
            reference
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to load library from artifact: ${artifactPath.absolutePath}" }
            null
        }
    }
    
    override suspend fun loadLibrariesFromArtifacts(artifacts: List<File>): Map<String, LibraryReference> {
        val results = mutableMapOf<String, LibraryReference>()
        
        for (artifact in artifacts) {
            loadLibraryFromArtifact(artifact)?.let { ref ->
                results[ref.identifier] = ref
            }
        }
        
        return results
    }
    
    override fun addArtifactLibrary(identifier: String, jarFile: File) {
        artifactLibraries[identifier] = jarFile
        logger.info { "Added artifact library: $identifier -> ${jarFile.absolutePath}" }
    }
    
    override fun getLoadedLibrary(identifier: String): LibraryReference? {
        return loadedLibraries[identifier]
    }
    
    override fun unloadLibrary(identifier: String) {
        loadedLibraries.remove(identifier)?.let { reference ->
            try {
                reference.classLoader.close()
                logger.info { "Unloaded library: $identifier" }
            } catch (e: Exception) {
                logger.warn(e) { "Error unloading library: $identifier" }
            }
        }
    }
    
    override fun getAllLoadedLibraries(): List<LibraryReference> {
        return loadedLibraries.values.toList()
    }
}

/**
 * Implementación en memoria del repositorio de librerías.
 * 
 * Para uso básico del Pipeline DSL sin configuración externa.
 */
class InMemoryLibraryRepository : LibraryRepository {
    private val libraries = ConcurrentHashMap<String, LibraryDefinition>()
    
    init {
        // Cargar librerías estándar de Kotlin
        loadStandardKotlinLibraries()
    }
    
    override suspend fun getLibrary(identifier: String): LibraryDefinition? {
        return libraries[identifier]
    }
    
    override suspend fun saveLibrary(library: LibraryDefinition) {
        libraries[library.identifier] = library
    }
    
    override suspend fun deleteLibrary(identifier: String) {
        libraries.remove(identifier)
    }
    
    override suspend fun searchLibraries(query: String): List<LibraryDefinition> {
        return libraries.values.filter { 
            it.identifier.contains(query, ignoreCase = true) ||
            it.metadata.name.contains(query, ignoreCase = true)
        }
    }
    
    private fun loadStandardKotlinLibraries() {
        // Kotlin standard library
        val kotlinStdLib = LibraryDefinition(
            identifier = "kotlin-stdlib:latest",
            version = "2.1.21",
            jarFiles = findKotlinStdLibJars(),
            dependencies = emptyList(),
            metadata = LibraryMetadata(
                name = "Kotlin Standard Library",
                description = "Kotlin standard library",
                author = "JetBrains",
                license = "Apache 2.0"
            )
        )
        libraries[kotlinStdLib.identifier] = kotlinStdLib
        
        // Kotlinx Coroutines
        val coroutines = LibraryDefinition(
            identifier = "kotlinx-coroutines-core:latest",
            version = "1.10.2",
            jarFiles = findCoroutinesJars(),
            dependencies = listOf("kotlin-stdlib:latest"),
            metadata = LibraryMetadata(
                name = "Kotlinx Coroutines Core",
                description = "Coroutines support for Kotlin",
                author = "JetBrains",
                license = "Apache 2.0"
            )
        )
        libraries[coroutines.identifier] = coroutines
    }
    
    private fun findKotlinStdLibJars(): List<File> {
        // Buscar en el classpath actual
        return try {
            val classLoader = this::class.java.classLoader
            val urls = (classLoader as? URLClassLoader)?.urLs ?: emptyArray()
            urls.filter { it.path.contains("kotlin-stdlib") }
                .map { File(it.toURI()) }
                .filter { it.exists() }
        } catch (e: Exception) {
            logger.warn(e) { "Could not find Kotlin stdlib JARs" }
            emptyList()
        }
    }
    
    private fun findCoroutinesJars(): List<File> {
        return try {
            val classLoader = this::class.java.classLoader
            val urls = (classLoader as? URLClassLoader)?.urLs ?: emptyArray()
            urls.filter { it.path.contains("kotlinx-coroutines") }
                .map { File(it.toURI()) }
                .filter { it.exists() }
        } catch (e: Exception) {
            logger.warn(e) { "Could not find Kotlinx Coroutines JARs" }
            emptyList()
        }
    }
}