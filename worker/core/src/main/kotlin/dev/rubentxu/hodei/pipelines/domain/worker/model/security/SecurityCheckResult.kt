package dev.rubentxu.hodei.pipelines.domain.worker.model.security

/**
 * Security check result
 */
sealed class SecurityCheckResult {
    object Allowed : SecurityCheckResult()
    data class Denied(val violations: List<SecurityViolation>) : SecurityCheckResult()
}