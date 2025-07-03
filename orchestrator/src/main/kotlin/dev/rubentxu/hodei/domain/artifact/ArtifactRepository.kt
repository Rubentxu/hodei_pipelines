package dev.rubentxu.hodei.domain.artifact

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*

/**
 * Repository interface for artifact persistence and retrieval
 */
interface ArtifactRepository {
    
    /**
     * Save an artifact to the repository
     */
    suspend fun save(artifact: Artifact): Either<RepositoryError, Artifact>
    
    /**
     * Find artifact by ID
     */
    suspend fun findById(id: DomainId): Either<RepositoryError, Artifact?>
    
    /**
     * Find artifacts by job ID
     */
    suspend fun findByJobId(jobId: DomainId): Either<RepositoryError, List<Artifact>>
    
    /**
     * Find artifacts by execution ID
     */
    suspend fun findByExecutionId(executionId: DomainId): Either<RepositoryError, List<Artifact>>
    
    /**
     * Find artifacts by pool ID
     */
    suspend fun findByPoolId(poolId: DomainId): Either<RepositoryError, List<Artifact>>
    
    /**
     * Find artifacts by name and version
     */
    suspend fun findByNameAndVersion(name: String, version: String, poolId: DomainId): Either<RepositoryError, Artifact?>
    
    /**
     * Find all versions of an artifact by name
     */
    suspend fun findVersionsByName(name: String, poolId: DomainId): Either<RepositoryError, List<Artifact>>
    
    /**
     * Find artifacts by status
     */
    suspend fun findByStatus(status: ArtifactStatus): Either<RepositoryError, List<Artifact>>
    
    /**
     * Find expired artifacts that can be cleaned up
     */
    suspend fun findExpiredArtifacts(): Either<RepositoryError, List<Artifact>>
    
    /**
     * Find artifacts by type
     */
    suspend fun findByType(type: ArtifactType, poolId: DomainId): Either<RepositoryError, List<Artifact>>
    
    /**
     * Delete artifact by ID
     */
    suspend fun deleteById(id: DomainId): Either<RepositoryError, Boolean>
    
    /**
     * Update artifact status
     */
    suspend fun updateStatus(id: DomainId, status: ArtifactStatus): Either<RepositoryError, Artifact?>
    
    /**
     * Count artifacts by pool
     */
    suspend fun countByPool(poolId: DomainId): Either<RepositoryError, Long>
    
    /**
     * Get total storage size used by pool
     */
    suspend fun getTotalSizeByPool(poolId: DomainId): Either<RepositoryError, Long>
}