package dev.rubentxu.hodei.domain.artifact

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Artifact represents files, data, or results produced by job executions
 * Supports versioning, metadata, and lifecycle management
 */
@Serializable
data class Artifact(
    val id: DomainId,
    val name: String,
    val type: ArtifactType,
    val version: String = "1.0.0",
    val jobId: DomainId,
    val executionId: DomainId? = null,
    val poolId: DomainId,
    val status: ArtifactStatus = ArtifactStatus.PENDING,
    val storageLocation: String? = null,
    val contentType: String? = null,
    val sizeBytes: Long = 0L,
    val checksum: String? = null,
    val checksumAlgorithm: ChecksumAlgorithm = ChecksumAlgorithm.SHA256,
    val metadata: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val retentionPolicy: RetentionPolicy = RetentionPolicy.default(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val expiresAt: Instant? = null
) {
    init {
        require(name.isNotBlank()) { "Artifact name cannot be blank" }
        require(version.isNotBlank()) { "Artifact version cannot be blank" }
        require(sizeBytes >= 0) { "Artifact size cannot be negative" }
        if (status == ArtifactStatus.AVAILABLE) {
            require(storageLocation != null) { "Available artifacts must have storage location" }
        }
    }

    /**
     * Check if artifact is expired based on retention policy
     */
    fun isExpired(currentTime: Instant = kotlinx.datetime.Clock.System.now()): Boolean {
        return expiresAt?.let { currentTime >= it } ?: false
    }

    /**
     * Check if artifact can be safely deleted
     */
    fun canBeDeleted(): Boolean {
        return status in listOf(ArtifactStatus.EXPIRED, ArtifactStatus.FAILED, ArtifactStatus.DELETED)
    }

    /**
     * Generate a unique artifact path based on job and execution
     */
    fun generateStoragePath(): String {
        val basePath = "artifacts/$poolId/$jobId"
        return if (executionId != null) {
            "$basePath/$executionId/$name-$version"
        } else {
            "$basePath/$name-$version"
        }
    }

    /**
     * Create a new version of this artifact
     */
    fun createNextVersion(newVersion: String): Artifact {
        return copy(
            id = DomainId.generate(),
            version = newVersion,
            status = ArtifactStatus.PENDING,
            storageLocation = null,
            sizeBytes = 0L,
            checksum = null,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    /**
     * Mark artifact as available with storage details
     */
    fun markAsAvailable(
        location: String,
        size: Long,
        checksum: String? = null,
        contentType: String? = null
    ): Artifact {
        return copy(
            status = ArtifactStatus.AVAILABLE,
            storageLocation = location,
            sizeBytes = size,
            checksum = checksum,
            contentType = contentType,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }

    /**
     * Mark artifact as failed with error information
     */
    fun markAsFailed(errorMessage: String): Artifact {
        val errorMetadata = metadata + ("error" to errorMessage)
        return copy(
            status = ArtifactStatus.FAILED,
            metadata = errorMetadata,
            updatedAt = kotlinx.datetime.Clock.System.now()
        )
    }
}

/**
 * Types of artifacts that can be produced or consumed
 */
@Serializable
enum class ArtifactType {
    FILE,           // Single file
    DIRECTORY,      // Directory with multiple files
    LOG,            // Log output from execution
    REPORT,         // Generated report (HTML, PDF, etc.)
    DATA,           // Structured data (JSON, CSV, etc.)
    IMAGE,          // Container image or binary
    ARCHIVE,        // Compressed archive (ZIP, TAR, etc.)
    METADATA,       // Metadata about execution
    CUSTOM          // Custom artifact type
}

/**
 * Current status of an artifact in its lifecycle
 */
@Serializable
enum class ArtifactStatus {
    PENDING,        // Artifact creation is pending
    UPLOADING,      // Artifact is being uploaded
    AVAILABLE,      // Artifact is available for use
    DOWNLOADING,    // Artifact is being downloaded
    PROCESSING,     // Artifact is being processed
    FAILED,         // Artifact operation failed
    EXPIRED,        // Artifact has expired
    DELETED         // Artifact has been deleted
}

/**
 * Checksum algorithms supported for artifact integrity
 */
@Serializable
enum class ChecksumAlgorithm {
    MD5,
    SHA1,
    SHA256,
    SHA512
}

/**
 * Retention policy for artifact lifecycle management
 */
@Serializable
data class RetentionPolicy(
    val retentionDays: Int? = null,
    val maxVersions: Int? = null,
    val deleteOnJobCompletion: Boolean = false,
    val autoCleanup: Boolean = true
) {
    companion object {
        fun default() = RetentionPolicy(
            retentionDays = 30,
            maxVersions = 5,
            deleteOnJobCompletion = false,
            autoCleanup = true
        )

        fun temporary() = RetentionPolicy(
            retentionDays = 1,
            maxVersions = 1,
            deleteOnJobCompletion = true,
            autoCleanup = true
        )

        fun permanent() = RetentionPolicy(
            retentionDays = null,
            maxVersions = null,
            deleteOnJobCompletion = false,
            autoCleanup = false
        )
    }
}