package dev.rubentxu.hodei.execution.infrastructure.api.streaming

import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.http.*
import io.ktor.server.response.*
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun Application.configureExecutionStreaming(executionEngineService: ExecutionEngineService) {
    routing {
        // Simple health endpoint for now
        get("/api/v1/executions/health") {
            call.respond(HttpStatusCode.OK, mapOf(
                "status" to "healthy",
                "service" to "execution-streaming"
            ))
        }
        
        // Active executions status
        get("/api/v1/executions/active") {
            val activeExecutions = executionEngineService.getActiveExecutions()
                .map { context ->
                    mapOf(
                        "executionId" to context.execution.id.value,
                        "jobId" to context.job.id.value,
                        "jobName" to context.job.name,
                        "status" to context.execution.status.name,
                        "workerId" to context.workerId,
                        "startedAt" to context.execution.startedAt?.toString(),
                        "eventCount" to context.events.size,
                        "logCount" to context.logs.size
                    )
                }
            
            call.respond(HttpStatusCode.OK, mapOf(
                "activeExecutions" to activeExecutions,
                "count" to activeExecutions.size
            ))
        }
    }
}