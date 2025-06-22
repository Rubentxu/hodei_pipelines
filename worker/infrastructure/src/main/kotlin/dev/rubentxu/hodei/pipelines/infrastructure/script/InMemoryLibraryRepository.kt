package dev.rubentxu.hodei.pipelines.infrastructure.script

import dev.rubentxu.hodei.pipelines.domain.worker.model.library.LibraryDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.ports.LibraryRepository
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory library repository (for testing/development)
 */
class InMemoryLibraryRepository : LibraryRepository {
    private val libraries = ConcurrentHashMap<String, LibraryDefinition>()

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
}