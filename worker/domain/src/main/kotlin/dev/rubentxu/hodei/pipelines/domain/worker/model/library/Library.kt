package dev.rubentxu.hodei.pipelines.domain.worker.model.library

import java.io.File
import java.net.URLClassLoader
import java.time.Instant
import mu.KotlinLogging
private val logger = KotlinLogging.logger {}

/**
 * Library definition
 */
data class LibraryDefinition(
    val identifier: String,
    val version: String,
    val jarFiles: List<File>,
    val dependencies: List<String>,
    val metadata: LibraryMetadata,
    val permissions: Set<Permission> = emptySet(),
    val checksums: Map<String, String> = emptyMap()
)

/**
 * Library metadata
 */
data class LibraryMetadata(
    val name: String,
    val description: String,
    val author: String,
    val homepage: String? = null,
    val license: String? = null,
    val tags: Set<String> = emptySet(),
    val minimumVersion: String? = null,
    val maximumVersion: String? = null
)

/**
 * Library permissions
 */
enum class Permission {
    FILE_SYSTEM_ACCESS,
    NETWORK_ACCESS,
    SYSTEM_PROPERTY_ACCESS,
    ENVIRONMENT_ACCESS,
    REFLECTION_ACCESS,
    NATIVE_ACCESS
}

/**
 * Library reference (loaded library)
 */
data class LibraryReference(
    val identifier: String,
    val definition: LibraryDefinition,
    val classLoader: URLClassLoader,
    val loadedAt: Instant,
    val usageCount: Long = 0
) {
    fun <T> getInstance(className: String): T? {
        return try {
            val clazz = classLoader.loadClass(className)
            @Suppress("UNCHECKED_CAST")
            clazz.getDeclaredConstructor().newInstance() as T
        } catch (e: Exception) {
            logger.warn { "Failed to instantiate class $className: ${e.message}" }
            null
        }
    }

    fun hasClass(className: String): Boolean {
        return try {
            classLoader.loadClass(className)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
    }
}

/**
 * Library exceptions
 */
class LibraryNotFoundException(identifier: String) : Exception("Library not found: $identifier")
class LibraryDownloadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LibraryVerificationException(message: String, cause: Throwable? = null) : Exception(message, cause)
class LibraryLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)