package dev.rubentxu.hodei.worker

import dev.rubentxu.hodei.worker.client.WorkerGrpcClient
import dev.rubentxu.hodei.worker.execution.ShellExecutor
import dev.rubentxu.hodei.worker.execution.KotlinScriptExecutor
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

/**
 * Main entry point for the Hodei Pipeline Worker
 */
fun main(args: Array<String>) {
    logger.info { "Starting Hodei Pipeline Worker..." }
    
    val config = parseWorkerConfig(args)
    logger.info { "Worker configuration: $config" }
    
    runBlocking {
        val worker = HodeiWorker(config)
        try {
            worker.start()
            worker.awaitTermination()
        } catch (e: Exception) {
            logger.error(e) { "Worker failed with error" }
            exitProcess(1)
        } finally {
            worker.shutdown()
        }
    }
}

/**
 * Parse command line arguments into worker configuration
 */
private fun parseWorkerConfig(args: Array<String>): WorkerConfig {
    var workerId: String? = null
    var orchestratorHost = "localhost"
    var orchestratorPort = 9090
    var workDir = System.getProperty("user.dir")
    
    var i = 0
    while (i < args.size) {
        when (args[i]) {
            "--worker-id" -> {
                workerId = args.getOrNull(++i) ?: throw IllegalArgumentException("Missing worker-id value")
            }
            "--orchestrator-host" -> {
                orchestratorHost = args.getOrNull(++i) ?: throw IllegalArgumentException("Missing orchestrator-host value")
            }
            "--orchestrator-port" -> {
                orchestratorPort = args.getOrNull(++i)?.toIntOrNull() ?: throw IllegalArgumentException("Invalid orchestrator-port value")
            }
            "--work-dir" -> {
                workDir = args.getOrNull(++i) ?: throw IllegalArgumentException("Missing work-dir value")
            }
            "--help", "-h" -> {
                printUsage()
                exitProcess(0)
            }
            else -> {
                throw IllegalArgumentException("Unknown argument: ${args[i]}")
            }
        }
        i++
    }
    
    return WorkerConfig(
        workerId = workerId ?: generateWorkerId(),
        orchestratorHost = orchestratorHost,
        orchestratorPort = orchestratorPort,
        workDir = workDir
    )
}

private fun generateWorkerId(): String {
    val hostname = try {
        java.net.InetAddress.getLocalHost().hostName
    } catch (e: Exception) {
        "unknown"
    }
    
    val timestamp = System.currentTimeMillis()
    return "worker-$hostname-$timestamp"
}

private fun printUsage() {
    println("""
        Hodei Pipeline Worker
        
        Usage: worker [options]
        
        Options:
          --worker-id <id>           Unique identifier for this worker (auto-generated if not provided)
          --orchestrator-host <host> Orchestrator host (default: localhost)
          --orchestrator-port <port> Orchestrator port (default: 9090)
          --work-dir <path>          Working directory for job execution (default: current directory)
          --help, -h                 Show this help message
        
        Examples:
          worker --worker-id worker-001 --orchestrator-host 192.168.1.100
          worker --work-dir /tmp/worker-workspace
    """.trimIndent())
}

/**
 * Worker configuration
 */
data class WorkerConfig(
    val workerId: String,
    val orchestratorHost: String,
    val orchestratorPort: Int,
    val workDir: String
)

/**
 * Main Worker implementation
 */
class HodeiWorker(private val config: WorkerConfig) {
    
    private lateinit var grpcClient: WorkerGrpcClient
    private lateinit var shellExecutor: ShellExecutor
    private lateinit var kotlinScriptExecutor: KotlinScriptExecutor
    
    suspend fun start() {
        logger.info { "Initializing worker ${config.workerId}..." }
        
        // Initialize executors
        shellExecutor = ShellExecutor(config.workDir)
        kotlinScriptExecutor = KotlinScriptExecutor(config.workDir)
        
        // Initialize and connect gRPC client
        grpcClient = WorkerGrpcClient(
            workerId = config.workerId,
            orchestratorHost = config.orchestratorHost,
            orchestratorPort = config.orchestratorPort,
            shellExecutor = shellExecutor,
            kotlinScriptExecutor = kotlinScriptExecutor
        )
        
        logger.info { "Connecting to orchestrator at ${config.orchestratorHost}:${config.orchestratorPort}..." }
        grpcClient.connect()
        
        logger.info { "Worker ${config.workerId} started and ready for jobs" }
    }
    
    suspend fun awaitTermination() {
        grpcClient.awaitTermination()
    }
    
    suspend fun shutdown() {
        logger.info { "Shutting down worker ${config.workerId}..." }
        grpcClient.shutdown()
        shellExecutor.cleanup()
        kotlinScriptExecutor.cleanup()
        logger.info { "Worker shutdown complete" }
    }
}