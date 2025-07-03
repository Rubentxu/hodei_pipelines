package dev.rubentxu.hodei.infrastructure.config

import dev.rubentxu.hodei.templatemanagement.application.services.TemplateService
import dev.rubentxu.hodei.jobmanagement.application.services.JobService
import dev.rubentxu.hodei.jobmanagement.application.services.JobAPIService
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.infrastructure.grpc.WorkerManager
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IInstanceManager
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IResourceMonitor
import dev.rubentxu.hodei.resourcemanagement.infrastructure.kubernetes.KubernetesInstanceManager
import dev.rubentxu.hodei.resourcemanagement.infrastructure.kubernetes.KubernetesResourceMonitor
import dev.rubentxu.hodei.domain.worker.WorkerFactory
import dev.rubentxu.hodei.infrastructure.worker.DefaultWorkerFactory
import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.security.domain.repositories.UserRepository
import dev.rubentxu.hodei.security.domain.repositories.RoleRepository
import dev.rubentxu.hodei.security.domain.repositories.PermissionRepository
import dev.rubentxu.hodei.security.domain.repositories.AuditRepository
import dev.rubentxu.hodei.templatemanagement.infrastructure.persistence.InMemoryTemplateRepository
import dev.rubentxu.hodei.jobmanagement.infrastructure.persistence.InMemoryJobRepository
import dev.rubentxu.hodei.resourcemanagement.infrastructure.persistence.InMemoryResourcePoolRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryUserRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryRoleRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryPermissionRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryAuditRepository
import dev.rubentxu.hodei.infrastructure.grpc.GrpcServerManager
import dev.rubentxu.hodei.infrastructure.grpc.OrchestratorGrpcService
import dev.rubentxu.hodei.infrastructure.grpc.WorkerCommunicationService
import dev.rubentxu.hodei.execution.infrastructure.api.controllers.ExecutionController
import dev.rubentxu.hodei.infrastructure.api.controllers.HealthController
import dev.rubentxu.hodei.resourcemanagement.infrastructure.api.PoolController
import dev.rubentxu.hodei.infrastructure.api.controllers.WorkerController
import dev.rubentxu.hodei.infrastructure.api.controllers.AdminController
import dev.rubentxu.hodei.security.infrastructure.api.AuthController
import dev.rubentxu.hodei.templatemanagement.infrastructure.api.controllers.TemplateControllerNew
import dev.rubentxu.hodei.security.application.services.JwtService
import dev.rubentxu.hodei.security.application.services.AuthService
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val appModule = module {
    // Infrastructure adapters - Repositories
    single<TemplateRepository> { InMemoryTemplateRepository() }
    single<JobRepository> { InMemoryJobRepository() }
    single<ResourcePoolRepository> { InMemoryResourcePoolRepository() }
    // Security repositories
    single<UserRepository> { InMemoryUserRepository() }
    single<RoleRepository> { InMemoryRoleRepository() }
    single<PermissionRepository> { InMemoryPermissionRepository() }
    single<AuditRepository> { InMemoryAuditRepository() }
    
    // Infrastructure adapters - Ports implementations
    single<IInstanceManager> { KubernetesInstanceManager() }
    single<IResourceMonitor> { KubernetesResourceMonitor() }
    single<WorkerFactory> { DefaultWorkerFactory(get(), get()) }
    
    // Application services
    singleOf(::TemplateService)
    singleOf(::JobService)
    singleOf(::JobAPIService)
    single<WorkerManager> { WorkerManagerService() }
    single { ResourcePoolService(get(), get()) }
    
    // Security services
    single { JwtService() }
    single { AuthService(get(), get(), get(), get()) }
    
    // Create services in the right order to avoid circular dependencies
    single { 
        ExecutionEngineService(get(), workerManager = get(), workerFactory = get())
    }
    
    single { 
        val executionEngine = get<ExecutionEngineService>()
        val grpcService = OrchestratorGrpcService(executionEngine, get())
        
        // Configure the worker communication after gRPC service is created
        executionEngine.configureWorkerCommunication(grpcService)
        
        grpcService
    }
    
    // Bind WorkerCommunicationService to OrchestratorGrpcService  
    single<WorkerCommunicationService> { get<OrchestratorGrpcService>() }
    
    // gRPC Server
    single { GrpcServerManager(port = 9090, orchestratorService = get()) }
    
    // API Controllers
    single { ExecutionController(get(), get()) }
    single { HealthController(get(), get(), get(), get()) }
    single { dev.rubentxu.hodei.jobmanagement.infrastructure.api.JobController(get()) }
    single { PoolController(get()) }
    single { WorkerController(get()) }
    single { AdminController() }
    single { AuthController(get()) }
    single { TemplateControllerNew(get()) }
}