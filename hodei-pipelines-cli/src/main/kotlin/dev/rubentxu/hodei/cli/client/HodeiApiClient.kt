package dev.rubentxu.hodei.cli.client

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
 * HTTP client for communicating with the Hodei Orchestrator REST API.
 * 
 * This is the core client that handles all API communication,
 * authentication, and WebSocket streaming.
 */
class HodeiApiClient(
    private val baseUrl: String,
    private val authManager: AuthManager,
    private val verbose: Boolean = false
) {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
        
        if (verbose) {
            install(Logging) {
                level = LogLevel.INFO
            }
        }
        
        install(WebSockets)
    }
    
    // =================== AUTHENTICATION ===================
    
    suspend fun login(url: String, username: String, password: String): Result<LoginResponse> {
        return try {
            val response: HttpResponse = httpClient.post("$url/v1/auth/login") {
                contentType(ContentType.Application.Json)
                setBody(LoginRequest(username, password))
            }
            
            if (response.status.isSuccess()) {
                val loginResponse = response.body<LoginResponse>()
                Result.success(loginResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Login failed: ${response.status} - $errorBody"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            val token = authManager.getCurrentToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response: HttpResponse = httpClient.post("$baseUrl/v1/auth/logout") {
                bearerAuth(token.accessToken)
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Logout failed: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun whoami(): Result<UserInfo> {
        return try {
            val token = authManager.getCurrentToken()
                ?: return Result.failure(Exception("Not authenticated"))
            
            val response: HttpResponse = httpClient.get("$baseUrl/v1/auth/whoami") {
                bearerAuth(token.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val userInfo = response.body<UserInfo>()
                Result.success(userInfo)
            } else {
                Result.failure(Exception("Failed to get user info: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // =================== HEALTH & SYSTEM ===================
    
    suspend fun healthCheck(): Result<HealthResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/health")
            
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
    
    suspend fun getVersion(): Result<VersionResponse> {
        return try {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/admin/info")
            
            if (response.status.isSuccess()) {
                val versionResponse = response.body<VersionResponse>()
                Result.success(versionResponse)
            } else {
                Result.failure(Exception("Failed to get version: ${response.status}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    // =================== RESOURCE POOLS ===================
    
    suspend fun getPools(): Result<List<ResourcePool>> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/pools") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val pools = response.body<List<ResourcePool>>()
                Result.success(pools)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get pools: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun createPool(request: CreatePoolRequest): Result<ResourcePool> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.post("$baseUrl/v1/pools") {
                bearerAuth(it.accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val pool = response.body<ResourcePool>()
                Result.success(pool)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to create pool: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun deletePool(poolId: String): Result<Unit> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.delete("$baseUrl/v1/pools/$poolId") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to delete pool: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun getPoolStatus(poolId: String): Result<ResourcePool> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/pools/$poolId") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val pool = response.body<ResourcePool>()
                Result.success(pool)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get pool status: ${response.status} - $errorBody"))
            }
        }
    }
    
    // =================== JOBS ===================
    
    suspend fun getJobs(): Result<List<Job>> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/jobs") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val jobs = response.body<List<Job>>()
                Result.success(jobs)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get jobs: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun submitJob(request: JobSubmissionRequest): Result<JobSubmissionResponse> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.post("$baseUrl/v1/jobs") {
                bearerAuth(it.accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val jobResponse = response.body<JobSubmissionResponse>()
                Result.success(jobResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to submit job: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun getJobStatus(jobId: String): Result<Job> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/jobs/$jobId") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val job = response.body<Job>()
                Result.success(job)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get job status: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun getJobLogs(jobId: String): Result<JobLogsResponse> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/jobs/$jobId/logs") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val logsResponse = response.body<JobLogsResponse>()
                Result.success(logsResponse)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get job logs: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun cancelJob(jobId: String): Result<Unit> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.delete("$baseUrl/v1/jobs/$jobId") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                Result.success(Unit)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to cancel job: ${response.status} - $errorBody"))
            }
        }
    }
    
    // =================== WORKERS ===================
    
    suspend fun getWorkers(): Result<List<Worker>> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/workers") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val workers = response.body<List<Worker>>()
                Result.success(workers)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get workers: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun getWorkerStatus(workerId: String): Result<Worker> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/workers/$workerId") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val worker = response.body<Worker>()
                Result.success(worker)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get worker status: ${response.status} - $errorBody"))
            }
        }
    }
    
    // =================== TEMPLATES ===================
    
    suspend fun getTemplates(): Result<List<Template>> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/templates") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val templates = response.body<List<Template>>()
                Result.success(templates)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get templates: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun createTemplate(request: CreateTemplateRequest): Result<Template> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.post("$baseUrl/v1/templates") {
                bearerAuth(it.accessToken)
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            
            if (response.status.isSuccess()) {
                val template = response.body<Template>()
                Result.success(template)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to create template: ${response.status} - $errorBody"))
            }
        }
    }
    
    suspend fun getTemplate(templateId: String): Result<Template> {
        return authenticatedRequest {
            val response: HttpResponse = httpClient.get("$baseUrl/v1/templates/$templateId") {
                bearerAuth(it.accessToken)
            }
            
            if (response.status.isSuccess()) {
                val template = response.body<Template>()
                Result.success(template)
            } else {
                val errorBody = response.bodyAsText()
                Result.failure(Exception("Failed to get template: ${response.status} - $errorBody"))
            }
        }
    }
    
    // =================== STREAMING ===================
    
    suspend fun streamJobLogs(jobId: String): Flow<LogEntry> = flow {
        try {
            val token = authManager.getCurrentToken()
                ?: throw Exception("Not authenticated")
            
            httpClient.webSocket(
                method = HttpMethod.Get,
                host = baseUrl.substringAfter("://").substringBefore(":"),
                port = baseUrl.substringAfterLast(":").toIntOrNull() ?: 8080,
                path = "/v1/jobs/$jobId/logs/stream",
                request = {
                    header("Authorization", "Bearer ${token.accessToken}")
                }
            ) {
                for (frame in incoming) {
                    if (frame is Frame.Text) {
                        val text = frame.readText()
                        try {
                            val logEntry = Json.decodeFromString<LogEntry>(text)
                            emit(logEntry)
                        } catch (e: Exception) {
                            // Emit error log entry
                            emit(LogEntry(
                                timestamp = kotlinx.datetime.Clock.System.now().toString(),
                                level = "ERROR",
                                message = "Failed to parse log entry: ${e.message}",
                                source = "cli",
                                metadata = mapOf("rawMessage" to text)
                            ))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            emit(LogEntry(
                timestamp = kotlinx.datetime.Clock.System.now().toString(),
                level = "ERROR", 
                message = "WebSocket connection failed: ${e.message}",
                source = "cli",
                metadata = emptyMap()
            ))
        }
    }
    
    // =================== HELPER METHODS ===================
    
    private suspend fun <T> authenticatedRequest(
        block: suspend (AuthToken) -> Result<T>
    ): Result<T> {
        val token = authManager.getCurrentToken()
            ?: return Result.failure(Exception("Not authenticated. Run 'hp login <url>' first."))
        
        return try {
            block(token)
        } catch (e: Exception) {
            // Check if token expired and suggest re-login
            if (e.message?.contains("401") == true || e.message?.contains("Unauthorized") == true) {
                Result.failure(Exception("Authentication expired. Please run 'hp login <url>' again."))
            } else {
                Result.failure(e)
            }
        }
    }
    
    fun close() {
        httpClient.close()
    }
}