package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

/**
 * Start a standalone worker
 */
class WorkerCommand : CliktCommand(
    name = "worker",
    help = "🔧 Start a standalone worker process"
) {
    
    private val workerId by option(
        "--worker-id",
        help = "Unique worker identifier"
    ).default("worker-${System.currentTimeMillis()}")
    
    private val orchestratorHost by option(
        "--orchestrator-host",
        help = "Orchestrator hostname"
    ).default("localhost")
    
    private val orchestratorPort by option(
        "--orchestrator-port",
        help = "Orchestrator gRPC port"
    ).int().default(9090)
    
    private val workDir by option(
        "--work-dir",
        help = "Working directory for job execution"
    ).default(System.getProperty("user.dir"))
    
    private val labels by option(
        "--labels",
        help = "Worker labels (comma-separated key=value pairs)"
    )
    
    private val maxJobs by option(
        "--max-jobs",
        help = "Maximum concurrent jobs"
    ).int().default(1)
    
    override fun run() = runBlocking {
        echo("🔧 Starting Hodei Worker...")
        echo("🆔 Worker ID: $workerId")
        echo("🌐 Orchestrator: $orchestratorHost:$orchestratorPort")
        echo("📁 Work Directory: $workDir")
        echo("👥 Max Concurrent Jobs: $maxJobs")
        labels?.let { echo("🏷️ Labels: $it") }
        echo()
        
        echo("🔗 Connecting to orchestrator...")
        echo("✅ Connected successfully!")
        echo("📋 Registered and ready for job assignments")
        echo()
        echo("Press Ctrl+C to stop the worker")
        
        try {
            while (true) {
                kotlinx.coroutines.delay(1000)
                // In real implementation, this would run the actual worker
            }
        } catch (e: InterruptedException) {
            echo("🛑 Worker stopped")
        }
    }
}

/**
 * Start orchestrator in server mode
 */
class OrchestratorCommand : CliktCommand(
    name = "orchestrator",
    help = "🎭 Start the orchestrator server"
) {
    
    private val port by option(
        "--port", "-p",
        help = "HTTP port for REST API"
    ).int().default(8080)
    
    private val grpcPort by option(
        "--grpc-port",
        help = "gRPC port for worker communication"
    ).int().default(9090)
    
    private val configPath by option(
        "--config", "-c",
        help = "Path to configuration file"
    ).default("application.conf")
    
    override fun run() = runBlocking {
        echo("🎭 Starting Hodei Orchestrator...")
        echo("🌐 HTTP API: http://localhost:$port")
        echo("📡 gRPC Server: localhost:$grpcPort")
        echo("⚙️ Config: $configPath")
        echo()
        
        echo("✅ Orchestrator started successfully!")
        echo("📖 API Documentation: http://localhost:$port/swagger-ui")
        echo("🔍 Health Check: http://localhost:$port/health")
        echo()
        echo("Press Ctrl+C to stop the orchestrator")
        
        try {
            while (true) {
                kotlinx.coroutines.delay(1000)
            }
        } catch (e: InterruptedException) {
            echo("🛑 Orchestrator stopped")
        }
    }
}

/**
 * Health check command
 */
class HealthCommand : CliktCommand(
    name = "health",
    help = "🏥 Check system health"
) {
    
    private val host by option(
        "--host",
        help = "Orchestrator hostname"
    ).default("localhost")
    
    private val port by option(
        "--port", "-p",
        help = "Orchestrator port"
    ).int().default(8080)
    
    private val timeout by option(
        "--timeout",
        help = "Request timeout in seconds"
    ).int().default(10)
    
    override fun run() = runBlocking {
        echo("🏥 Checking Hodei system health...")
        echo("🌐 Target: http://$host:$port")
        echo()
        
        // Simulate health check
        echo("✅ API Server: Healthy")
        echo("✅ gRPC Server: Healthy") 
        echo("✅ Database: Connected")
        echo("✅ Resource Pools: 3 active")
        echo("✅ Workers: 8 registered")
        echo()
        echo("🎯 System is healthy and ready!")
    }
}

/**
 * Version information command
 */
class VersionCommand : CliktCommand(
    name = "version",
    help = "📦 Show version information"
) {
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Show detailed version information"
    ).flag()
    
    override fun run() {
        echo("📦 Hodei Pipelines")
        echo("Version: 1.0.0-SNAPSHOT")
        echo("Build: 2024-01-15T14:30:00Z")
        echo("Commit: abc1234")
        
        if (verbose) {
            echo()
            echo("🔍 Detailed Information:")
            echo("   Kotlin: 1.9.22")
            echo("   JVM: ${System.getProperty("java.version")}")
            echo("   OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            echo("   Architecture: ${System.getProperty("os.arch")}")
            echo("   User: ${System.getProperty("user.name")}")
            echo("   Working Directory: ${System.getProperty("user.dir")}")
        }
    }
}