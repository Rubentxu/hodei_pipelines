package dev.rubentxu.hodei.pipelines.infrastructure.grpc

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

/**
 * Tests for cache optimization and transfer metrics
 */
class CacheOptimizationTest {

    @Test
    fun `should track cache query metrics correctly`() = runTest {
        // Given
        val tracker = TransferMetricsTracker()
        
        // When
        tracker.recordCacheQuery("job-1", 5, 3) // 60% hit rate
        tracker.recordCacheQuery("job-2", 3, 3) // 100% hit rate
        tracker.recordCacheQuery("job-3", 4, 1) // 25% hit rate
        
        // Then
        val metrics = tracker.getMetrics()
        assertEquals(3, metrics.totalCacheQueries)
        assertEquals(7, metrics.totalCacheHits) // 3 + 3 + 1
        assertEquals(58.33, metrics.averageCacheHitRate, 0.1) // 7/12 * 100
    }

    @Test
    fun `should track artifact transfer metrics correctly`() = runTest {
        // Given
        val tracker = TransferMetricsTracker()
        
        // When
        tracker.recordArtifactTransfer("artifact-1", 1024)
        tracker.recordArtifactTransfer("artifact-2", 2048)
        tracker.recordArtifactTransfer("artifact-3", 512)
        
        // Then
        val metrics = tracker.getMetrics()
        assertEquals(3, metrics.totalArtifactsTransferred)
        assertEquals(3584, metrics.totalBytesTransferred) // 1024 + 2048 + 512
    }

    @Test
    fun `should track transfer session metrics correctly`() = runTest {
        // Given
        val tracker = TransferMetricsTracker()
        val startTime = java.time.Instant.now()
        
        // When
        Thread.sleep(10) // Small delay to ensure different timestamps
        tracker.recordTransferSession("job-1", startTime, 3)
        Thread.sleep(10)
        tracker.recordTransferSession("job-2", startTime, 2)
        
        // Then
        val metrics = tracker.getMetrics()
        assertEquals(2, metrics.transferSessions)
        assertTrue(metrics.averageTransferTimeMs > 0) // Should have some transfer time
    }

    @Test
    fun `should handle empty metrics correctly`() = runTest {
        // Given
        val tracker = TransferMetricsTracker()
        
        // When
        val metrics = tracker.getMetrics()
        
        // Then
        assertEquals(0, metrics.totalCacheQueries)
        assertEquals(0, metrics.totalCacheHits)
        assertEquals(0.0, metrics.averageCacheHitRate)
        assertEquals(0, metrics.totalArtifactsTransferred)
        assertEquals(0, metrics.totalBytesTransferred)
        assertEquals(0.0, metrics.averageTransferTimeMs)
        assertEquals(0, metrics.transferSessions)
    }

    @Test
    fun `should calculate cache hit rate with zero artifacts queried`() = runTest {
        // Given
        val tracker = TransferMetricsTracker()
        
        // When - Record cache queries with zero total artifacts (edge case)
        tracker.recordCacheQuery("job-empty", 0, 0)
        
        // Then
        val metrics = tracker.getMetrics()
        assertEquals(0.0, metrics.averageCacheHitRate) // Should not divide by zero
    }

    @Test
    fun `should validate transfer metrics data classes`() {
        // Test CacheQueryMetric
        val cacheMetric = CacheQueryMetric(
            jobId = "test-job",
            totalArtifacts = 5,
            cacheHits = 3,
            timestamp = java.time.Instant.now()
        )
        assertEquals("test-job", cacheMetric.jobId)
        assertEquals(5, cacheMetric.totalArtifacts)
        assertEquals(3, cacheMetric.cacheHits)

        // Test ArtifactTransferMetric
        val transferMetric = ArtifactTransferMetric(
            artifactId = "test-artifact",
            sizeBytes = 1024,
            timestamp = java.time.Instant.now()
        )
        assertEquals("test-artifact", transferMetric.artifactId)
        assertEquals(1024, transferMetric.sizeBytes)

        // Test TransferSessionMetric
        val now = java.time.Instant.now()
        val sessionMetric = TransferSessionMetric(
            jobId = "test-job",
            startTime = now,
            endTime = now.plusSeconds(1),
            artifactCount = 3
        )
        assertEquals("test-job", sessionMetric.jobId)
        assertEquals(3, sessionMetric.artifactCount)
        assertTrue(sessionMetric.endTime.isAfter(sessionMetric.startTime))
    }

    @Test
    fun `should create PendingJob correctly`() {
        // Test that PendingJob data class works as expected
        val mockChannel = object : kotlinx.coroutines.flow.FlowCollector<dev.rubentxu.hodei.pipelines.proto.ServerToWorker> {
            override suspend fun emit(value: dev.rubentxu.hodei.pipelines.proto.ServerToWorker) {
                // Mock implementation
            }
        }
        
        val jobRequest = dev.rubentxu.hodei.pipelines.proto.ExecuteJobRequest.newBuilder()
            .setJobDefinition(
                dev.rubentxu.hodei.pipelines.proto.JobDefinition.newBuilder()
                    .setId(dev.rubentxu.hodei.pipelines.proto.JobIdentifier.newBuilder().setValue("test-job").build())
                    .setName("Test Job")
                    .build()
            )
            .build()
        
        val pendingJob = PendingJob(
            jobRequest = jobRequest,
            workerId = "worker-1",
            channel = mockChannel,
            requestTime = java.time.Instant.now()
        )
        
        assertEquals("worker-1", pendingJob.workerId)
        assertEquals("test-job", pendingJob.jobRequest.jobDefinition.id.value)
        assertEquals("Test Job", pendingJob.jobRequest.jobDefinition.name)
    }
}