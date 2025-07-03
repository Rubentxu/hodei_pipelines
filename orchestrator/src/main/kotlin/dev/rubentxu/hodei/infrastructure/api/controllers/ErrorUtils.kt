package dev.rubentxu.hodei.infrastructure.api.controllers

import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.infrastructure.api.dto.ErrorResponseDto
import kotlinx.datetime.Clock

// Extension function to convert DomainError to ErrorResponseDto
fun DomainError.toErrorResponse(): ErrorResponseDto = when (this) {
    is ValidationError -> ErrorResponseDto(
        error = "VALIDATION_ERROR",
        message = message,
        timestamp = Clock.System.now()
    )
    is NotFoundError -> ErrorResponseDto(
        error = "NOT_FOUND",
        message = message,
        timestamp = Clock.System.now()
    )
    is BusinessRuleError -> ErrorResponseDto(
        error = "BUSINESS_LOGIC_ERROR",
        message = message,
        timestamp = Clock.System.now()
    )
    is ConflictError -> ErrorResponseDto(
        error = "CONFLICT",
        message = message,
        timestamp = Clock.System.now()
    )
    is InsufficientResourcesError -> ErrorResponseDto(
        error = "INSUFFICIENT_RESOURCES",
        message = message,
        timestamp = Clock.System.now()
    )
    is RepositoryError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
    is BusinessLogicError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
    is SystemError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
    is SecurityError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
    is ProvisioningError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
    is WorkerCreationError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
    is WorkerDeletionError -> ErrorResponseDto(
        error = code,
        message = message,
        timestamp = Clock.System.now()
    )
}