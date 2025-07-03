package dev.rubentxu.hodei.shared.domain.errors

import kotlinx.serialization.Serializable

@Serializable
sealed interface DomainError {
    val code: String
    val message: String
    val details: Map<String, String>
        get() = emptyMap()
}

@Serializable
data class ValidationError(
    override val code: String = "VALIDATION_ERROR",
    override val message: String,
    val field: String? = null,
    override val details: Map<String, String> = emptyMap()
) : DomainError

@Serializable
data class NotFoundError(
    override val code: String = "NOT_FOUND",
    override val message: String,
    val entityType: String,
    val entityId: String,
    override val details: Map<String, String> = emptyMap()
) : DomainError

@Serializable
data class ConflictError(
    override val code: String = "CONFLICT",
    override val message: String,
    override val details: Map<String, String> = emptyMap()
) : DomainError

@Serializable
data class BusinessRuleError(
    override val code: String,
    override val message: String,
    override val details: Map<String, String> = emptyMap()
) : DomainError

@Serializable
data class InsufficientResourcesError(
    override val code: String = "INSUFFICIENT_RESOURCES",
    override val message: String,
    val requested: Map<String, String>,
    val available: Map<String, String>,
    override val details: Map<String, String> = emptyMap()
) : DomainError

@Serializable
sealed interface RepositoryError : DomainError {
    @Serializable
    data class NotFoundError(
        override val code: String = "REPOSITORY_NOT_FOUND",
        override val message: String,
        val entityType: String,
        val entityId: String
    ) : RepositoryError

    @Serializable
    data class Conflict(
        override val code: String = "REPOSITORY_CONFLICT",
        override val message: String
    ) : RepositoryError

    @Serializable
    data class OperationFailed(
        override val code: String = "REPOSITORY_OPERATION_FAILED",
        override val message: String
    ) : RepositoryError

    @Serializable
    data class Unknown(
        override val code: String = "REPOSITORY_ERROR",
        override val message: String
    ) : RepositoryError
}

@Serializable
sealed interface BusinessLogicError : DomainError {
    @Serializable
    data class DuplicateEntity(
        override val code: String = "DUPLICATE_ENTITY",
        override val message: String,
        val entityType: String,
        val entityId: String
    ) : BusinessLogicError

    @Serializable
    data class InvalidOperation(
        override val code: String = "INVALID_OPERATION",
        override val message: String,
        val operation: String
    ) : BusinessLogicError
}

@Serializable
sealed interface SystemError : DomainError {
    @Serializable
    data class InternalError(
        override val code: String = "INTERNAL_ERROR",
        override val message: String
    ) : SystemError
}

// Companion object with factory methods for common errors  
class DomainErrorFactory {
    companion object {
        fun NotFound(entityType: String, entityId: String): NotFoundError =
            NotFoundError(
                message = "$entityType with ID $entityId was not found",
                entityType = entityType,
                entityId = entityId
            )
        
        fun BusinessRule(message: String): BusinessRuleError =
            BusinessRuleError(
                code = "BUSINESS_RULE_VIOLATION",
                message = message
            )
        
        fun Technical(message: String, cause: Throwable? = null): SystemError.InternalError =
            SystemError.InternalError(
                message = if (cause != null) "$message: ${cause.message}" else message
            )
        
        fun Authentication(message: String): SecurityError.Authentication =
            SecurityError.Authentication(message = message)
            
        fun Authorization(message: String): SecurityError.Authorization =
            SecurityError.Authorization(message = message)
    }
}

@Serializable
sealed interface SecurityError : DomainError {
    @Serializable
    data class Authentication(
        override val code: String = "AUTHENTICATION_ERROR",
        override val message: String
    ) : SecurityError
    
    @Serializable
    data class Authorization(
        override val code: String = "AUTHORIZATION_ERROR", 
        override val message: String
    ) : SecurityError
}

/**
 * Provisioning errors
 */
sealed interface ProvisioningError : DomainError {
    data class InsufficientResourcesError(
        override val message: String,
        override val code: String = "INSUFFICIENT_RESOURCES"
    ) : ProvisioningError

    data class InvalidSpecError(
        override val message: String,
        override val code: String = "INVALID_SPEC"
    ) : ProvisioningError

    data class ProvisioningFailedError(
        override val message: String,
        override val code: String = "PROVISIONING_FAILED"
    ) : ProvisioningError
}

/**
 * Worker creation specific errors
 */
sealed interface WorkerCreationError : DomainError {
    data class InsufficientResourcesError(
        override val message: String,
        override val code: String = "INSUFFICIENT_RESOURCES"
    ) : WorkerCreationError

    data class PoolNotSupportedError(
        override val message: String,
        override val code: String = "POOL_NOT_SUPPORTED"
    ) : WorkerCreationError

    data class ProvisioningFailedError(
        override val message: String,
        override val code: String = "PROVISIONING_FAILED"
    ) : WorkerCreationError

    data class ConfigurationError(
        override val message: String,
        override val code: String = "CONFIGURATION_ERROR"
    ) : WorkerCreationError
}

/**
 * Worker deletion specific errors
 */
sealed interface WorkerDeletionError : DomainError {
    data class WorkerNotFoundError(
        override val message: String,
        override val code: String = "WORKER_NOT_FOUND"
    ) : WorkerDeletionError

    data class DeletionFailedError(
        override val message: String,
        override val code: String = "DELETION_FAILED"
    ) : WorkerDeletionError

    data class ResourceCleanupFailedError(
        override val message: String,
        override val code: String = "RESOURCE_CLEANUP_FAILED"
    ) : WorkerDeletionError
}