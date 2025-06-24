package dev.rubentxu.hodei.pipelines.dsl.security

import mu.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Implementación del sandbox para Pipeline DSL.
 * Proporciona funcionalidad de seguridad similar al sandbox de Jenkins Pipeline DSL.
 */
class PipelineSandbox(
    override val securityPolicy: SecurityPolicy
) : PipelineSecurityManager {
    
    override fun checkScriptAccess(script: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        val violations = mutableListOf<SecurityViolation>()
        
        // Verificar tamaño del script
        if (script.length > securityPolicy.maxScriptSize) {
            violations.add(SecurityViolation.ScriptTooLarge("Script size ${script.length} exceeds limit ${securityPolicy.maxScriptSize}"))
        }
        
        // Verificar patrones peligrosos
        DANGEROUS_PATTERNS.forEach { (pattern, description) ->
            if (pattern.containsMatchIn(script)) {
                violations.add(SecurityViolation.DangerousPattern(pattern.pattern, description))
            }
        }
        
        // Verificar llamadas al sistema
        if (!securityPolicy.allowSystemCalls) {
            SYSTEM_CALL_PATTERNS.forEach { pattern ->
                if (pattern.containsMatchIn(script)) {
                    violations.add(SecurityViolation.UnauthorizedSystemCall("System call detected: ${pattern.pattern}"))
                }
            }
        }
        
        // Verificar acceso a archivos
        if (!securityPolicy.allowFileSystemAccess) {
            FILE_ACCESS_PATTERNS.forEach { pattern ->
                if (pattern.containsMatchIn(script)) {
                    violations.add(SecurityViolation.UnauthorizedFileAccess("File access detected: ${pattern.pattern}"))
                }
            }
        }
        
        // Verificar acceso a red
        if (!securityPolicy.allowNetworkAccess) {
            NETWORK_ACCESS_PATTERNS.forEach { pattern ->
                if (pattern.containsMatchIn(script)) {
                    violations.add(SecurityViolation.UnauthorizedNetworkAccess("Network access detected: ${pattern.pattern}"))
                }
            }
        }
        
        // Verificar reflexión
        if (!securityPolicy.allowReflection) {
            REFLECTION_PATTERNS.forEach { pattern ->
                if (pattern.containsMatchIn(script)) {
                    violations.add(SecurityViolation.UnauthorizedReflection("Reflection usage detected: ${pattern.pattern}"))
                }
            }
        }
        
        // Verificar métodos bloqueados
        securityPolicy.blockedMethods.forEach { method ->
            if (script.contains(method)) {
                violations.add(SecurityViolation.UnauthorizedMethodCall(method.substringBeforeLast('.'), method.substringAfterLast('.')))
            }
        }
        
        return if (violations.isEmpty()) {
            SecurityCheckResult.Allowed
        } else {
            if (securityPolicy.logViolations) {
                logger.warn { "Script security check failed with ${violations.size} violations: ${violations.map { it.message }}" }
            }
            SecurityCheckResult.Denied(violations)
        }
    }
    
    override fun checkLibraryAccess(libraryId: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        // Verificar librerías explícitamente bloqueadas
        if (securityPolicy.blockedLibraries.any { pattern -> 
            libraryId.matches(Regex(pattern.replace("*", ".*"))) 
        }) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.BlockedLibrary("Library '$libraryId' is explicitly blocked")
            ))
        }
        
        // Si hay lista de permitidas, solo permitir las de la lista
        if (securityPolicy.allowedLibraries.isNotEmpty()) {
            val isAllowed = securityPolicy.allowedLibraries.any { pattern ->
                libraryId.matches(Regex(pattern.replace("*", ".*")))
            }
            
            if (!isAllowed) {
                return SecurityCheckResult.Denied(listOf(
                    SecurityViolation.UnauthorizedLibrary("Library '$libraryId' is not in the allowed list")
                ))
            }
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkFileAccess(path: String, operation: FileOperation): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        val violations = mutableListOf<SecurityViolation>()
        
        // Verificar si el acceso al sistema de archivos está permitido
        if (!securityPolicy.allowFileSystemAccess) {
            violations.add(SecurityViolation.UnauthorizedFileAccess("File system access is disabled"))
        }
        
        // Verificar rutas bloqueadas
        if (securityPolicy.blockedPaths.any { blockedPath -> path.startsWith(blockedPath) }) {
            violations.add(SecurityViolation.RestrictedPath("Access to path '$path' is restricted"))
        }
        
        // Verificar rutas permitidas (si están configuradas)
        if (securityPolicy.allowedPaths.isNotEmpty()) {
            val isAllowed = securityPolicy.allowedPaths.any { allowedPath -> 
                path.startsWith(allowedPath) 
            }
            if (!isAllowed) {
                violations.add(SecurityViolation.RestrictedPath("Path '$path' is not in allowed paths"))
            }
        }
        
        // Verificar operaciones específicas
        when (operation) {
            FileOperation.READ -> {
                if (!securityPolicy.allowFileRead) {
                    violations.add(SecurityViolation.UnauthorizedFileAccess("File read operation not allowed"))
                }
            }
            FileOperation.WRITE, FileOperation.CREATE, FileOperation.MODIFY -> {
                if (!securityPolicy.allowFileWrite) {
                    violations.add(SecurityViolation.UnauthorizedFileAccess("File write operation not allowed"))
                }
            }
            FileOperation.DELETE -> {
                if (!securityPolicy.allowFileWrite) {
                    violations.add(SecurityViolation.UnauthorizedFileAccess("File delete operation not allowed"))
                }
            }
            FileOperation.EXECUTE -> {
                if (!securityPolicy.allowFileExecute) {
                    violations.add(SecurityViolation.UnauthorizedFileAccess("File execute operation not allowed"))
                }
            }
        }
        
        return if (violations.isEmpty()) {
            SecurityCheckResult.Allowed
        } else {
            SecurityCheckResult.Denied(violations)
        }
    }
    
    override fun checkNetworkAccess(host: String, port: Int): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        val violations = mutableListOf<SecurityViolation>()
        
        if (!securityPolicy.allowNetworkAccess) {
            violations.add(SecurityViolation.UnauthorizedNetworkAccess("Network access is disabled"))
        }
        
        // Verificar hosts bloqueados
        if (securityPolicy.blockedHosts.contains(host)) {
            violations.add(SecurityViolation.RestrictedHost("Access to host '$host' is restricted"))
        }
        
        // Verificar localhost
        if (!securityPolicy.allowLocalhost && LOCALHOST_PATTERNS.contains(host)) {
            violations.add(SecurityViolation.RestrictedHost("Access to localhost is restricted"))
        }
        
        // Verificar puertos restringidos
        if (!securityPolicy.allowRestrictedPorts && RESTRICTED_PORTS.contains(port)) {
            violations.add(SecurityViolation.RestrictedPort("Access to port $port is restricted"))
        }
        
        return if (violations.isEmpty()) {
            SecurityCheckResult.Allowed
        } else {
            SecurityCheckResult.Denied(violations)
        }
    }
    
    override fun checkSystemPropertyAccess(property: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        if (!securityPolicy.allowSystemProperties && RESTRICTED_SYSTEM_PROPERTIES.contains(property)) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.RestrictedSystemProperty("Access to system property '$property' is restricted")
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkMethodCall(className: String, methodName: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        val fullMethodName = "$className.$methodName"
        
        if (securityPolicy.blockedMethods.contains(fullMethodName)) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedMethodCall(className, methodName)
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkConstructorCall(className: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        if (securityPolicy.blockedLibraries.any { pattern ->
            className.matches(Regex(pattern.replace("*", ".*")))
        }) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedConstructor(className)
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkStaticAccess(className: String, memberName: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        if (securityPolicy.blockedLibraries.any { pattern ->
            className.matches(Regex(pattern.replace("*", ".*")))
        }) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedStaticAccess(className, memberName)
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun checkReflectionAccess(operation: String): SecurityCheckResult {
        if (!securityPolicy.sandboxEnabled) {
            return SecurityCheckResult.Allowed
        }
        
        if (!securityPolicy.allowReflection) {
            return SecurityCheckResult.Denied(listOf(
                SecurityViolation.UnauthorizedReflection("Reflection operation '$operation' not allowed")
            ))
        }
        
        return SecurityCheckResult.Allowed
    }
    
    override fun performFullSecurityCheck(script: String): SecurityCheckResult {
        val results = listOf(
            checkScriptAccess(script)
        )
        
        val allViolations = results.filterIsInstance<SecurityCheckResult.Denied>()
            .flatMap { it.violations }
        
        return if (allViolations.isEmpty()) {
            SecurityCheckResult.Allowed
        } else {
            SecurityCheckResult.Denied(allViolations)
        }
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
            Regex("com\\.sun\\.") to "Internal Sun classes",
            Regex("javax\\.script\\.") to "Script engine usage",
            Regex("jdk\\.internal\\.") to "Internal JDK classes"
        )
        
        private val SYSTEM_CALL_PATTERNS = listOf(
            Regex("Runtime\\.getRuntime\\(\\)\\.exec"),
            Regex("ProcessBuilder\\("),
            Regex("new\\s+ProcessBuilder"),
            Regex("System\\.exit"),
            Regex("Runtime\\.getRuntime\\(\\)\\.halt")
        )
        
        private val FILE_ACCESS_PATTERNS = listOf(
            Regex("new\\s+File\\("),
            Regex("FileInputStream\\("),
            Regex("FileOutputStream\\("),
            Regex("Files\\."),
            Regex("Paths\\."),
            Regex("File\\.createTempFile")
        )
        
        private val NETWORK_ACCESS_PATTERNS = listOf(
            Regex("new\\s+URL\\("),
            Regex("HttpURLConnection"),
            Regex("new\\s+Socket\\("),
            Regex("ServerSocket\\("),
            Regex("InetAddress\\."),
            Regex("URLConnection")
        )
        
        private val REFLECTION_PATTERNS = listOf(
            Regex("::class\\.java"),
            Regex("Class\\.forName"),
            Regex("\\.javaClass"),
            Regex("java\\.lang\\.reflect\\."),
            Regex("Method\\.invoke"),
            Regex("Field\\.set"),
            Regex("Constructor\\.newInstance")
        )
        
        private val LOCALHOST_PATTERNS = listOf(
            "localhost",
            "127.0.0.1",
            "0.0.0.0",
            "::1"
        )
        
        private val RESTRICTED_PORTS = setOf(
            21, 22, 23, 25, 53, 80, 110, 143, 443, 993, 995, 3389, 5432, 3306
        )
        
        private val RESTRICTED_SYSTEM_PROPERTIES = setOf(
            "java.home",
            "user.home",
            "user.name",
            "os.name",
            "java.class.path",
            "java.library.path",
            "java.version",
            "java.vendor"
        )
    }
}