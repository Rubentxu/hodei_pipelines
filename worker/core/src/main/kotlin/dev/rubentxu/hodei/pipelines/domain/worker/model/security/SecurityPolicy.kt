package dev.rubentxu.hodei.pipelines.domain.worker.model.security

import java.time.Duration

/**
 * Security policy configuration
 */
data class SecurityPolicy(
    val allowSystemCalls: Boolean = false,
    val allowFileSystemAccess: Boolean = true,
    val allowFileRead: Boolean = true,
    val allowFileWrite: Boolean = false,
    val allowFileExecute: Boolean = false,
    val allowNetworkAccess: Boolean = true,
    val allowLocalhost: Boolean = false,
    val allowRestrictedPorts: Boolean = false,
    val allowReflection: Boolean = false,
    val allowSystemProperties: Boolean = false,
    val allowedLibraries: Set<String> = emptySet(),
    val blockedLibraries: Set<String> = setOf(
        "java.lang.Runtime",
        "java.lang.ProcessBuilder",
        "java.lang.reflect.*",
        "sun.*",
        "com.sun.*"
    ),
    val maxExecutionTime: Duration = Duration.ofMinutes(30),
    val maxMemoryUsage: Long = 512 * 1024 * 1024, // 512MB
    val maxScriptSize: Int = 100 * 1024, // 100KB
    val sandboxEnabled: Boolean = true
) {
    companion object {
        fun strict() = SecurityPolicy(
            allowSystemCalls = false,
            allowFileSystemAccess = false,
            allowNetworkAccess = false,
            allowReflection = false,
            sandboxEnabled = true
        )

        fun permissive() = SecurityPolicy(
            allowSystemCalls = true,
            allowFileSystemAccess = true,
            allowFileWrite = true,
            allowFileExecute = true,
            allowNetworkAccess = true,
            allowLocalhost = true,
            allowRestrictedPorts = true,
            allowReflection = true,
            allowSystemProperties = true,
            sandboxEnabled = false
        )

        fun development() = SecurityPolicy(
            allowSystemCalls = true,
            allowFileSystemAccess = true,
            allowFileRead = true,
            allowFileWrite = true,
            allowNetworkAccess = true,
            allowLocalhost = true,
            allowReflection = false,
            sandboxEnabled = true
        )
    }
}