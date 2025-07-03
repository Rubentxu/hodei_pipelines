package dev.rubentxu.hodei.infrastructure.api.controllers

import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceQuotas
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceLimit
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

fun Route.resourcePoolRoutes(resourcePoolService: ResourcePoolService) {
    
    route("/api/v1/pools") {
        
        // List all resource pools
        get {
            try {
                val pools = resourcePoolService.listPools()
                call.respond(HttpStatusCode.OK, pools)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to list pools: ${e.message}"))
            }
        }
        
        // Create new resource pool
        post {
            try {
                val request = call.receive<CreateResourcePoolRequest>()
                
                val quotas = request.resourceQuotas?.let { quotaReq ->
                    ResourceQuotas(
                        cpu = quotaReq.cpu?.let { ResourceLimit(it.requests, it.limits) },
                        memory = quotaReq.memory?.let { ResourceLimit(it.requests, it.limits) },
                        storage = quotaReq.storage?.let { ResourceLimit(it.requests, it.limits) },
                        maxWorkers = quotaReq.maxWorkers,
                        maxJobs = quotaReq.maxJobs,
                        maxConcurrentJobs = quotaReq.maxConcurrentJobs,
                        customLimits = quotaReq.customLimits ?: emptyMap()
                    )
                } ?: ResourceQuotas.basic()
                
                val result = resourcePoolService.createPool(
                    name = request.name,
                    displayName = request.displayName,
                    description = request.description,
                    resourceQuotas = quotas,
                    labels = request.labels ?: emptyMap(),
                    annotations = request.annotations ?: emptyMap(),
                    createdBy = call.principal()?.username ?: "unknown"
                )
                
                result.fold(
                    { error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error)) },
                    { pool -> call.respond(HttpStatusCode.Created, pool) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to create pool: ${e.message}"))
            }
        }
        
        // Get specific resource pool
        get("/{poolId}") {
            try {
                val poolId = call.parameters["poolId"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool ID is required")
                )
                
                val result = resourcePoolService.getPool(DomainId(poolId))
                result.fold(
                    { error -> call.respond(HttpStatusCode.NotFound, mapOf("error" to error)) },
                    { pool -> call.respond(HttpStatusCode.OK, pool) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to get pool: ${e.message}"))
            }
        }
        
        // Get resource pool by name
        get("/name/{poolName}") {
            try {
                val poolName = call.parameters["poolName"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool name is required")
                )
                
                val result = resourcePoolService.getPoolByName(poolName)
                result.fold(
                    { error -> call.respond(HttpStatusCode.NotFound, mapOf("error" to error)) },
                    { pool -> call.respond(HttpStatusCode.OK, pool) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to get pool: ${e.message}"))
            }
        }
        
        // Update resource pool quotas
        put("/{poolId}/quotas") {
            try {
                val poolId = call.parameters["poolId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool ID is required")
                )
                
                val request = call.receive<UpdateQuotasRequest>()
                
                val quotas = ResourceQuotas(
                    cpu = request.cpu?.let { ResourceLimit(it.requests, it.limits) },
                    memory = request.memory?.let { ResourceLimit(it.requests, it.limits) },
                    storage = request.storage?.let { ResourceLimit(it.requests, it.limits) },
                    maxWorkers = request.maxWorkers,
                    maxJobs = request.maxJobs,
                    maxConcurrentJobs = request.maxConcurrentJobs,
                    customLimits = request.customLimits ?: emptyMap()
                )
                
                val result = resourcePoolService.updateQuotas(DomainId(poolId), quotas)
                result.fold(
                    { error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error)) },
                    { pool -> call.respond(HttpStatusCode.OK, pool) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to update quotas: ${e.message}"))
            }
        }
        
        // Add label to resource pool
        put("/{poolId}/labels/{key}") {
            try {
                val poolId = call.parameters["poolId"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool ID is required")
                )
                val key = call.parameters["key"] ?: return@put call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Label key is required")
                )
                
                val request = call.receive<LabelRequest>()
                
                val result = resourcePoolService.addLabel(DomainId(poolId), key, request.value)
                result.fold(
                    { error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error)) },
                    { pool -> call.respond(HttpStatusCode.OK, pool) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to add label: ${e.message}"))
            }
        }
        
        // Remove label from resource pool
        delete("/{poolId}/labels/{key}") {
            try {
                val poolId = call.parameters["poolId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool ID is required")
                )
                val key = call.parameters["key"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Label key is required")
                )
                
                val result = resourcePoolService.removeLabel(DomainId(poolId), key)
                result.fold(
                    { error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error)) },
                    { pool -> call.respond(HttpStatusCode.OK, pool) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to remove label: ${e.message}"))
            }
        }
        
        // Get resource pool usage statistics
        get("/{poolName}/usage") {
            try {
                val poolName = call.parameters["poolName"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool name is required")
                )
                
                val result = resourcePoolService.getPoolResourceUsage(poolName)
                result.fold(
                    { error -> call.respond(HttpStatusCode.NotFound, mapOf("error" to error)) },
                    { usage -> call.respond(HttpStatusCode.OK, usage) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to get usage: ${e.message}"))
            }
        }
        
        // Check quota violations
        get("/{poolName}/violations") {
            try {
                val poolName = call.parameters["poolName"] ?: return@get call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool name is required")
                )
                
                val violations = resourcePoolService.checkQuotaViolations(poolName)
                call.respond(HttpStatusCode.OK, mapOf("violations" to violations))
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to check violations: ${e.message}"))
            }
        }
        
        // Delete resource pool
        delete("/{poolId}") {
            try {
                val poolId = call.parameters["poolId"] ?: return@delete call.respond(
                    HttpStatusCode.BadRequest, mapOf("error" to "Pool ID is required")
                )
                
                val result = resourcePoolService.deletePool(DomainId(poolId))
                result.fold(
                    { error -> call.respond(HttpStatusCode.BadRequest, mapOf("error" to error)) },
                    { call.respond(HttpStatusCode.NoContent) }
                )
                
            } catch (e: Exception) {
                call.respond(HttpStatusCode.InternalServerError, mapOf("error" to "Failed to delete pool: ${e.message}"))
            }
        }
    }
}

// Request/Response DTOs
@Serializable
data class CreateResourcePoolRequest(
    val name: String,
    val displayName: String? = null,
    val description: String? = null,
    val resourceQuotas: ResourceQuotasRequest? = null,
    val labels: Map<String, String>? = null,
    val annotations: Map<String, String>? = null
)

@Serializable
data class ResourceQuotasRequest(
    val cpu: ResourceLimitRequest? = null,
    val memory: ResourceLimitRequest? = null,
    val storage: ResourceLimitRequest? = null,
    val maxWorkers: Int? = null,
    val maxJobs: Int? = null,
    val maxConcurrentJobs: Int? = null,
    val customLimits: Map<String, String>? = null
)

@Serializable
data class ResourceLimitRequest(
    val requests: String,
    val limits: String
)

@Serializable
data class UpdateQuotasRequest(
    val cpu: ResourceLimitRequest? = null,
    val memory: ResourceLimitRequest? = null,
    val storage: ResourceLimitRequest? = null,
    val maxWorkers: Int? = null,
    val maxJobs: Int? = null,
    val maxConcurrentJobs: Int? = null,
    val customLimits: Map<String, String>? = null
)

@Serializable
data class LabelRequest(
    val value: String
)

// Simple user principal for authentication context
data class UserPrincipal(val username: String)

// Extension to get user principal (simplified)
fun ApplicationCall.principal(): UserPrincipal? {
    // In a real implementation, this would extract from JWT or session
    return UserPrincipal("default-user")
}