package dev.rubentxu.hodei.pipelines.infrastructure.config

import dev.rubentxu.hodei.pipelines.application.CreateAndExecuteJobUseCase
import dev.rubentxu.hodei.pipelines.application.RegisterWorkerUseCase
import dev.rubentxu.hodei.pipelines.infrastructure.event.InMemoryEventPublisher
import dev.rubentxu.hodei.pipelines.infrastructure.repository.InMemoryJobRepository
import dev.rubentxu.hodei.pipelines.infrastructure.repository.InMemoryWorkerRepository
import dev.rubentxu.hodei.pipelines.infrastructure.service.InMemoryJobExecutor
import dev.rubentxu.hodei.pipelines.infrastructure.service.InMemoryWorkerManagementService
import dev.rubentxu.hodei.pipelines.port.*
import mu.KotlinLogging

/**
 * Configuration class for MVP with in-memory implementations
 * Provides dependency injection setup for all components
 */
class InMemoryConfiguration {

    private val logger = KotlinLogging.logger {}

    // Repositories
    private val jobRepository: JobRepository = InMemoryJobRepository()
    private val workerRepository: WorkerRepository = InMemoryWorkerRepository()
    
    // Services
    private val jobExecutor: JobExecutor = InMemoryJobExecutor()
    private val workerManagementService: WorkerManagementService = InMemoryWorkerManagementService(workerRepository)
    private val eventPublisher: EventPublisher = InMemoryEventPublisher()
    
    // Use Cases
    val createAndExecuteJobUseCase = CreateAndExecuteJobUseCase(
        jobRepository = jobRepository,
        workerRepository = workerRepository,
        jobExecutor = jobExecutor,
        eventPublisher = eventPublisher
    )
    
    val registerWorkerUseCase = RegisterWorkerUseCase(
        workerRepository = workerRepository,
        eventPublisher = eventPublisher
    )
    
    init {
        logger.info { "In-memory configuration initialized" }
    }

    // Expose components for direct access if needed
    fun getJobRepository(): JobRepository = jobRepository
    fun getWorkerRepository(): WorkerRepository = workerRepository
    fun getJobExecutor(): JobExecutor = jobExecutor
    fun getWorkerManagementService(): WorkerManagementService = workerManagementService
    fun getEventPublisher(): EventPublisher = eventPublisher
}