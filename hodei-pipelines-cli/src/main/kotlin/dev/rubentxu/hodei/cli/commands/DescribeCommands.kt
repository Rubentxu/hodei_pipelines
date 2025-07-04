package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import dev.rubentxu.hodei.cli.client.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

/**
 * Describe commands provide detailed information about resources,
 * similar to kubectl describe or oc describe.
 */

class PoolDescribeCommand : CliktCommand(
    name = "describe",
    help = "Show detailed information about a resource pool"
) {
    private val poolId by argument(help = "Pool ID to describe")
    private val output by option("--output", "-o", help = "Output format (json, yaml)").default("text")
    
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
                    when (output) {
                        "json" -> {
                            val json = Json { prettyPrint = true }
                            echo(json.encodeToString(pool))
                        }
                        else -> {
                            echo("Name:         ${pool.name}")
                            echo("Namespace:    default")
                            echo("ID:           ${pool.id}")
                            echo("Type:         ${pool.type}")
                            echo("Provider:     ${pool.provider}")
                            echo("Status:       ${pool.status}")
                            echo("Created:      ${pool.createdAt}")
                            echo("Updated:      ${pool.updatedAt}")
                            echo("Created By:   ${pool.createdBy}")
                            
                            echo("")
                            echo("Capacity:")
                            pool.capacity?.let { capacity ->
                                echo("  CPU Cores:        ${capacity.totalCpuCores}")
                                echo("  Memory:           ${capacity.totalMemoryMB} MB")
                                capacity.totalStorageGB?.let { echo("  Storage:          $it GB") }
                                echo("  Max Workers:      ${capacity.maxInstances}")
                                if (capacity.availableInstanceTypes.isNotEmpty()) {
                                    echo("  Instance Types:   ${capacity.availableInstanceTypes.joinToString(", ")}")
                                }
                            } ?: echo("  <none>")
                            
                            echo("")
                            echo("Current Utilization:")
                            pool.utilization?.let { util ->
                                echo("  CPU Usage:        ${String.format("%.1f", util.usedCpu)}/${String.format("%.1f", util.totalCpu)} cores (${String.format("%.1f", (util.usedCpu/util.totalCpu)*100)}%)")
                                echo("  Memory Usage:     ${util.usedMemoryBytes/1024/1024}/${util.totalMemoryBytes/1024/1024} MB (${String.format("%.1f", (util.usedMemoryBytes.toDouble()/util.totalMemoryBytes)*100)}%)")
                                echo("  Disk Usage:       ${util.usedDiskBytes/1024/1024/1024}/${util.totalDiskBytes/1024/1024/1024} GB (${String.format("%.1f", (util.usedDiskBytes.toDouble()/util.totalDiskBytes)*100)}%)")
                                echo("  Running Jobs:     ${util.runningJobs}")
                                echo("  Queued Jobs:      ${util.queuedJobs}")
                                echo("  Last Update:      ${util.timestamp}")
                            } ?: echo("  <none>")
                            
                            echo("")
                            echo("Metadata:")
                            if (pool.metadata.isNotEmpty()) {
                                pool.metadata.forEach { (key, value) ->
                                    echo("  $key: $value")
                                }
                            } else {
                                echo("  <none>")
                            }
                            
                            echo("")
                            echo("Events:")
                            echo("  <none>")  // TODO: Implement events when available
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to describe pool: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class JobDescribeCommand : CliktCommand(
    name = "describe",
    help = "Show detailed information about a job"
) {
    private val jobId by argument(help = "Job ID to describe")
    private val output by option("--output", "-o", help = "Output format (json, yaml)").default("text")
    
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
                    when (output) {
                        "json" -> {
                            val json = Json { prettyPrint = true }
                            echo(json.encodeToString(job))
                        }
                        else -> {
                            echo("Name:         ${job.name}")
                            echo("Namespace:    default")
                            echo("ID:           ${job.id}")
                            echo("Type:         ${job.type}")
                            echo("Priority:     ${job.priority}")
                            echo("Status:       ${job.status}")
                            job.description?.let { echo("Description:  $it") }
                            echo("Created:      ${job.createdAt}")
                            echo("Updated:      ${job.updatedAt}")
                            echo("Created By:   ${job.createdBy}")
                            
                            echo("")
                            echo("Execution Details:")
                            echo("  Resource Pool:    ${job.resourcePoolId ?: "<none>"}")
                            echo("  Worker ID:        ${job.workerId ?: "<none>"}")
                            echo("  Started At:       ${job.startedAt ?: "<not started>"}")
                            echo("  Completed At:     ${job.completedAt ?: "<not completed>"}")
                            job.duration?.let { 
                                val minutes = it / 60000
                                val seconds = (it % 60000) / 1000
                                echo("  Duration:         ${minutes}m ${seconds}s")
                            }
                            
                            echo("")
                            echo("Progress:")
                            job.progress?.let { echo("  Completion:       ${String.format("%.1f", it)}%") }
                            job.currentStep?.let { echo("  Current Step:     $it") }
                            if (job.progress == null && job.currentStep == null) {
                                echo("  <none>")
                            }
                            
                            echo("")
                            echo("Pipeline Content:")
                            echo("  <truncated - use 'hp job logs $jobId' to view execution logs>")
                            
                            echo("")
                            echo("Metadata:")
                            if (job.metadata.isNotEmpty()) {
                                job.metadata.forEach { (key, value) ->
                                    echo("  $key: $value")
                                }
                            } else {
                                echo("  <none>")
                            }
                            
                            echo("")
                            echo("Events:")
                            echo("  Type    Reason    Age    From    Message")
                            echo("  ----    ------    ---    ----    -------")
                            when (job.status.lowercase()) {
                                "queued" -> echo("  Normal  Queued    now    scheduler    Job queued for execution")
                                "running" -> echo("  Normal  Started   now    executor     Job started on worker ${job.workerId}")
                                "completed" -> echo("  Normal  Completed now    executor     Job completed successfully")
                                "failed" -> echo("  Warning Failed    now    executor     Job execution failed")
                            }
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to describe job: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class WorkerDescribeCommand : CliktCommand(
    name = "describe",
    help = "Show detailed information about a worker"
) {
    private val workerId by argument(help = "Worker ID to describe")
    private val output by option("--output", "-o", help = "Output format (json, yaml)").default("text")
    
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
                    when (output) {
                        "json" -> {
                            val json = Json { prettyPrint = true }
                            echo(json.encodeToString(worker))
                        }
                        else -> {
                            echo("Name:         ${worker.name}")
                            echo("Namespace:    default")
                            echo("ID:           ${worker.id}")
                            echo("Type:         ${worker.type}")
                            echo("Status:       ${worker.status}")
                            echo("Pool:         ${worker.resourcePoolId}")
                            echo("Connected:    ${worker.connectedAt}")
                            echo("Last Ping:    ${worker.lastHeartbeat}")
                            
                            echo("")
                            echo("Current Work:")
                            worker.currentJobId?.let {
                                echo("  Job ID:           $it")
                                echo("  Status:           Processing")
                            } ?: echo("  <idle>")
                            
                            echo("")
                            echo("Capabilities:")
                            if (worker.capabilities.isNotEmpty()) {
                                worker.capabilities.forEach { cap ->
                                    echo("  - $cap")
                                }
                            } else {
                                echo("  <none>")
                            }
                            
                            echo("")
                            echo("System Info:")
                            echo("  Architecture:     <unknown>")  // TODO: Add when available
                            echo("  CPU:              <unknown>")
                            echo("  Memory:           <unknown>")
                            echo("  OS:               <unknown>")
                            
                            echo("")
                            echo("Metadata:")
                            if (worker.metadata.isNotEmpty()) {
                                worker.metadata.forEach { (key, value) ->
                                    echo("  $key: $value")
                                }
                            } else {
                                echo("  <none>")
                            }
                            
                            echo("")
                            echo("Events:")
                            echo("  Type    Reason      Age    From    Message")
                            echo("  ----    ------      ---    ----    -------")
                            echo("  Normal  Connected   now    worker  Worker connected to pool")
                            if (worker.currentJobId != null) {
                                echo("  Normal  JobAssigned now    scheduler Job ${worker.currentJobId} assigned")
                            }
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to describe worker: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}

class TemplateDescribeCommand : CliktCommand(
    name = "describe",
    help = "Show detailed information about a template"
) {
    private val templateId by argument(help = "Template ID to describe")
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
                            echo("Name:         ${template.name}")
                            echo("Namespace:    default")
                            echo("ID:           ${template.id}")
                            echo("Type:         ${template.type}")
                            echo("Version:      ${template.version}")
                            echo("Status:       ${template.status}")
                            echo("Description:  ${template.description}")
                            echo("Created:      ${template.createdAt}")
                            echo("Updated:      ${template.updatedAt}")
                            echo("Created By:   ${template.createdBy}")
                            
                            echo("")
                            echo("Specification:")
                            if (template.spec.isNotEmpty()) {
                                template.spec.forEach { (key, value) ->
                                    echo("  $key: $value")
                                }
                            } else {
                                echo("  <none>")
                            }
                            
                            echo("")
                            echo("Usage:")
                            echo("  Jobs Created:     <unknown>")  // TODO: Add when available
                            echo("  Last Used:        <unknown>")
                            echo("  Success Rate:     <unknown>")
                            
                            echo("")
                            echo("Events:")
                            echo("  Type    Reason    Age    From    Message")
                            echo("  ----    ------    ---    ----    -------")
                            echo("  Normal  Created   now    user    Template created")
                        }
                    }
                },
                onFailure = { error ->
                    echo("❌ Failed to describe template: ${error.message}")
                }
            )
        } finally {
            apiClient.close()
        }
    }
}