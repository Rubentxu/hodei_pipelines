package dev.rubentxu.hodei.templatemanagement.infrastructure.api.routes

import dev.rubentxu.hodei.templatemanagement.infrastructure.api.controllers.templateRoutes
import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import dev.rubentxu.hodei.templatemanagement.infrastructure.persistence.InMemoryTemplateRepository
import io.ktor.server.routing.*

fun Route.templateRoutes() {
    // Simple MVP setup without DI
    val templateRepository = InMemoryTemplateRepository()
    val templateService = TemplateService(templateRepository)
    templateRoutes(templateService)
}