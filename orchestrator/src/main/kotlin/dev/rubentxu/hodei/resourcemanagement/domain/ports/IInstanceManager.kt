package dev.rubentxu.hodei.resourcemanagement.domain.ports

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.ProvisioningError
import dev.rubentxu.hodei.execution.domain.entities.Execution
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import kotlinx.datetime.Instant

/**
 * Puerto para abstraer la complejidad de instanciar recursos computacionales
 * sobre infraestructuras heterogéneas (Kubernetes, Docker, Cloud VMs)
 */
interface IInstanceManager {
    
    /**
     * Provision a new compute instance for job execution
     * @param poolId The resource pool ID to provision from
     * @param spec The instance specification
     * @return Instance if successful, error otherwise
     */
    suspend fun provisionInstance(
        poolId: DomainId,
        spec: InstanceSpec
    ): Either<ProvisioningError, ComputeInstance>
    
    /**
     * Terminate a compute instance and its resources
     * @param instanceId The ID of the instance to terminate
     * @return Unit if successful, error otherwise
     */
    suspend fun terminateInstance(instanceId: DomainId): Either<ProvisioningError, Unit>
    
    /**
     * Get the current status of a compute instance
     * @param instanceId The ID of the instance to check
     * @return InstanceStatus with current state information
     */
    suspend fun getInstanceStatus(instanceId: DomainId): Result<InstanceStatus>
    
    /**
     * List all instances in a specific resource pool
     * @param resourcePoolId The ID of the resource pool
     * @return List of ComputeInstance objects
     */
    suspend fun listInstances(resourcePoolId: DomainId): Result<List<ComputeInstance>>
    
    /**
     * Scale the number of instances in a resource pool
     * @param resourcePoolId The ID of the resource pool
     * @param targetCount Desired number of instances
     * @return ScalingResult with operation details
     */
    suspend fun scaleInstances(
        resourcePoolId: DomainId, 
        targetCount: Int
    ): Result<ScalingResult>
    
    /**
     * Get available instance types/flavors for a resource pool
     * @param resourcePoolId The ID of the resource pool
     * @return List of available instance types
     */
    suspend fun getAvailableInstanceTypes(resourcePoolId: DomainId): Result<List<InstanceType>>
}

/**
 * Represents a compute instance managed by the instance manager
 */
data class ComputeInstance(
    val id: DomainId,
    val name: String,
    val type: InstanceType,
    val status: InstanceStatus,
    val resourcePoolId: DomainId,
    val executionId: DomainId?,
    val metadata: Map<String, String>,
    val createdAt: Instant,
    val lastUpdatedAt: Instant
)



// InstanceType moved to InstanceTypes.kt

/**
 * Result of a scaling operation
 */
data class ScalingResult(
    val requestedCount: Int,
    val actualCount: Int,
    val provisionedInstances: List<DomainId>,
    val failedInstances: List<String>,
    val operationId: DomainId
)