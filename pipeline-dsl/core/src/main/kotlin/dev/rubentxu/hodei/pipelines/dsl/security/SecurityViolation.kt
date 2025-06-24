package dev.rubentxu.hodei.pipelines.dsl.security

import kotlinx.serialization.Serializable

/**
 * Violaciones de seguridad en el sandbox del Pipeline DSL.
 * Implementa funcionalidad similar al sandbox de Jenkins Pipeline DSL.
 */
@Serializable
sealed class SecurityViolation(val message: String) {
    
    @Serializable
    data class DangerousPattern(val pattern: String, val description: String) : 
        SecurityViolation("Dangerous pattern detected: $description ($pattern)")
    
    @Serializable
    data class UnauthorizedSystemCall(val details: String = "Unauthorized system call") : 
        SecurityViolation(details)
    
    @Serializable
    data class UnauthorizedFileAccess(val details: String = "Unauthorized file access") : 
        SecurityViolation(details)
    
    @Serializable
    data class UnauthorizedNetworkAccess(val details: String = "Unauthorized network access") : 
        SecurityViolation(details)
    
    @Serializable
    data class UnauthorizedReflection(val details: String = "Unauthorized reflection usage") : 
        SecurityViolation(details)
    
    @Serializable
    data class UnauthorizedLibrary(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class BlockedLibrary(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class DangerousLibrary(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class RestrictedPath(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class RestrictedHost(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class RestrictedPort(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class RestrictedSystemProperty(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class ScriptTooLarge(val details: String) : 
        SecurityViolation(details)
    
    @Serializable
    data class UnauthorizedMethodCall(val className: String, val methodName: String) : 
        SecurityViolation("Unauthorized method call: $className.$methodName")
    
    @Serializable
    data class UnauthorizedConstructor(val className: String) : 
        SecurityViolation("Unauthorized constructor: $className")
    
    @Serializable
    data class UnauthorizedStaticAccess(val className: String, val memberName: String) : 
        SecurityViolation("Unauthorized static access: $className.$memberName")
}