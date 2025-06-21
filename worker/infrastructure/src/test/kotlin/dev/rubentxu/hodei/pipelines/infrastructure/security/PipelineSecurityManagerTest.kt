package dev.rubentxu.hodei.pipelines.infrastructure.security

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach

class PipelineSecurityManagerTest {
    
    private lateinit var securityManager: PipelineSecurityManager
    
    @BeforeEach
    fun setup() {
        securityManager = DefaultPipelineSecurityManager(SecurityPolicy.development())
    }
    
    @Test
    fun `should allow safe script`() {
        // Given
        val safeScript = """
            val message = "Hello World"
            println(message)
        """.trimIndent()
        
        // When
        val result = securityManager.checkScriptAccess(safeScript)
        
        // Then
        assertEquals(SecurityCheckResult.Allowed, result)
    }
    
    @Test
    fun `should deny script with dangerous patterns`() {
        // Given
        val dangerousScript = """
            System.exit(1)
            Runtime.getRuntime().exec("rm -rf /")
        """.trimIndent()
        
        // When
        val result = securityManager.checkScriptAccess(dangerousScript)
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.isNotEmpty())
        assertTrue(deniedResult.violations.any { it.message.contains("System exit") })
    }
    
    @Test
    fun `should deny script with file system access when disabled`() {
        // Given
        val strictSecurityManager = DefaultPipelineSecurityManager(SecurityPolicy.strict())
        val scriptWithFileAccess = """
            val file = File("/etc/passwd")
            file.readText()
        """.trimIndent()
        
        // When
        val result = strictSecurityManager.checkScriptAccess(scriptWithFileAccess)
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { 
            it.message.contains("file system access") || it.message.contains("File(")
        })
    }
    
    @Test
    fun `should deny script that is too large`() {
        // Given
        val policy = SecurityPolicy(maxScriptSize = 100)
        val securityManagerWithSizeLimit = DefaultPipelineSecurityManager(policy)
        val largeScript = "println(\"Hello\")\n".repeat(50) // More than 100 characters
        
        // When
        val result = securityManagerWithSizeLimit.checkScriptAccess(largeScript)
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { it.message.contains("Script size") })
    }
    
    @Test
    fun `should allow whitelisted library`() {
        // Given
        val policy = SecurityPolicy(allowedLibraries = setOf("kotlin-stdlib", "kotlinx-coroutines"))
        val securityManagerWithWhitelist = DefaultPipelineSecurityManager(policy)
        
        // When
        val result = securityManagerWithWhitelist.checkLibraryAccess("kotlin-stdlib")
        
        // Then
        assertEquals(SecurityCheckResult.Allowed, result)
    }
    
    @Test
    fun `should deny non-whitelisted library`() {
        // Given
        val policy = SecurityPolicy(allowedLibraries = setOf("kotlin-stdlib"))
        val securityManagerWithWhitelist = DefaultPipelineSecurityManager(policy)
        
        // When
        val result = securityManagerWithWhitelist.checkLibraryAccess("malicious-lib")
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
    }
    
    @Test
    fun `should deny blocked library`() {
        // Given
        val policy = SecurityPolicy(blockedLibraries = setOf("dangerous-lib"))
        val securityManagerWithBlocklist = DefaultPipelineSecurityManager(policy)
        
        // When
        val result = securityManagerWithBlocklist.checkLibraryAccess("dangerous-lib")
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { it.message.contains("blocked") })
    }
    
    @Test
    fun `should deny dangerous library patterns`() {
        // When
        val result = securityManager.checkLibraryAccess("sun.internal.security")
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { it.message.contains("dangerous") })
    }
    
    @Test
    fun `should allow file access when enabled`() {
        // When
        val result = securityManager.checkFileAccess("/tmp/test.txt", FileOperation.READ)
        
        // Then
        assertEquals(SecurityCheckResult.Allowed, result)
    }
    
    @Test
    fun `should deny access to restricted paths`() {
        // When
        val result = securityManager.checkFileAccess("/etc/passwd", FileOperation.READ)
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { it.message.contains("restricted") })
    }
    
    @Test
    fun `should deny network access to localhost when disabled`() {
        // Given
        val policy = SecurityPolicy(allowLocalhost = false)
        val restrictiveSecurityManager = DefaultPipelineSecurityManager(policy)
        
        // When
        val result = restrictiveSecurityManager.checkNetworkAccess("localhost", 8080)
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { it.message.contains("localhost") })
    }
    
    @Test
    fun `should deny access to restricted ports`() {
        // Given
        val policy = SecurityPolicy(allowRestrictedPorts = false)
        val restrictiveSecurityManager = DefaultPipelineSecurityManager(policy)
        
        // When
        val result = restrictiveSecurityManager.checkNetworkAccess("example.com", 22)
        
        // Then
        assertTrue(result is SecurityCheckResult.Denied)
        val deniedResult = result as SecurityCheckResult.Denied
        assertTrue(deniedResult.violations.any { it.message.contains("port 22") })
    }
    
    @Test
    fun `security policies should have correct defaults`() {
        // Test strict policy
        val strict = SecurityPolicy.strict()
        assertFalse(strict.allowSystemCalls)
        assertFalse(strict.allowFileSystemAccess)
        assertFalse(strict.allowNetworkAccess)
        assertTrue(strict.sandboxEnabled)
        
        // Test permissive policy
        val permissive = SecurityPolicy.permissive()
        assertTrue(permissive.allowSystemCalls)
        assertTrue(permissive.allowFileSystemAccess)
        assertTrue(permissive.allowNetworkAccess)
        assertFalse(permissive.sandboxEnabled)
        
        // Test development policy
        val development = SecurityPolicy.development()
        assertTrue(development.allowSystemCalls)
        assertTrue(development.allowFileSystemAccess)
        assertTrue(development.allowNetworkAccess)
        assertTrue(development.sandboxEnabled)
    }
}