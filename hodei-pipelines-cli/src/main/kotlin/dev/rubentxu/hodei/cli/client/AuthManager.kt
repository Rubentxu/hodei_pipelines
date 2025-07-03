package dev.rubentxu.hodei.cli.client

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Manages authentication tokens and user credentials for the CLI.
 * 
 * Similar to how kubectl manages contexts and credentials,
 * this stores authentication state in ~/.hodei/config
 */
class AuthManager(
    private val configDir: String = System.getProperty("user.home") + "/.hodei"
) {
    
    private val configFile = File(configDir, "config")
    private val json = Json { 
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    init {
        // Ensure config directory exists
        Files.createDirectories(Paths.get(configDir))
    }
    
    suspend fun login(url: String, loginResponse: LoginResponse): Result<Unit> {
        return try {
            val config = loadConfig()
            
            val contextName = extractContextName(url)
            val newContext = Context(
                url = url,
                user = loginResponse.user.username,
                token = loginResponse.token
            )
            
            val updatedConfig = config.copy(
                currentContext = contextName,
                contexts = config.contexts + (contextName to newContext)
            )
            
            saveConfig(updatedConfig)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    suspend fun logout(): Result<Unit> {
        return try {
            val config = loadConfig()
            val currentContext = config.currentContext
            
            if (currentContext != null && config.contexts.containsKey(currentContext)) {
                val updatedContexts = config.contexts - currentContext
                val newCurrentContext = if (updatedContexts.isNotEmpty()) {
                    updatedContexts.keys.first()
                } else {
                    null
                }
                
                val updatedConfig = config.copy(
                    currentContext = newCurrentContext,
                    contexts = updatedContexts
                )
                
                saveConfig(updatedConfig)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun getCurrentToken(): AuthToken? {
        return try {
            val config = loadConfig()
            val currentContext = config.currentContext ?: return null
            val context = config.contexts[currentContext] ?: return null
            context.token
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCurrentUser(): UserInfo? {
        return try {
            val config = loadConfig()
            val currentContext = config.currentContext ?: return null
            val context = config.contexts[currentContext] ?: return null
            
            UserInfo(
                username = context.user,
                orchestratorUrl = context.url,
                context = currentContext
            )
        } catch (e: Exception) {
            null
        }
    }
    
    fun getCurrentContext(): String? {
        return try {
            loadConfig().currentContext
        } catch (e: Exception) {
            null
        }
    }
    
    fun getContexts(): Map<String, Context> {
        return try {
            loadConfig().contexts
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    fun switchContext(contextName: String): Result<Unit> {
        return try {
            val config = loadConfig()
            
            if (!config.contexts.containsKey(contextName)) {
                return Result.failure(Exception("Context '$contextName' not found"))
            }
            
            val updatedConfig = config.copy(currentContext = contextName)
            saveConfig(updatedConfig)
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun addContext(name: String, url: String): Result<Unit> {
        return try {
            val config = loadConfig()
            
            val newContext = Context(
                url = url,
                user = "",
                token = null
            )
            
            val updatedConfig = config.copy(
                contexts = config.contexts + (name to newContext)
            )
            
            saveConfig(updatedConfig)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun removeContext(contextName: String): Result<Unit> {
        return try {
            val config = loadConfig()
            
            if (!config.contexts.containsKey(contextName)) {
                return Result.failure(Exception("Context '$contextName' not found"))
            }
            
            val updatedContexts = config.contexts - contextName
            val newCurrentContext = if (config.currentContext == contextName) {
                updatedContexts.keys.firstOrNull()
            } else {
                config.currentContext
            }
            
            val updatedConfig = config.copy(
                currentContext = newCurrentContext,
                contexts = updatedContexts
            )
            
            saveConfig(updatedConfig)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    fun isAuthenticated(): Boolean {
        return getCurrentToken() != null
    }
    
    fun getCurrentUrl(): String? {
        return try {
            val config = loadConfig()
            val currentContext = config.currentContext ?: return null
            val context = config.contexts[currentContext] ?: return null
            context.url
        } catch (e: Exception) {
            null
        }
    }
    
    private fun loadConfig(): CliConfig {
        return if (configFile.exists()) {
            try {
                val configText = configFile.readText()
                json.decodeFromString<CliConfig>(configText)
            } catch (e: Exception) {
                // If config is corrupted, start fresh
                CliConfig()
            }
        } else {
            CliConfig()
        }
    }
    
    private fun saveConfig(config: CliConfig) {
        val configText = json.encodeToString(config)
        configFile.writeText(configText)
        
        // Set proper permissions (600) for security
        configFile.setReadable(false, false)
        configFile.setReadable(true, true)
        configFile.setWritable(false, false)
        configFile.setWritable(true, true)
        configFile.setExecutable(false, false)
    }
    
    private fun extractContextName(url: String): String {
        // Extract a reasonable context name from URL
        // e.g., "http://localhost:8080" -> "localhost"
        // e.g., "https://hodei.company.com" -> "company"
        return try {
            val cleanUrl = url.substringAfter("://")
            val host = cleanUrl.substringBefore(":")
            val parts = host.split(".")
            
            when {
                host == "localhost" -> "local"
                parts.size >= 2 -> parts[parts.size - 2] // company from company.com
                else -> host
            }
        } catch (e: Exception) {
            "default"
        }
    }
}

@Serializable
data class CliConfig(
    val currentContext: String? = null,
    val contexts: Map<String, Context> = emptyMap()
)

@Serializable
data class Context(
    val url: String,
    val user: String,
    val token: AuthToken? = null
)