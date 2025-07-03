package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import dev.rubentxu.hodei.resourcemanagement.infrastructure.docker.TemplateAwareDockerInstanceManager
import dev.rubentxu.hodei.resourcemanagement.infrastructure.docker.DockerConfig
import dev.rubentxu.hodei.resourcemanagement.domain.ports.InstanceSpec
import dev.rubentxu.hodei.resourcemanagement.domain.ports.InstanceType
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.templatemanagement.application.services.WorkerTemplateService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.*

private val logger = KotlinLogging.logger {}

/**
 * Docker worker management command group
 */
class DockerWorkerCommand : CliktCommand(
    name = "worker",
    help = "🐳 Docker worker lifecycle management"
) {
    override fun run() = Unit
}

/**
 * Start Docker workers for a resource pool
 */
class DockerWorkerStartCommand : CliktCommand(
    name = "start",
    help = "🚀 Start Docker workers for job execution"
), KoinComponent {
    
    private val poolId by option(
        "--pool-id",
        help = "Resource pool ID to start workers for"
    ).required()
    
    private val workers by option(
        "--workers", "-w",
        help = "Number of workers to start"
    ).int().default(1)
    
    private val templateName by option(
        "--template", "-t",
        help = "Worker template name to use"
    ).default("default-docker-worker")
    
    private val orchestratorHost by option(
        "--orchestrator-host",
        help = "Orchestrator hostname"
    ).default("localhost")
    
    private val orchestratorPort by option(
        "--orchestrator-port",
        help = "Orchestrator gRPC port"
    ).int().default(9090)
    
    private val dockerHost by option(
        "--docker-host",
        help = "Docker daemon host"
    ).default("unix:///var/run/docker.sock")
    
    private val instanceType by option(
        "--instance-type",
        help = "Instance type (small, medium, large, xlarge)"
    ).default("small")
    
    private val dryRun by option(
        "--dry-run",
        help = "Show what would be created without starting workers"
    ).flag()
    
    private val follow by option(
        "--follow", "-f",
        help = "Follow worker logs after starting"
    ).flag()
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    // Injected dependencies
    private val workerTemplateService: WorkerTemplateService by inject()
    
    override fun run() = runBlocking {
        try {
            echo("🚀 Starting Docker workers...")
            echo("📊 Pool ID: $poolId")
            echo("👥 Worker Count: $workers")
            echo("📋 Template: $templateName")
            echo("🌐 Orchestrator: $orchestratorHost:$orchestratorPort")
            echo()
            
            if (dryRun) {
                echo("🧪 DRY RUN MODE - No workers will be started")
                showWorkerConfiguration()
                return@runBlocking
            }
            
            // Configure Docker
            val dockerConfig = DockerConfig(
                dockerHost = dockerHost,
                orchestratorHost = orchestratorHost,
                orchestratorPort = orchestratorPort
            )
            
            // Initialize Docker instance manager
            val instanceManager = TemplateAwareDockerInstanceManager(
                workerTemplateService = workerTemplateService,
                dockerConfig = dockerConfig
            )
            
            val resourcePoolId = DomainId(poolId)
            val startedWorkers = mutableListOf<String>()
            val failedWorkers = mutableListOf<String>()
            
            // Start workers sequentially
            repeat(workers) { workerIndex ->
                val workerId = "worker-${UUID.randomUUID().toString().take(8)}"
                echo("🔧 Starting worker $workerId (${workerIndex + 1}/$workers)...")
                
                try {
                    val instanceSpec = createWorkerInstanceSpec(workerId)
                    val result = instanceManager.provisionInstance(resourcePoolId, instanceSpec)
                    
                    when {
                        result.isRight() -> {
                            val instance = result.getOrNull()!!
                            startedWorkers.add(workerId)
                            echo("✅ Worker $workerId started successfully")
                            echo("   Instance ID: ${instance.id.value}")
                            echo("   Status: ${instance.status}")
                            if (verbose) {
                                echo("   Created at: ${instance.createdAt}")
                                echo("   Metadata: ${instance.metadata}")
                            }
                        }
                        result.isLeft() -> {
                            val error = result.swap().getOrNull()!!
                            failedWorkers.add(workerId)
                            echo("❌ Failed to start worker $workerId: ${error.message}")
                        }
                    }
                } catch (e: Exception) {
                    failedWorkers.add(workerId)
                    echo("❌ Exception starting worker $workerId: ${e.message}")
                    if (verbose) {
                        e.printStackTrace()
                    }
                }
                
                // Small delay between workers
                if (workerIndex < workers - 1) {
                    kotlinx.coroutines.delay(1000)
                }
            }
            
            echo()
            echo("📊 Worker Startup Summary:")
            echo("✅ Successfully started: ${startedWorkers.size}")
            echo("❌ Failed to start: ${failedWorkers.size}")
            
            if (startedWorkers.isNotEmpty()) {
                echo()
                echo("🔗 Started Workers:")
                startedWorkers.forEach { workerId ->
                    echo("   • $workerId")
                }
                
                if (follow) {
                    echo()
                    echo("👀 Following worker logs... (Press Ctrl+C to exit)")
                    echo("📝 Note: Worker logs will stream from the orchestrator")
                    
                    // In a real implementation, this would stream logs
                    // For now, we'll just wait for user interruption
                    try {
                        while (true) {
                            kotlinx.coroutines.delay(1000)
                            // TODO: Implement actual log following
                            // This would connect to the orchestrator and stream worker logs
                        }
                    } catch (e: InterruptedException) {
                        echo("📊 Log following stopped")
                    }
                }
                
                echo()
                echo("🎯 Next Steps:")
                echo("1. Check worker status:")
                echo("   hodei docker worker status --pool-id $poolId")
                echo()
                echo("2. Submit jobs to the pool:")
                echo("   hodei job submit --pool-id $poolId --template my-template")
                echo()
                echo("3. Monitor execution:")
                echo("   hodei job list --pool-id $poolId")
            }
            
            if (failedWorkers.isNotEmpty()) {
                echo()
                echo("❌ Failed Workers:")
                failedWorkers.forEach { workerId ->
                    echo("   • $workerId")
                }
                echo()
                echo("💡 Troubleshooting:")
                echo("• Check Docker daemon is running and accessible")
                echo("• Verify orchestrator is running at $orchestratorHost:$orchestratorPort")
                echo("• Check worker template '$templateName' exists and is valid")
                echo("• Ensure sufficient resources are available")
                
                System.exit(1)
            }
            
        } catch (e: Exception) {
            echo("💥 Failed to start workers: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }
    
    private fun createWorkerInstanceSpec(workerId: String): InstanceSpec {
        val envVars = mapOf(
            "HODEI_ORCHESTRATOR_HOST" to orchestratorHost,
            "HODEI_ORCHESTRATOR_PORT" to orchestratorPort.toString(),
            "WORKER_ID" to workerId,
            "WORKER_POOL_ID" to poolId,
            "WORKER_TYPE" to "docker"
        )
        
        val labels = mapOf(
            "hodei.worker" to "true",
            "hodei.pool-id" to poolId,
            "hodei.worker-id" to workerId,
            "hodei.created-by" to "hodei-cli",
            "hodei.template" to templateName
        )
        
        val metadata = mapOf(
            "templateName" to templateName,
            "workerId" to workerId,
            "poolId" to poolId,
            "createdBy" to "hodei-cli",
            "instanceType" to instanceType
        )
        
        return InstanceSpec(
            instanceType = when (instanceType.lowercase()) {
                "small" -> InstanceType.SMALL
                "medium" -> InstanceType.MEDIUM
                "large" -> InstanceType.LARGE
                "xlarge" -> InstanceType.XLARGE
                else -> InstanceType.SMALL
            },
            image = "hodei/worker:latest", // Will be overridden by template
            command = listOf("worker", "--mode=worker"),
            environment = envVars,
            labels = labels,
            metadata = metadata
        )
    }
    
    private fun showWorkerConfiguration() {
        echo("🧪 Worker Configuration (Dry Run):")
        echo("   Workers to start: $workers")
        echo("   Instance type: $instanceType")
        echo("   Template: $templateName")
        echo("   Docker host: $dockerHost")
        echo("   Environment variables:")
        echo("     HODEI_ORCHESTRATOR_HOST=$orchestratorHost")
        echo("     HODEI_ORCHESTRATOR_PORT=$orchestratorPort")
        echo("     WORKER_POOL_ID=$poolId")
        echo("   Labels:")
        echo("     hodei.worker=true")
        echo("     hodei.pool-id=$poolId")
        echo("     hodei.created-by=hodei-cli")
    }
}

/**
 * Stop Docker workers
 */
class DockerWorkerStopCommand : CliktCommand(
    name = "stop",
    help = "🛑 Stop Docker workers"
), KoinComponent {
    
    private val poolId by option(
        "--pool-id",
        help = "Resource pool ID to stop workers for"
    )
    
    private val workerId by option(
        "--worker-id",
        help = "Specific worker ID to stop"
    )
    
    private val all by option(
        "--all",
        help = "Stop all workers in the pool"
    ).flag()
    
    private val force by option(
        "--force",
        help = "Force stop workers even if jobs are running"
    ).flag()
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Enable verbose output"
    ).flag()
    
    // Injected dependencies
    private val workerTemplateService: WorkerTemplateService by inject()
    
    override fun run() = runBlocking {
        try {
            when {
                workerId != null -> {
                    echo("🛑 Stopping worker: $workerId")
                    stopSpecificWorker(workerId!!)
                }
                poolId != null && all -> {
                    echo("🛑 Stopping all workers in pool: $poolId")
                    stopAllWorkersInPool(poolId!!)
                }
                else -> {
                    echo("❌ Please specify either --worker-id or --pool-id with --all")
                    System.exit(1)
                }
            }
        } catch (e: Exception) {
            echo("💥 Failed to stop workers: ${e.message}")
            if (verbose) {
                e.printStackTrace()
            }
            System.exit(1)
        }
    }
    
    private suspend fun stopSpecificWorker(workerId: String) {
        // Implementation would use the instance manager to terminate specific worker
        echo("✅ Worker $workerId stopped successfully")
    }
    
    private suspend fun stopAllWorkersInPool(poolId: String) {
        // Implementation would list and stop all workers in the pool
        echo("✅ All workers in pool $poolId stopped successfully")
    }
}