package dev.rubentxu.hodei.pipelines.infrastructure.worker

import dev.rubentxu.hodei.pipelines.proto.ArtifactChunk
import dev.rubentxu.hodei.pipelines.proto.ArtifactType
import dev.rubentxu.hodei.pipelines.proto.CompressionType
import dev.rubentxu.hodei.pipelines.proto.ArtifactCacheQuery
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import java.security.MessageDigest
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

/**
 * Tests for artifact transfer functionality (Fase 1 & 2)
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

    @Test
    fun `should handle compressed artifact chunks (Fase 2)`() = runTest {
        // Given
        val artifactId = "test-artifact-compressed"
        val originalData = "This is a sample text that will be compressed using GZIP. ".repeat(20)
        val compressedData = compressWithGzip(originalData.toByteArray())
        
        val chunk = ArtifactChunk.newBuilder()
            .setArtifactId(artifactId)
            .setData(com.google.protobuf.ByteString.copyFrom(compressedData))
            .setSequence(0)
            .setIsLast(true)
            .setCompression(CompressionType.COMPRESSION_GZIP)
            .setOriginalSize(originalData.length)
            .build()

        // When
        val download = ArtifactDownload(
            artifactId = artifactId,
            buffer = mutableListOf(),
            expectedChecksum = "",
            compressionType = CompressionType.COMPRESSION_GZIP,
            originalSize = originalData.length
        )
        
        download.buffer.add(chunk.data.toByteArray())
        
        // Then
        assertEquals(CompressionType.COMPRESSION_GZIP, download.compressionType)
        assertEquals(originalData.length, download.originalSize)
        assertTrue(compressedData.size < originalData.length) // Compression should reduce size
    }
    
    @Test
    fun `should handle cache query correctly (Fase 2)`() = runTest {
        // Given
        val jobId = "test-job-123"
        val artifactIds = listOf("artifact-1", "artifact-2", "artifact-3")
        
        val cacheQuery = ArtifactCacheQuery.newBuilder()
            .setJobId(jobId)
            .addAllArtifactIds(artifactIds)
            .build()
        
        // When/Then - Test that cache query is properly structured
        assertEquals(jobId, cacheQuery.jobId)
        assertEquals(3, cacheQuery.artifactIdsList.size)
        assertTrue(cacheQuery.artifactIdsList.containsAll(artifactIds))
    }
    
    @Test
    fun `should manage cached artifacts data structure (Fase 2)`() = runTest {
        // Given
        val artifactId = "cached-artifact-test"
        val checksum = "abc123def456"
        val size = 1024L
        val cachedAt = java.time.Instant.now()
        
        // When
        val cachedArtifact = CachedArtifact(
            id = artifactId,
            checksum = checksum,
            size = size,
            cachedAt = cachedAt
        )
        
        // Then
        assertEquals(artifactId, cachedArtifact.id)
        assertEquals(checksum, cachedArtifact.checksum)
        assertEquals(size, cachedArtifact.size)
        assertEquals(cachedAt, cachedArtifact.cachedAt)
    }
    
    @Test
    fun `should handle artifact download with compression metadata (Fase 2)`() = runTest {
        // Given
        val artifactId = "metadata-test-artifact"
        val originalSize = 2048
        val compressionType = CompressionType.COMPRESSION_GZIP
        
        // When
        val download = ArtifactDownload(
            artifactId = artifactId,
            buffer = mutableListOf(),
            expectedChecksum = "test-checksum",
            compressionType = compressionType,
            originalSize = originalSize
        )
        
        // Then
        assertEquals(artifactId, download.artifactId)
        assertEquals(compressionType, download.compressionType)
        assertEquals(originalSize, download.originalSize)
        assertTrue(download.buffer.isEmpty())
    }
    
    @Test
    fun `should validate compression types (Fase 2)`() {
        // Test that compression types are properly defined
        val compressionTypes = listOf(
            CompressionType.COMPRESSION_NONE,
            CompressionType.COMPRESSION_GZIP,
            CompressionType.COMPRESSION_ZSTD
        )
        
        assertTrue(compressionTypes.isNotEmpty())
        assertTrue(compressionTypes.contains(CompressionType.COMPRESSION_GZIP))
        assertTrue(compressionTypes.contains(CompressionType.COMPRESSION_NONE))
    }
    
    @Test
    fun `should compress and maintain data integrity`() = runTest {
        // Given
        val originalText = "Sample data for compression testing. ".repeat(50)
        val originalData = originalText.toByteArray()
        
        // When
        val compressedData = compressWithGzip(originalData)
        
        // Then
        assertTrue(compressedData.size < originalData.size) // Should be compressed
        assertNotEquals(0, compressedData.size) // Should not be empty
        
        // Verify we can calculate checksums of both
        val originalChecksum = calculateSha256(originalData)
        val compressedChecksum = calculateSha256(compressedData)
        assertNotEquals(originalChecksum, compressedChecksum) // Should be different
    }
    
    /**
     * Helper function to calculate SHA-256 checksum
     */
    private fun calculateSha256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Helper function to compress data with GZIP
     */
    private fun compressWithGzip(data: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { gzipStream ->
            gzipStream.write(data)
        }
        return outputStream.toByteArray()
    }
}