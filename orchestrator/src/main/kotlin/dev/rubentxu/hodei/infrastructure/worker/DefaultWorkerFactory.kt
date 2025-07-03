package dev.rubentxu.hodei.infrastructure.worker

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.WorkerCreationError
import dev.rubentxu.hodei.shared.domain.errors.WorkerDeletionError
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.resourcemanagement.domain.ports.IInstanceManager
import dev.rubentxu.hodei.resourcemanagement.domain.ports.InstanceSpec
import dev.rubentxu.hodei.resourcemanagement.domain.ports.InstanceType
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.domain.worker.*
import dev.rubentxu.hodei.infrastructure.grpc.WorkerManager
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * Default implementation of WorkerFactory that uses IInstanceManager
 * to provision workers across different infrastructure types.
 */
class DefaultWorkerFactory(
    private val instanceManager: IInstanceManager,
    private val resourceMonitor: dev.rubentxu.hodei.resourcemanagement.domain.ports.IResourceMonitor,
    private val workerConfigurations: Map<String, WorkerConfiguration> = defaultWorkerConfigurations()
) : WorkerFactory {
    
    private val logger = LoggerFactory.getLogger(DefaultWorkerFactory::class.java)
    private val activeWorkers = mutableMapOf<String, WorkerInstance>()
    
    override suspend fun createWorker(
        job: Job,
        resourcePool: ResourcePool
    ): Either<WorkerCreationError, WorkerInstance> {
        logger.info("Creating worker for job ${job.id} in pool ${resourcePool.name} (type: ${resourcePool.type})")
        
        // Use the configured instance manager
        // In the future, we could have multiple instance managers by pool type
        
        // Get worker configuration for this pool type
        val workerConfig = workerConfigurations[resourcePool.type]
            ?: return WorkerCreationError.ConfigurationError(
                "No worker configuration found for pool type: ${resourcePool.type}"
            ).left()
        
        // Create instance specification based on job requirements and worker config
        val instanceSpec = createInstanceSpec(job, resourcePool, workerConfig)
        
        try {
            // Provision the instance
            val provisionResult = withTimeout(workerConfig.provisioningTimeout) {
                instanceManager.provisionInstance(
                    poolId = resourcePool.id,
                    spec = instanceSpec
                )
            }
            
            return provisionResult.fold(
                { error ->
                    logger.error("Failed to provision worker: $error")
                    WorkerCreationError.ProvisioningFailedError(error.toString()).left()
                },
                { instance ->
                    val workerId = instance.metadata["workerId"] ?: UUID.randomUUID().toString()
                    val workerInstance = WorkerInstance(
                        workerId = workerId,
                        poolId = resourcePool.id.value,
                        poolType = resourcePool.type,
                        instanceType = instanceSpec.instanceType.name,
                        metadata = instance.metadata + mapOf(
                            "instanceId" to instance.id.value,
                            "jobId" to job.id.value
                        )
                    )
                    
                    // Track the worker
                    activeWorkers[workerId] = workerInstance
                    
                    logger.info("Successfully created worker $workerId for job ${job.id}")
                    workerInstance.right()
                }
            )
        } catch (e: Exception) {
            logger.error("Error creating worker for job ${job.id}", e)
            return WorkerCreationError.ProvisioningFailedError(
                "Failed to create worker: ${e.message}"
            ).left()
        }
    }
    
    override suspend fun destroyWorker(workerId: String): Either<WorkerDeletionError, Unit> {
        logger.info("Destroying worker $workerId")
        
        val workerInstance = activeWorkers[workerId]
            ?: return WorkerDeletionError.WorkerNotFoundError(
                "Worker $workerId not found in active workers"
            ).left()
        
        // Use the configured instance manager
        
        val instanceId = workerInstance.metadata["instanceId"]
            ?: return WorkerDeletionError.DeletionFailedError(
                "No instance ID found for worker $workerId"
            ).left()
        
        try {
            // In the future, use instanceManager.terminateInstance when implemented
            // For now, just remove from tracking
            activeWorkers.remove(workerId)
            logger.info("Successfully destroyed worker $workerId")
            return Unit.right()
        } catch (e: Exception) {
            logger.error("Error destroying worker $workerId", e)
            return WorkerDeletionError.DeletionFailedError(
                "Failed to destroy worker: ${e.message}"
            ).left()
        }
    }
    
    override fun supportsPoolType(poolType: String): Boolean {
        return workerConfigurations.containsKey(poolType)
    }
    
    private fun createInstanceSpec(
        job: Job,
        resourcePool: ResourcePool,
        workerConfig: WorkerConfiguration
    ): InstanceSpec {
        // Extract resource requirements from job
        val cpuRequest = job.resourceRequirements["cpu"]?.toDoubleOrNull() ?: workerConfig.defaultCpu
        val memoryRequest = parseMemoryToMB(job.resourceRequirements["memory"] ?: workerConfig.defaultMemory)
        
        // Determine instance type based on requirements
        val instanceType = selectInstanceType(cpuRequest, memoryRequest, workerConfig)
        
        // Build worker command with necessary parameters
        val workerCommand = buildWorkerCommand(job, resourcePool, workerConfig)
        
        return InstanceSpec(
            instanceType = instanceType,
            image = workerConfig.workerImage,
            command = workerCommand,
            environment = buildWorkerEnvironment(job, resourcePool),
            labels = mapOf(
                "job-id" to job.id.value,
                "pool-id" to resourcePool.id.value,
                "managed-by" to "hodei-pipelines"
            ),
            metadata = mapOf(
                "workerId" to UUID.randomUUID().toString(),
                "jobName" to job.name
            )
        )
    }
    
    private fun selectInstanceType(cpu: Double, memoryMB: Int, config: WorkerConfiguration): InstanceType {
        // Simple instance type selection based on resources
        return when {
            cpu <= 1.0 && memoryMB <= 2048 -> InstanceType.SMALL
            cpu <= 2.0 && memoryMB <= 4096 -> InstanceType.MEDIUM
            cpu <= 4.0 && memoryMB <= 8192 -> InstanceType.LARGE
            else -> InstanceType.XLARGE
        }
    }
    
    private fun buildWorkerCommand(
        job: Job,
        resourcePool: ResourcePool,
        config: WorkerConfiguration
    ): List<String> {
        return listOf(
            config.workerBinary,
            "--server", config.serverEndpoint,
            "--pool-id", resourcePool.id.value,
            "--tls" // Always use TLS for security
        )
    }
    
    private fun buildWorkerEnvironment(job: Job, resourcePool: ResourcePool): Map<String, String> {
        return mapOf(
            "HODEI_JOB_ID" to job.id.value,
            "HODEI_POOL_ID" to resourcePool.id.value,
            "HODEI_POOL_TYPE" to resourcePool.type,
            "HODEI_LOG_LEVEL" to "INFO"
        )
    }
    
    private fun parseMemoryToMB(memory: String): Int {
        return try {
            when {
                memory.endsWith("Gi") -> memory.removeSuffix("Gi").toInt() * 1024
                memory.endsWith("Mi") -> memory.removeSuffix("Mi").toInt()
                memory.endsWith("G") -> memory.removeSuffix("G").toInt() * 1000
                memory.endsWith("M") -> memory.removeSuffix("M").toInt()
                else -> memory.toInt()
            }
        } catch (e: NumberFormatException) {
            logger.warn("Failed to parse memory string: $memory, defaulting to 2048MB")
            2048
        }
    }
}

