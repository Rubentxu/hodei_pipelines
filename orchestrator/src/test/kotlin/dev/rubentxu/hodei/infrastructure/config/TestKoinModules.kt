package dev.rubentxu.hodei.infrastructure.config

import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import dev.rubentxu.hodei.jobmanagement.application.services.JobService
import dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import dev.rubentxu.hodei.templatemanagement.infrastructure.persistence.InMemoryTemplateRepository
import dev.rubentxu.hodei.jobmanagement.infrastructure.persistence.InMemoryJobRepository
import dev.rubentxu.hodei.execution.infrastructure.api.controllers.ExecutionController
import dev.rubentxu.hodei.infrastructure.api.controllers.HealthController
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.JobController
import dev.rubentxu.hodei.infrastructure.api.controllers.WorkerController
import dev.rubentxu.hodei.infrastructure.api.controllers.AdminController
import dev.rubentxu.hodei.templatemanagement.infrastructure.api.controllers.TemplateControllerNew
import dev.rubentxu.hodei.security.application.services.JwtService
import dev.rubentxu.hodei.security.application.services.AuthService
import dev.rubentxu.hodei.security.domain.repositories.UserRepository
import dev.rubentxu.hodei.security.domain.repositories.RoleRepository
import dev.rubentxu.hodei.security.domain.repositories.PermissionRepository
import dev.rubentxu.hodei.security.domain.repositories.AuditRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryUserRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryRoleRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryPermissionRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryAuditRepository
import dev.rubentxu.hodei.security.infrastructure.api.AuthController
import dev.rubentxu.hodei.domain.worker.WorkerFactory
import dev.rubentxu.hodei.domain.worker.WorkerInstance
import dev.rubentxu.hodei.shared.domain.errors.WorkerCreationError
import dev.rubentxu.hodei.shared.domain.errors.WorkerDeletionError
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import dev.rubentxu.hodei.resourcemanagement.infrastructure.persistence.InMemoryResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.infrastructure.api.PoolController
import arrow.core.Either
import arrow.core.right
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import dev.rubentxu.hodei.infrastructure.grpc.MockWorkerCommunicationService
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IResourceMonitor
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePoolUtilization
import kotlinx.datetime.Clock

/**
 * Test module that provides security dependencies for integration tests
 */
val testSecurityModule = module {
    // Security repositories
    single<UserRepository> { InMemoryUserRepository() }
    single<RoleRepository> { InMemoryRoleRepository() }
    single<PermissionRepository> { InMemoryPermissionRepository() }
    single<AuditRepository> { InMemoryAuditRepository() }
    
    // Security services
    single { JwtService() }
    single { AuthService(get(), get(), get(), get()) }
    
    // Security controller
    single { AuthController(get()) }
}

/**
 * Test module without gRPC services for basic integration tests
 */
val testBasicModule = module {
    // Infrastructure adapters - Repositories
    single<TemplateRepository> { InMemoryTemplateRepository() }
    single<JobRepository> { InMemoryJobRepository() }
    single<ResourcePoolRepository> { InMemoryResourcePoolRepository() }
    
    // Application services (without gRPC dependencies)
    singleOf(::TemplateService)
    singleOf(::JobService)
    singleOf(::JobAPIService)
    single { 
        val workerManager = WorkerManagerService()
        // Register worker synchronously during initialization
        kotlinx.coroutines.runBlocking {
            workerManager.registerWorker("test-worker-1")
            println("TestKoinModule: Registered test worker, found ${workerManager.getRegisteredWorkers().size} workers")
        }
        workerManager
    }
    single { ResourcePoolService(get(), get()) }
    
    // Mock WorkerFactory for tests
    single<WorkerFactory> {
        object : WorkerFactory {
            override suspend fun createWorker(job: Job, resourcePool: ResourcePool): Either<WorkerCreationError, WorkerInstance> {
                // Use the pre-registered worker ID
                return WorkerInstance(
                    workerId = "test-worker-1",
                    poolId = resourcePool.id.value,
                    poolType = resourcePool.type,
                    instanceType = "test"
                ).right()
            }
            override suspend fun destroyWorker(workerId: String): Either<WorkerDeletionError, Unit> = Unit.right()
            override fun supportsPoolType(poolType: String): Boolean = true
        }
    }
    
    // Simple execution engine without gRPC
    single { 
        ExecutionEngineService(get(), workerCommunicationService = MockWorkerCommunicationService(), workerManager = get(), workerFactory = get())
    }
    
    // API Controllers
    single { ExecutionController(get(), get()) }
    single { HealthController(get(), get(), get(), get()) }
    single { JobController(get()) }
    single { PoolController(get()) }
    single { WorkerController(get()) }
    single { AdminController() }
    single { TemplateControllerNew(get()) }
}

