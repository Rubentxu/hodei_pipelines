package dev.rubentxu.hodei.templatemanagement.infrastructure.api.controllers

import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.templatemanagement.domain.entities.Template
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import dev.rubentxu.hodei.templatemanagement.infrastructure.api.dto.*
import dev.rubentxu.hodei.shared.infrastructure.api.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*

private val logger = KotlinLogging.logger {}

fun Route.templateRoutes(templateService: TemplateService) {
    route("/templates") {
        
        get {
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 1
            val pageSize = call.request.queryParameters["pageSize"]?.toIntOrNull() ?: 20
            val status = call.request.queryParameters["status"]?.let { 
                try { TemplateStatus.valueOf(it) } catch (e: IllegalArgumentException) { null }
            }
            val search = call.request.queryParameters["search"]

            try {
                if (search != null) {
                    // Search templates
                    val templates = templateService.searchTemplates(search, status).toList()
                    val response = PaginatedResponse(
                        items = templates.map { it.toDto() },
                        page = page,
                        pageSize = pageSize,
                        totalPages = 1,
                        totalItems = templates.size.toLong()
                    )
                    call.respond(HttpStatusCode.OK, response)
                } else {
                    // List templates with pagination
                    templateService.listTemplates(page, pageSize, status)
                        .fold(
                            { error -> call.respondError(error) },
                            { (templates, total) ->
                                val totalPages = ((total + pageSize - 1) / pageSize).toInt()
                                val response = PaginatedResponse(
                                    items = templates.map { it.toDto() },
                                    page = page,
                                    pageSize = pageSize,
                                    totalPages = totalPages,
                                    totalItems = total
                                )
                                call.respond(HttpStatusCode.OK, response)
                            }
                        )
                }
            } catch (e: Exception) {
                logger.error(e) { "Error listing templates" }
                call.respond(HttpStatusCode.InternalServerError, 
                    ErrorResponse("INTERNAL_ERROR", "Internal server error"))
            }
        }

        post {
            try {
                val request = call.receive<CreateTemplateRequest>()
                val createdBy = call.getUserId() // Assume we have authentication

                templateService.createTemplate(
                    name = request.name,
                    version = "1.0.0", // Default version for new templates
                    spec = request.spec,
                    description = request.description,
                    parentTemplateId = request.parentTemplateId?.let { DomainId(it) },
                    createdBy = createdBy
                ).fold(
                    { error -> call.respondError(error) },
                    { template -> call.respond(HttpStatusCode.Created, template.toDto()) }
                )
            } catch (e: Exception) {
                logger.error(e) { "Error creating template" }
                call.respond(HttpStatusCode.BadRequest, 
                    ErrorResponse("BAD_REQUEST", "Invalid request body"))
            }
        }

        route("/{templateId}") {
            get {
                val templateId = call.parameters["templateId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, 
                    ErrorResponse("BAD_REQUEST", "Missing templateId")
                )

                templateService.getTemplate(DomainId(templateId))
                    .fold(
                        { error -> call.respondError(error) },
                        { template -> call.respond(HttpStatusCode.OK, template.toDto()) }
                    )
            }

            put {
                val templateId = call.parameters["templateId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Missing templateId")
                )

                try {
                    val request = call.receive<UpdateTemplateRequest>()
                    
                    templateService.updateTemplate(
                        templateId = DomainId(templateId),
                        description = request.description,
                        spec = request.spec
                    ).fold(
                        { error -> call.respondError(error) },
                        { template -> call.respond(HttpStatusCode.OK, template.toDto()) }
                    )
                } catch (e: Exception) {
                    logger.error(e) { "Error updating template" }
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse("BAD_REQUEST", "Invalid request body"))
                }
            }

            delete {
                val templateId = call.parameters["templateId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Missing templateId")
                )

                templateService.deleteTemplate(DomainId(templateId))
                    .fold(
                        { error -> call.respondError(error) },
                        { call.respond(HttpStatusCode.NoContent) }
                    )
            }

            post("/publish") {
                val templateId = call.parameters["templateId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Missing templateId")
                )

                try {
                    val request = call.receiveNullable<PublishTemplateRequest>()
                    val version = request?.version ?: "1.0.0"

                    templateService.publishTemplate(DomainId(templateId), version)
                        .fold(
                            { error -> call.respondError(error) },
                            { template -> call.respond(HttpStatusCode.OK, template.toDto()) }
                        )
                } catch (e: Exception) {
                    logger.error(e) { "Error publishing template" }
                    call.respond(HttpStatusCode.BadRequest,
                        ErrorResponse("BAD_REQUEST", "Invalid request body"))
                }
            }

            post("/deprecate") {
                val templateId = call.parameters["templateId"] ?: return@post call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse("BAD_REQUEST", "Missing templateId")
                )

                templateService.deprecateTemplate(DomainId(templateId))
                    .fold(
                        { error -> call.respondError(error) },
                        { template -> call.respond(HttpStatusCode.OK, template.toDto()) }
                    )
            }
        }

        get("/by-name/{templateName}") {
            val templateName = call.parameters["templateName"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", "Missing templateName")
            )

            templateService.getTemplateVersions(templateName)
                .fold(
                    { error -> call.respondError(error) },
                    { templates -> 
                        call.respond(HttpStatusCode.OK, templates.map { it.toDto() })
                    }
                )
        }

        get("/by-name/{templateName}/versions/{version}") {
            val templateName = call.parameters["templateName"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", "Missing templateName")
            )
            val version = call.parameters["version"] ?: return@get call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("BAD_REQUEST", "Missing version")
            )

            templateService.getTemplateByNameAndVersion(templateName, version)
                .fold(
                    { error -> call.respondError(error) },
                    { template -> call.respond(HttpStatusCode.OK, template.toDto()) }
                )
        }
    }
}

