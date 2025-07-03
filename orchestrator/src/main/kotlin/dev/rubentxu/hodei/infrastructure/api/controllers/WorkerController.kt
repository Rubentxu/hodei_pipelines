package dev.rubentxu.hodei.infrastructure.api.controllers

import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.infrastructure.api.dto.*
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.flow.toList

private val logger = KotlinLogging.logger {}

class WorkerController(
    private val workerManager: WorkerManagerService
) {
    fun Route.workerRoutes() {
        route("/workers") {
            get {
                logger.info { "Listing all workers" }
                try {
                    val workers = workerManager.getAllWorkers().map { worker ->
                        WorkerDto(
                            id = worker.id.value,
                            poolId = worker.poolId.value,
                            status = worker.status.name,
                            capabilities = listOf(
                                "cpu:${worker.capabilities.cpu}",
                                "memory:${worker.capabilities.memory}",
                                "storage:${worker.capabilities.storage}"
                            ),
                            currentExecutionId = worker.executionId?.value,
                            lastHeartbeat = worker.lastHeartbeat?.toString() ?: "never",
                            registeredAt = worker.createdAt.toString(),
                            stats = WorkerStatsDto(
                                totalExecutions = 0,
                                successfulExecutions = 0,
                                failedExecutions = 0,
                                averageExecutionTime = 0.0
                            )
                        )
                    }
                    
                    call.respond(HttpStatusCode.OK, workers)
                } catch (e: Exception) {
                    logger.error(e) { "Error listing workers" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "internal_error",
                            message = "Failed to list workers: ${e.message}"
                        )
                    )
                }
            }
            
            get("{workerId}") {
                val workerId = call.parameters["workerId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "missing_parameter", message = "Worker ID is required")
                )
                
                logger.info { "Getting worker details: $workerId" }
                try {
                    val worker = workerManager.getWorkerById(workerId)
                    if (worker != null) {
                        val workerDto = WorkerDto(
                            id = worker.id.value,
                            poolId = worker.poolId.value,
                            status = worker.status.name,
                            capabilities = listOf(
                                "cpu:${worker.capabilities.cpu}",
                                "memory:${worker.capabilities.memory}",
                                "storage:${worker.capabilities.storage}"
                            ),
                            currentExecutionId = worker.executionId?.value,
                            lastHeartbeat = worker.lastHeartbeat?.toString() ?: "never",
                            registeredAt = worker.createdAt.toString(),
                            stats = WorkerStatsDto(
                                totalExecutions = 0,
                                successfulExecutions = 0,
                                failedExecutions = 0,
                                averageExecutionTime = 0.0
                            )
                        )
                        call.respond(HttpStatusCode.OK, workerDto)
                    } else {
                        call.respond(
                            HttpStatusCode.NotFound,
                            ErrorResponse(error = "not_found", message = "Worker not found")
                        )
                    }
                } catch (e: Exception) {
                    logger.error(e) { "Error getting worker $workerId" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "internal_error",
                            message = "Failed to get worker: ${e.message}"
                        )
                    )
                }
            }
            
            delete("{workerId}") {
                val workerId = call.parameters["workerId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest,
                    ErrorResponse(error = "missing_parameter", message = "Worker ID is required")
                )
                
                logger.info { "Terminating worker: $workerId" }
                try {
                    workerManager.unregisterWorker(workerId)
                    call.respond(HttpStatusCode.NoContent)
                } catch (e: Exception) {
                    logger.error(e) { "Error terminating worker $workerId" }
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        ErrorResponse(
                            error = "internal_error",
                            message = "Failed to terminate worker: ${e.message}"
                        )
                    )
                }
            }
        }
    }
}