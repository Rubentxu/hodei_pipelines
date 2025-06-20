package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.application.CreateAndExecuteJobUseCase
import dev.rubentxu.hodei.pipelines.application.RegisterWorkerUseCase
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.JobExecutorServiceImpl
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.WorkerManagementServiceImpl
import dev.rubentxu.hodei.pipelines.infrastructure.config.InMemoryConfiguration
import io.grpc.Server
import io.grpc.ServerBuilder
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import java.util.concurrent.TimeUnit

/**
 * Main Hodei Pipelines Server Application
 * Provides gRPC services for worker management and job execution
 */
class HodeiPipelinesServer(
    private val port: Int = 9090
) {
    private val logger = KotlinLogging.logger {}
    private var server: Server? = null
    
    /**
     * Start the gRPC server
     */
    fun start() {
        logger.info { "Starting Hodei Pipelines Server on port $port..." }
        
        // Create configuration and dependencies
        val config = InMemoryConfiguration()
        
        // Get use cases from configuration (they are already pre-configured)
        val registerWorkerUseCase = config.registerWorkerUseCase
        val createAndExecuteJobUseCase = config.createAndExecuteJobUseCase
        
        // Create gRPC service implementations
        val workerManagementService = WorkerManagementServiceImpl(
            registerWorkerUseCase = registerWorkerUseCase,
            workerRepository = config.getWorkerRepository()
        )
        
        val jobExecutorService = JobExecutorServiceImpl(
            createAndExecuteJobUseCase = createAndExecuteJobUseCase,
            jobExecutor = config.getJobExecutor()
        )
        
        // Build and start server
        server = ServerBuilder.forPort(port)
            .addService(workerManagementService)
            .addService(jobExecutorService)
            .build()
            .start()
        
        logger.info { "Hodei Pipelines Server started successfully on port $port" }
        logger.info { "Services available:" }
        logger.info { "  - WorkerManagementService: worker registration, heartbeat, lifecycle" }
        logger.info { "  - JobExecutorService: job execution and monitoring" }
        
        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Shutting down Hodei Pipelines Server..." }
            this@HodeiPipelinesServer.stop()
            logger.info { "Server shut down complete" }
        })
    }
    
    /**
     * Stop the server
     */
    fun stop() {
        server?.let {
            it.shutdown()
            try {
                if (!it.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn { "Server did not terminate gracefully, forcing shutdown" }
                    it.shutdownNow()
                    if (!it.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.error { "Server did not terminate after forced shutdown" }
                    }
                }
            } catch (e: InterruptedException) {
                logger.warn { "Interrupted while waiting for server termination" }
                it.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
    }
    
    /**
     * Block until the server shuts down
     */
    fun blockUntilShutdown() {
        server?.awaitTermination()
    }
    
    /**
     * Get server info
     */
    fun getServerInfo(): ServerInfo {
        return ServerInfo(
            port = port,
            isRunning = server?.isShutdown == false,
            services = listOf(
                "WorkerManagementService",
                "JobExecutorService"
            )
        )
    }
}

/**
 * Server information
 */
data class ServerInfo(
    val port: Int,
    val isRunning: Boolean,
    val services: List<String>
)

/**
 * Main entry point for the server application
 */
fun main(args: Array<String>) {
    val port = args.firstOrNull()?.toIntOrNull() ?: 
               System.getenv("SERVER_PORT")?.toIntOrNull() ?: 
               9090
    
    val server = HodeiPipelinesServer(port)
    
    try {
        server.start()
        server.blockUntilShutdown()
    } catch (e: Exception) {
        KotlinLogging.logger {}.error(e) { "Failed to start Hodei Pipelines Server" }
        System.exit(1)
    }
}