package dev.rubentxu.hodei.application.services

import arrow.core.right
import arrow.core.getOrElse
import dev.rubentxu.hodei.domain.artifact.ArtifactStatus
import dev.rubentxu.hodei.domain.artifact.ArtifactType
import dev.rubentxu.hodei.domain.artifact.RetentionPolicy
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.infrastructure.repository.InMemoryArtifactRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

class ArtifactServiceTest : DescribeSpec({
    describe("ArtifactService") {
        val repository = InMemoryArtifactRepository()
        val service = ArtifactService(repository)
        val poolId = DomainId.generate()
        val jobId = DomainId.generate()
        val executionId = DomainId.generate()

        beforeEach {
            repository.clear()
        }

        describe("artifact creation") {
            it("should create artifact successfully") {
                val result = service.createArtifact(
                    name = "test-output",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId,
                    executionId = executionId
                )

                result.isRight() shouldBe true
                result.map { artifact ->
                    artifact.name shouldBe "test-output"
                    artifact.type shouldBe ArtifactType.FILE
                    artifact.jobId shouldBe jobId
                    artifact.poolId shouldBe poolId
                    artifact.executionId shouldBe executionId
                    artifact.status shouldBe ArtifactStatus.PENDING
                }
            }

            it("should create artifact with custom retention policy") {
                val policy = RetentionPolicy.temporary()
                
                val result = service.createArtifact(
                    name = "temp-file",
                    type = ArtifactType.LOG,
                    jobId = jobId,
                    poolId = poolId,
                    retentionPolicy = policy
                )

                result.isRight() shouldBe true
                result.map { artifact ->
                    artifact.retentionPolicy shouldBe policy
                    // Just check that expiration is set for temporary policy
                    artifact.expiresAt shouldNotBe null
                }
            }

            it("should create artifact with metadata and labels") {
                val metadata = mapOf("source" to "test", "format" to "json")
                val labels = mapOf("env" to "test", "team" to "platform")
                
                val result = service.createArtifact(
                    name = "config-file",
                    type = ArtifactType.DATA,
                    jobId = jobId,
                    poolId = poolId,
                    metadata = metadata,
                    labels = labels
                )

                result.isRight() shouldBe true
                result.map { artifact ->
                    artifact.metadata shouldBe metadata
                    artifact.labels shouldBe labels
                }
            }
        }

        describe("artifact status management") {
            it("should mark artifact as available") {
                val createResult = service.createArtifact(
                    name = "upload-test",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId
                )

                createResult.isRight() shouldBe true
                val artifactId = createResult.map { it.id }.getOrElse { error("Failed to create") }

                val result = service.markArtifactAvailable(
                    artifactId = artifactId,
                    storageLocation = "/storage/path/file.txt",
                    sizeBytes = 2048,
                    checksum = "sha256:abc123",
                    contentType = "text/plain"
                )

                result.isRight() shouldBe true
                result.map { artifact ->
                    artifact.status shouldBe ArtifactStatus.AVAILABLE
                    artifact.storageLocation shouldBe "/storage/path/file.txt"
                    artifact.sizeBytes shouldBe 2048
                    artifact.checksum shouldBe "sha256:abc123"
                    artifact.contentType shouldBe "text/plain"
                }
            }

            it("should mark artifact as failed") {
                val createResult = service.createArtifact(
                    name = "failed-upload",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId
                )

                createResult.isRight() shouldBe true
                val artifactId = createResult.map { it.id }.getOrElse { error("Failed to create") }

                val result = service.markArtifactFailed(
                    artifactId = artifactId,
                    errorMessage = "Network connection timeout"
                )

                result.isRight() shouldBe true
                result.map { artifact ->
                    artifact.status shouldBe ArtifactStatus.FAILED
                    artifact.metadata["error"] shouldBe "Network connection timeout"
                }
            }

            it("should handle non-existent artifact") {
                val nonExistentId = DomainId.generate()
                
                val result = service.markArtifactAvailable(
                    artifactId = nonExistentId,
                    storageLocation = "/path",
                    sizeBytes = 1024
                )

                result.isLeft() shouldBe true
            }
        }

        describe("artifact retrieval") {
            beforeEach {
                // Create test artifacts
                service.createArtifact("job-output-1", ArtifactType.FILE, jobId, poolId, executionId)
                service.createArtifact("job-output-2", ArtifactType.LOG, jobId, poolId, executionId)
                service.createArtifact("other-job-output", ArtifactType.FILE, DomainId.generate(), poolId)
            }

            it("should get artifacts for job") {
                val result = service.getJobArtifacts(jobId)

                result.isRight() shouldBe true
                result.map { artifacts ->
                    artifacts.size shouldBe 2
                    artifacts.all { it.jobId == jobId } shouldBe true
                }
            }

            it("should get artifacts for execution") {
                val result = service.getExecutionArtifacts(executionId)

                result.isRight() shouldBe true
                result.map { artifacts ->
                    artifacts.size shouldBe 2
                    artifacts.all { it.executionId == executionId } shouldBe true
                }
            }
        }

        describe("artifact versioning") {
            it("should create and retrieve artifact versions") {
                // Create initial version
                val createResult = service.createArtifact(
                    name = "versioned-artifact",
                    type = ArtifactType.DATA,
                    jobId = jobId,
                    poolId = poolId,
                    version = "1.0.0"
                )

                createResult.isRight() shouldBe true
                val originalId = createResult.map { it.id }.getOrElse { error("Failed to create") }

                // Create new version
                delay(10) // Ensure different timestamps
                val versionResult = service.createArtifactVersion(originalId, "2.0.0")

                versionResult.isRight() shouldBe true

                // Get all versions
                val versionsResult = service.getArtifactVersions("versioned-artifact", poolId)

                versionsResult.isRight() shouldBe true
                versionsResult.map { versions ->
                    versions.size shouldBe 2
                    versions.map { it.version }.toSet() shouldBe setOf("1.0.0", "2.0.0")
                    versions.all { it.name == "versioned-artifact" } shouldBe true
                }
            }

            it("should get latest artifact version") {
                // Create multiple versions
                service.createArtifact("multi-version", ArtifactType.FILE, jobId, poolId, version = "1.0.0")
                delay(10)
                service.createArtifact("multi-version", ArtifactType.FILE, jobId, poolId, version = "2.0.0")
                delay(10)
                service.createArtifact("multi-version", ArtifactType.FILE, jobId, poolId, version = "1.5.0")

                val result = service.getLatestArtifactVersion("multi-version", poolId)

                result.isRight() shouldBe true
                result.map { latest ->
                    latest shouldNotBe null
                    latest?.version shouldBe "1.5.0" // Latest by creation time, not version number
                }
            }
        }

        describe("artifact deletion") {
            it("should delete expired artifact") {
                val createResult = service.createArtifact(
                    name = "expired-artifact",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId
                )

                createResult.isRight() shouldBe true
                val artifactId = createResult.map { it.id }.getOrElse { error("Failed to create") }

                // Mark as expired
                repository.updateStatus(artifactId, ArtifactStatus.EXPIRED)

                val deleteResult = service.deleteArtifact(artifactId)

                deleteResult.isRight() shouldBe true
                deleteResult.map { success ->
                    success shouldBe true
                }

                // Verify deletion
                val findResult = repository.findById(artifactId)
                findResult.map { it shouldBe null }
            }

            it("should not delete active artifact") {
                val createResult = service.createArtifact(
                    name = "active-artifact",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId
                )

                createResult.isRight() shouldBe true
                val artifactId = createResult.map { it.id }.getOrElse { error("Failed to create") }

                // Mark as available (active)
                service.markArtifactAvailable(artifactId, "/path", 1024)

                val deleteResult = service.deleteArtifact(artifactId)

                deleteResult.isLeft() shouldBe true
            }
        }

        describe("storage statistics") {
            it("should calculate pool storage stats") {
                // Create artifacts with different sizes
                val artifact1Result = service.createArtifact("file1", ArtifactType.FILE, jobId, poolId)
                val artifact2Result = service.createArtifact("file2", ArtifactType.FILE, jobId, poolId)

                artifact1Result.isRight() shouldBe true
                artifact2Result.isRight() shouldBe true

                val id1 = artifact1Result.map { it.id }.getOrElse { error("Failed") }
                val id2 = artifact2Result.map { it.id }.getOrElse { error("Failed") }

                service.markArtifactAvailable(id1, "/path1", 1024)
                service.markArtifactAvailable(id2, "/path2", 2048)

                val statsResult = service.getPoolStorageStats(poolId)

                statsResult.isRight() shouldBe true
                statsResult.map { stats ->
                    stats.poolId shouldBe poolId
                    stats.artifactCount shouldBe 2
                    stats.totalSizeBytes shouldBe 3072
                }
            }
        }

        describe("retention policy enforcement") {
            it("should enforce version limits") {
                val policy = RetentionPolicy(maxVersions = 2, autoCleanup = true)
                
                // Create multiple versions
                service.createArtifact("limited-versions", ArtifactType.FILE, jobId, poolId, version = "1.0.0", retentionPolicy = policy)
                delay(10)
                service.createArtifact("limited-versions", ArtifactType.FILE, jobId, poolId, version = "2.0.0", retentionPolicy = policy)
                delay(10)
                service.createArtifact("limited-versions", ArtifactType.FILE, jobId, poolId, version = "3.0.0", retentionPolicy = policy)

                val result = service.enforceRetentionPolicy(poolId)

                result.isRight() shouldBe true
                result.map { policyResult ->
                    policyResult.poolId shouldBe poolId
                    policyResult.artifactsDeleted shouldBe 1 // Oldest version deleted
                    policyResult.artifactsKept shouldBe 2   // Latest 2 versions kept
                }
            }
        }
    }
})