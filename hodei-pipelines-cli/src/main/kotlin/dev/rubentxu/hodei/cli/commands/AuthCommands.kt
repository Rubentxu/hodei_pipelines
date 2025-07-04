package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import dev.rubentxu.hodei.cli.client.AuthManager
import dev.rubentxu.hodei.cli.client.HodeiApiClient
import kotlinx.coroutines.runBlocking

/**
 * Login command - authenticate with Hodei orchestrator
 * Usage: hp login <url> [options]
 */
class LoginCommand : CliktCommand(
    name = "login",
    help = "🔐 Authenticate with Hodei orchestrator"
) {
    
    private val url by argument(
        help = "Orchestrator URL (e.g., http://localhost:8080)"
    )
    
    private val username by option(
        "--username", "-u",
        help = "Username for authentication"
    ).prompt("Username")
    
    private val password by option(
        "--password", "-p",
        help = "Password for authentication"
    ).prompt("Password", hideInput = true)
    
    private val context by option(
        "--context",
        help = "Name for this context (defaults to auto-generated)"
    )
    
    private val insecure by option(
        "--insecure",
        help = "Skip TLS certificate verification"
    ).flag()
    
    override fun run() = runBlocking {
        echo("🔐 Authenticating with $url...")
        
        val authManager = AuthManager()
        val apiClient = HodeiApiClient(url, authManager, verbose = true)
        
        try {
            // First, check if orchestrator is reachable
            val healthResult = apiClient.healthCheck()
            if (healthResult.isFailure) {
                echo("❌ Failed to connect to orchestrator at $url")
                echo("   ${healthResult.exceptionOrNull()?.message}")
                echo("")
                echo("💡 Make sure the orchestrator is running:")
                echo("   gradle :orchestrator:run")
                return@runBlocking
            }
            
            echo("✅ Connected to orchestrator")
            
            // Attempt login
            val loginResult = apiClient.login(url, username, password)
            
            loginResult.fold(
                onSuccess = { loginResponse ->
                    // Save authentication
                    val saveResult = authManager.login(url, loginResponse)
                    saveResult.fold(
                        onSuccess = {
                            echo("✅ Authentication successful!")
                            echo("")
                            echo("👤 User: ${loginResponse.user?.username ?: "unknown"}")
                            echo("🌐 Server: $url")
                            echo("🎯 Context: ${authManager.getCurrentContext()}")
                            echo("⏰ Token expires in: ${loginResponse.expiresIn ?: "unknown"}s")
                            echo("")
                            echo("🚀 You can now use hp commands:")
                            echo("   hp pool list")
                            echo("   hp job list")
                            echo("   hp health")
                        },
                        onFailure = { error ->
                            echo("❌ Failed to save authentication: ${error.message}")
                        }
                    )
                },
                onFailure = { error ->
                    echo("❌ Authentication failed: ${error.message}")
                    echo("")
                    echo("💡 Please check:")
                    echo("   • Username and password are correct")
                    echo("   • Orchestrator has authentication enabled")
                    echo("   • Network connectivity to $url")
                }
            )
            
        } finally {
            apiClient.close()
        }
    }
}

/**
 * Logout command - clear authentication
 * Usage: hp logout
 */
class LogoutCommand : CliktCommand(
    name = "logout",
    help = "🚪 Logout from current orchestrator"
) {
    
    private val all by option(
        "--all",
        help = "Logout from all contexts"
    ).flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        
        if (!authManager.isAuthenticated()) {
            echo("ℹ️ Not currently logged in")
            return@runBlocking
        }
        
        val currentUser = authManager.getCurrentUser()
        val currentUrl = authManager.getCurrentUrl()
        
        echo("🚪 Logging out...")
        echo("👤 User: ${currentUser?.username}")
        echo("🌐 Server: $currentUrl")
        
        // Attempt to notify server of logout
        currentUrl?.let { url ->
            val apiClient = HodeiApiClient(url, authManager)
            try {
                val logoutResult = apiClient.logout()
                if (logoutResult.isSuccess) {
                    echo("✅ Server notified of logout")
                }
                apiClient.close()
            } catch (e: Exception) {
                // Continue with local logout even if server notification fails
                echo("⚠️ Could not notify server (continuing with local logout)")
            }
        }
        
        // Clear local authentication
        val result = authManager.logout()
        result.fold(
            onSuccess = {
                echo("✅ Logged out successfully")
                
                if (authManager.getContexts().isNotEmpty()) {
                    echo("")
                    echo("💡 Other contexts are still available:")
                    authManager.getContexts().forEach { (name, context) ->
                        val marker = if (name == authManager.getCurrentContext()) "* " else "  "
                        echo("$marker$name -> ${context.url}")
                    }
                }
            },
            onFailure = { error ->
                echo("❌ Failed to logout: ${error.message}")
            }
        )
    }
}

