package dev.rubentxu.hodei.pipelines.dsl.security

import kotlinx.serialization.Serializable

/**
 * Resultado de verificación de seguridad en Pipeline DSL.
 */
@Serializable
sealed class SecurityCheckResult {
    @Serializable
    object Allowed : SecurityCheckResult()
    
    @Serializable
    data class Denied(val violations: List<SecurityViolation>) : SecurityCheckResult()
}