package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import dev.rubentxu.hodei.resourcemanagement.infrastructure.docker.DockerEnvironmentBootstrap
import dev.rubentxu.hodei.resourcemanagement.infrastructure.docker.DockerConfig
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

private val logger = KotlinLogging.logger {}

/**
 * Docker discovery command for MVP implementation
 * 
 * Auto-discovers local Docker environment and registers it as a resource pool
 * This is the main MVP feature that enables easy Docker worker setup
 */
class DockerDiscoverCommand : CliktCommand(
    name = "discover",
    help = "🔍 Auto-discover and register local Docker environment"
) {
    
    private val dockerHost by option(
        "--docker-host",
        help = "Docker daemon host (default: unix:///var/run/docker.sock)"
    ).default("unix:///var/run/docker.sock")
    
    private val poolName by option(
        "--pool-name",
        help = "Name for the discovered resource pool"
    ).default("local-docker")
    
    private val orchestratorHost by option(
        "--orchestrator-host",
        help = "Orchestrator hostname for worker connection"
    ).default("localhost")
    
    private val orchestratorPort by option(
        "--orchestrator-port",
        help = "Orchestrator gRPC port for worker connection"
    ).int().default(9090)
    
    private val autoRegister by option(
        "--auto-register",
        help = "Automatically register discovered environment as resource pool"
    ).flag(default = true)
    
    private val maxWorkers by option(
        "--max-workers",
        help = "Maximum number of concurrent workers (0 = auto-detect)"
    ).int().default(0)
    
    private val dryRun by option(
        "--dry-run",
        help = "Show what would be discovered without making changes"
    ).flag()
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    override fun run() = runBlocking {
        try {
            echo("🔍 Discovering Docker environment...")
            if (dryRun) {
                echo("🧪 DRY RUN MODE - No changes will be made")
            }
            echo()
            
            // Configure Docker client
            val dockerConfig = DockerConfig(
                dockerHost = dockerHost,
                orchestratorHost = orchestratorHost,
                orchestratorPort = orchestratorPort
            )
            
            // Initialize Docker environment bootstrap
            val bootstrap = DockerEnvironmentBootstrap(dockerConfig)
            
            echo("🐳 Connecting to Docker daemon at: $dockerHost")
            
            // Check Docker availability
            val isAvailable = bootstrap.isDockerAvailable()
            if (!isAvailable) {
                echo("❌ Docker daemon is not available or accessible")
                echo("💡 Make sure Docker is running and accessible at: $dockerHost")
                System.exit(1)
            }
            
            echo("✅ Docker daemon is accessible")
            
            // Get Docker info
            val dockerInfo = bootstrap.getDockerEnvironmentInfo()
            if (dockerInfo.isFailure) {
                echo("❌ Failed to get Docker environment info: ${dockerInfo.exceptionOrNull()?.message}")
                System.exit(1)
            }
            
            val info = dockerInfo.getOrThrow()
            echo("📊 Docker Environment Information:")
            echo("   Version: ${info.version}")
            echo("   API Version: ${info.apiVersion}")
            echo("   Total Memory: ${info.totalMemory / (1024 * 1024)} MB")
            echo("   CPU Cores: ${info.cpuCount}")
            echo("   Running Containers: ${info.containersRunning}")
            echo("   Available Images: ${info.imagesCount}")
            
            // Calculate optimal worker configuration
            val capacity = bootstrap.calculateOptimalConfiguration()
            echo()
            echo("🧮 Optimal Configuration:")
            echo("   Max Concurrent Workers: ${capacity.maxWorkers}")
            echo("   Memory per Worker: ${capacity.memoryPerWorkerMB} MB")
            echo("   CPU per Worker: ${capacity.cpuPerWorker} cores")
            
            // Validate Docker compatibility
            val compatibility = bootstrap.validateDockerCompatibility()
            if (compatibility.isFailure) {
                echo("⚠️ Docker compatibility issues detected:")
                echo("   ${compatibility.exceptionOrNull()?.message}")
                echo("💡 The system may work but with reduced functionality")
            } else {
                echo("✅ Docker environment is fully compatible")
            }
            
            echo()
            
            if (dryRun) {
                echo("🧪 DRY RUN: Would create resource pool with the following configuration:")
                showResourcePoolConfig(info, capacity)
                return@runBlocking
            }
            
            if (autoRegister) {
                echo("📝 Auto-registering Docker environment as resource pool...")
                
                val poolId = DomainId.generate()
                val registrationResult = bootstrap.registerAsResourcePool(
                    poolId = poolId,
                    poolName = poolName,
                    maxWorkers = if (maxWorkers > 0) maxWorkers else capacity.maxWorkers
                )
                
                if (registrationResult.isSuccess) {
                    val pool = registrationResult.getOrThrow()
                    echo("✅ Successfully registered Docker environment!")
                    echo("🆔 Resource Pool ID: ${pool.id.value}")
                    echo("📝 Pool Name: ${pool.name}")
                    echo("📊 Max Workers: ${pool.policies.scaling.maxWorkers}")
                    echo("🌐 Status: ${pool.status}")
                    
                    echo()
                    echo("🚀 Next Steps:")
                    echo("1. Start the orchestrator server:")
                    echo("   hodei server start")
                    echo()
                    echo("2. Launch Docker workers:")
                    echo("   hodei docker worker start --pool-id ${pool.id.value} --workers 2")
                    echo()
                    echo("3. Submit jobs via API or CLI:")
                    echo("   hodei job submit --template my-template --pool-id ${pool.id.value}")
                    
                } else {
                    echo("❌ Failed to register resource pool: ${registrationResult.exceptionOrNull()?.message}")
                    System.exit(1)
                }
            } else {
                echo("ℹ️ Auto-registration disabled. Resource pool configuration:")
                showResourcePoolConfig(info, capacity)
                echo()
                echo("💡 To register manually, use:")
                echo("   hodei pool create --name $poolName --type docker --max-workers ${capacity.maxWorkers}")
            }
            
        } catch (e: Exception) {
            echo("💥 Discovery failed: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }
    
    private fun showResourcePoolConfig(
        info: DockerEnvironmentBootstrap.DockerEnvironmentInfo,
        capacity: DockerEnvironmentBootstrap.OptimalConfiguration
    ) {
        echo("   Pool Name: $poolName")
        echo("   Type: Docker")
        echo("   Docker Host: $dockerHost")
        echo("   Max Workers: ${capacity.maxWorkers}")
        echo("   Worker Template: hodei/worker:latest")
        echo("   Resource Limits:")
        echo("     - Memory: ${capacity.memoryPerWorkerMB} MB per worker")
        echo("     - CPU: ${capacity.cpuPerWorker} cores per worker")
        echo("   Connection:")
        echo("     - Orchestrator: $orchestratorHost:$orchestratorPort")
    }
}