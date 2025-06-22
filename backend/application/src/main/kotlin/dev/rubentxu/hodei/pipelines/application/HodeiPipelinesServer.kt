package dev.rubentxu.hodei.pipelines.application

import dev.rubentxu.hodei.pipelines.infrastructure.config.InMemoryConfiguration
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.JobExecutorServiceImpl
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.WorkerManagementServiceImpl
import io.grpc.Server
import io.grpc.ServerBuilder
import mu.KotlinLogging
import java.util.concurrent.TimeUnit
import io.grpc.protobuf.services.ProtoReflectionService

/**
 * Main Hodei Pipelines Server Application
 * Provides gRPC services for worker management and job execution
 */
class HodeiPipelinesServer(
    private val port: Int = 9090
) {
    private val logger = KotlinLogging.logger {}
    private var server: Server? = null


    private fun printBanner() {
        val banner = """
888    888               888          d8b      8888888b.                     .d88888b.                    
888    888               888          Y8P      888  "Y88b                   d88P" "Y88b                   
888    888               888                   888    888                   888     888                   
8888888888  .d88b.   .d88888  .d88b.  888      888    888  .d88b.  888  888 888     888 88888b.  .d8888b  
888    888 d88""88b d88" 888 d8P  Y8b 888      888    888 d8P  Y8b 888  888 888     888 888 "88b 88K      
888    888 888  888 888  888 88888888 888      888    888 88888888 Y88  88P 888     888 888  888 "Y8888b. 
888    888 Y88..88P Y88b 888 Y8b.     888      888  .d88P Y8b.      Y8bd8P  Y88b. .d88P 888 d88P      X88 
888    888  "Y88P"   "Y88888  "Y8888  888      8888888P"   "Y8888    Y88P    "Y88888P"  88888P"   88888P' 
                                                                                        888               
                                                                                        888               
                                                                                        888               

        """.trimIndent()
        println(banner)
        println(" :: Hodei Pipelines Server :: (v1.0.0)")
        println(" >> Listening on 0.0.0.0:$port")
        println()
    }

    /**
     * Start the gRPC server
     */
    fun start() {
        printBanner()
        logger.info { "Starting Hodei Pipelines Server on port $port..." }
        logger.info { "Initializing server components and dependencies" }

        // Create configuration and dependencies
        val config = InMemoryConfiguration()
        logger.debug { "In-memory configuration initialized successfully" }

        // Get use cases from configuration (they are already pre-configured)
        val registerWorkerUseCase = config.registerWorkerUseCase
        val createAndExecuteJobUseCase = config.createAndExecuteJobUseCase
        logger.debug { "Core use cases retrieved from configuration" }

        // Create gRPC service implementations
        val workerManagementService = WorkerManagementServiceImpl(
            registerWorkerUseCase = registerWorkerUseCase,
            workerRepository = config.getWorkerRepository()
        )
        logger.debug { "Worker Management Service initialized" }

        val jobExecutorService = JobExecutorServiceImpl(
            createAndExecuteJobUseCase = createAndExecuteJobUseCase,
            jobExecutor = config.getJobExecutor()
        )
        logger.debug { "Job Executor Service initialized" }

        // Build and start server
        logger.info { "Building gRPC server with configured services" }
        server = ServerBuilder.forPort(port)
            .addService(workerManagementService)
            .addService(jobExecutorService)
            .addService(ProtoReflectionService.newInstance())
            .build()
            .start()

        logger.info { "Hodei Pipelines Server started successfully on port $port" }
        logger.info { "Server services initialized and ready to accept connections" }
        logger.info { "Available Services:" }
        logger.info { "  - WorkerManagementService: worker registration, heartbeat, lifecycle" }
        logger.info { "  - JobExecutorService: job execution and monitoring" }
        logger.info { "  - Reflection Service: available for gRPC client discovery" }

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(Thread {
            logger.info { "Shutdown signal received - initiating graceful server shutdown" }
            this@HodeiPipelinesServer.stop()
            logger.info { "Server shutdown process completed" }
        })
    }

    /**
     * Stop the server
     */
    fun stop() {
        server?.let {
            logger.info { "Stopping gRPC server - initiating graceful shutdown" }
            it.shutdown()
            try {
                logger.debug { "Waiting for server termination (timeout: 10 seconds)" }
                if (!it.awaitTermination(10, TimeUnit.SECONDS)) {
                    logger.warn { "Server did not terminate within timeout period, forcing shutdown" }
                    it.shutdownNow()
                    logger.debug { "Forced shutdown initiated, waiting additional 5 seconds" }
                    if (!it.awaitTermination(5, TimeUnit.SECONDS)) {
                        logger.error { "Server did not terminate even after forced shutdown" }
                    }
                } else {
                    logger.info { "Server terminated gracefully" }
                }
            } catch (e: InterruptedException) {
                logger.warn { "Server shutdown was interrupted, forcing immediate shutdown" }
                it.shutdownNow()
                Thread.currentThread().interrupt()
            }
        } ?: logger.warn { "Stop called but server was not running" }
    }

    /**
     * Block until the server shuts down
     */
    fun blockUntilShutdown() {
        logger.debug { "Blocking main thread until server shuts down" }
        server?.awaitTermination()
    }

    /**
     * Get server info
     */
    fun getServerInfo(): ServerInfo {
        val isRunning = server?.isShutdown == false
        logger.debug { "Server info requested - running status: $isRunning, port: $port" }
        return ServerInfo(
            port = port,
            isRunning = isRunning,
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
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Hodei Pipelines Server application" }
    val port = args.firstOrNull()?.toIntOrNull() ?: System.getenv("SERVER_PORT")?.toIntOrNull() ?: 9090
    logger.info { "Server configured to use port: $port" }

    val server = HodeiPipelinesServer(port)

    try {
        logger.info { "Initializing server..." }
        server.start()
        logger.info { "Server started successfully, waiting for connections" }
        server.blockUntilShutdown()
    } catch (e: Exception) {
        logger.error(e) { "Critical error: Failed to start Hodei Pipelines Server: ${e.message}" }
        System.exit(1)
    }
}