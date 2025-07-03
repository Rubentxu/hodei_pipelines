package dev.rubentxu.hodei.domain.artifact

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.domain.artifact.Artifact
import dev.rubentxu.hodei.domain.artifact.ArtifactType
import dev.rubentxu.hodei.domain.artifact.ArtifactStatus
import dev.rubentxu.hodei.domain.artifact.RetentionPolicy
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock

class ArtifactTest : DescribeSpec({
    describe("Artifact") {
        val now = Clock.System.now()
        val jobId = DomainId.generate()
        val poolId = DomainId.generate()
        val executionId = DomainId.generate()

        describe("creation and validation") {
            it("should create valid artifact") {
                val artifact = Artifact(
                    id = DomainId.generate(),
                    name = "test-artifact",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId,
                    createdAt = now,
                    updatedAt = now
                )

                artifact.name shouldBe "test-artifact"
                artifact.type shouldBe ArtifactType.FILE
                artifact.status shouldBe ArtifactStatus.PENDING
                artifact.version shouldBe "1.0.0"
            }

            it("should validate artifact name") {
                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    Artifact(
                        id = DomainId.generate(),
                        name = "",
                        type = ArtifactType.FILE,
                        jobId = jobId,
                        poolId = poolId,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                exception.message shouldBe "Artifact name cannot be blank"
            }

            it("should validate available artifacts have storage location") {
                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    Artifact(
                        id = DomainId.generate(),
                        name = "test",
                        type = ArtifactType.FILE,
                        jobId = jobId,
                        poolId = poolId,
                        status = ArtifactStatus.AVAILABLE,
                        storageLocation = null,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                exception.message shouldBe "Available artifacts must have storage location"
            }

            it("should validate non-negative size") {
                val exception = io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    Artifact(
                        id = DomainId.generate(),
                        name = "test",
                        type = ArtifactType.FILE,
                        jobId = jobId,
                        poolId = poolId,
                        sizeBytes = -1,
                        createdAt = now,
                        updatedAt = now
                    )
                }
                exception.message shouldBe "Artifact size cannot be negative"
            }
        }

        describe("storage path generation") {
            it("should generate path with execution ID") {
                val artifact = Artifact(
                    id = DomainId.generate(),
                    name = "output.txt",
                    type = ArtifactType.FILE,
                    version = "2.1.0",
                    jobId = jobId,
                    executionId = executionId,
                    poolId = poolId,
                    createdAt = now,
                    updatedAt = now
                )

                val expectedPath = "artifacts/$poolId/$jobId/$executionId/output.txt-2.1.0"
                artifact.generateStoragePath() shouldBe expectedPath
            }

            it("should generate path without execution ID") {
                val artifact = Artifact(
                    id = DomainId.generate(),
                    name = "template.json",
                    type = ArtifactType.DATA,
                    version = "1.0.0",
                    jobId = jobId,
                    poolId = poolId,
                    createdAt = now,
                    updatedAt = now
                )

                val expectedPath = "artifacts/$poolId/$jobId/template.json-1.0.0"
                artifact.generateStoragePath() shouldBe expectedPath
            }
        }

        describe("lifecycle management") {
            val artifact = Artifact(
                id = DomainId.generate(),
                name = "test-file",
                type = ArtifactType.FILE,
                jobId = jobId,
                poolId = poolId,
                createdAt = now,
                updatedAt = now
            )

            it("should mark artifact as available") {
                val available = artifact.markAsAvailable(
                    location = "/storage/path",
                    size = 1024,
                    checksum = "abc123",
                    contentType = "text/plain"
                )

                available.status shouldBe ArtifactStatus.AVAILABLE
                available.storageLocation shouldBe "/storage/path"
                available.sizeBytes shouldBe 1024
                available.checksum shouldBe "abc123"
                available.contentType shouldBe "text/plain"
                available.updatedAt shouldNotBe artifact.updatedAt
            }

            it("should mark artifact as failed") {
                val failed = artifact.markAsFailed("Upload failed")

                failed.status shouldBe ArtifactStatus.FAILED
                failed.metadata["error"] shouldBe "Upload failed"
                failed.updatedAt shouldNotBe artifact.updatedAt
            }

            it("should create next version") {
                val nextVersion = artifact.createNextVersion("2.0.0")

                nextVersion.id shouldNotBe artifact.id
                nextVersion.version shouldBe "2.0.0"
                nextVersion.status shouldBe ArtifactStatus.PENDING
                nextVersion.storageLocation shouldBe null
                nextVersion.sizeBytes shouldBe 0L
                nextVersion.checksum shouldBe null
            }
        }

        describe("expiration and cleanup") {
            it("should check if artifact is expired") {
                val expiredArtifact = Artifact(
                    id = DomainId.generate(),
                    name = "expired",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId,
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = now.minus(kotlin.time.Duration.parse("1h"))
                )

                expiredArtifact.isExpired() shouldBe true
            }

            it("should check if artifact is not expired") {
                val futureExpiry = now.plus(kotlin.time.Duration.parse("720h")) // 30 days
                val validArtifact = Artifact(
                    id = DomainId.generate(),
                    name = "valid",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId,
                    createdAt = now,
                    updatedAt = now,
                    expiresAt = futureExpiry
                )

                validArtifact.isExpired() shouldBe false
            }

            it("should check if artifact can be deleted") {
                val deletableArtifact = Artifact(
                    id = DomainId.generate(),
                    name = "deletable",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId,
                    status = ArtifactStatus.EXPIRED,
                    createdAt = now,
                    updatedAt = now
                )

                deletableArtifact.canBeDeleted() shouldBe true
            }

            it("should check if artifact cannot be deleted") {
                val artifact = Artifact(
                    id = DomainId.generate(),
                    name = "active",
                    type = ArtifactType.FILE,
                    jobId = jobId,
                    poolId = poolId,
                    status = ArtifactStatus.AVAILABLE,
                    storageLocation = "/storage/active",
                    createdAt = now,
                    updatedAt = now
                )

                artifact.canBeDeleted() shouldBe false
            }
        }
    }

    describe("RetentionPolicy") {
        it("should create default policy") {
            val policy = RetentionPolicy.default()
            
            policy.retentionDays shouldBe 30
            policy.maxVersions shouldBe 5
            policy.deleteOnJobCompletion shouldBe false
            policy.autoCleanup shouldBe true
        }

        it("should create temporary policy") {
            val policy = RetentionPolicy.temporary()
            
            policy.retentionDays shouldBe 1
            policy.maxVersions shouldBe 1
            policy.deleteOnJobCompletion shouldBe true
            policy.autoCleanup shouldBe true
        }

        it("should create permanent policy") {
            val policy = RetentionPolicy.permanent()
            
            policy.retentionDays shouldBe null
            policy.maxVersions shouldBe null
            policy.deleteOnJobCompletion shouldBe false
            policy.autoCleanup shouldBe false
        }
    }
})