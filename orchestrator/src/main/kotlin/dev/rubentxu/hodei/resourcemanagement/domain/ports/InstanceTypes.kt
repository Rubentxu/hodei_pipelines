package dev.rubentxu.hodei.resourcemanagement.domain.ports

import dev.rubentxu.hodei.shared.domain.errors.DomainError
import dev.rubentxu.hodei.shared.domain.primitives.DomainId

/**
 * Instance specification for provisioning
 */
data class InstanceSpec(
    val instanceType: InstanceType,
    val image: String,
    val command: List<String> = emptyList(),
    val environment: Map<String, String> = emptyMap(),
    val labels: Map<String, String> = emptyMap(),
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Available instance types
 */
enum class InstanceType {
    SMALL,
    MEDIUM,
    LARGE,
    XLARGE,
    CUSTOM
}

/**
 * Represents a provisioned instance
 */
data class Instance(
    val instanceId: DomainId,
    val instanceType: InstanceType,
    val status: InstanceStatus,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Represents the status of a compute instance
 */
enum class InstanceStatus {
    PROVISIONING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED,
    TERMINATED
}

