package dev.rubentxu.hodei.pipelines.dsl.library

/**
 * Library repository interface
 */
interface LibraryRepository {
    suspend fun getLibrary(identifier: String): LibraryDefinition?
    suspend fun saveLibrary(library: LibraryDefinition)
    suspend fun deleteLibrary(identifier: String)
    suspend fun searchLibraries(query: String): List<LibraryDefinition>
}