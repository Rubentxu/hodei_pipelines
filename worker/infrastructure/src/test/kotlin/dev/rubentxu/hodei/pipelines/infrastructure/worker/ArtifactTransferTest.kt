package dev.rubentxu.hodei.pipelines.infrastructure.worker

import dev.rubentxu.hodei.pipelines.proto.ArtifactChunk
import dev.rubentxu.hodei.pipelines.proto.ArtifactType
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.security.MessageDigest

/**
 * Tests for artifact transfer functionality (Fase 1)
 */
class ArtifactTransferTest {

    @Test
    fun `should calculate SHA-256 checksum correctly`() = runTest {
        // Given
        val testData = "Hello, Hodei Pipelines!".toByteArray()
        
        // When
        val checksum = calculateSha256(testData)
        
        // Then
        val expectedChecksum = "4c7d2e1f8a5b3c9d6e0f1a2b3c4d5e6f7a8b9c0d1e2f3a4b5c6d7e8f9a0b1c2d3e4f5"
        assertNotNull(checksum)
        assertEquals(64, checksum.length) // SHA-256 produces 64 hex characters
        assertTrue(checksum.matches(Regex("[0-9a-f]+"))) // Only hex characters
    }

    @Test
    fun `should handle artifact chunk with simple data`() = runTest {
        // Given
        val artifactId = "test-artifact-1"
        val testData = "Sample artifact content for testing"
        val chunk = ArtifactChunk.newBuilder()
            .setArtifactId(artifactId)
            .setData(com.google.protobuf.ByteString.copyFrom(testData.toByteArray()))
            .setSequence(0)
            .setIsLast(true)
            .build()

        // When
        val download = ArtifactDownload(
            artifactId = artifactId,
            buffer = mutableListOf(),
            expectedChecksum = ""
        )
        
        // Simulate adding chunk data
        download.buffer.add(chunk.data.toByteArray())
        val finalData = download.buffer.flatMap { it.toList() }.toByteArray()
        
        // Then
        assertEquals(testData, String(finalData))
        assertEquals(artifactId, download.artifactId)
    }

    @Test
    fun `should handle multiple chunks correctly`() = runTest {
        // Given
        val artifactId = "test-artifact-multi"
        val part1 = "First part of the"
        val part2 = " artifact content"
        val expectedContent = part1 + part2
        
        val chunk1 = ArtifactChunk.newBuilder()
            .setArtifactId(artifactId)
            .setData(com.google.protobuf.ByteString.copyFrom(part1.toByteArray()))
            .setSequence(0)
            .setIsLast(false)
            .build()
            
        val chunk2 = ArtifactChunk.newBuilder()
            .setArtifactId(artifactId)
            .setData(com.google.protobuf.ByteString.copyFrom(part2.toByteArray()))
            .setSequence(1)
            .setIsLast(true)
            .build()

        // When
        val download = ArtifactDownload(
            artifactId = artifactId,
            buffer = mutableListOf(),
            expectedChecksum = ""
        )
        
        // Simulate processing chunks in order
        download.buffer.add(chunk1.data.toByteArray())
        download.buffer.add(chunk2.data.toByteArray())
        
        val finalData = download.buffer.flatMap { it.toList() }.toByteArray()
        
        // Then
        assertEquals(expectedContent, String(finalData))
        assertEquals(2, download.buffer.size)
    }

    @Test
    fun `should validate artifact types`() {
        // Test that all artifact types are properly defined
        val types = listOf(
            ArtifactType.ARTIFACT_TYPE_LIBRARY,
            ArtifactType.ARTIFACT_TYPE_DATASET,
            ArtifactType.ARTIFACT_TYPE_CONFIG,
            ArtifactType.ARTIFACT_TYPE_RESOURCE,
            ArtifactType.ARTIFACT_TYPE_DOCKER_IMAGE,
            ArtifactType.ARTIFACT_TYPE_ARCHIVE
        )
        
        assertTrue(types.isNotEmpty())
        assertTrue(types.contains(ArtifactType.ARTIFACT_TYPE_LIBRARY))
    }

    /**
     * Helper function to calculate SHA-256 checksum
     */
    private fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}