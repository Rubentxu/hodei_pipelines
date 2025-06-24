package dev.rubentxu.hodei.pipelines.dsl.security

/**
 * Gestor de seguridad para el Pipeline DSL.
 * Implementa un sandbox robusto similar al de Jenkins Pipeline DSL.
 */
interface PipelineSecurityManager {
    val securityPolicy: SecurityPolicy
    
    /**
     * Verifica si un script puede ejecutarse de forma segura.
     */
    fun checkScriptAccess(script: String): SecurityCheckResult
    
    /**
     * Verifica si una librería puede ser cargada.
     */
    fun checkLibraryAccess(libraryId: String): SecurityCheckResult
    
    /**
     * Verifica si una operación de archivo está permitida.
     */
    fun checkFileAccess(path: String, operation: FileOperation): SecurityCheckResult
    
    /**
     * Verifica si el acceso a red está permitido.
     */
    fun checkNetworkAccess(host: String, port: Int): SecurityCheckResult
    
    /**
     * Verifica si el acceso a propiedades del sistema está permitido.
     */
    fun checkSystemPropertyAccess(property: String): SecurityCheckResult
    
    /**
     * Verifica si una llamada a método está permitida.
     */
    fun checkMethodCall(className: String, methodName: String): SecurityCheckResult
    
    /**
     * Verifica si la construcción de una clase está permitida.
     */
    fun checkConstructorCall(className: String): SecurityCheckResult
    
    /**
     * Verifica si el acceso a miembros estáticos está permitido.
     */
    fun checkStaticAccess(className: String, memberName: String): SecurityCheckResult
    
    /**
     * Verifica si el uso de reflexión está permitido.
     */
    fun checkReflectionAccess(operation: String): SecurityCheckResult
    
    /**
     * Verifica todos los aspectos de seguridad de un script.
     */
    fun performFullSecurityCheck(script: String): SecurityCheckResult
}