package dev.rubentxu.hodei.pipelines.domain.worker.ports

import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryDefinition

/**
 * Library repository interface
 */
interface LibraryRepository {
    suspend fun getLibrary(identifier: String): LibraryDefinition?
    suspend fun saveLibrary(library: LibraryDefinition)
    suspend fun deleteLibrary(identifier: String)
    suspend fun searchLibraries(query: String): List<LibraryDefinition>
}