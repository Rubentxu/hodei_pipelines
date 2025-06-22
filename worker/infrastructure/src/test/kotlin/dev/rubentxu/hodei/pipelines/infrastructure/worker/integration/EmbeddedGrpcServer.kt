package dev.rubentxu.hodei.pipelines.infrastructure.worker.integration

import dev.rubentxu.hodei.pipelines.proto.ArtifactType
import dev.rubentxu.hodei.pipelines.proto.CompressionType
import io.grpc.Server
import io.grpc.ServerBuilder
import mu.KotlinLogging
import java.io.Closeable
import java.util.concurrent.TimeUnit

/**
 * Embedded gRPC server for integration testing
 * Provides a complete test environment for worker-server communication
 */
class EmbeddedGrpcServer : Closeable {
    
    private val logger = KotlinLogging.logger {}
    private var server: Server? = null
    
    // Mock services
    val jobExecutorService = MockJobExecutorService()
    val workerManagementService = MockWorkerManagementService()
    
    // Server configuration
    var port: Int = 0
        private set
    
    fun start(port: Int = 0): EmbeddedGrpcServer {
        val serverBuilder = ServerBuilder.forPort(port)
            .addService(jobExecutorService)
            .addService(workerManagementService)
        
        server = serverBuilder.build()
        server!!.start()
        this.port = server!!.port
        
        logger.info { "Embedded gRPC server started on port ${this.port}" }
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Shutting down gRPC server due to JVM shutdown" }
            stop()
        })
        
        return this
    }
    
    fun stop() {
        server?.let { srv ->
            logger.info { "Stopping embedded gRPC server..." }
            srv.shutdown()
            try {
                if (!srv.awaitTermination(5, TimeUnit.SECONDS)) {
                    srv.shutdownNow()
                    if (!srv.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.warn { "gRPC server did not terminate cleanly" }
                    }
                }
            } catch (e: InterruptedException) {
                srv.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        server = null
    }
    
    override fun close() {
        stop()
    }
    
    fun isRunning(): Boolean = server?.isShutdown == false
    
    fun awaitTermination() {
        server?.awaitTermination()
    }
    
    // Test utilities
    fun addTestJob(job: TestJob) {
        jobExecutorService.addJob(job)
    }
    
    fun configureWorkerManagement(
        registrationShouldFail: Boolean = false,
        failureMessage: String = "Registration failed for testing",
        heartbeatInterval: Int = 10
    ) {
        workerManagementService.registrationShouldFail = registrationShouldFail
        workerManagementService.registrationFailureMessage = failureMessage
        workerManagementService.heartbeatInterval = heartbeatInterval
    }
    
    fun configureJobExecution(
        simulateArtifactTransfer: Boolean = true,
        simulateCacheQueries: Boolean = true,
        simulateErrors: Boolean = false
    ) {
        jobExecutorService.simulateArtifactTransfer = simulateArtifactTransfer
        jobExecutorService.simulateCacheQueries = simulateCacheQueries
        jobExecutorService.simulateErrors = simulateErrors
    }
    
    fun clearTestData() {
        jobExecutorService.clearHistory()
        workerManagementService.clearRegistrations()
    }
    
    fun waitForWorkerConnection(workerId: String, timeoutMs: Long = 5000): Boolean {
        return workerManagementService.waitForWorkerRegistration(workerId, timeoutMs) &&
               jobExecutorService.waitForWorkerConnection(workerId, timeoutMs)
    }
    
    fun getTestMetrics(): TestMetrics {
        return TestMetrics(
            registeredWorkers = workerManagementService.getRegisteredWorkers().size,
            connectedWorkers = jobExecutorService.getConnectedWorkers().size,
            receivedMessages = jobExecutorService.getReceivedMessages().size,
            jobsInQueue = jobExecutorService.jobsToSend.size
        )
    }
}

/**
 * Test metrics aggregation
 */
data class TestMetrics(
    val registeredWorkers: Int,
    val connectedWorkers: Int,
    val receivedMessages: Int,
    val jobsInQueue: Int
)

/**
 * Builder for test artifacts with common patterns
 */
object TestArtifactBuilder {
    
    fun createSimpleArtifact(
        id: String = "test-artifact-${System.currentTimeMillis()}",
        name: String = "test.txt",
        content: String = "Test artifact content"
    ): TestArtifact {
        val data = content.toByteArray()
        return TestArtifact(
            id = id,
            name = name,
            type = ArtifactType.ARTIFACT_TYPE_CONFIG,
            data = data,
            checksum = calculateSha256(data),
            path = "/tmp/$name"
        )
    }
    
    fun createCompressedArtifact(
        id: String = "compressed-artifact-${System.currentTimeMillis()}",
        name: String = "config.yaml",
        content: String = "# Configuration file\n".repeat(100) // Repetitive content for compression
    ): TestArtifact {
        val originalData = content.toByteArray()
        val compressedData = compressWithGzip(originalData)
        
        return TestArtifact(
            id = id,
            name = name,
            type = ArtifactType.ARTIFACT_TYPE_CONFIG,
            data = compressedData,
            checksum = calculateSha256(originalData), // Checksum of original data
            path = "/tmp/$name",
            compression = CompressionType.COMPRESSION_GZIP,
            originalSize = originalData.size
        )
    }
    
    fun createLargeArtifact(
        id: String = "large-artifact-${System.currentTimeMillis()}",
        name: String = "large-data.bin",
        sizeKB: Int = 100
    ): TestArtifact {
        val data = ByteArray(sizeKB * 1024) { (it % 256).toByte() }
        return TestArtifact(
            id = id,
            name = name,
            type = ArtifactType.ARTIFACT_TYPE_DATASET,
            data = data,
            checksum = calculateSha256(data),
            path = "/tmp/$name"
        )
    }
    
    private fun calculateSha256(data: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(data)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
    
    private fun compressWithGzip(data: ByteArray): ByteArray {
        val outputStream = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(outputStream).use { gzipStream ->
            gzipStream.write(data)
        }
        return outputStream.toByteArray()
    }
}

/**
 * Builder for test jobs with common patterns
 */
object TestJobBuilder {
    
    fun createSimpleJob(
        id: String = "test-job-${System.currentTimeMillis()}",
        name: String = "Test Job",
        script: String = "echo 'Hello from test job'"
    ): TestJob {
        return TestJob(
            id = id,
            name = name,
            script = script,
            artifacts = emptyList()
        )
    }
    
    fun createJobWithArtifacts(
        id: String = "job-with-artifacts-${System.currentTimeMillis()}",
        name: String = "Job with Artifacts",
        script: String = "echo 'Processing artifacts...'; ls -la /tmp/",
        artifacts: List<TestArtifact>
    ): TestJob {
        return TestJob(
            id = id,
            name = name,
            script = script,
            artifacts = artifacts
        )
    }
    
    fun createComplexJob(
        id: String = "complex-job-${System.currentTimeMillis()}",
        name: String = "Complex Test Job"
    ): TestJob {
        val artifacts = listOf(
            TestArtifactBuilder.createSimpleArtifact("config-1", "app.properties"),
            TestArtifactBuilder.createCompressedArtifact("config-2", "logging.yaml"),
            TestArtifactBuilder.createLargeArtifact("data-1", "dataset.csv", 50)
        )
        
        val script = """
            echo "Starting complex job execution..."
            echo "Checking artifacts:"
            ls -la /tmp/
            echo "Processing configuration..."
            cat /tmp/app.properties
            echo "Job completed successfully"
        """.trimIndent()
        
        return TestJob(
            id = id,
            name = name,
            script = script,
            artifacts = artifacts
        )
    }
}