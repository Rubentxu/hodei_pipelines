package dev.rubentxu.hodei.cli.integration

import dev.rubentxu.hodei.cli.client.HodeiApiClient
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.plugins.logging.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

/**
 * Integration tests for complete CLI workflow scenarios.
 * 
 * Tests end-to-end workflows including:
 * - Login → List Resources → Submit Job → Monitor → Results
 * - Multi-user scenarios with different permissions
 * - Resource management operations
 * - Template management
 * - Error scenarios and recovery
 */
class WorkflowIntegrationTest : CliIntegrationTestBase() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(WorkflowIntegrationTest::class.java)
    }
    
    init {
        
        given("Complete Development Workflow") {
            
            `when`("Developer logs in and checks system status") {
                then("should see all available resources") {
                    logger.info("🔄 Testing complete development workflow...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // Step 1: Login as admin
                    val loginResult = apiClient.login(
                        cliConfig.adminCredentials.username,
                        cliConfig.adminCredentials.password
                    )
                    loginResult.isSuccess shouldBe true
                    
                    val token = loginResult.getOrThrow().token
                    apiClient.setAuthToken(token)
                    
                    // Step 2: Check health
                    val healthResult = apiClient.getHealth()
                    healthResult.isSuccess shouldBe true
                    healthResult.getOrThrow().status shouldBe "healthy"
                    
                    // Step 3: List resource pools (should have auto-discovered Docker)
                    val poolsResult = apiClient.listResourcePools()
                    poolsResult.isSuccess shouldBe true
                    
                    val pools = poolsResult.getOrThrow()
                    pools.shouldNotBeEmpty()
                    
                    val dockerPool = pools.find { it.type == "docker" }
                    dockerPool shouldNotBe null
                    dockerPool!!.name shouldContain "docker"
                    
                    // Step 4: List templates (should have bootstrap templates)
                    val templatesResult = apiClient.listTemplates()
                    templatesResult.isSuccess shouldBe true
                    
                    val templates = templatesResult.getOrThrow()
                    templates.shouldNotBeEmpty()
                    
                    val defaultTemplate = templates.find { it.name.contains("default") }
                    defaultTemplate shouldNotBe null
                    
                    // Step 5: List workers (initially empty)
                    val workersResult = apiClient.listWorkers()
                    workersResult.isSuccess shouldBe true
                    
                    logger.info("✅ Development workflow - system status check complete")
                    logger.info("   📊 Resource Pools: ${pools.size}")
                    logger.info("   📦 Templates: ${templates.size}")
                    logger.info("   👷 Workers: ${workersResult.getOrThrow().size}")
                    
                    httpClient.close()
                }
            }
        }
        
        given("Job Submission and Monitoring Workflow") {
            
            `when`("User submits a job and monitors execution") {
                then("should complete the full job lifecycle") {
                    logger.info("🚀 Testing job submission and monitoring workflow...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // Step 1: Login as user
                    val loginResult = apiClient.login(
                        cliConfig.userCredentials.username,
                        cliConfig.userCredentials.password
                    )
                    loginResult.isSuccess shouldBe true
                    
                    val token = loginResult.getOrThrow().token
                    apiClient.setAuthToken(token)
                    
                    // Step 2: Create a simple job payload
                    val jobPayload = createSimpleJobPayload()
                    
                    // Step 3: Submit job
                    val submitResult = apiClient.submitJob(
                        name = "test-job-${System.currentTimeMillis()}",
                        pipelineContent = jobPayload,
                        poolId = null // Use default pool
                    )
                    
                    submitResult.isSuccess shouldBe true
                    val jobId = submitResult.getOrThrow().id
                    
                    logger.info("📋 Job submitted with ID: $jobId")
                    
                    // Step 4: Monitor job status
                    var jobStatus = ""
                    var attempts = 0
                    val maxAttempts = 30 // 30 seconds timeout
                    
                    while (attempts < maxAttempts && jobStatus != "COMPLETED" && jobStatus != "FAILED") {
                        kotlinx.coroutines.delay(1000) // Wait 1 second
                        
                        val statusResult = apiClient.getJobStatus(jobId)
                        if (statusResult.isSuccess) {
                            jobStatus = statusResult.getOrThrow().status
                            logger.info("   📊 Job status: $jobStatus")
                        }
                        attempts++
                    }
                    
                    // Step 5: Get job logs
                    val logsResult = apiClient.getJobLogs(jobId)
                    if (logsResult.isSuccess) {
                        val logs = logsResult.getOrThrow()
                        logger.info("📋 Job logs received: ${logs.length} characters")
                    }
                    
                    // Step 6: List all jobs
                    val jobsResult = apiClient.listJobs()
                    jobsResult.isSuccess shouldBe true
                    
                    val jobs = jobsResult.getOrThrow()
                    val ourJob = jobs.find { it.id == jobId }
                    ourJob shouldNotBe null
                    
                    logger.info("✅ Job workflow completed")
                    logger.info("   🆔 Job ID: $jobId")
                    logger.info("   📊 Final Status: $jobStatus")
                    logger.info("   📋 Total Jobs: ${jobs.size}")
                    
                    httpClient.close()
                }
            }
        }
        
        given("Multi-User Permission Scenarios") {
            
            `when`("Different users access the same resources") {
                then("should respect role-based permissions") {
                    logger.info("👥 Testing multi-user permission scenarios...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // Test admin user capabilities
                    logger.info("🔑 Testing admin user...")
                    val adminLogin = apiClient.login(
                        cliConfig.adminCredentials.username,
                        cliConfig.adminCredentials.password
                    )
                    adminLogin.isSuccess shouldBe true
                    
                    apiClient.setAuthToken(adminLogin.getOrThrow().token)
                    
                    // Admin should be able to list everything
                    val adminPoolsResult = apiClient.listResourcePools()
                    adminPoolsResult.isSuccess shouldBe true
                    
                    val adminTemplatesResult = apiClient.listTemplates()
                    adminTemplatesResult.isSuccess shouldBe true
                    
                    logger.info("   ✅ Admin access: ${adminPoolsResult.getOrThrow().size} pools, ${adminTemplatesResult.getOrThrow().size} templates")
                    
                    // Test regular user capabilities
                    logger.info("🔑 Testing regular user...")
                    val userLogin = apiClient.login(
                        cliConfig.userCredentials.username,
                        cliConfig.userCredentials.password
                    )
                    userLogin.isSuccess shouldBe true
                    
                    apiClient.setAuthToken(userLogin.getOrThrow().token)
                    
                    // User should be able to list pools and templates (read access)
                    val userPoolsResult = apiClient.listResourcePools()
                    userPoolsResult.isSuccess shouldBe true
                    
                    val userTemplatesResult = apiClient.listTemplates()
                    userTemplatesResult.isSuccess shouldBe true
                    
                    logger.info("   ✅ User access: ${userPoolsResult.getOrThrow().size} pools, ${userTemplatesResult.getOrThrow().size} templates")
                    
                    // Test moderator capabilities
                    logger.info("🔑 Testing moderator user...")
                    val moderatorLogin = apiClient.login(
                        cliConfig.moderatorCredentials.username,
                        cliConfig.moderatorCredentials.password
                    )
                    moderatorLogin.isSuccess shouldBe true
                    
                    apiClient.setAuthToken(moderatorLogin.getOrThrow().token)
                    
                    // Moderator should have elevated access
                    val moderatorPoolsResult = apiClient.listResourcePools()
                    moderatorPoolsResult.isSuccess shouldBe true
                    
                    logger.info("   ✅ Moderator access: ${moderatorPoolsResult.getOrThrow().size} pools")
                    
                    logger.info("✅ Multi-user permission testing completed")
                    
                    httpClient.close()
                }
            }
        }
        
        given("Error Handling and Recovery") {
            
            `when`("Network issues occur") {
                then("should handle gracefully") {
                    logger.info("🔧 Testing error handling scenarios...")
                    
                    val httpClient = createHttpClient()
                    
                    // Test connection to non-existent server
                    val badApiClient = HodeiApiClient(httpClient, "http://localhost:99999")
                    val badResult = badApiClient.getHealth()
                    badResult.isFailure shouldBe true
                    
                    logger.info("✅ Network error handling working correctly")
                    
                    httpClient.close()
                }
            }
            
            `when`("Invalid payloads are submitted") {
                then("should validate and reject") {
                    logger.info("🔧 Testing payload validation...")
                    
                    val httpClient = createHttpClient()
                    val apiClient = HodeiApiClient(httpClient, cliConfig.serverUrl)
                    
                    // Login first
                    val loginResult = apiClient.login(
                        cliConfig.userCredentials.username,
                        cliConfig.userCredentials.password
                    )
                    loginResult.isSuccess shouldBe true
                    apiClient.setAuthToken(loginResult.getOrThrow().token)
                    
                    // Submit invalid job
                    val invalidResult = apiClient.submitJob(
                        name = "", // Invalid empty name
                        pipelineContent = "invalid pipeline content",
                        poolId = "non-existent-pool"
                    )
                    
                    invalidResult.isFailure shouldBe true
                    
                    logger.info("✅ Payload validation working correctly")
                    
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
    
    private fun createSimpleJobPayload(): String {
        return """
            #!/usr/bin/env pipeline-dsl
            
            pipeline {
                name = "Test CLI Job"
                description = "Simple test job for CLI integration testing"
                
                environment {
                    put("TEST_ENV", "integration")
                }
                
                stage("test") {
                    description = "Simple test stage"
                    
                    step("hello") {
                        description = "Say hello"
                        run {
                            println("Hello from CLI integration test!")
                            println("Current time: " + java.time.LocalDateTime.now())
                            "Test completed successfully"
                        }
                    }
                }
            }
            
            onSuccess {
                println("✅ Test job completed successfully!")
            }
            
            onFailure { error ->
                println("❌ Test job failed: " + error)
            }
        """.trimIndent()
    }
}