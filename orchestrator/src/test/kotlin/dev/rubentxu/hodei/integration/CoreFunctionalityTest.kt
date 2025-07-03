package dev.rubentxu.hodei.integration

import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.jobmanagement.application.services.JobService
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.domain.worker.WorkerFactory
import dev.rubentxu.hodei.domain.worker.WorkerInstance
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.shared.domain.errors.WorkerCreationError
import dev.rubentxu.hodei.shared.domain.errors.WorkerDeletionError
import arrow.core.Either
import arrow.core.right
import dev.rubentxu.hodei.jobmanagement.infrastructure.persistence.InMemoryJobRepository
import dev.rubentxu.hodei.infrastructure.grpc.MockWorkerCommunicationService
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Core functionality tests that avoid gRPC complexity and focus on essential business logic
 */
class CoreFunctionalityTest {

    @Test
    fun `complete job execution workflow - core functionality`() = runTest {
        println("\n=== CORE FUNCTIONALITY TEST: Complete Job Execution ===")
        
        // 1. Setup services with NoOp communication (no gRPC)
        val jobRepository = InMemoryJobRepository()
        val workerManager = WorkerManagerService()
        val simpleWorkerFactory = object : WorkerFactory {
            override suspend fun createWorker(job: Job, resourcePool: ResourcePool): Either<WorkerCreationError, WorkerInstance> {
                return WorkerInstance(
                    workerId = "simple-worker-1",
                    poolId = resourcePool.id.value,
                    poolType = resourcePool.type,
                    instanceType = "test"
                ).right()
            }
            override suspend fun destroyWorker(workerId: String): Either<WorkerDeletionError, Unit> = Unit.right()
            override fun supportsPoolType(poolType: String): Boolean = true
        }
        
        // Register the worker that will be "created"
        workerManager.registerWorker("simple-worker-1")
        
        val executionEngine = ExecutionEngineService(
            jobRepository, 
            workerCommunicationService = MockWorkerCommunicationService(), 
            workerManager = workerManager, 
            workerFactory = simpleWorkerFactory
        )
        
        // 2. Create a job
        val job = Job(
            id = DomainId.generate(),
            name = "simple-test-job",
            namespace = "default",
            templateId = null,
            spec = buildJsonObject {
                put("command", "echo 'Hello Test'")
            },
            status = JobStatus.QUEUED,
            priority = Priority.HIGH,
            parameters = buildJsonObject {},
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test-user"
        )
        
        jobRepository.save(job).fold(
            { fail("Failed to save job: $it") },
            { savedJob -> 
                println("1. Job created: ${savedJob.id.value}")
                assertEquals(JobStatus.QUEUED, savedJob.status)
            }
        )
        
        // 3. Create a resource pool
        val resourcePool = ResourcePool(
            id = DomainId.generate(),
            name = "simple-pool",
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxWorkers = 10,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
        
        // 4. Start execution (this should work now with registered worker)
        println("2. Starting job execution...")
        val execution = executionEngine.startExecution(job, resourcePool, executionEngine.getOrchestratorToken()).fold(
            { fail("Failed to start execution: $it") },
            { exec -> 
                println("3. Execution started: ${exec.id.value}")
                exec
            }
        )
        
        // 5. Simulate completion directly
        println("4. Simulating execution completion...")
        val activeExecution = executionEngine.getActiveExecutions().find { it.job.id == job.id }
        assertNotNull(activeExecution, "Should have active execution")
        
        if (activeExecution != null) {
            // Complete the job
            val runningJob = if (activeExecution.job.status == JobStatus.QUEUED) {
                activeExecution.job.start(activeExecution.execution.id)
            } else {
                activeExecution.job
            }
            val completedJob = runningJob.complete()
            jobRepository.update(completedJob)
        }
        
        // 6. Verify job status
        val updatedJob = jobRepository.findById(job.id).fold(
            { fail("Failed to find job: $it") },
            { it }
        )
        
        println("5. Final job status: ${updatedJob?.status}")
        assertEquals(JobStatus.COMPLETED, updatedJob?.status)
        
        println("✅ Core functionality test passed!")
    }

    @Test
    fun `job execution with failure - core functionality`() = runTest {
        println("\n=== CORE FUNCTIONALITY TEST: Job Execution with Failure ===")
        
        // Setup (similar to success test)
        val jobRepository = InMemoryJobRepository()
        val workerManager = WorkerManagerService()
        val simpleWorkerFactory = object : WorkerFactory {
            override suspend fun createWorker(job: Job, resourcePool: ResourcePool): Either<WorkerCreationError, WorkerInstance> {
                return WorkerInstance(
                    workerId = "simple-worker-2",
                    poolId = resourcePool.id.value,
                    poolType = resourcePool.type,
                    instanceType = "test"
                ).right()
            }
            override suspend fun destroyWorker(workerId: String): Either<WorkerDeletionError, Unit> = Unit.right()
            override fun supportsPoolType(poolType: String): Boolean = true
        }
        
        workerManager.registerWorker("simple-worker-2")
        
        val executionEngine = ExecutionEngineService(
            jobRepository, 
            workerCommunicationService = MockWorkerCommunicationService(), 
            workerManager = workerManager, 
            workerFactory = simpleWorkerFactory
        )
        
        // Create failing job
        val job = Job(
            id = DomainId.generate(),
            name = "failing-job",
            namespace = "default",
            templateId = null,
            spec = buildJsonObject {
                put("command", "exit 1")
            },
            status = JobStatus.QUEUED,
            priority = Priority.NORMAL,
            parameters = buildJsonObject {},
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test-user"
        )
        
        jobRepository.save(job)
        
        val resourcePool = ResourcePool(
            id = DomainId.generate(),
            name = "simple-pool-2",
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxWorkers = 10,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
        
        // Start execution
        val execution = executionEngine.startExecution(job, resourcePool, executionEngine.getOrchestratorToken()).fold(
            { fail("Failed to start execution: $it") },
            { it }
        )
        
        println("1. Execution started: ${execution.id.value}")
        
        // Simulate failure
        val activeExecution = executionEngine.getActiveExecutions().find { it.job.id == job.id }
        if (activeExecution != null) {
            val runningJob = if (activeExecution.job.status == JobStatus.QUEUED) {
                activeExecution.job.start(activeExecution.execution.id)
            } else {
                activeExecution.job
            }
            val failedJob = runningJob.fail("Command exited with code 1")
            jobRepository.update(failedJob)
        }
        
        // Verify job failed
        val updatedJob = jobRepository.findById(job.id).fold(
            { fail("Failed to find job: $it") },
            { it }
        )
        
        println("2. Final job status: ${updatedJob?.status}")
        assertEquals(JobStatus.FAILED, updatedJob?.status)
        
        println("✅ Failure handling test passed!")
    }

    @Test
    fun `multiple workers handling jobs - core functionality`() = runTest {
        println("\n=== CORE FUNCTIONALITY TEST: Multiple Workers ===")
        
        // Setup with multiple workers
        val jobRepository = InMemoryJobRepository()
        val workerManager = WorkerManagerService()
        
        // Register multiple workers
        workerManager.registerWorker("simple-worker-1")
        workerManager.registerWorker("simple-worker-2")
        
        var workerCounter = 1
        val simpleWorkerFactory = object : WorkerFactory {
            override suspend fun createWorker(job: Job, resourcePool: ResourcePool): Either<WorkerCreationError, WorkerInstance> {
                return WorkerInstance(
                    workerId = "simple-worker-${workerCounter++}",
                    poolId = resourcePool.id.value,
                    poolType = resourcePool.type,
                    instanceType = "test"
                ).right()
            }
            override suspend fun destroyWorker(workerId: String): Either<WorkerDeletionError, Unit> = Unit.right()
            override fun supportsPoolType(poolType: String): Boolean = true
        }
        
        val executionEngine = ExecutionEngineService(
            jobRepository, 
            workerCommunicationService = MockWorkerCommunicationService(), 
            workerManager = workerManager, 
            workerFactory = simpleWorkerFactory
        )
        
        // Verify workers registered
        val workers = workerManager.getAllWorkers()
        assertEquals(2, workers.size)
        println("1. Registered ${workers.size} workers")
        
        // Create resource pool
        val resourcePool = ResourcePool(
            id = DomainId.generate(),
            name = "multi-pool",
            type = "kubernetes",
            status = PoolStatus.ACTIVE,
            maxWorkers = 10,
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now(),
            createdBy = "test"
        )
        
        // Create multiple jobs
        val jobs = (1..2).map { i ->
            Job(
                id = DomainId.generate(),
                name = "multi-job-$i",
                namespace = "default",
                templateId = null,
                spec = buildJsonObject {
                    put("command", "echo 'Job $i'")
                },
                status = JobStatus.QUEUED,
                priority = Priority.NORMAL,
                parameters = buildJsonObject {},
                createdAt = Clock.System.now(),
                updatedAt = Clock.System.now(),
                createdBy = "test-user"
            )
        }
        
        jobs.forEach { job ->
            jobRepository.save(job)
        }
        
        // Start executions
        val executions = jobs.mapNotNull { job ->
            executionEngine.startExecution(job, resourcePool, executionEngine.getOrchestratorToken()).fold(
                { error -> 
                    println("Could not start execution for job ${job.name}: $error")
                    null
                },
                { it }
            )
        }
        
        println("2. Started ${executions.size} executions")
        assertTrue(executions.isNotEmpty(), "Should start at least one execution")
        
        // Complete all executions
        val activeExecutions = executionEngine.getActiveExecutions()
        activeExecutions.forEach { context ->
            val runningJob = if (context.job.status == JobStatus.QUEUED) {
                context.job.start(context.execution.id)
            } else {
                context.job
            }
            val completedJob = runningJob.complete()
            jobRepository.update(completedJob)
        }
        
        // Verify completions
        val completedJobs = jobs.count { job ->
            jobRepository.findById(job.id).fold(
                { false },
                { it?.status == JobStatus.COMPLETED }
            )
        }
        
        println("3. Completed $completedJobs jobs")
        assertTrue(completedJobs > 0, "Should complete at least one job")
        
        println("✅ Multiple workers test passed!")
    }
}