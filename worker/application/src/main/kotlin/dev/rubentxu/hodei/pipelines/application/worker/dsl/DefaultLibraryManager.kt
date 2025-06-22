package dev.rubentxu.hodei.pipelines.application.worker.dsl

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.LibraryManager
import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryDownloadException
import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryNotFoundException
import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryReference
import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryVerificationException
import dev.rubentxu.hodei.pipelines.domain.worker.ports.LibraryRepository
import dev.rubentxu.hodei.pipelines.domain.worker.ports.PipelineSecurityManager
import dev.rubentxu.hodei.pipelines.domain.worker.model.security.SecurityCheckResult
import mu.KotlinLogging
import java.io.File
import java.net.URL
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarFile
private val logger = KotlinLogging.logger {}
/**
 * Default implementation of LibraryManager
 */
class DefaultLibraryManager(
    private val libraryRepository: LibraryRepository,
    private val securityManager: PipelineSecurityManager,
    private val cacheDirectory: File = File(System.getProperty("java.io.tmpdir"), "hodei-libraries"),
    private val trustedRepositories: List<String> = listOf(
        "https://repo1.maven.org/maven2/",
        "https://jcenter.bintray.com/"
    )
) : LibraryManager {

    private val loadedLibraries = ConcurrentHashMap<String, LibraryReference>()
    private val classLoaderCache = ConcurrentHashMap<String, URLClassLoader>()

    init {
        if (!cacheDirectory.exists()) {
            cacheDirectory.mkdirs()
        }
    }

    override suspend fun loadLibrary(identifier: String): LibraryReference {
        return loadedLibraries.getOrPut(identifier) {
            logger.info { "Loading library: $identifier" }

            val libraryDef = libraryRepository.getLibrary(identifier)
                ?: throw LibraryNotFoundException(identifier)

            // Security check
            val securityResult = securityManager.checkLibraryAccess(identifier)
            if (securityResult is SecurityCheckResult.Denied) {
                throw SecurityException("Library access denied: ${securityResult.violations}")
            }

            // Create secure class loader
            val classLoader = createSecureClassLoader(libraryDef)

            LibraryReference(
                identifier = identifier,
                definition = libraryDef,
                classLoader = classLoader,
                loadedAt = Instant.now()
            )
        }
    }

    override suspend fun registerLibrary(library: LibraryDefinition) {
        logger.info { "Registering library: ${library.identifier}" }
        libraryRepository.saveLibrary(library)
    }

    override suspend fun unloadLibrary(identifier: String) {
        logger.info { "Unloading library: $identifier" }
        loadedLibraries.remove(identifier)
        classLoaderCache.remove(identifier)?.close()
    }

    override fun getCoreLibraries(): List<File> {
        // Return core Kotlin and Java libraries
        val kotlinStdlib = findKotlinStdlib()
        val javaBase = findJavaBase()

        return listOfNotNull(kotlinStdlib, javaBase)
    }

    override fun getLoadedLibraries(): Map<String, LibraryReference> {
        return loadedLibraries.toMap()
    }

    override suspend fun downloadLibrary(coordinates: String, version: String): File {
        val (groupId, artifactId) = parseCoordinates(coordinates)
        val fileName = "$artifactId-$version.jar"
        val cacheFile = File(cacheDirectory, fileName)

        if (cacheFile.exists()) {
            logger.debug { "Library already cached: $fileName" }
            return cacheFile
        }

        logger.info { "Downloading library: $coordinates:$version" }

        for (repository in trustedRepositories) {
            try {
                val url = buildMavenUrl(repository, groupId, artifactId, version)
                downloadFile(url, cacheFile)

                // Verify downloaded JAR
                verifyJarFile(cacheFile)

                logger.info { "Successfully downloaded: $fileName" }
                return cacheFile

            } catch (e: Exception) {
                logger.warn { "Failed to download from $repository: ${e.message}" }
            }
        }

        throw LibraryDownloadException("Failed to download library: $coordinates:$version")
    }

    private fun createSecureClassLoader(library: LibraryDefinition): URLClassLoader {
        val urls = library.jarFiles.map { it.toURI().toURL() }.toTypedArray()

        return object : URLClassLoader(urls, this::class.java.classLoader) {
            override fun loadClass(name: String?): Class<*> {
                // Check if class is allowed
                if (name != null && isClassBlocked(name)) {
                    throw ClassNotFoundException("Class $name is not allowed in sandbox")
                }
                return super.loadClass(name)
            }

            private fun isClassBlocked(className: String): Boolean {
                val blockedPackages = listOf(
                    "java.lang.Runtime",
                    "java.lang.ProcessBuilder",
                    "java.lang.reflect.",
                    "sun.",
                    "com.sun.",
                    "jdk.internal."
                )
                return blockedPackages.any { className.startsWith(it) }
            }
        }
    }

    private fun parseCoordinates(coordinates: String): Pair<String, String> {
        val parts = coordinates.split(":")
        if (parts.size < 2) {
            throw IllegalArgumentException("Invalid coordinates format: $coordinates (expected: groupId:artifactId)")
        }
        return parts[0] to parts[1]
    }

    private fun buildMavenUrl(repository: String, groupId: String, artifactId: String, version: String): String {
        val groupPath = groupId.replace(".", "/")
        return "$repository$groupPath/$artifactId/$version/$artifactId-$version.jar"
    }

    private suspend fun downloadFile(url: String, destination: File) {
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 30000
            connection.readTimeout = 60000

            connection.getInputStream().use { input ->
                Files.copy(input, destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: Exception) {
            throw LibraryDownloadException("Failed to download from $url: ${e.message}", e)
        }
    }

    private fun verifyJarFile(file: File) {
        try {
            JarFile(file).use { jar ->
                // Basic JAR verification
                val manifest = jar.manifest
                logger.debug { "JAR manifest: ${manifest?.mainAttributes}" }

                // Check for suspicious entries
                val entries = jar.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    if (entry.name.contains("..") || entry.name.startsWith("/")) {
                        throw SecurityException("Suspicious JAR entry: ${entry.name}")
                    }
                }
            }
        } catch (e: Exception) {
            file.delete()
            throw LibraryVerificationException("JAR verification failed: ${e.message}", e)
        }
    }

    private fun findKotlinStdlib(): File? {
        return try {
            val kotlinClass = String::class.java
            val location = kotlinClass.protectionDomain.codeSource.location
            File(location.toURI())
        } catch (e: Exception) {
            logger.warn { "Failed to find Kotlin stdlib: ${e.message}" }
            null
        }
    }

    private fun findJavaBase(): File? {
        return try {
            val javaHome = System.getProperty("java.home")
            val rtJar = File(javaHome, "lib/rt.jar")
            if (rtJar.exists()) rtJar else null
        } catch (e: Exception) {
            logger.warn { "Failed to find Java base: ${e.message}" }
            null
        }
    }
}