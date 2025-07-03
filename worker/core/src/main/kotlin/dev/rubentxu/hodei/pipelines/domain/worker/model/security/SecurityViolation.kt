package dev.rubentxu.hodei.pipelines.domain.worker.model.security

/**
 * Security violations
 */
sealed class SecurityViolation(val message: String) {
    class DangerousPattern(val pattern: String, val description: String) : SecurityViolation("Dangerous pattern detected: $description ($pattern)")
    class UnauthorizedSystemCall(message: String = "Unauthorized system call") : SecurityViolation(message)
    class UnauthorizedFileAccess(message: String = "Unauthorized file access") : SecurityViolation(message)
    class UnauthorizedNetworkAccess(message: String = "Unauthorized network access") : SecurityViolation(message)
    class UnauthorizedReflection(message: String = "Unauthorized reflection usage") : SecurityViolation(message)
    class UnauthorizedLibrary(message: String) : SecurityViolation(message)
    class BlockedLibrary(message: String) : SecurityViolation(message)
    class DangerousLibrary(message: String) : SecurityViolation(message)
    class RestrictedPath(message: String) : SecurityViolation(message)
    class RestrictedHost(message: String) : SecurityViolation(message)
    class RestrictedPort(message: String) : SecurityViolation(message)
    class RestrictedSystemProperty(message: String) : SecurityViolation(message)
    class ScriptTooLarge(message: String) : SecurityViolation(message)
}