/**
 * WhoAmI command - show current user info
 * Usage: hp whoami
 */
class WhoAmICommand : CliktCommand(
    name = "whoami",
    help = "👤 Show current user and context information"
) {
    
    private val verbose by option(
        "--verbose", "-v",
        help = "Show detailed information"
    ).flag()
    
    override fun run() = runBlocking {
        val authManager = AuthManager()
        
        if (!authManager.isAuthenticated()) {
            echo("❌ Not authenticated")
            echo("")
            echo("💡 Run 'hp login <url>' to authenticate")
            return@runBlocking
        }
        
        val currentUser = authManager.getCurrentUser()
        val currentContext = authManager.getCurrentContext()
        val currentUrl = authManager.getCurrentUrl()
        
        if (currentUser == null) {
            echo("❌ Authentication data corrupted")
            echo("💡 Please run 'hp login <url>' again")
            return@runBlocking
        }
        
        echo("👤 Current User Information")
        echo("=" .repeat(40))
        echo("Username: ${currentUser.username}")
        echo("Context:  $currentContext")
        echo("Server:   $currentUrl")
        
        if (verbose) {
            echo("")
            echo("🔍 Detailed Information:")
            
            // Test connection
            currentUrl?.let { url ->
                val apiClient = HodeiApiClient(url, authManager)
                try {
                    val whoamiResult = apiClient.whoami()
                    whoamiResult.fold(
                        onSuccess = { userInfo ->
                            echo("✅ Connection: Active")
                            echo("🔐 Token: Valid")
                        },
                        onFailure = { error ->
                            echo("❌ Connection: Failed")
                            echo("   Error: ${error.message}")
                        }
                    )
                    
                    // Get server version
                    val versionResult = apiClient.getVersion()
                    versionResult.fold(
                        onSuccess = { version ->
                            echo("📦 Server Version: ${version.version}")
                            echo("🔧 Server App: ${version.applicationName}")
                        },
                        onFailure = {
                            echo("📦 Server Version: Unknown")
                        }
                    )
                    
                    apiClient.close()
                } catch (e: Exception) {
                    echo("❌ Connection: Failed (${e.message})")
                }
            }
            
            echo("")
            echo("🎯 Available Contexts:")
            authManager.getContexts().forEach { (name, context) ->
                val marker = if (name == currentContext) "* " else "  "
                val userInfo = if (context.user.isNotEmpty()) " (${context.user})" else ""
                echo("$marker$name -> ${context.url}$userInfo")
            }
        }
    }
}

/**
 * Config command - manage configuration
 * Usage: hp config <subcommand>
 */
class ConfigCommand : CliktCommand(
    name = "config",
    help = "⚙️ Manage CLI configuration"
) {
    override fun run() = Unit
}

/**
 * List contexts subcommand
 */
class ConfigGetContextsCommand : CliktCommand(
    name = "get-contexts",
    help = "List all available contexts"
) {
    
    override fun run() {
        val authManager = AuthManager()
        val contexts = authManager.getContexts()
        val currentContext = authManager.getCurrentContext()
        
        if (contexts.isEmpty()) {
            echo("No contexts found")
            echo("💡 Run 'hp login <url>' to create a context")
            return
        }
        
        echo("🎯 Available Contexts:")
        echo("")
        contexts.forEach { (name, context) ->
            val marker = if (name == currentContext) "* " else "  "
            val userInfo = if (context.user.isNotEmpty()) " (${context.user})" else ""
            echo("$marker$name -> ${context.url}$userInfo")
        }
    }
}

/**
 * Use context subcommand
 */
class ConfigUseContextCommand : CliktCommand(
    name = "use-context",
    help = "Switch to a different context"
) {
    
    private val contextName by argument(
        help = "Name of the context to use"
    )
    
    override fun run() {
        val authManager = AuthManager()
        
        val result = authManager.switchContext(contextName)
        result.fold(
            onSuccess = {
                echo("✅ Switched to context '$contextName'")
                
                val userInfo = authManager.getCurrentUser()
                if (userInfo != null) {
                    echo("👤 User: ${userInfo.username}")
                    echo("🌐 Server: ${userInfo.orchestratorUrl}")
                }
            },
            onFailure = { error ->
                echo("❌ ${error.message}")
                
                echo("")
                echo("💡 Available contexts:")
                authManager.getContexts().keys.forEach { name ->
                    echo("   $name")
                }
            }
        )
    }
}

/**
 * Current context subcommand
 */
class ConfigCurrentContextCommand : CliktCommand(
    name = "current-context",
    help = "Show current context"
) {
    
    override fun run() {
        val authManager = AuthManager()
        val currentContext = authManager.getCurrentContext()
        
        if (currentContext != null) {
            echo(currentContext)
        } else {
            echo("No current context")
            echo("💡 Run 'hp login <url>' to create a context")
        }
    }
}