package dev.rubentxu.hodei.infrastructure.api.routes

import dev.rubentxu.hodei.jobmanagement.infrastructure.api.JobController
import dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService
import io.ktor.server.routing.*

fun Route.jobRoutes() {
    // Simple MVP setup without DI
    val jobAPIService = JobAPIService()
    val jobController = JobController(jobAPIService)
    
    with(jobController) {
        jobRoutes()
    }
}