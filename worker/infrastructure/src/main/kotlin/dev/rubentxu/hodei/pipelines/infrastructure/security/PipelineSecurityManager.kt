package dev.rubentxu.hodei.pipelines.infrastructure.security

import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.FileOperation
import dev.rubentxu.hodei.pipelines.domain.worker.ports.PipelineSecurityManager
import dev.rubentxu.hodei.pipelines.domain.worker.model.security.SecurityCheckResult
import dev.rubentxu.hodei.pipelines.domain.worker.model.security.SecurityPolicy
import dev.rubentxu.hodei.pipelines.domain.worker.model.security.SecurityViolation
import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Default implementation of PipelineSecurityManager
 */
class DefaultPipelineSecurityManager(
    override val securityPolicy: SecurityPolicy
) : PipelineSecurityManager {
    
    override fun checkScriptAccess(script: String): SecurityCheckResult {
        val violations = mutableListOf<SecurityViolation>()
        
        // Check for dangerous patterns
        DANGEROUS_PATTERNS.forEach { (pattern, description) ->
            if (pattern.containsMatchIn(script)) {
                violations.add(SecurityViolation.DangerousPattern(pattern.pattern, description))
            }
        }
        
        // Check for system calls
        if (script.contains("Runtime.getRuntime()") || script.contains("ProcessBuilder")) {
            if (!securityPolicy.allowSystemCalls) {
                violations.add(SecurityViolation.UnauthorizedSystemCall("Direct system calls not allowed"))
            }
        }
        
        // Check for file system access
        if (script.contains("File(") || script.contains("FileInputStream") || script.contains("FileOutputStream")) {
            if (!securityPolicy.allowFileSystemAccess) {
                violations.add(SecurityViolation.UnauthorizedFileAccess("Direct file system access not allowed"))
            }
        }
        
        // Check for network access
        if (script.contains("URL(") || script.contains("HttpURLConnection") || script.contains("Socket(")) {
            if (!securityPolicy.allowNetworkAccess) {
                violations.add(SecurityViolation.UnauthorizedNetworkAccess("Direct network access not allowed"))
            }
        }
        
        // Check for reflection usage
        if (script.contains("::class.java") || script.contains("Class.forName") || script.contains("reflection")) {
            if (!securityPolicy.allowReflection) {
                violations.add(SecurityViolation.UnauthorizedReflection("Reflection not allowed"))
            }
        }
        
        // Check script size
        if (script.length > securityPolicy.maxScriptSize) {
            violations.add(SecurityViolation.ScriptTooLarge("Script size ${script.length} exceeds limit ${securityPolicy.maxScriptSize}"))
        }
        
        return if (violations.isEmpty()) {
            SecurityCheckResult.Allowed
        } else {
            logger.warn { "Script security check failed with ${violations.size} violations" }
            SecurityCheckResult.Denied(violations)
        }
    }
    
    override fun checkLibraryAccess(libraryId: String): SecurityCheckResult {
        // Check if library is explicitly blocked
        if (libraryId in securityPolicy.blockedLibraries) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.BlockedLibrary("Library '$libraryId' is explicitly blocked")
            ))
        }
        
        // If allowedLibraries is not empty, only allow libraries in the list
        if (securityPolicy.allowedLibraries.isNotEmpty() && libraryId !in securityPolicy.allowedLibraries) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedLibrary("Library '$libraryId' is not in the allowed list")
            ))
        }
        
        // Check for dangerous library patterns
        val dangerousPatterns = listOf(
            ".*javax\\.script.*",
            ".*java\\.lang\\.reflect.*",
            ".*sun\\..*",
            ".*com\\.sun\\..*"
        )
        
        if (dangerousPatterns.any { libraryId.matches(Regex(it)) }) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.DangerousLibrary("Library '$libraryId' contains potentially dangerous classes")
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkFileAccess(path: String, operation: FileOperation): SecurityCheckResult {
        if (!securityPolicy.allowFileSystemAccess) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedFileAccess("File system access is disabled")
            ))
        }
        
        // Check for restricted paths
        val restrictedPaths = listOf(
            "/etc/",
            "/bin/",
            "/sbin/",
            "/usr/bin/",
            "/usr/sbin/",
            "/sys/",
            "/proc/",
            "C:\\\\Windows\\\\",
            "C:\\\\Program Files\\\\"
        )
        
        if (restrictedPaths.any { path.startsWith(it) }) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.RestrictedPath("Access to path '$path' is restricted")
            ))
        }
        
        // Check operation-specific restrictions
        when (operation) {
            FileOperation.READ -> {
                if (!securityPolicy.allowFileRead) {
                    return SecurityCheckResult.Denied(listOf(
                        SecurityViolation.UnauthorizedFileAccess("File read operation not allowed")
                    ))
                }
            }
            FileOperation.WRITE, FileOperation.DELETE -> {
                if (!securityPolicy.allowFileWrite) {
                    return SecurityCheckResult.Denied(listOf(
                        SecurityViolation.UnauthorizedFileAccess("File write/delete operation not allowed")
                    ))
                }
            }
            FileOperation.EXECUTE -> {
                if (!securityPolicy.allowFileExecute) {
                    return SecurityCheckResult.Denied(listOf(
                        SecurityViolation.UnauthorizedFileAccess("File execute operation not allowed")
                    ))
                }
            }
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkNetworkAccess(host: String, port: Int): SecurityCheckResult {
        if (!securityPolicy.allowNetworkAccess) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedNetworkAccess("Network access is disabled")
            ))
        }
        
        // Check for restricted hosts
        val restrictedHosts = listOf(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1"
        )
        
        if (host in restrictedHosts && !securityPolicy.allowLocalhost) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.RestrictedHost("Access to localhost is restricted")
            ))
        }
        
        // Check for restricted ports
        val restrictedPorts = listOf(22, 23, 25, 53, 80, 443, 993, 995)
        if (port in restrictedPorts && !securityPolicy.allowRestrictedPorts) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.RestrictedPort("Access to port $port is restricted")
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkSystemPropertyAccess(property: String): SecurityCheckResult {
        val restrictedProperties = listOf(
            "java.home",
            "user.home",
            "user.name",
            "os.name",
            "java.class.path",
            "java.library.path"
        )
        
        if (property in restrictedProperties && !securityPolicy.allowSystemProperties) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.RestrictedSystemProperty("Access to system property '$property' is restricted")
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    companion object {
        private val DANGEROUS_PATTERNS = mapOf(
            Regex("System\\.exit\\s*\\(") to "System exit calls",
            Regex("Runtime\\.getRuntime\\s*\\(\\s*\\)\\.exec") to "Runtime exec calls",
            Regex("ProcessBuilder\\s*\\(") to "ProcessBuilder usage",
            Regex("File\\s*\\(\\s*\"/") to "Absolute file path access",
            Regex("java\\.lang\\.reflect") to "Reflection usage",
            Regex("Class\\.forName") to "Dynamic class loading",
            Regex("System\\.setProperty") to "System property modification",
            Regex("System\\.setSecurityManager") to "Security manager modification",
            Regex("Thread\\.stop\\s*\\(") to "Unsafe thread termination",
            Regex("sun\\.") to "Internal Sun classes",
            Regex("com\\.sun\\.") to "Internal Sun classes"
        )
    }
}