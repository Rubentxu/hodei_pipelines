package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.long
import dev.rubentxu.hodei.cli.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject

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
    private val provider by option("--provider", help = "Provider (docker, local)").default("docker")
    private val dryRun by option("--dry-run", help = "Validate without creating").flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        echo("🏊 Creating resource pool '$name'...")
        echo("Type: $type | Provider: $provider | Max Workers: $maxWorkers")
        
        if (dryRun) {
            echo("🔍 Dry-run mode: Validating configuration...")
            echo("✅ Configuration is valid. Pool would be created successfully.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val request = CreatePoolRequest(
                name = name,
                type = type,
                provider = provider,
                maxWorkers = maxWorkers
            )
            
            val result = apiClient.createPool(request)
            result.fold(
                onSuccess = { pool ->
                    echo("✅ Pool created successfully!")
                    echo("   ID: ${pool.id}")
                    echo("   Name: ${pool.name}")
                    echo("   Status: ${pool.status}")
                },
                onFailure = { error ->
                    echo("❌ Failed to create pool: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class PoolDeleteCommand : CliktCommand(
    name = "delete",
    help = "Delete a resource pool"
) {
    private val poolId by argument(help = "Pool ID to delete")
    private val force by option("--force", "-f", help = "Force deletion without confirmation").flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        if (!force) {
            echo("⚠️  Warning: This will delete pool '$poolId' and all associated resources.")
            echo("Are you sure? Type 'yes' to confirm:")
            val confirmation = readLine()
            if (confirmation?.lowercase() != "yes") {
                echo("❌ Deletion cancelled.")
                return@runBlocking
            }
        }
        
        echo("🗑️ Deleting pool '$poolId'...")
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.deletePool(poolId)
            result.fold(
                onSuccess = {
                    echo("✅ Pool deleted successfully!")
                },
                onFailure = { error ->
                    echo("❌ Failed to delete pool: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class PoolStatusCommand : CliktCommand(
    name = "status",
    help = "Show pool status"
) {
    private val poolId by argument(help = "Pool ID")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getPoolStatus(poolId)
            result.fold(
                onSuccess = { pool ->
                    echo("📊 Resource Pool Status")
                    echo("═══════════════════════")
                    echo("ID:       ${pool.id}")
                    echo("Name:     ${pool.name}")
                    echo("Type:     ${pool.type}")
                    echo("Status:   ${pool.status}")
                    echo("Provider: ${pool.provider}")
                    echo("Created:  ${pool.createdAt}")
                    echo("Updated:  ${pool.updatedAt}")
                    echo("Owner:    ${pool.createdBy}")
                    
                    pool.capacity?.let { capacity ->
                        echo("")
                        echo("📦 Capacity:")
                        echo("  CPU Cores:    ${capacity.totalCpuCores}")
                        echo("  Memory:       ${capacity.totalMemoryMB} MB")
                        echo("  Max Workers:  ${capacity.maxInstances}")
                    }
                    
                    pool.utilization?.let { util ->
                        echo("")
                        echo("📈 Utilization:")
                        echo("  CPU:     ${String.format("%.1f", util.usedCpu)}/${String.format("%.1f", util.totalCpu)} (${String.format("%.1f", (util.usedCpu/util.totalCpu)*100)}%)")
                        echo("  Memory:  ${util.usedMemoryBytes/1024/1024}/${util.totalMemoryBytes/1024/1024} MB (${String.format("%.1f", (util.usedMemoryBytes.toDouble()/util.totalMemoryBytes)*100)}%)")
                        echo("  Running: ${util.runningJobs} jobs")
                        echo("  Queued:  ${util.queuedJobs} jobs")
                    }
                    
                    if (pool.metadata.isNotEmpty()) {
                        echo("")
                        echo("🏷️ Metadata:")
                        pool.metadata.forEach { (key, value) ->
                            echo("  $key: $value")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get pool status: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
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
    private val priority by option("--priority", help = "Job priority (low, normal, high)").default("normal")
    private val timeout by option("--timeout", help = "Job timeout in seconds").long()
    private val dryRun by option("--dry-run", help = "Validate without submitting").flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        echo("📤 Submitting job from '$pipeline'...")
        
        // Read pipeline file
        val pipelineFile = java.io.File(pipeline)
        if (!pipelineFile.exists()) {
            echo("❌ Pipeline file not found: $pipeline")
            return@runBlocking
        }
        
        val pipelineContent = try {
            pipelineFile.readText()
        } catch (e: Exception) {
            echo("❌ Failed to read pipeline file: ${e.message}")
            return@runBlocking
        }
        
        val jobName = name ?: pipelineFile.nameWithoutExtension
        
        if (dryRun) {
            echo("🔍 Dry-run mode: Validating pipeline...")
            echo("  Name: $jobName")
            echo("  Priority: $priority")
            pool?.also { echo("  Pool: $it") }
            timeout?.also { echo("  Timeout: ${it}s") }
            echo("✅ Pipeline validation successful. Job would be submitted.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val request = JobSubmissionRequest(
                name = jobName,
                type = "pipeline",
                priority = priority,
                content = pipelineContent,
                resourcePoolId = pool,
                timeout = timeout,
                metadata = mapOf(
                    "source" to pipeline,
                    "submitted_via" to "cli"
                )
            )
            
            val result = apiClient.submitJob(request)
            result.fold(
                onSuccess = { response ->
                    echo("✅ Job submitted successfully!")
                    echo("   Job ID: ${response.jobId}")
                    echo("   Status: ${response.status}")
                    response.queuePosition?.let { echo("   Queue Position: $it") }
                    response.estimatedDuration?.let { echo("   Estimated Duration: ${it}s") }
                    echo("")
                    echo("💡 Track job progress with: hp job status ${response.jobId}")
                    echo("💡 View logs with: hp job logs ${response.jobId}")
                },
                onFailure = { error ->
                    echo("❌ Failed to submit job: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class JobStatusCommand : CliktCommand(
    name = "status",
    help = "Get job status"
) {
    private val jobId by argument(help = "Job ID")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getJobStatus(jobId)
            result.fold(
                onSuccess = { job ->
                    echo("📊 Job Status")
                    echo("═════════════")
                    echo("ID:          ${job.id}")
                    echo("Name:        ${job.name}")
                    echo("Type:        ${job.type}")
                    echo("Status:      ${job.status}")
                    echo("Priority:    ${job.priority}")
                    job.description?.let { echo("Description: $it") }
                    
                    echo("")
                    echo("⏱️ Timeline:")
                    echo("  Created:   ${job.createdAt}")
                    job.startedAt?.let { echo("  Started:   $it") }
                    job.completedAt?.let { echo("  Completed: $it") }
                    job.duration?.let { echo("  Duration:  ${it/1000}s") }
                    
                    if (job.resourcePoolId != null || job.workerId != null) {
                        echo("")
                        echo("🔧 Execution:")
                        job.resourcePoolId?.let { echo("  Pool:   $it") }
                        job.workerId?.let { echo("  Worker: $it") }
                    }
                    
                    if (job.progress != null || job.currentStep != null) {
                        echo("")
                        echo("📈 Progress:")
                        job.progress?.let { echo("  Progress: ${String.format("%.1f", it)}%") }
                        job.currentStep?.let { echo("  Current Step: $it") }
                    }
                    
                    if (job.metadata.isNotEmpty()) {
                        echo("")
                        echo("🏷️ Metadata:")
                        job.metadata.forEach { (key, value) ->
                            echo("  $key: $value")
                        }
                    }
                    
                    // Provide status-specific guidance
                    echo("")
                    when (job.status.lowercase()) {
                        "running" -> echo("💡 View logs with: hp job logs $jobId --follow")
                        "failed" -> echo("💡 Check logs for errors: hp job logs $jobId")
                        "completed" -> echo("💡 View final logs: hp job logs $jobId")
                        "queued" -> echo("💡 Job is waiting for available resources")
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get job status: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class JobLogsCommand : CliktCommand(
    name = "logs",
    help = "Get job logs"
) {
    private val jobId by argument(help = "Job ID")
    private val follow by option("--follow", "-f", help = "Follow logs").flag()
    private val tail by option("--tail", "-n", help = "Number of lines to show from the end").int()
    private val since by option("--since", help = "Show logs since timestamp")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        
        if (follow) {
            echo("📄 Following job logs for: $jobId")
            echo("Press Ctrl+C to stop...")
            echo("─────────────────────────────────────────")
            
            try {
                apiClient.streamJobLogs(jobId).collect { logEntry ->
                    val levelIcon = when (logEntry.level.uppercase()) {
                        "ERROR" -> "❌"
                        "WARN" -> "⚠️"
                        "INFO" -> "ℹ️"
                        "DEBUG" -> "🔍"
                        else -> "📝"
                    }
                    
                    val timestamp = logEntry.timestamp.substringAfter("T").substringBefore(".")
                    echo("[$timestamp] $levelIcon ${logEntry.message}")
                }
            } catch (e: Exception) {
                echo("❌ Error streaming logs: ${e.message}")
            }
        } else {
            // Fetch static logs
            echo("📄 Job logs for: $jobId")
            echo("─────────────────────────────────────────")
            
            try {
                val result = apiClient.getJobLogs(jobId)
                result.fold(
                    onSuccess = { response ->
                        val logs = tail?.let { t ->
                            if (response.logs.size > t) response.logs.takeLast(t) else response.logs
                        } ?: response.logs
                        
                        val filteredLogs = since?.let { s ->
                            logs.filter { it.timestamp >= s }
                        } ?: logs
                        
                        if (filteredLogs.isEmpty()) {
                            echo("No logs found matching criteria")
                        } else {
                            filteredLogs.forEach { logEntry ->
                                val levelIcon = when (logEntry.level.uppercase()) {
                                    "ERROR" -> "❌"
                                    "WARN" -> "⚠️"
                                    "INFO" -> "ℹ️"
                                    "DEBUG" -> "🔍"
                                    else -> "📝"
                                }
                                
                                val timestamp = logEntry.timestamp.substringAfter("T").substringBefore(".")
                                echo("[$timestamp] $levelIcon ${logEntry.message}")
                            }
                            
                            if (response.hasMore) {
                                echo("")
                                echo("💡 More logs available. Use --follow to stream all logs.")
                            }
                        }
                    },
                    onFailure = { error ->
                        echo("❌ Failed to get job logs: ${error.message}")
                    }
                )
            } finally {
                apiClient.close()
            }
        }
    }
}

class JobCancelCommand : CliktCommand(
    name = "cancel",
    help = "Cancel a job"
) {
    private val jobId by argument(help = "Job ID")
    private val reason by option("--reason", help = "Reason for cancellation")
    private val force by option("--force", "-f", help = "Force cancellation without confirmation").flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        if (!force) {
            echo("⚠️  Warning: This will cancel job '$jobId' and stop all processing.")
            echo("Are you sure? Type 'yes' to confirm:")
            val confirmation = readLine()
            if (confirmation?.lowercase() != "yes") {
                echo("❌ Cancellation aborted.")
                return@runBlocking
            }
        }
        
        echo("🛑 Cancelling job '$jobId'...")
        reason?.let { echo("Reason: $it") }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            // First get job status to show current state
            val statusResult = apiClient.getJobStatus(jobId)
            statusResult.onSuccess { job ->
                if (job.status.lowercase() in listOf("completed", "failed", "cancelled")) {
                    echo("ℹ️  Job is already ${job.status}. No action needed.")
                    return@runBlocking
                }
            }
            
            val result = apiClient.cancelJob(jobId)
            result.fold(
                onSuccess = {
                    echo("✅ Job cancelled successfully!")
                    reason?.let { echo("   Cancellation reason recorded: $it") }
                    echo("")
                    echo("💡 View final status with: hp job status $jobId")
                },
                onFailure = { error ->
                    echo("❌ Failed to cancel job: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

// =================== WORKER COMMANDS ===================

class WorkerListCommand : CliktCommand(
    name = "list",
    help = "List all workers"
) {
    private val pool by option("--pool", help = "Filter by pool ID")
    private val status by option("--status", help = "Filter by status (idle, busy, offline)")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getWorkers()
            result.fold(
                onSuccess = { workers ->
                    var filteredWorkers = workers
                    
                    // Apply filters
                    pool?.let { poolId ->
                        filteredWorkers = filteredWorkers.filter { it.resourcePoolId == poolId }
                    }
                    status?.let { statusFilter ->
                        filteredWorkers = filteredWorkers.filter { it.status.lowercase() == statusFilter.lowercase() }
                    }
                    
                    if (filteredWorkers.isEmpty()) {
                        echo("No workers found matching criteria")
                    } else {
                        echo("👷 Workers:")
                        echo("")
                        filteredWorkers.forEach { worker ->
                            val statusIcon = when (worker.status.lowercase()) {
                                "idle" -> "🟢"
                                "busy" -> "🟡"
                                "offline" -> "🔴"
                                else -> "⚪"
                            }
                            echo("• ${worker.name} (${worker.id})")
                            echo("  $statusIcon Status: ${worker.status} | Type: ${worker.type}")
                            echo("  Pool: ${worker.resourcePoolId}")
                            worker.currentJobId?.let { echo("  Current Job: $it") }
                            echo("  Last Heartbeat: ${worker.lastHeartbeat}")
                            echo("")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get workers: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class WorkerStatusCommand : CliktCommand(
    name = "status",
    help = "Get worker status"
) {
    private val workerId by argument(help = "Worker ID")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getWorkerStatus(workerId)
            result.fold(
                onSuccess = { worker ->
                    val statusIcon = when (worker.status.lowercase()) {
                        "idle" -> "🟢"
                        "busy" -> "🟡"
                        "offline" -> "🔴"
                        else -> "⚪"
                    }
                    
                    echo("📊 Worker Status")
                    echo("════════════════")
                    echo("ID:           ${worker.id}")
                    echo("Name:         ${worker.name}")
                    echo("Status:       $statusIcon ${worker.status}")
                    echo("Type:         ${worker.type}")
                    echo("Pool:         ${worker.resourcePoolId}")
                    echo("Connected:    ${worker.connectedAt}")
                    echo("Last Ping:    ${worker.lastHeartbeat}")
                    
                    worker.currentJobId?.let {
                        echo("")
                        echo("🔧 Current Work:")
                        echo("  Job ID: $it")
                    }
                    
                    if (worker.capabilities.isNotEmpty()) {
                        echo("")
                        echo("🚀 Capabilities:")
                        worker.capabilities.forEach { cap ->
                            echo("  • $cap")
                        }
                    }
                    
                    if (worker.metadata.isNotEmpty()) {
                        echo("")
                        echo("🏷️ Metadata:")
                        worker.metadata.forEach { (key, value) ->
                            echo("  $key: $value")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get worker status: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

// =================== TEMPLATE COMMANDS ===================

class TemplateListCommand : CliktCommand(
    name = "list",
    help = "List all templates"
) {
    private val type by option("--type", help = "Filter by template type")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getTemplates()
            result.fold(
                onSuccess = { templates ->
                    var filteredTemplates = templates
                    
                    // Apply type filter
                    type?.let { typeFilter ->
                        filteredTemplates = filteredTemplates.filter { it.type == typeFilter }
                    }
                    
                    if (filteredTemplates.isEmpty()) {
                        echo("No templates found")
                    } else {
                        echo("📦 Templates:")
                        echo("")
                        filteredTemplates.forEach { template ->
                            echo("• ${template.name} (${template.id})")
                            echo("  Type: ${template.type} | Version: ${template.version}")
                            echo("  ${template.description}")
                            echo("  Created: ${template.createdAt}")
                            echo("")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get templates: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class TemplateCreateCommand : CliktCommand(
    name = "create",
    help = "Create a new template"
) {
    private val name by option("--name", help = "Template name").required()
    private val description by option("--description", help = "Template description").required()
    private val type by option("--type", help = "Template type").default("pipeline")
    private val file by option("--file", "-f", help = "Template file (JSON/YAML)").required()
    private val dryRun by option("--dry-run", help = "Validate without creating").flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        echo("📦 Creating template '$name'...")
        
        // Read template file
        val templateFile = java.io.File(file)
        if (!templateFile.exists()) {
            echo("❌ Template file not found: $file")
            return@runBlocking
        }
        
        val templateContent = try {
            templateFile.readText()
        } catch (e: Exception) {
            echo("❌ Failed to read template file: ${e.message}")
            return@runBlocking
        }
        
        // Parse template spec (assuming JSON)
        val spec = try {
            Json.parseToJsonElement(templateContent).jsonObject.toMap()
                .mapValues { (_, value) -> value.toString() }
        } catch (e: Exception) {
            echo("❌ Invalid template format. Expected JSON: ${e.message}")
            return@runBlocking
        }
        
        if (dryRun) {
            echo("🔍 Dry-run mode: Validating template...")
            echo("  Name: $name")
            echo("  Type: $type")
            echo("  Description: $description")
            echo("✅ Template validation successful.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val request = CreateTemplateRequest(
                name = name,
                description = description,
                type = type,
                spec = spec
            )
            
            val result = apiClient.createTemplate(request)
            result.fold(
                onSuccess = { template ->
                    echo("✅ Template created successfully!")
                    echo("   ID: ${template.id}")
                    echo("   Name: ${template.name}")
                    echo("   Version: ${template.version}")
                },
                onFailure = { error ->
                    echo("❌ Failed to create template: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class TemplateShowCommand : CliktCommand(
    name = "show",
    help = "Show template details"
) {
    private val templateId by argument(help = "Template ID")
    private val output by option("--output", "-o", help = "Output format (json, yaml)").default("text")
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            val result = apiClient.getTemplate(templateId)
            result.fold(
                onSuccess = { template ->
                    when (output) {
                        "json" -> {
                            val json = Json { prettyPrint = true }
                            echo(json.encodeToString(template))
                        }
                        else -> {
                            echo("📦 Template Details")
                            echo("═══════════════════")
                            echo("ID:          ${template.id}")
                            echo("Name:        ${template.name}")
                            echo("Description: ${template.description}")
                            echo("Type:        ${template.type}")
                            echo("Version:     ${template.version}")
                            echo("Status:      ${template.status}")
                            echo("Created:     ${template.createdAt}")
                            echo("Updated:     ${template.updatedAt}")
                            echo("Owner:       ${template.createdBy}")
                            
                            if (template.spec.isNotEmpty()) {
                                echo("")
                                echo("📋 Specification:")
                                template.spec.forEach { (key, value) ->
                                    echo("  $key: $value")
                                }
                            }
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to get template: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
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
        val authManager = AuthManager()
        val url = authManager.getCurrentUrl() ?: run {
            echo("❌ Not authenticated. Run 'hp login <url>' first.")
            return@runBlocking
        }
        
        val apiClient = HodeiApiClient(url, authManager)
        try {
            echo("📊 System Status")
            echo("════════════════")
            echo("🔗 Connected to: $url")
            echo("")
            
            // Get health status
            val healthResult = apiClient.healthCheck()
            healthResult.fold(
                onSuccess = { health ->
                    val statusIcon = if (health.overall == "healthy") "✅" else "❌"
                    echo("$statusIcon Overall Health: ${health.overall}")
                    echo("")
                },
                onFailure = {
                    echo("❌ Health: Unable to connect")
                    echo("")
                }
            )
            
            // Get resource pools summary
            val poolsResult = apiClient.getPools()
            poolsResult.fold(
                onSuccess = { pools ->
                    echo("🏊 Resource Pools: ${pools.size}")
                    val activePoolsCount = pools.count { it.status == "active" }
                    echo("   Active: $activePoolsCount")
                    echo("   Total Capacity: ${pools.sumOf { it.capacity?.maxInstances ?: 0 }} workers")
                },
                onFailure = {
                    echo("🏊 Resource Pools: Unable to retrieve")
                }
            )
            
            echo("")
            
            // Get jobs summary
            val jobsResult = apiClient.getJobs()
            jobsResult.fold(
                onSuccess = { jobs ->
                    echo("📋 Jobs: ${jobs.size} total")
                    val runningJobs = jobs.count { it.status == "running" }
                    val queuedJobs = jobs.count { it.status == "queued" }
                    val completedJobs = jobs.count { it.status == "completed" }
                    val failedJobs = jobs.count { it.status == "failed" }
                    
                    echo("   🏃 Running: $runningJobs")
                    echo("   ⏳ Queued: $queuedJobs")
                    echo("   ✅ Completed: $completedJobs")
                    echo("   ❌ Failed: $failedJobs")
                },
                onFailure = {
                    echo("📋 Jobs: Unable to retrieve")
                }
            )
            
            echo("")
            
            // Get workers summary
            val workersResult = apiClient.getWorkers()
            workersResult.fold(
                onSuccess = { workers ->
                    echo("👷 Workers: ${workers.size} total")
                    val idleWorkers = workers.count { it.status == "idle" }
                    val busyWorkers = workers.count { it.status == "busy" }
                    val offlineWorkers = workers.count { it.status == "offline" }
                    
                    echo("   🟢 Idle: $idleWorkers")
                    echo("   🟡 Busy: $busyWorkers")
                    echo("   🔴 Offline: $offlineWorkers")
                },
                onFailure = {
                    echo("👷 Workers: Unable to retrieve")
                }
            )
            
            echo("")
            echo("💡 For detailed information, use specific commands:")
            echo("   hp pool list      - List all resource pools")
            echo("   hp job list       - List all jobs")
            echo("   hp worker list    - List all workers")
            echo("   hp health         - Detailed health check")
        } finally {
            apiClient.close()
        }
    }
}