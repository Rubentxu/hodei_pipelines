package dev.rubentxu.hodei.pipelines.dsl.security

import kotlinx.serialization.Serializable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Política de seguridad para el sandbox del Pipeline DSL.
 * Proporciona configuración granular similar al sandbox de Jenkins Pipeline DSL.
 */
@Serializable
data class SecurityPolicy(
    // Llamadas al sistema
    val allowSystemCalls: Boolean = false,
    
    // Acceso al sistema de archivos
    val allowFileSystemAccess: Boolean = true,
    val allowFileRead: Boolean = true,
    val allowFileWrite: Boolean = false,
    val allowFileExecute: Boolean = false,
    val allowedPaths: Set<String> = setOf(".", "./", "tmp/", "/tmp/"),
    val blockedPaths: Set<String> = setOf("/etc/", "/bin/", "/sbin/", "/usr/bin/", "/usr/sbin/", "/sys/", "/proc/"),
    
    // Acceso a la red
    val allowNetworkAccess: Boolean = true,
    val allowLocalhost: Boolean = false,
    val allowRestrictedPorts: Boolean = false,
    val allowedHosts: Set<String> = emptySet(),
    val blockedHosts: Set<String> = setOf("localhost", "127.0.0.1", "0.0.0.0", "::1"),
    
    // Reflexión y metaprogramación
    val allowReflection: Boolean = false,
    val allowDynamicClassLoading: Boolean = false,
    val allowJavaScriptEngine: Boolean = false,
    
    // Propiedades del sistema
    val allowSystemProperties: Boolean = false,
    val allowEnvironmentVariables: Boolean = true,
    
    // Librerías y clases
    val allowedLibraries: Set<String> = setOf(
        "kotlin.*",
        "kotlinx.coroutines.*",
        "kotlinx.serialization.*",
        "java.util.*",
        "java.time.*",
        "java.math.*",
        "java.text.*"
    ),
    val blockedLibraries: Set<String> = setOf(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.reflect.*",
        "javax.script.*",
        "sun.*",
        "com.sun.*",
        "jdk.internal.*"
    ),
    
    // Métodos específicos bloqueados
    val blockedMethods: Set<String> = setOf(
        "java.lang.System.exit",
        "java.lang.System.setSecurityManager",
        "java.lang.System.setProperty",
        "java.lang.Runtime.exec",
        "java.lang.Runtime.halt",
        "java.lang.Thread.stop",
        "java.lang.Class.forName"
    ),
    
    // Límites de recursos
    val maxExecutionTime: Duration = 30.minutes,
    val maxMemoryUsage: Long = 512 * 1024 * 1024, // 512MB
    val maxScriptSize: Int = 100 * 1024, // 100KB
    val maxOutputSize: Long = 10 * 1024 * 1024, // 10MB
    val maxConcurrentSteps: Int = 10,
    
    // Configuración del sandbox
    val sandboxEnabled: Boolean = true,
    val strictMode: Boolean = false,
    val logViolations: Boolean = true,
    val failOnViolation: Boolean = true
) {
    companion object {
        /**
         * Política estricta para entornos de producción.
         */
        fun strict() = SecurityPolicy(
            allowSystemCalls = false,
            allowFileSystemAccess = false,
            allowFileWrite = false,
            allowFileExecute = false,
            allowNetworkAccess = false,
            allowLocalhost = false,
            allowReflection = false,
            allowDynamicClassLoading = false,
            allowJavaScriptEngine = false,
            allowSystemProperties = false,
            allowEnvironmentVariables = false,
            sandboxEnabled = true,
            strictMode = true,
            failOnViolation = true
        )

        /**
         * Política permisiva para debugging (¡NO usar en producción!).
         */
        fun permissive() = SecurityPolicy(
            allowSystemCalls = true,
            allowFileSystemAccess = true,
            allowFileWrite = true,
            allowFileExecute = true,
            allowNetworkAccess = true,
            allowLocalhost = true,
            allowRestrictedPorts = true,
            allowReflection = true,
            allowDynamicClassLoading = true,
            allowJavaScriptEngine = true,
            allowSystemProperties = true,
            allowEnvironmentVariables = true,
            sandboxEnabled = false,
            strictMode = false,
            failOnViolation = false
        )

        /**
         * Política para desarrollo local.
         */
        fun development() = SecurityPolicy(
            allowSystemCalls = false,
            allowFileSystemAccess = true,
            allowFileRead = true,
            allowFileWrite = true,
            allowNetworkAccess = true,
            allowLocalhost = true,
            allowReflection = false,
            allowDynamicClassLoading = false,
            allowSystemProperties = false,
            sandboxEnabled = true,
            strictMode = false,
            logViolations = true,
            failOnViolation = false
        )

        /**
         * Política para CI/CD.
         */
        fun cicd() = SecurityPolicy(
            allowSystemCalls = false,
            allowFileSystemAccess = true,
            allowFileRead = true,
            allowFileWrite = true,
            allowFileExecute = false,
            allowNetworkAccess = true,
            allowLocalhost = false,
            allowReflection = false,
            allowSystemProperties = false,
            sandboxEnabled = true,
            strictMode = true,
            maxExecutionTime = 60.minutes,
            maxMemoryUsage = 1024 * 1024 * 1024, // 1GB
            failOnViolation = true
        )
    }
}