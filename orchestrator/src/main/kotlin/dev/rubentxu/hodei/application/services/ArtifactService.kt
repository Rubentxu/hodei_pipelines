package dev.rubentxu.hodei.application.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.domain.artifact.Artifact
import dev.rubentxu.hodei.domain.artifact.ArtifactRepository
import dev.rubentxu.hodei.domain.artifact.ArtifactStatus
import dev.rubentxu.hodei.domain.artifact.ArtifactType
import dev.rubentxu.hodei.domain.artifact.RetentionPolicy
import dev.rubentxu.hodei.shared.domain.errors.DomainError
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.RepositoryError
import dev.rubentxu.hodei.shared.domain.errors.ValidationError
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Service for managing artifacts in job executions
 */
class ArtifactService(
    private val artifactRepository: ArtifactRepository
) {
    private val logger = LoggerFactory.getLogger(ArtifactService::class.java)

    /**
     * Create a new artifact for a job execution
     */
    suspend fun createArtifact(
        name: String,
        type: ArtifactType,
        jobId: DomainId,
        poolId: DomainId,
        executionId: DomainId? = null,
        version: String = "1.0.0",
        retentionPolicy: RetentionPolicy = RetentionPolicy.default(),
        metadata: Map<String, String> = emptyMap(),
        labels: Map<String, String> = emptyMap()
    ): Either<DomainError, Artifact> {
        logger.debug("Creating artifact $name for job $jobId")
        
        val now = Clock.System.now()
        val expiresAt = retentionPolicy.retentionDays?.let { days ->
            now.plus(kotlin.time.Duration.parse("${days * 24}h"))
        }
        
        val artifact = Artifact(
            id = DomainId.generate(),
            name = name,
            type = type,
            version = version,
            jobId = jobId,
            executionId = executionId,
            poolId = poolId,
            status = ArtifactStatus.PENDING,
            metadata = metadata,
            labels = labels,
            retentionPolicy = retentionPolicy,
            createdAt = now,
            updatedAt = now,
            expiresAt = expiresAt
        )
        
        return artifactRepository.save(artifact).mapLeft { it as DomainError }
    }

    /**
     * Mark artifact as available with storage information
     */
    suspend fun markArtifactAvailable(
        artifactId: DomainId,
        storageLocation: String,
        sizeBytes: Long,
        checksum: String? = null,
        contentType: String? = null
    ): Either<DomainError, Artifact> {
        logger.debug("Marking artifact $artifactId as available at $storageLocation")
        
        return artifactRepository.findById(artifactId)
            .mapLeft { it as DomainError }
            .flatMap { artifact ->
                if (artifact == null) {
                    RepositoryError.NotFoundError(message = "Artifact not found", entityType = "Artifact", entityId = artifactId.value).left()
                } else {
                    val updated = artifact.markAsAvailable(storageLocation, sizeBytes, checksum, contentType)
                    artifactRepository.save(updated).mapLeft { it as DomainError }
                }
            }
    }

    /**
     * Mark artifact as failed with error information
     */
    suspend fun markArtifactFailed(
        artifactId: DomainId,
        errorMessage: String
    ): Either<DomainError, Artifact> {
        logger.warn("Marking artifact $artifactId as failed: $errorMessage")
        
        return artifactRepository.findById(artifactId)
            .mapLeft { it as DomainError }
            .flatMap { artifact ->
                if (artifact == null) {
                    RepositoryError.NotFoundError(message = "Artifact not found", entityType = "Artifact", entityId = artifactId.value).left()
                } else {
                    val updated = artifact.markAsFailed(errorMessage)
                    artifactRepository.save(updated).mapLeft { it as DomainError }
                }
            }
    }

    /**
     * Get artifacts for a specific job
     */
    suspend fun getJobArtifacts(jobId: DomainId): Either<DomainError, List<Artifact>> {
        logger.debug("Getting artifacts for job $jobId")
        return artifactRepository.findByJobId(jobId).mapLeft { it as DomainError }
    }

    /**
     * Get artifacts for a specific execution
     */
    suspend fun getExecutionArtifacts(executionId: DomainId): Either<DomainError, List<Artifact>> {
        logger.debug("Getting artifacts for execution $executionId")
        return artifactRepository.findByExecutionId(executionId).mapLeft { it as DomainError }
    }

    /**
     * Get all versions of an artifact
     */
    suspend fun getArtifactVersions(name: String, poolId: DomainId): Either<DomainError, List<Artifact>> {
        logger.debug("Getting versions of artifact $name in pool $poolId")
        return artifactRepository.findVersionsByName(name, poolId).mapLeft { it as DomainError }
    }

    /**
     * Get latest version of an artifact
     */
    suspend fun getLatestArtifactVersion(name: String, poolId: DomainId): Either<DomainError, Artifact?> {
        return getArtifactVersions(name, poolId).map { versions ->
            versions.maxByOrNull { it.createdAt }
        }
    }

    /**
     * Create a new version of an existing artifact
     */
    suspend fun createArtifactVersion(
        artifactId: DomainId,
        newVersion: String
    ): Either<DomainError, Artifact> {
        logger.debug("Creating new version $newVersion for artifact $artifactId")
        
        return artifactRepository.findById(artifactId)
            .mapLeft { it as DomainError }
            .flatMap { artifact ->
                if (artifact == null) {
                    RepositoryError.NotFoundError(message = "Artifact not found", entityType = "Artifact", entityId = artifactId.value).left()
                } else {
                    val newVersionArtifact = artifact.createNextVersion(newVersion)
                    artifactRepository.save(newVersionArtifact).mapLeft { it as DomainError }
                }
            }
    }

    /**
     * Delete an artifact
     */
    suspend fun deleteArtifact(artifactId: DomainId): Either<DomainError, Boolean> {
        logger.debug("Deleting artifact $artifactId")
        
        return artifactRepository.findById(artifactId)
            .mapLeft { it as DomainError }
            .flatMap { artifact ->
                if (artifact == null) {
                    RepositoryError.NotFoundError(message = "Artifact not found", entityType = "Artifact", entityId = artifactId.value).left()
                } else if (!artifact.canBeDeleted()) {
                    ValidationError(code = "ARTIFACT_DELETION_ERROR", message = "Artifact cannot be deleted in current status: ${artifact.status}").left()
                } else {
                    artifactRepository.deleteById(artifactId).mapLeft { it as DomainError }
                }
            }
    }

    /**
     * Clean up expired artifacts
     */
    suspend fun cleanupExpiredArtifacts(): Either<DomainError, Int> {
        logger.info("Starting cleanup of expired artifacts")
        
        return artifactRepository.findExpiredArtifacts()
            .mapLeft { it as DomainError }
            .flatMap { expiredArtifacts ->
                var deletedCount = 0
                
                for (artifact in expiredArtifacts) {
                    if (artifact.retentionPolicy.autoCleanup) {
                        logger.debug("Deleting expired artifact: ${artifact.id}")
                        artifactRepository.deleteById(artifact.id)
                        deletedCount++
                    } else {
                        // Just mark as expired if auto-cleanup is disabled
                        artifactRepository.updateStatus(artifact.id, ArtifactStatus.EXPIRED)
                    }
                }
                
                logger.info("Cleaned up $deletedCount expired artifacts")
                deletedCount.right()
            }
    }

    /**
     * Get storage statistics for a pool
     */
    suspend fun getPoolStorageStats(poolId: DomainId): Either<DomainError, PoolStorageStats> {
        return artifactRepository.countByPool(poolId)
            .mapLeft { it as DomainError }
            .flatMap { count ->
                artifactRepository.getTotalSizeByPool(poolId)
                    .mapLeft { it as DomainError }
                    .map { totalSize ->
                        PoolStorageStats(
                            poolId = poolId,
                            artifactCount = count,
                            totalSizeBytes = totalSize
                        )
                    }
            }
    }

    /**
     * Enforce retention policy for artifacts in a pool
     */
    suspend fun enforceRetentionPolicy(poolId: DomainId): Either<DomainError, RetentionPolicyResult> {
        logger.info("Enforcing retention policy for pool $poolId")
        
        return artifactRepository.findByPoolId(poolId)
            .mapLeft { it as DomainError }
            .flatMap { artifacts ->
                val artifactsByName = artifacts.groupBy { it.name }
                var deletedCount = 0
                var keptCount = 0
                
                for ((name, versions) in artifactsByName) {
                    val sortedVersions = versions.sortedByDescending { it.createdAt }
                    
                    for (artifact in sortedVersions) {
                        val policy = artifact.retentionPolicy
                        val shouldDelete = when {
                            artifact.isExpired() -> true
                            policy.maxVersions != null -> {
                                val versionIndex = sortedVersions.indexOf(artifact)
                                versionIndex >= policy.maxVersions
                            }
                            else -> false
                        }
                        
                        if (shouldDelete && policy.autoCleanup) {
                            artifactRepository.deleteById(artifact.id)
                            deletedCount++
                        } else {
                            keptCount++
                        }
                    }
                }
                
                RetentionPolicyResult(
                    poolId = poolId,
                    artifactsDeleted = deletedCount,
                    artifactsKept = keptCount
                ).right()
            }
    }
}

/**
 * Storage statistics for a resource pool
 */
data class PoolStorageStats(
    val poolId: DomainId,
    val artifactCount: Long,
    val totalSizeBytes: Long
)

/**
 * Result of retention policy enforcement
 */
data class RetentionPolicyResult(
    val poolId: DomainId,
    val artifactsDeleted: Int,
    val artifactsKept: Int
)