/**
 * Worker configuration for different pool types
 */
data class WorkerConfiguration(
    val workerImage: String,
    val workerBinary: String,
    val serverEndpoint: String,
    val defaultCpu: Double,
    val defaultMemory: String,
    val provisioningTimeout: kotlin.time.Duration
)

/**
 * Default worker configurations for common pool types
 */
fun defaultWorkerConfigurations(): Map<String, WorkerConfiguration> = mapOf(
    "kubernetes" to WorkerConfiguration(
        workerImage = "hodei/worker:latest",
        workerBinary = "/app/worker",
        serverEndpoint = "hodei-orchestrator:50051",
        defaultCpu = 1.0,
        defaultMemory = "2Gi",
        provisioningTimeout = 60.seconds
    ),
    "docker" to WorkerConfiguration(
        workerImage = "hodei/worker:latest",
        workerBinary = "/app/worker",
        serverEndpoint = "host.docker.internal:50051",
        defaultCpu = 1.0,
        defaultMemory = "2048M",
        provisioningTimeout = 30.seconds
    ),
    "local" to WorkerConfiguration(
        workerImage = "hodei/worker:latest",
        workerBinary = "./worker",
        serverEndpoint = "localhost:50051",
        defaultCpu = 1.0,
        defaultMemory = "2048M",
        provisioningTimeout = 10.seconds
    )
)