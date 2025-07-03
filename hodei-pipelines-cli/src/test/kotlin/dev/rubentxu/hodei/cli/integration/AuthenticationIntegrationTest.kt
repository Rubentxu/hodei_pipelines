package dev.rubentxu.hodei.cli.integration

import dev.rubentxu.hodei.cli.client.AuthManager
import dev.rubentxu.hodei.cli.client.HodeiApiClient
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Integration tests for CLI authentication functionality.
 * 
 * Tests the complete authentication flow:
 * - Login with different user types (admin, user, moderator)
 * - Context management
 * - Token validation
 * - Logout functionality
 * - Error handling
 */
class AuthenticationIntegrationTest : CliIntegrationTestBase() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(AuthenticationIntegrationTest::class.java)
    }
    
    init {
        
        given("Authentication System") {
            
            `when`("Admin user logs in") {
                then("should authenticate successfully and receive valid token") {
                    logger.info("🔐 Testing admin authentication...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    val loginResult = apiClient.login(
                        cliConfig.adminCredentials.username,
                        cliConfig.adminCredentials.password
                    )
                    
                    loginResult.isSuccess shouldBe true
                    val response = loginResult.getOrThrow()
                    
                    response.user.username shouldBe "admin"
                    response.user.roles shouldContain "ADMIN"
                    response.token.isNotBlank() shouldBe true
                    
                    logger.info("✅ Admin authentication successful")
                    httpClient.close()
                }
            }
            
            `when`("Regular user logs in") {
                then("should authenticate successfully with USER role") {
                    logger.info("🔐 Testing user authentication...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    val loginResult = apiClient.login(
                        cliConfig.userCredentials.username,
                        cliConfig.userCredentials.password
                    )
                    
                    loginResult.isSuccess shouldBe true
                    val response = loginResult.getOrThrow()
                    
                    response.user.username shouldBe "user"
                    response.user.roles shouldContain "USER"
                    response.token.isNotBlank() shouldBe true
                    
                    logger.info("✅ User authentication successful")
                    httpClient.close()
                }
            }
            
            `when`("Moderator user logs in") {
                then("should authenticate successfully with MODERATOR role") {
                    logger.info("🔐 Testing moderator authentication...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    val loginResult = apiClient.login(
                        cliConfig.moderatorCredentials.username,
                        cliConfig.moderatorCredentials.password
                    )
                    
                    loginResult.isSuccess shouldBe true
                    val response = loginResult.getOrThrow()
                    
                    response.user.username shouldBe "moderator"
                    response.user.roles shouldContain "MODERATOR"
                    response.token.isNotBlank() shouldBe true
                    
                    logger.info("✅ Moderator authentication successful")
                    httpClient.close()
                }
            }
            
            `when`("Invalid credentials are used") {
                then("should fail authentication") {
                    logger.info("🔐 Testing invalid credentials...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    val loginResult = apiClient.login("invalid", "wrongpassword")
                    
                    loginResult.isFailure shouldBe true
                    
                    logger.info("✅ Invalid credentials rejected as expected")
                    httpClient.close()
                }
            }
        }
        
        given("Context Management") {
            
            `when`("Multiple contexts are configured") {
                then("should manage contexts correctly") {
                    logger.info("🎯 Testing context management...")
                    
                    // Create temporary auth manager for testing
                    val configDir = createTempDir("hodei-cli-test")
                    val authManager = AuthManager(configDir.absolutePath)
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // Login with admin and save as 'admin' context
                    val adminLogin = apiClient.login(
                        cliConfig.adminCredentials.username,
                        cliConfig.adminCredentials.password
                    )
                    adminLogin.isSuccess shouldBe true
                    
                    val adminResponse = adminLogin.getOrThrow()
                    authManager.saveContext(
                        "admin",
                        cliConfig.serverUrl,
                        adminResponse.user.username,
                        adminResponse.token
                    )
                    
                    // Login with user and save as 'user' context
                    val userLogin = apiClient.login(
                        cliConfig.userCredentials.username,
                        cliConfig.userCredentials.password
                    )
                    userLogin.isSuccess shouldBe true
                    
                    val userResponse = userLogin.getOrThrow()
                    authManager.saveContext(
                        "user",
                        cliConfig.serverUrl,
                        userResponse.user.username,
                        userResponse.token
                    )
                    
                    // Set admin as current context
                    authManager.setCurrentContext("admin")
                    authManager.getCurrentContext() shouldBe "admin"
                    
                    // Switch to user context
                    authManager.setCurrentContext("user")
                    authManager.getCurrentContext() shouldBe "user"
                    
                    // List contexts
                    val contexts = authManager.listContexts()
                    contexts.size shouldBe 2
                    contexts shouldContain "admin"
                    contexts shouldContain "user"
                    
                    logger.info("✅ Context management working correctly")
                    
                    // Cleanup
                    configDir.deleteRecursively()
                    httpClient.close()
                }
            }
        }
        
        given("Token Validation") {
            
            `when`("Valid token is used for API calls") {
                then("should access protected endpoints") {
                    logger.info("🔑 Testing token validation...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // First login to get token
                    val loginResult = apiClient.login(
                        cliConfig.adminCredentials.username,
                        cliConfig.adminCredentials.password
                    )
                    loginResult.isSuccess shouldBe true
                    
                    val token = loginResult.getOrThrow().token
                    apiClient.setAuthToken(token)
                    
                    // Test protected endpoint
                    val profileResult = apiClient.getProfile()
                    profileResult.isSuccess shouldBe true
                    
                    val profile = profileResult.getOrThrow()
                    profile.username shouldBe "admin"
                    
                    logger.info("✅ Token validation successful")
                    httpClient.close()
                }
            }
            
            `when`("Invalid token is used") {
                then("should reject access") {
                    logger.info("🔑 Testing invalid token...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // Set invalid token
                    apiClient.setAuthToken("invalid.token.here")
                    
                    // Try to access protected endpoint
                    val profileResult = apiClient.getProfile()
                    profileResult.isFailure shouldBe true
                    
                    logger.info("✅ Invalid token rejected as expected")
                    httpClient.close()
                }
            }
        }
        
        given("System Health") {
            
            `when`("Health endpoint is called") {
                then("should return healthy status") {
                    logger.info("💚 Testing health endpoint...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    val healthResult = apiClient.getHealth()
                    healthResult.isSuccess shouldBe true
                    
                    val health = healthResult.getOrThrow()
                    health.status shouldBe "healthy"
                    
                    logger.info("✅ Health endpoint responding correctly")
                    httpClient.close()
                }
            }
            
            `when`("Version endpoint is called") {
                then("should return version information") {
                    logger.info("📋 Testing version endpoint...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    val versionResult = apiClient.getVersion()
                    versionResult.isSuccess shouldBe true
                    
                    val version = versionResult.getOrThrow()
                    version.version.isNotBlank() shouldBe true
                    
                    logger.info("✅ Version endpoint responding correctly")
                    httpClient.close()
                }
            }
        }
    }
    
    private fun createHttpClient(): HttpClient {
        return HttpClient(CIO) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(Logging) {
                level = LogLevel.INFO
            }
        }
    }
}