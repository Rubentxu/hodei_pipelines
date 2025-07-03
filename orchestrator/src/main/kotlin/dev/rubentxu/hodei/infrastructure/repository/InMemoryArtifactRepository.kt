package dev.rubentxu.hodei.infrastructure.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.domain.artifact.Artifact
import dev.rubentxu.hodei.domain.artifact.ArtifactRepository
import dev.rubentxu.hodei.domain.artifact.ArtifactStatus
import dev.rubentxu.hodei.domain.artifact.ArtifactType
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.RepositoryError
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock

/**
 * In-memory implementation of ArtifactRepository for testing and development
 */
class InMemoryArtifactRepository : ArtifactRepository {
    
    private val artifacts = mutableMapOf<DomainId, Artifact>()
    private val mutex = Mutex()
    
    override suspend fun save(artifact: Artifact): Either<RepositoryError, Artifact> = mutex.withLock {
        try {
            artifacts[artifact.id] = artifact
            artifact.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to save artifact: ${e.message}").left()
        }
    }
    
    override suspend fun findById(id: DomainId): Either<RepositoryError, Artifact?> = mutex.withLock {
        try {
            artifacts[id].right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find artifact: ${e.message}").left()
        }
    }
    
    override suspend fun findByJobId(jobId: DomainId): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            artifacts.values.filter { it.jobId == jobId }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifacts by job: ${e.message}").left()
        }
    }
    
    override suspend fun findByExecutionId(executionId: DomainId): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            artifacts.values.filter { it.executionId == executionId }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifacts by execution: ${e.message}").left()
        }
    }
    
    override suspend fun findByPoolId(poolId: DomainId): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            artifacts.values.filter { it.poolId == poolId }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifacts by pool: ${e.message}").left()
        }
    }
    
    override suspend fun findByNameAndVersion(name: String, version: String, poolId: DomainId): Either<RepositoryError, Artifact?> = mutex.withLock {
        try {
            artifacts.values.find { 
                it.name == name && it.version == version && it.poolId == poolId 
            }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifact by name and version: ${e.message}").left()
        }
    }
    
    override suspend fun findVersionsByName(name: String, poolId: DomainId): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            artifacts.values
                .filter { it.name == name && it.poolId == poolId }
                .sortedByDescending { it.createdAt }
                .right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifact versions: ${e.message}").left()
        }
    }
    
    override suspend fun findByStatus(status: ArtifactStatus): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            artifacts.values.filter { it.status == status }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifacts by status: ${e.message}").left()
        }
    }
    
    override suspend fun findExpiredArtifacts(): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            val currentTime = Clock.System.now()
            artifacts.values.filter { it.isExpired(currentTime) }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find expired artifacts: ${e.message}").left()
        }
    }
    
    override suspend fun findByType(type: ArtifactType, poolId: DomainId): Either<RepositoryError, List<Artifact>> = mutex.withLock {
        try {
            artifacts.values
                .filter { it.type == type && it.poolId == poolId }
                .right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to find artifacts by type: ${e.message}").left()
        }
    }
    
    override suspend fun deleteById(id: DomainId): Either<RepositoryError, Boolean> = mutex.withLock {
        try {
            val removed = artifacts.remove(id) != null
            removed.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to delete artifact: ${e.message}").left()
        }
    }
    
    override suspend fun updateStatus(id: DomainId, status: ArtifactStatus): Either<RepositoryError, Artifact?> = mutex.withLock {
        try {
            val artifact = artifacts[id]
            if (artifact != null) {
                val updated = artifact.copy(
                    status = status,
                    updatedAt = Clock.System.now()
                )
                artifacts[id] = updated
                updated.right()
            } else {
                null.right()
            }
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to update artifact status: ${e.message}").left()
        }
    }
    
    override suspend fun countByPool(poolId: DomainId): Either<RepositoryError, Long> = mutex.withLock {
        try {
            artifacts.values.count { it.poolId == poolId }.toLong().right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to count artifacts by pool: ${e.message}").left()
        }
    }
    
    override suspend fun getTotalSizeByPool(poolId: DomainId): Either<RepositoryError, Long> = mutex.withLock {
        try {
            artifacts.values
                .filter { it.poolId == poolId }
                .sumOf { it.sizeBytes }
                .right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message ="Failed to get total size by pool: ${e.message}").left()
        }
    }
    
    /**
     * Clear all artifacts (for testing)
     */
    suspend fun clear() = mutex.withLock {
        artifacts.clear()
    }
    
    /**
     * Get all artifacts (for testing)
     */
    suspend fun getAll(): List<Artifact> = mutex.withLock {
        artifacts.values.toList()
    }
}