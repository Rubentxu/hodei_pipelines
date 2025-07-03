package dev.rubentxu.hodei.execution.infrastructure.api.controllers

import arrow.core.Either
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.execution.infrastructure.api.dto.*
import dev.rubentxu.hodei.shared.infrastructure.api.dto.ErrorResponseDto
import dev.rubentxu.hodei.shared.infrastructure.api.dto.PaginationMetaDto
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

class ExecutionController(
    private val executionEngine: ExecutionEngineService,
    private val jobAPIService: JobAPIService
) {
    
    fun Route.executionRoutes() {
        route("/executions") {
            get {
                handleListExecutions(call)
            }
            
            route("/{executionId}") {
                get {
                    handleGetExecution(call)
                }
                
                delete {
                    handleCancelExecution(call)
                }
                
                get("/logs") {
                    handleGetExecutionLogs(call)
                }
                
                get("/events") {
                    handleGetExecutionEvents(call)
                }
                
                get("/replay") {
                    handleGetExecutionReplay(call)
                }
            }
        }
        
        // Jobs execution management
        route("/jobs/{jobId}/executions") {
            get {
                handleListJobExecutions(call)
            }
            
            post {
                // Direct execution is no longer allowed. All jobs must go through orchestrator.
                call.respond(
                    HttpStatusCode.Forbidden,
                    ErrorResponseDto(
                        error = "DIRECT_EXECUTION_FORBIDDEN",
                        message = "Direct job execution is not allowed. Please submit jobs through POST /jobs endpoint which will automatically queue them for execution.",
                        timestamp = Clock.System.now(),
                        traceId = call.request.headers["X-Trace-Id"]
                    )
                )
            }
        }
    }
    
    private suspend fun handleListExecutions(call: ApplicationCall) {
        try {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            val status = call.request.queryParameters["status"]
            val jobId = call.request.queryParameters["jobId"]
            val workerId = call.request.queryParameters["workerId"]
            
            // Get all active executions from execution engine
            val activeExecutions = executionEngine.getActiveExecutions()
            
            // Simple pagination for MVP
            val totalElements = activeExecutions.size
            val totalPages = (totalElements + size - 1) / size
            val startIndex = page * size
            val endIndex = minOf(startIndex + size, totalElements)
            
            val paginatedExecutions = if (startIndex < totalElements) {
                activeExecutions.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            val response = ExecutionListResponse(
                data = paginatedExecutions.map { context ->
                    ExecutionDto(
                        id = context.execution.id.value,
                        jobId = context.job.id.value,
                        workerId = context.workerId,
                        status = context.execution.status.name,
                        createdAt = context.execution.createdAt,
                        startedAt = context.execution.startedAt,
                        completedAt = context.execution.completedAt,
                        exitCode = context.execution.exitCode,
                        failureReason = context.execution.errorMessage
                    )
                },
                meta = PaginationMetaDto(
                    page = page,
                    size = size,
                    totalElements = totalElements,
                    totalPages = totalPages,
                    hasNext = page < totalPages - 1,
                    hasPrevious = page > 0
                )
            )
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleGetExecution(call: ApplicationCall) {
        try {
            val executionId = call.parameters["executionId"] ?: return call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "MISSING_PARAMETER",
                    message = "Missing executionId parameter",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
            
            // Find execution in active executions
            val execution = executionEngine.getActiveExecutions()
                .find { it.execution.id.value == executionId }
            
            if (execution != null) {
                val response = ExecutionDto(
                    id = execution.execution.id.value,
                    jobId = execution.job.id.value,
                    workerId = execution.workerId,
                    status = execution.execution.status.name,
                    createdAt = execution.execution.createdAt,
                    startedAt = execution.execution.startedAt,
                    completedAt = execution.execution.completedAt,
                    exitCode = execution.execution.exitCode,
                    failureReason = execution.execution.errorMessage
                )
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponseDto(
                        error = "NOT_FOUND",
                        message = "Execution not found: $executionId",
                        timestamp = Clock.System.now(),
                        traceId = call.request.headers["X-Trace-Id"]
                    )
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleCancelExecution(call: ApplicationCall) {
        try {
            val executionId = call.parameters["executionId"] ?: return call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "MISSING_PARAMETER",
                    message = "Missing executionId parameter",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
            
            val request = try {
                call.receive<CancelExecutionRequest>()
            } catch (e: Exception) {
                CancelExecutionRequest() // Default empty request
            }
            
            // Find the execution context
            val executionContext = executionEngine.getActiveExecutions()
                .find { it.execution.id.value == executionId }
                
            if (executionContext != null) {
                // Cancel via execution engine
                when (val result = executionEngine.cancelExecution(
                    executionContext.execution.id,
                    request.reason ?: "Manual cancellation"
                )) {
                    is Either.Left -> {
                        call.respond(
                            HttpStatusCode.InternalServerError,
                            ErrorResponseDto(
                                error = "EXECUTION_ERROR",
                                message = result.value.toString(),
                                timestamp = Clock.System.now(),
                                traceId = call.request.headers["X-Trace-Id"]
                            )
                        )
                    }
                    is Either.Right -> {
                        call.respond(HttpStatusCode.OK, mapOf("message" to "Execution cancelled"))
                    }
                }
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponseDto(
                        error = "NOT_FOUND",
                        message = "Execution not found: $executionId",
                        timestamp = Clock.System.now(),
                        traceId = call.request.headers["X-Trace-Id"]
                    )
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleListJobExecutions(call: ApplicationCall) {
        try {
            val jobId = call.parameters["jobId"] ?: return call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "MISSING_PARAMETER",
                    message = "Missing jobId parameter",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
            
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            
            // Filter executions by jobId
            val jobExecutions = executionEngine.getActiveExecutions()
                .filter { it.job.id.value == jobId }
            
            // Simple pagination
            val totalElements = jobExecutions.size
            val totalPages = (totalElements + size - 1) / size
            val startIndex = page * size
            val endIndex = minOf(startIndex + size, totalElements)
            
            val paginatedExecutions = if (startIndex < totalElements) {
                jobExecutions.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            val response = ExecutionListResponse(
                data = paginatedExecutions.map { context ->
                    ExecutionDto(
                        id = context.execution.id.value,
                        jobId = context.job.id.value,
                        workerId = context.workerId,
                        status = context.execution.status.name,
                        createdAt = context.execution.createdAt,
                        startedAt = context.execution.startedAt,
                        completedAt = context.execution.completedAt,
                        exitCode = context.execution.exitCode,
                        failureReason = context.execution.errorMessage
                    )
                },
                meta = PaginationMetaDto(
                    page = page,
                    size = size,
                    totalElements = totalElements,
                    totalPages = totalPages,
                    hasNext = page < totalPages - 1,
                    hasPrevious = page > 0
                )
            )
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleGetExecutionLogs(call: ApplicationCall) {
        try {
            val executionId = call.parameters["executionId"] ?: return call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "MISSING_PARAMETER",
                    message = "Missing executionId parameter",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
            
            // For MVP, return empty logs with note
            val response = ExecutionLogsResponse(
                logs = emptyList(),
                message = "Log streaming not implemented in MVP"
            )
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleGetExecutionEvents(call: ApplicationCall) {
        try {
            val executionId = call.parameters["executionId"] ?: return call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "MISSING_PARAMETER",
                    message = "Missing executionId parameter",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
            
            // For MVP, return empty events with note
            val response = ExecutionEventsResponse(
                events = emptyList(),
                message = "Event streaming not implemented in MVP"
            )
            
            call.respond(HttpStatusCode.OK, response)
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleGetExecutionReplay(call: ApplicationCall) {
        try {
            val executionId = call.parameters["executionId"] ?: return call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "MISSING_PARAMETER",
                    message = "Missing executionId parameter",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
            
            // For MVP, return basic replay info
            val execution = executionEngine.getActiveExecutions()
                .find { it.execution.id.value == executionId }
            
            if (execution != null) {
                val response = ExecutionReplayResponse(
                    executionId = execution.execution.id.value,
                    jobId = execution.job.id.value,
                    status = execution.execution.status.name,
                    events = emptyList(),
                    logs = emptyList(),
                    message = "Full replay not implemented in MVP"
                )
                call.respond(HttpStatusCode.OK, response)
            } else {
                call.respond(
                    HttpStatusCode.NotFound,
                    ErrorResponseDto(
                        error = "NOT_FOUND",
                        message = "Execution not found: $executionId",
                        timestamp = Clock.System.now(),
                        traceId = call.request.headers["X-Trace-Id"]
                    )
                )
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponseDto(
                    error = "INTERNAL_ERROR",
                    message = "Internal server error",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
}