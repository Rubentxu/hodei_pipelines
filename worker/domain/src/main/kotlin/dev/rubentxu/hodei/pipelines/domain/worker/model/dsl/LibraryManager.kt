package dev.rubentxu.hodei.pipelines.domain.worker.model.dsl

import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryReference
import java.io.File

/**
 * File operations
 */
enum class FileOperation {
    READ,
    WRITE,
    DELETE,
    EXECUTE
}

/**
 * Library management interface
 */
interface LibraryManager {
    suspend fun loadLibrary(identifier: String): LibraryReference
    suspend fun registerLibrary(library: LibraryDefinition)
    suspend fun unloadLibrary(identifier: String)
    fun getCoreLibraries(): List<File>
    fun getLoadedLibraries(): Map<String, LibraryReference>
    suspend fun downloadLibrary(coordinates: String, version: String): File
}