package dev.rubentxu.hodei.pipelines.dsl.cli.client

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.websocket.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlinx.datetime.Instant

/**
 * Client for communicating with the Hodei Pipelines Orchestrator.
 * 
 * Provides methods to submit jobs, monitor execution status, and receive
 * real-time updates via WebSocket connections.
 */
class OrchestratorClient(
    private val baseUrl: String = "http://localhost:8080",
    private val websocketUrl: String = "ws://localhost:8080"
) {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        install(Logging) {
            level = LogLevel.INFO
        }
        
        install(WebSockets)
    }
    
    /**
     * Submit a job for execution
     */
    suspend fun submitJob(jobRequest: JobSubmissionRequest): Result<JobSubmissionResponse> {
        return try {
            val response: HttpResponse = httpClient.post("$baseUrl/api/v1/jobs") {
                contentType(ContentType.Application.Json)
                setBody(jobRequest)
            }
            
            if (response.status.isSuccess()) {
                val jobResponse = response.body<JobSubmissionResponse>()
                Result.success(jobResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to submit job: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get job execution status
     */
    suspend fun getJobStatus(jobId: String): Result<JobStatusResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v1/jobs/$jobId")
            
            if (response.status.isSuccess()) {
                val statusResponse = response.body<JobStatusResponse>()
                Result.success(statusResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get job status: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get job execution logs
     */
    suspend fun getJobLogs(jobId: String): Result<JobLogsResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v1/jobs/$jobId/logs")
            
            if (response.status.isSuccess()) {
                val logsResponse = response.body<JobLogsResponse>()
                Result.success(logsResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get job logs: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Stream real-time execution updates via WebSocket
     */
    suspend fun streamJobUpdates(jobId: String): Flow<ExecutionUpdate> = flow {
        try {
            httpClient.webSocket(
                method = HttpMethod.Get,
                host = websocketUrl.substringAfter("://").substringBefore(":"),
                port = websocketUrl.substringAfterLast(":").toIntOrNull() ?: 8080,
                path = "/api/v1/jobs/$jobId/stream"
            ) {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val update = Json.decodeFromString<ExecutionUpdate>(text)
                            emit(update)
                        } catch (e: Exception) {
                            // Emit error update
                            emit(ExecutionUpdate(
                                jobId = jobId,
                                timestamp = kotlinx.datetime.Clock.System.now(),
                                type = UpdateType.ERROR,
                                message = "Failed to parse update: ${e.message}",
                                data = mapOf("rawMessage" to text)
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(ExecutionUpdate(
                jobId = jobId,
                timestamp = kotlinx.datetime.Clock.System.now(),
                type = UpdateType.ERROR,
                message = "WebSocket connection failed: ${e.message}",
                data = emptyMap()
            ))
        }
    }
    
    /**
     * Check orchestrator health
     */
    suspend fun healthCheck(): Result<HealthResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/health")
            
            if (response.status.isSuccess()) {
                val healthResponse = response.body<HealthResponse>()
                Result.success(healthResponse)
            } else {
                Result.failure(Exception("Health check failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * List available resource pools
     */
    suspend fun getResourcePools(): Result<List<ResourcePoolInfo>> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/api/v1/pools")
            
            if (response.status.isSuccess()) {
                val pools = response.body<List<ResourcePoolInfo>>()
                Result.success(pools)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get resource pools: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Close the HTTP client
     */
    fun close() {
        httpClient.close()
    }
}

/**
 * Data classes for API communication
 */
@Serializable
data class JobSubmissionRequest(
    val name: String,
    val description: String? = null,
    val pipelineContent: String,
    val type: String = "pipeline",
    val priority: String = "normal",
    val resourcePoolId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    val timeout: Long? = null
)

@Serializable
data class JobSubmissionResponse(
    val jobId: String,
    val status: String,
    val message: String,
    val estimatedDuration: Long? = null,
    val queuePosition: Int? = null
)

@Serializable
data class JobStatusResponse(
    val jobId: String,
    val name: String,
    val status: String,
    val progress: Double? = null,
    val currentStep: String? = null,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val duration: Long? = null,
    val resourcePoolId: String? = null,
    val workerId: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class JobLogsResponse(
    val jobId: String,
    val logs: List<LogEntry>,
    val totalLines: Int,
    val hasMore: Boolean = false
)

@Serializable
data class LogEntry(
    val timestamp: String,
    val level: String,
    val message: String,
    val source: String? = null,
    val metadata: Map<String, String> = emptyMap()
)

@Serializable
data class ExecutionUpdate(
    val jobId: String,
    val timestamp: Instant,
    val type: UpdateType,
    val message: String,
    val data: Map<String, String> = emptyMap()
)

@Serializable
enum class UpdateType {
    STATUS_CHANGE,
    LOG_OUTPUT,
    PROGRESS,
    ERROR,
    COMPLETION
}

@Serializable
data class HealthResponse(
    val status: String,
    val version: String? = null,
    val uptime: Long? = null,
    val services: Map<String, String> = emptyMap()
)

@Serializable
data class ResourcePoolInfo(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val capacity: PoolCapacity? = null,
    val utilization: PoolUtilization? = null
)

@Serializable
data class PoolCapacity(
    val maxInstances: Int,
    val cpuCores: Int,
    val memoryMB: Long,
    val storageGB: Long? = null
)

@Serializable
data class PoolUtilization(
    val currentInstances: Int,
    val cpuUsagePercent: Double,
    val memoryUsagePercent: Double,
    val storageUsagePercent: Double? = null
)