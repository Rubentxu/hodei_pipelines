package dev.rubentxu.hodei.infrastructure.grpc

import io.grpc.Server
import io.grpc.ServerBuilder
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder
import io.grpc.protobuf.services.ProtoReflectionService
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class GrpcServerManager(
    private val port: Int = 9090,
    private val orchestratorService: OrchestratorGrpcService,
    private val workerServiceAdapter: WorkerServiceAdapter? = null
) {
    private var server: Server? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun start() {
        logger.info { "Starting gRPC server on port $port" }
        
        val serverBuilder = NettyServerBuilder.forPort(port)
            .addService(orchestratorService)
            .addService(ProtoReflectionService.newInstance()) // Enable reflection for debugging
        
        // Add the new simplified WorkerService if provided
        workerServiceAdapter?.let { adapter ->
            serverBuilder.addService(adapter)
            logger.info { "Added WorkerServiceAdapter to gRPC server" }
        }
        
        serverBuilder
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(5, TimeUnit.SECONDS)
            .maxConnectionAge(300, TimeUnit.SECONDS)
            .maxConnectionAgeGrace(60, TimeUnit.SECONDS)
            .maxInboundMessageSize(4 * 1024 * 1024) // 4MB
            .maxInboundMetadataSize(8 * 1024) // 8KB
        
        server = serverBuilder.build()
        
        try {
            server?.start()
            logger.info { "gRPC server started on port $port" }
            
            // Add shutdown hook
            Runtime.getRuntime().addShutdownHook(Thread {
                logger.info { "Shutting down gRPC server due to JVM shutdown" }
                shutdown()
            })
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to start gRPC server" }
            throw e
        }
    }

    fun shutdown() {
        logger.info { "Shutting down gRPC server" }
        
        server?.let { srv ->
            try {
                // Try graceful shutdown first
                srv.shutdown()
                
                // Wait for graceful shutdown
                if (!srv.awaitTermination(30, TimeUnit.SECONDS)) {
                    logger.warn { "gRPC server did not terminate gracefully, forcing shutdown" }
                    srv.shutdownNow()
                    
                    // Wait for forced shutdown
                    if (!srv.awaitTermination(10, TimeUnit.SECONDS)) {
                        logger.error { "gRPC server did not terminate after forced shutdown" }
                    }
                }
                
                logger.info { "gRPC server shut down successfully" }
            } catch (e: InterruptedException) {
                logger.warn { "Interrupted while shutting down gRPC server" }
                srv.shutdownNow()
                Thread.currentThread().interrupt()
            }
        }
        
        // Shutdown the orchestrator service
        orchestratorService.shutdown()
        
        // Cancel the coroutine scope
        scope.cancel()
    }

    fun blockUntilShutdown() {
        server?.awaitTermination()
    }

    fun getPort(): Int {
        return server?.port ?: port
    }

    fun isRunning(): Boolean {
        return server?.isShutdown?.not() ?: false
    }
}