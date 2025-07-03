package dev.rubentxu.hodei.templatemanagement.infrastructure.api.controllers

import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import io.ktor.server.routing.*

class TemplateControllerNew(
    private val templateService: TemplateService
) {
    
    fun Route.templateRoutes() {
        // Delegate to existing template routes implementation
        templateRoutes(templateService)
    }
}