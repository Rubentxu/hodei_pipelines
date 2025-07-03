package dev.rubentxu.hodei.jobmanagement.domain.entities

import kotlinx.serialization.Serializable

/**
 * Types of jobs that can be executed
 */
@Serializable
enum class JobType {
    SHELL,
    PYTHON_SCRIPT,
    KOTLIN_SCRIPT,
    CONTAINER
}