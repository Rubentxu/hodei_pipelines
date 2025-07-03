package dev.rubentxu.hodei.pipelines.infrastructure.application

import dev.rubentxu.hodei.pipelines.dsl.execution.PipelineEngine
import dev.rubentxu.hodei.pipelines.infrastructure.grpc.WorkerGrpcClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import mu.KotlinLogging
import kotlin.system.exitProcess

/**
 * Pipeline Worker Application
 * Standalone worker that connects to Hodei Pipelines Server
 */
object PipelineWorkerApp {
    
    private val logger = KotlinLogging.logger {}
    
    fun run(args: Array<String>) {

        logger.info { "Starting Hodei Pipeline Worker..." }
        
        try {
            // Parse command line arguments and environment variables
            val config = parseConfiguration(args)

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
            println(" :: Hodei Pipeline Worker :: (v1.0.0)")
            println(" >> Worker ID: $config.workerId")
            println(" >> Connecting to server at $config.serverHost:$config.serverPort")
            println()


            logger.info { "Worker Configuration:" }
            logger.info { "  Worker ID: ${config.workerId}" }
            logger.info { "  Worker Name: ${config.workerName}" }
            logger.info { "  Server: ${config.serverHost}:${config.serverPort}" }
            logger.info { "  Capabilities: ${config.capabilities}" }
            
            // Create and start worker
            val pipelineEngine = PipelineEngine()
            val workerClient = WorkerGrpcClient(
                workerId = config.workerId,
                serverHost = config.serverHost,
                serverPort = config.serverPort,
                pipelineEngine = pipelineEngine
            )
            
            // Connect to orchestrator and keep alive
            runBlocking {
                workerClient.connect()
                
                // Keep the worker running
                while (workerClient.isConnected()) {
                    delay(1000)
                }
                
                logger.info { "Worker disconnected, shutting down" }
                workerClient.close()
            }
            
        } catch (e: Exception) {
            logger.error(e) { "Failed to start Pipeline Worker" }
            exitProcess(1)
        }
    }
    
    private fun parseConfiguration(args: Array<String>): WorkerConfiguration {
        // Default configuration
        var workerId = System.getenv("WORKER_ID") ?: "worker-${System.currentTimeMillis()}"
        var workerName = System.getenv("WORKER_NAME") ?: "Pipeline Worker"
        var serverHost = System.getenv("SERVER_HOST") ?: "localhost"
        var serverPort = System.getenv("SERVER_PORT")?.toIntOrNull() ?: 9090
        
        // Parse command line arguments
        var i = 0
        while (i < args.size) {
            when (args[i]) {
                "--worker-id" -> {
                    if (i + 1 < args.size) {
                        workerId = args[++i]
                    } else {
                        throw IllegalArgumentException("--worker-id requires a value")
                    }
                }
                "--worker-name" -> {
                    if (i + 1 < args.size) {
                        workerName = args[++i]
                    } else {
                        throw IllegalArgumentException("--worker-name requires a value")
                    }
                }
                "--server-host" -> {
                    if (i + 1 < args.size) {
                        serverHost = args[++i]
                    } else {
                        throw IllegalArgumentException("--server-host requires a value")
                    }
                }
                "--server-port" -> {
                    if (i + 1 < args.size) {
                        serverPort = args[++i].toIntOrNull() ?: throw IllegalArgumentException("Invalid port number")
                    } else {
                        throw IllegalArgumentException("--server-port requires a value")
                    }
                }
                "--help", "-h" -> {
                    printUsage()
                    exitProcess(0)
                }
                else -> {
                    logger.warn { "Unknown argument: ${args[i]}" }
                }
            }
            i++
        }
        
        // Build capabilities map
        val capabilities = buildMap {
            put("os", System.getProperty("os.name").lowercase())
            put("arch", System.getProperty("os.arch"))
            put("javaVersion", System.getProperty("java.version"))
            put("kotlinVersion", System.getProperty("kotlin.version") ?: "unknown")
            put("maxConcurrentJobs", System.getenv("MAX_CONCURRENT_JOBS") ?: "5")
            
            // Add custom capabilities from environment
            System.getenv("WORKER_LABELS")?.let { labels ->
                put("labels", labels)
            }
            
            System.getenv("WORKER_CAPABILITIES")?.let { caps ->
                // Parse "key1=value1,key2=value2" format
                caps.split(",").forEach { pair ->
                    val (key, value) = pair.split("=", limit = 2)
                    put(key.trim(), value.trim())
                }
            }
        }
        
        return WorkerConfiguration(
            workerId = workerId,
            workerName = workerName,
            serverHost = serverHost,
            serverPort = serverPort,
            capabilities = capabilities
        )
    }
    
    private fun printUsage() {
        println("""
            Hodei Pipeline Worker
            
            Usage: worker [OPTIONS]
            
            OPTIONS:
              --worker-id <id>      Unique worker identifier (default: auto-generated)
              --worker-name <name>  Human-readable worker name (default: "Pipeline Worker")
              --server-host <host>  Hodei server hostname (default: localhost)
              --server-port <port>  Hodei server port (default: 9090)
              --help, -h           Show this help message
            
            ENVIRONMENT VARIABLES:
              WORKER_ID             Same as --worker-id
              WORKER_NAME           Same as --worker-name
              SERVER_HOST           Same as --server-host
              SERVER_PORT           Same as --server-port
              MAX_CONCURRENT_JOBS   Maximum concurrent jobs (default: 5)
              WORKER_LABELS         Comma-separated worker labels
              WORKER_CAPABILITIES   Additional capabilities (key1=value1,key2=value2)
            
            EXAMPLES:
              # Start worker with default settings
              worker
              
              # Start worker with custom name and server
              worker --worker-name "Build Worker 1" --server-host prod-server --server-port 9090
              
              # Start worker with environment variables
              WORKER_NAME="Test Worker" SERVER_HOST=staging worker
        """.trimIndent())
    }
}

/**
 * Worker configuration data class
 */
data class WorkerConfiguration(
    val workerId: String,
    val workerName: String,
    val serverHost: String,
    val serverPort: Int,
    val capabilities: Map<String, String>
)

/**
 * Main entry point for the worker application
 */
fun main(args: Array<String>) {
    PipelineWorkerApp.run(args)
}