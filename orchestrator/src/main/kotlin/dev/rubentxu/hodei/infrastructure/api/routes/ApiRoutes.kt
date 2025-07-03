package dev.rubentxu.hodei.infrastructure.api.routes

import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.infrastructure.api.controllers.*
import dev.rubentxu.hodei.execution.infrastructure.api.controllers.ExecutionController
import dev.rubentxu.hodei.templatemanagement.infrastructure.api.controllers.TemplateControllerNew
import dev.rubentxu.hodei.resourcemanagement.infrastructure.api.PoolController
import dev.rubentxu.hodei.security.infrastructure.api.AuthController
import dev.rubentxu.hodei.templatemanagement.infrastructure.persistence.InMemoryTemplateRepository
import dev.rubentxu.hodei.resourcemanagement.infrastructure.kubernetes.KubernetesInstanceManager
import dev.rubentxu.hodei.resourcemanagement.infrastructure.kubernetes.KubernetesResourceMonitor
import dev.rubentxu.hodei.infrastructure.worker.DefaultWorkerFactory
import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import dev.rubentxu.hodei.resourcemanagement.infrastructure.persistence.InMemoryResourcePoolRepository
import io.ktor.server.routing.*
import org.koin.ktor.ext.get
import io.ktor.server.application.*
import org.koin.ktor.ext.inject

/**
 * Configures all API routes for the application
 * Uses manual dependency creation for compatibility with tests
 */
fun Route.configureApiRoutes() {
    route("/v1") {
        // Initialize services (MVP - simple initialization)
        val jobRepository = dev.rubentxu.hodei.jobmanagement.infrastructure.persistence.InMemoryJobRepository()
        val templateRepository = InMemoryTemplateRepository()
        val resourcePoolRepository = dev.rubentxu.hodei.resourcemanagement.infrastructure.persistence.InMemoryResourcePoolRepository()
        val instanceManager = KubernetesInstanceManager()
        val resourceMonitor = KubernetesResourceMonitor()
        val workerFactory = DefaultWorkerFactory(instanceManager, resourceMonitor)
        val workerManager = WorkerManagerService()
        
        // Initialize application services  
        val jobAPIService = dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService() // Shared between both controllers
        val jobService = dev.rubentxu.hodei.jobmanagement.application.services.JobService(jobRepository) // Only for health controller
        val resourcePoolService = ResourcePoolService(resourcePoolRepository, workerManager)
        val executionEngine = ExecutionEngineService(jobRepository, workerManager = workerManager, workerFactory = workerFactory)
        val templateService = TemplateService(templateRepository)
        
        // Initialize controllers - job and execution use jobAPIService for consistency
        val jobController = dev.rubentxu.hodei.jobmanagement.infrastructure.api.JobController(jobAPIService)
        val executionController = ExecutionController(executionEngine, jobAPIService)
        val poolController = dev.rubentxu.hodei.resourcemanagement.infrastructure.api.PoolController(resourcePoolService)
        val healthController = HealthController(
            jobService = jobAPIService,
            resourcePoolService = resourcePoolService,
            executionEngine = executionEngine,
            workerManager = workerManager
        )
        val templateController = TemplateControllerNew(templateService)
        val workerController = WorkerController(workerManager)
        val adminController = AdminController()
        
        // Try to get AuthController from Koin using application context
        val authController = try {
            application.inject<AuthController>().value
        } catch (e: Exception) {
            // If security dependencies are not available, skip auth routes
            null
        }
        
        // Configure routes
        with(templateController) {
            templateRoutes()
        }
        
        with(jobController) {
            jobRoutes()
        }
        
        with(executionController) {
            executionRoutes()
        }
        
        with(poolController) {
            poolRoutes()
        }
        
        with(workerController) {
            workerRoutes()
        }
        
        with(healthController) {
            healthRoutes()
        }
        
        with(adminController) {
            adminRoutes()
        }
        
        // Configure auth routes only if available
        authController?.let { controller ->
            with(controller) {
                authRoutes()
            }
        }
    }
}