private suspend fun ApplicationCall.respondError(error: DomainError) {
    when (error) {
        is NotFoundError -> respond(
            HttpStatusCode.NotFound,
            ErrorResponse("NOT_FOUND", error.message)
        )
        is ValidationError -> respond(
            HttpStatusCode.BadRequest,
            ErrorResponse("VALIDATION_ERROR", error.message)
        )
        is ConflictError -> respond(
            HttpStatusCode.Conflict,
            ErrorResponse("CONFLICT", error.message)
        )
        is BusinessRuleError -> respond(
            HttpStatusCode.UnprocessableEntity,
            ErrorResponse("BUSINESS_RULE", error.message)
        )
        is InsufficientResourcesError -> respond(
            HttpStatusCode.ServiceUnavailable,
            ErrorResponse("INSUFFICIENT_RESOURCES", error.message)
        )
        else -> respond(
            HttpStatusCode.InternalServerError,
            ErrorResponse("INTERNAL_ERROR", "Internal server error")
        )
    }
}

private fun ApplicationCall.getUserId(): String {
    // TODO: Extract user ID from JWT token or session
    // For now, return a placeholder
    return "user-123"
}

private fun Template.toDto(): TemplateDto {
    return TemplateDto(
        id = this.id.value,
        name = this.name,
        description = this.description,
        version = this.version.value,
        status = this.status.name,
        spec = this.spec,
        parentTemplateId = this.parentTemplateId?.value,
        createdAt = this.createdAt.toString(),
        updatedAt = this.updatedAt.toString(),
        createdBy = this.createdBy,
        statistics = buildJsonObject {
            put("totalExecutions", this@toDto.statistics.totalExecutions)
            put("successfulExecutions", this@toDto.statistics.successfulExecutions)
            put("failedExecutions", this@toDto.statistics.failedExecutions)
            put("averageDurationSeconds", this@toDto.statistics.averageDurationSeconds)
            put("successRate", this@toDto.statistics.successRate)
        }
    )
}