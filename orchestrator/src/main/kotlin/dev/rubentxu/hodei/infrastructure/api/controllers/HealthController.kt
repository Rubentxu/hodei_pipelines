package dev.rubentxu.hodei.infrastructure.api.controllers

import dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import dev.rubentxu.hodei.infrastructure.api.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

class HealthController(
    private val jobService: JobAPIService? = null,
    private val resourcePoolService: ResourcePoolService? = null,
    private val executionEngine: ExecutionEngineService? = null,
    private val workerManager: WorkerManagerService? = null
) {
    
    fun Route.healthRoutes() {
        route("/health") {
            get {
                handleHealthCheck(call)
            }
            
            get("/ready") {
                handleReadinessCheck(call)
            }
            
            get("/live") {
                handleLivenessCheck(call)
            }
        }
        
        route("/metrics") {
            get {
                handleMetrics(call)
            }
        }
        
        route("/admin") {
            get("/status") {
                handleAdminStatus(call)
            }
            
            get("/info") {
                handleSystemInfo(call)
            }
        }
    }
    
    private suspend fun handleHealthCheck(call: ApplicationCall) {
        try {
            val health = checkSystemHealth()
            val status = if (health.overall == "healthy") HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, health)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                HealthCheckResponse(
                    overall = "unhealthy",
                    timestamp = Clock.System.now(),
                    checks = mapOf(
                        "system" to ComponentHealth(
                            status = "unhealthy",
                            message = "Health check failed: ${e.message}",
                            timestamp = Clock.System.now()
                        )
                    )
                )
            )
        }
    }
    
    private suspend fun handleReadinessCheck(call: ApplicationCall) {
        try {
            val readiness = checkSystemReadiness()
            val status = if (readiness.ready) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, readiness)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                ReadinessCheckResponse(
                    ready = false,
                    timestamp = Clock.System.now(),
                    message = "Readiness check failed: ${e.message}"
                )
            )
        }
    }
    
    private suspend fun handleLivenessCheck(call: ApplicationCall) {
        try {
            val liveness = checkSystemLiveness()
            val status = if (liveness.alive) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable
            call.respond(status, liveness)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.ServiceUnavailable,
                LivenessCheckResponse(
                    alive = false,
                    timestamp = Clock.System.now(),
                    message = "Liveness check failed: ${e.message}"
                )
            )
        }
    }
    
    private suspend fun handleMetrics(call: ApplicationCall) {
        try {
            val metrics = collectSystemMetrics()
            call.respond(HttpStatusCode.OK, metrics)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "METRICS_ERROR",
                    message = "Failed to collect metrics: ${e.message}",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleAdminStatus(call: ApplicationCall) {
        try {
            val status = collectSystemStatus()
            call.respond(HttpStatusCode.OK, status)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "STATUS_ERROR",
                    message = "Failed to collect system status: ${e.message}",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleSystemInfo(call: ApplicationCall) {
        try {
            val info = collectSystemInfo()
            call.respond(HttpStatusCode.OK, info)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INFO_ERROR",
                    message = "Failed to collect system info: ${e.message}",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun checkSystemHealth(): HealthCheckResponse {
        val checks = mutableMapOf<String, ComponentHealth>()
        val timestamp = Clock.System.now()
        
        // Check API health
        checks["api"] = ComponentHealth(
            status = "healthy",
            message = "API server is running",
            timestamp = timestamp
        )
        
        // Check services health
        jobService?.let {
            try {
                it.findAll()
                checks["job_service"] = ComponentHealth(
                    status = "healthy",
                    message = "Job service is operational",
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                checks["job_service"] = ComponentHealth(
                    status = "unhealthy",
                    message = "Job service error: ${e.message}",
                    timestamp = timestamp
                )
            }
        }
        
        resourcePoolService?.let {
            checks["pool_service"] = ComponentHealth(
                status = "healthy",
                message = "Resource pool service is available",
                timestamp = timestamp
            )
        }
        
        executionEngine?.let {
            try {
                val activeExecutions = it.getActiveExecutions()
                checks["execution_engine"] = ComponentHealth(
                    status = "healthy",
                    message = "Execution engine is operational (${activeExecutions.size} active executions)",
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                checks["execution_engine"] = ComponentHealth(
                    status = "unhealthy",
                    message = "Execution engine error: ${e.message}",
                    timestamp = timestamp
                )
            }
        }
        
        workerManager?.let {
            try {
                val workers = it.getAllWorkers()
                checks["worker_manager"] = ComponentHealth(
                    status = "healthy",
                    message = "Worker manager is operational (${workers.size} workers)",
                    timestamp = timestamp
                )
            } catch (e: Exception) {
                checks["worker_manager"] = ComponentHealth(
                    status = "unhealthy",
                    message = "Worker manager error: ${e.message}",
                    timestamp = timestamp
                )
            }
        }
        
        val allHealthy = checks.values.all { it.status == "healthy" }
        val overall = if (allHealthy) "healthy" else "unhealthy"
        
        return HealthCheckResponse(
            overall = overall,
            timestamp = timestamp,
            checks = checks
        )
    }
    
    private suspend fun checkSystemReadiness(): ReadinessCheckResponse {
        val timestamp = Clock.System.now()
        
        // For MVP, system is ready if basic services are available
        val ready = try {
            jobService?.findAll()
            true
        } catch (e: Exception) {
            false
        }
        
        return ReadinessCheckResponse(
            ready = ready,
            timestamp = timestamp,
            message = if (ready) "System is ready to accept requests" else "System is not ready"
        )
    }
    
    private suspend fun checkSystemLiveness(): LivenessCheckResponse {
        val timestamp = Clock.System.now()
        
        // For MVP, system is alive if we can respond
        return LivenessCheckResponse(
            alive = true,
            timestamp = timestamp,
            message = "System is alive"
        )
    }
    
    private suspend fun collectSystemMetrics(): SystemMetricsResponse {
        val timestamp = Clock.System.now()
        
        val jobMetrics = jobService?.let {
            try {
                val jobs = it.findAll().fold({ emptyList() }, { it })
                JobMetrics(
                    totalJobs = jobs.size,
                    pendingJobs = jobs.count { job -> job.status.name == "PENDING" },
                    runningJobs = jobs.count { job -> job.status.name == "RUNNING" },
                    completedJobs = jobs.count { job -> job.status.name == "COMPLETED" },
                    failedJobs = jobs.count { job -> job.status.name == "FAILED" }
                )
            } catch (e: Exception) {
                JobMetrics(0, 0, 0, 0, 0)
            }
        } ?: JobMetrics(0, 0, 0, 0, 0)
        
        val executionMetrics = executionEngine?.let {
            try {
                val executions = it.getActiveExecutions()
                ExecutionMetrics(
                    activeExecutions = executions.size,
                    totalExecutionsToday = executions.size, // Simplified for MVP
                    avgExecutionTime = 0.0 // Not implemented in MVP
                )
            } catch (e: Exception) {
                ExecutionMetrics(0, 0, 0.0)
            }
        } ?: ExecutionMetrics(0, 0, 0.0)
        
        val workerMetrics = workerManager?.let {
            try {
                val workers = it.getAllWorkers()
                WorkerMetrics(
                    connectedWorkers = workers.size,
                    activeWorkers = workers.count { worker -> worker.status.name == "BUSY" },
                    idleWorkers = workers.count { worker -> worker.status.name == "IDLE" }
                )
            } catch (e: Exception) {
                WorkerMetrics(0, 0, 0)
            }
        } ?: WorkerMetrics(0, 0, 0)
        
        return SystemMetricsResponse(
            timestamp = timestamp,
            jobs = jobMetrics,
            executions = executionMetrics,
            workers = workerMetrics
        )
    }
    
    private suspend fun collectSystemStatus(): AdminStatusResponse {
        val timestamp = Clock.System.now()
        
        return AdminStatusResponse(
            version = "1.0.0-MVP",
            uptime = "unknown", // Not implemented in MVP
            timestamp = timestamp,
            environment = "development",
            features = listOf(
                "jobs", "executions", "templates", "pools", "workers", "grpc"
            ),
            limits = mapOf(
                "maxConcurrentJobs" to "100",
                "maxWorkers" to "50",
                "maxPools" to "10"
            )
        )
    }
    
    private suspend fun collectSystemInfo(): SystemInfoResponse {
        val timestamp = Clock.System.now()
        
        return SystemInfoResponse(
            applicationName = "Hodei Pipelines",
            version = "1.0.0-MVP",
            buildTime = "unknown", // Not implemented in MVP
            gitCommit = "unknown", // Not implemented in MVP
            jvmVersion = System.getProperty("java.version"),
            kotlinVersion = "unknown", // Could be extracted from build info
            timestamp = timestamp,
            properties = mapOf(
                "maxMemory" to "${Runtime.getRuntime().maxMemory() / 1024 / 1024} MB",
                "freeMemory" to "${Runtime.getRuntime().freeMemory() / 1024 / 1024} MB",
                "processors" to "${Runtime.getRuntime().availableProcessors()}"
            )
        )
    }
}