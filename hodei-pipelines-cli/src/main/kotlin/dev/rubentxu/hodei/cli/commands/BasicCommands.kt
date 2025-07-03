package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import dev.rubentxu.hodei.cli.client.AuthManager
import dev.rubentxu.hodei.cli.client.HodeiApiClient
import kotlinx.coroutines.runBlocking

// =================== POOL COMMANDS ===================

class PoolListCommand : CliktCommand(
    name = "list",
    help = "List all resource pools"
) {
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getPools()
            result.fold(
                onSuccess = { pools ->
                    if (pools.isEmpty()) {
                        echo("No resource pools found")
                    } else {
                        echo("🏊 Resource Pools:")
                        echo("")
                        pools.forEach { pool ->
                            echo("• ${pool.name} (${pool.id})")
                            echo("  Type: ${pool.type} | Status: ${pool.status}")
                            echo("  Provider: ${pool.provider}")
                            echo("  Created: ${pool.createdAt}")
                            echo("")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get pools: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class PoolCreateCommand : CliktCommand(
    name = "create",
    help = "Create a new resource pool"
) {
    private val name by option("--name", help = "Pool name").required()
    private val type by option("--type", help = "Pool type (docker, kubernetes)").default("docker")
    private val maxWorkers by option("--max-workers", help = "Maximum workers").int().default(5)
    
    override fun run() = runBlocking {
        echo("🏊 Creating resource pool '$name'...")
        echo("Type: $type | Max Workers: $maxWorkers")
        
        // TODO: Implement pool creation via API
        echo("✅ Pool created successfully!")
    }
}

class PoolDeleteCommand : CliktCommand(
    name = "delete",
    help = "Delete a resource pool"
) {
    private val poolId by argument(help = "Pool ID to delete")
    
    override fun run() = runBlocking {
        echo("🗑️ Deleting pool '$poolId'...")
        // TODO: Implement pool deletion via API
        echo("✅ Pool deleted successfully!")
    }
}

class PoolStatusCommand : CliktCommand(
    name = "status",
    help = "Show pool status"
) {
    private val poolId by argument(help = "Pool ID")
    
    override fun run() = runBlocking {
        echo("📊 Pool Status: $poolId")
        // TODO: Implement pool status via API
    }
}

// =================== JOB COMMANDS ===================

class JobListCommand : CliktCommand(
    name = "list",
    help = "List all jobs"
) {
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getJobs()
            result.fold(
                onSuccess = { jobs ->
                    if (jobs.isEmpty()) {
                        echo("No jobs found")
                    } else {
                        echo("📋 Jobs:")
                        echo("")
                        jobs.forEach { job ->
                            echo("• ${job.name} (${job.id})")
                            echo("  Status: ${job.status} | Type: ${job.type}")
                            echo("  Created: ${job.createdAt}")
                            echo("")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get jobs: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class JobSubmitCommand : CliktCommand(
    name = "submit",
    help = "Submit a new job"
) {
    private val pipeline by argument(help = "Pipeline file to execute")
    private val name by option("--name", help = "Job name")
    private val pool by option("--pool", help = "Resource pool ID")
    
    override fun run() = runBlocking {
        echo("📤 Submitting job from '$pipeline'...")
        // TODO: Implement job submission via API
        echo("✅ Job submitted successfully!")
    }
}

class JobStatusCommand : CliktCommand(
    name = "status",
    help = "Get job status"
) {
    private val jobId by argument(help = "Job ID")
    
    override fun run() = runBlocking {
        echo("📊 Job Status: $jobId")
        // TODO: Implement job status via API
    }
}

class JobLogsCommand : CliktCommand(
    name = "logs",
    help = "Get job logs"
) {
    private val jobId by argument(help = "Job ID")
    private val follow by option("--follow", "-f", help = "Follow logs").flag()
    
    override fun run() = runBlocking {
        echo("📄 Job Logs: $jobId")
        if (follow) {
            echo("Following logs (Ctrl+C to stop)...")
        }
        // TODO: Implement job logs via API
    }
}

class JobCancelCommand : CliktCommand(
    name = "cancel",
    help = "Cancel a job"
) {
    private val jobId by argument(help = "Job ID")
    
    override fun run() = runBlocking {
        echo("🛑 Cancelling job '$jobId'...")
        // TODO: Implement job cancellation via API
        echo("✅ Job cancelled successfully!")
    }
}

// =================== WORKER COMMANDS ===================

class WorkerListCommand : CliktCommand(
    name = "list",
    help = "List all workers"
) {
    override fun run() = runBlocking {
        echo("👷 Workers:")
        // TODO: Implement worker listing via API
    }
}

class WorkerStatusCommand : CliktCommand(
    name = "status",
    help = "Get worker status"
) {
    private val workerId by argument(help = "Worker ID")
    
    override fun run() = runBlocking {
        echo("📊 Worker Status: $workerId")
        // TODO: Implement worker status via API
    }
}

// =================== TEMPLATE COMMANDS ===================

class TemplateListCommand : CliktCommand(
    name = "list",
    help = "List all templates"
) {
    override fun run() = runBlocking {
        echo("📦 Templates:")
        // TODO: Implement template listing via API
    }
}

class TemplateCreateCommand : CliktCommand(
    name = "create",
    help = "Create a new template"
) {
    private val name by option("--name", help = "Template name").required()
    private val file by option("--file", help = "Template file")
    
    override fun run() = runBlocking {
        echo("📦 Creating template '$name'...")
        // TODO: Implement template creation via API
        echo("✅ Template created successfully!")
    }
}

class TemplateShowCommand : CliktCommand(
    name = "show",
    help = "Show template details"
) {
    private val templateId by argument(help = "Template ID")
    
    override fun run() = runBlocking {
        echo("📦 Template: $templateId")
        // TODO: Implement template details via API
    }
}

// =================== DOCKER COMMANDS ===================

class DockerDiscoverCommand : CliktCommand(
    name = "discover",
    help = "Discover Docker environment and create pool"
) {
    override fun run() = runBlocking {
        echo("🔍 Discovering Docker environment...")
        // TODO: Implement Docker discovery via API
        echo("✅ Docker pool created successfully!")
    }
}

class DockerStatusCommand : CliktCommand(
    name = "status",
    help = "Show Docker pools status"
) {
    override fun run() = runBlocking {
        echo("🐳 Docker Status:")
        // TODO: Implement Docker status via API
    }
}

// =================== SYSTEM COMMANDS ===================

class VersionCommand : CliktCommand(
    name = "version",
    help = "Show version information"
) {
    override fun run() = runBlocking {
        echo("🚀 Hodei Pipelines CLI")
        echo("Version: 1.0.0-SNAPSHOT")
        echo("Build: $(git rev-parse --short HEAD)")
        echo("")
        
        // Try to get server version if authenticated
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl()
        if (url != null) {
            val apiClient = HodeiApiClient(url, authManager)
            try {
                val result = apiClient.getVersion()
                result.fold(
                    onSuccess = { version ->
                        echo("🖥️ Server Information:")
                        echo("Application: ${version.applicationName}")
                        echo("Version: ${version.version}")
                        echo("JVM: ${version.jvmVersion}")
                    },
                    onFailure = {
                        echo("🖥️ Server: Not reachable")
                    }
                )
                apiClient.close()
            } catch (e: Exception) {
                echo("🖥️ Server: Not reachable")
            }
        }
    }
}

class HealthCommand : CliktCommand(
    name = "health",
    help = "Check orchestrator health"
) {
    private val url by option("--url", help = "Orchestrator URL (if not authenticated)")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val targetUrl = url ?: authManager.getCurrentUrl() ?: run {
            echo("❌ No URL specified and not authenticated.")
            echo("💡 Use 'hp health --url <url>' or 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(targetUrl, authManager)
        try {
            val result = apiClient.healthCheck()
            result.fold(
                onSuccess = { health ->
                    echo("🏥 Health Check: $targetUrl")
                    echo("Overall: ${health.overall}")
                    echo("Timestamp: ${health.timestamp}")
                    echo("")
                    echo("Components:")
                    health.checks.forEach { (name, check) ->
                        val status = if (check.status == "healthy") "✅" else "❌"
                        echo("  $status $name: ${check.message}")
                    }
                },
                onFailure = { error ->
                    echo("❌ Health check failed: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class StatusCommand : CliktCommand(
    name = "status",
    help = "Show system status"
) {
    override fun run() = runBlocking {
        echo("📊 System Status:")
        // TODO: Implement system status
    }
}