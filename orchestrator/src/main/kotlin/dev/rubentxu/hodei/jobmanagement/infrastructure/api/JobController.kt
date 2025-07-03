package dev.rubentxu.hodei.jobmanagement.infrastructure.api

import arrow.core.Either
import dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService
import io.github.oshai.kotlinlogging.KotlinLogging
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobContent
import dev.rubentxu.hodei.shared.domain.primitives.RetryPolicy
import dev.rubentxu.hodei.shared.domain.primitives.Version
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.*
import dev.rubentxu.hodei.shared.infrastructure.api.dto.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

private val logger = KotlinLogging.logger {}

class JobController(
    private val jobAPIService: JobAPIService
) {
    
    fun Route.jobRoutes() {
        route("/jobs") {
            get {
                handleListJobs(call)
            }
            
            post {
                handleCreateAdHocJob(call)
            }
            
            post("/from-template") {
                handleCreateJobFromTemplate(call)
            }
            
            route("/{jobId}") {
                get {
                    handleGetJob(call)
                }
                
                delete {
                    handleCancelJob(call)
                }
                
                post("/retry") {
                    handleRetryJob(call)
                }
            }
        }
    }
    
    private suspend fun handleListJobs(call: ApplicationCall) {
        try {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val size = call.request.queryParameters["size"]?.toIntOrNull() ?: 20
            val status = call.request.queryParameters["status"]
            val templateId = call.request.queryParameters["templateId"]
            val createdBy = call.request.queryParameters["createdBy"]
            val search = call.request.queryParameters["search"]
            
            // For now, return all jobs without filters (MVP implementation)
            when (val result = jobAPIService.findAll()) {
                is Either.Left -> {
                    call.respond(HttpStatusCode.InternalServerError, result.value.toErrorResponse())
                }
                is Either.Right -> {
                    val jobs = result.value
                    val totalElements = jobs.size
                    val totalPages = (totalElements + size - 1) / size
                    val startIndex = page * size
                    val endIndex = minOf(startIndex + size, totalElements)
                    
                    val paginatedJobs = if (startIndex < totalElements) {
                        jobs.subList(startIndex, endIndex)
                    } else {
                        emptyList()
                    }
                    
                    val response = JobListResponse(
                        data = paginatedJobs.map { it.toDto() },
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
                }
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
    
    private suspend fun handleCreateAdHocJob(call: ApplicationCall) {
        try {
            val request = call.receive<CreateAdHocJobRequest>()
            
            when (val result = jobAPIService.createAdHocJob(
                name = request.name,
                description = request.description,
                content = request.content.toDomain(),
                parameters = request.parameters,
                poolId = request.poolId?.let { DomainId(it) },
                priority = request.priority,
                retryPolicy = request.retryPolicy?.toDomain(),
                labels = request.labels,
                createdBy = "api-user", // TODO: Extract from auth context
                scheduledAt = request.scheduledAt
            )) {
                is Either.Left -> {
                    when (result.value) {
                        is ValidationError -> call.respond(
                            HttpStatusCode.BadRequest,
                            result.value.toErrorResponse()
                        )
                        else -> call.respond(
                            HttpStatusCode.InternalServerError,
                            result.value.toErrorResponse()
                        )
                    }
                }
                is Either.Right -> {
                    val jobForAPI = result.value
                    call.respond(HttpStatusCode.Created, jobForAPI.toDto())
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "INVALID_REQUEST",
                    message = "Invalid request body: ${e.message}",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleCreateJobFromTemplate(call: ApplicationCall) {
        try {
            val request = call.receive<CreateJobFromTemplateRequest>()
            
            when (val result = jobAPIService.createJobFromTemplate(
                templateId = DomainId(request.templateId),
                templateVersion = request.templateVersion?.let { Version(it) },
                name = request.name,
                description = request.description,
                parameters = request.parameters,
                poolId = request.poolId?.let { DomainId(it) },
                priority = request.priority,
                retryPolicy = request.retryPolicy?.toDomain(),
                labels = request.labels,
                createdBy = "api-user", // TODO: Extract from auth context
                scheduledAt = request.scheduledAt
            )) {
                is Either.Left -> {
                    when (result.value) {
                        is ValidationError -> call.respond(
                            HttpStatusCode.BadRequest,
                            result.value.toErrorResponse()
                        )
                        is NotFoundError -> call.respond(
                            HttpStatusCode.NotFound,
                            result.value.toErrorResponse()
                        )
                        else -> call.respond(
                            HttpStatusCode.InternalServerError,
                            result.value.toErrorResponse()
                        )
                    }
                }
                is Either.Right -> {
                    call.respond(HttpStatusCode.Created, result.value.toDto())
                }
            }
        } catch (e: Exception) {
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponseDto(
                    error = "INVALID_REQUEST",
                    message = "Invalid request body: ${e.message}",
                    timestamp = Clock.System.now(),
                    traceId = call.request.headers["X-Trace-Id"]
                )
            )
        }
    }
    
    private suspend fun handleGetJob(call: ApplicationCall) {
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
            
            when (val result = jobAPIService.findById(DomainId(jobId))) {
                is Either.Left -> {
                    when (result.value) {
                        is NotFoundError -> call.respond(
                            HttpStatusCode.NotFound,
                            result.value.toErrorResponse()
                        )
                        else -> call.respond(
                            HttpStatusCode.InternalServerError,
                            result.value.toErrorResponse()
                        )
                    }
                }
                is Either.Right -> {
                    call.respond(HttpStatusCode.OK, result.value.toDto())
                }
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
    
    private suspend fun handleCancelJob(call: ApplicationCall) {
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
            
            val request = try {
                call.receive<CancelJobRequest>()
            } catch (e: Exception) {
                CancelJobRequest() // Default empty request
            }
            
            when (val result = jobAPIService.cancel(DomainId(jobId), request.reason, request.force)) {
                is Either.Left -> {
                    when (result.value) {
                        is NotFoundError -> call.respond(
                            HttpStatusCode.NotFound,
                            result.value.toErrorResponse()
                        )
                        is BusinessRuleError -> call.respond(
                            HttpStatusCode.Conflict,
                            result.value.toErrorResponse()
                        )
                        else -> call.respond(
                            HttpStatusCode.InternalServerError,
                            result.value.toErrorResponse()
                        )
                    }
                }
                is Either.Right -> {
                    call.respond(HttpStatusCode.OK, result.value.toDto())
                }
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
    
    private suspend fun handleRetryJob(call: ApplicationCall) {
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
            
            when (val result = jobAPIService.retry(DomainId(jobId))) {
                is Either.Left -> {
                    when (result.value) {
                        is NotFoundError -> call.respond(
                            HttpStatusCode.NotFound,
                            result.value.toErrorResponse()
                        )
                        is BusinessRuleError -> call.respond(
                            HttpStatusCode.Conflict,
                            result.value.toErrorResponse()
                        )
                        else -> call.respond(
                            HttpStatusCode.InternalServerError,
                            result.value.toErrorResponse()
                        )
                    }
                }
                is Either.Right -> {
                    call.respond(HttpStatusCode.OK, result.value.toDto())
                }
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