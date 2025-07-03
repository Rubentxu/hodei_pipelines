package dev.rubentxu.hodei.cli.integration

import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption

/**
 * Integration tests that execute actual CLI commands.
 * 
 * This test class demonstrates how to:
 * - Execute real HP CLI commands
 * - Manage CLI configuration
 * - Test command output and exit codes
 * - Simulate real user interactions
 */
class CliCommandTest : CliIntegrationTestBase() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(CliCommandTest::class.java)
    }
    
    init {
        
        given("CLI Command Execution") {
            
            `when`("hp version command is executed") {
                then("should display version information") {
                    logger.info("📋 Testing 'hp version' command...")
                    
                    val result = executeRealCliCommand("version")
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "Hodei"
                    
                    logger.info("✅ Version command executed successfully")
                    logger.info("   Output: ${result.output.take(100)}...")
                }
            }
            
            `when`("hp login command is executed") {
                then("should authenticate successfully") {
                    logger.info("🔐 Testing 'hp login' command...")
                    
                    val loginCommand = "login ${cliConfig.serverUrl} " +
                            "--username ${cliConfig.adminCredentials.username} " +
                            "--password ${cliConfig.adminCredentials.password}"
                    
                    val result = executeRealCliCommand(loginCommand)
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "successful"
                    
                    logger.info("✅ Login command executed successfully")
                }
            }
            
            `when`("hp health command is executed") {
                then("should show orchestrator health") {
                    logger.info("💚 Testing 'hp health' command...")
                    
                    // First login
                    authenticateCliUser()
                    
                    val result = executeRealCliCommand("health")
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "healthy"
                    
                    logger.info("✅ Health command executed successfully")
                }
            }
            
            `when`("hp pool list command is executed") {
                then("should list available resource pools") {
                    logger.info("🏊 Testing 'hp pool list' command...")
                    
                    // First login
                    authenticateCliUser()
                    
                    val result = executeRealCliCommand("pool list")
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "docker"
                    
                    logger.info("✅ Pool list command executed successfully")
                }
            }
            
            `when`("hp template list command is executed") {
                then("should list available templates") {
                    logger.info("📦 Testing 'hp template list' command...")
                    
                    // First login
                    authenticateCliUser()
                    
                    val result = executeRealCliCommand("template list")
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "default"
                    
                    logger.info("✅ Template list command executed successfully")
                }
            }
            
            `when`("hp whoami command is executed") {
                then("should show current user information") {
                    logger.info("👤 Testing 'hp whoami' command...")
                    
                    // First login
                    authenticateCliUser()
                    
                    val result = executeRealCliCommand("whoami")
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "admin"
                    result.output shouldContain "ADMIN"
                    
                    logger.info("✅ Whoami command executed successfully")
                }
            }
            
            `when`("hp job submit command is executed") {
                then("should submit and track job") {
                    logger.info("🚀 Testing 'hp job submit' command...")
                    
                    // First login
                    authenticateCliUser()
                    
                    // Create a test pipeline file
                    val pipelineFile = createTestPipelineFile()
                    
                    val result = executeRealCliCommand("job submit ${pipelineFile.absolutePath} --name cli-test-job")
                    
                    result.exitCode shouldBe 0
                    result.output shouldContain "submitted"
                    
                    // Extract job ID from output (simplified parsing)
                    val jobIdRegex = "job-[a-f0-9-]+".toRegex()
                    val jobIdMatch = jobIdRegex.find(result.output)
                    
                    if (jobIdMatch != null) {
                        val jobId = jobIdMatch.value
                        logger.info("📋 Extracted job ID: $jobId")
                        
                        // Test job status command
                        val statusResult = executeRealCliCommand("job status $jobId")
                        statusResult.exitCode shouldBe 0
                        
                        logger.info("✅ Job status command executed successfully")
                    }
                    
                    // Cleanup
                    pipelineFile.delete()
                    
                    logger.info("✅ Job submit command executed successfully")
                }
            }
        }
        
        given("CLI Configuration Management") {
            
            `when`("Multiple contexts are configured") {
                then("should manage contexts correctly") {
                    logger.info("🎯 Testing context management commands...")
                    
                    // Login with different context names
                    val adminLoginResult = executeRealCliCommand(
                        "login ${cliConfig.serverUrl} " +
                                "--username ${cliConfig.adminCredentials.username} " +
                                "--password ${cliConfig.adminCredentials.password} " +
                                "--context admin-test"
                    )
                    adminLoginResult.exitCode shouldBe 0
                    
                    val userLoginResult = executeRealCliCommand(
                        "login ${cliConfig.serverUrl} " +
                                "--username ${cliConfig.userCredentials.username} " +
                                "--password ${cliConfig.userCredentials.password} " +
                                "--context user-test"
                    )
                    userLoginResult.exitCode shouldBe 0
                    
                    // List contexts
                    val contextsResult = executeRealCliCommand("config get-contexts")
                    contextsResult.exitCode shouldBe 0
                    contextsResult.output shouldContain "admin-test"
                    contextsResult.output shouldContain "user-test"
                    
                    // Switch context
                    val switchResult = executeRealCliCommand("config use-context admin-test")
                    switchResult.exitCode shouldBe 0
                    
                    // Verify current context
                    val currentResult = executeRealCliCommand("config current-context")
                    currentResult.exitCode shouldBe 0
                    currentResult.output shouldContain "admin-test"
                    
                    logger.info("✅ Context management commands executed successfully")
                }
            }
        }
        
        given("Error Scenarios") {
            
            `when`("Invalid commands are executed") {
                then("should show appropriate error messages") {
                    logger.info("❌ Testing error scenarios...")
                    
                    // Test invalid command
                    val invalidResult = executeRealCliCommand("invalid-command")
                    invalidResult.exitCode shouldBe 1
                    
                    // Test command without authentication
                    val unauthResult = executeRealCliCommand("pool list", clearAuth = true)
                    unauthResult.exitCode shouldBe 2 // Authentication error
                    
                    logger.info("✅ Error scenarios handled correctly")
                }
            }
        }
    }
    
    /**
     * Execute real CLI command
     */
    private suspend fun executeRealCliCommand(
        command: String,
        clearAuth: Boolean = false
    ): CliResult {
        val configDir = createTempCliConfig(clearAuth)
        
        try {
            val cliJar = findCliJar()
            val fullCommand = "java -jar ${cliJar.absolutePath} --config ${configDir.absolutePath}/.hodei/config $command"
            
            logger.debug("Executing: $fullCommand")
            
            val process = ProcessBuilder()
                .command("bash", "-c", fullCommand)
                .redirectErrorStream(true)
                .start()
            
            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            
            return CliResult(exitCode, output, "")
            
        } finally {
            configDir.deleteRecursively()
        }
    }
    
    /**
     * Authenticate CLI user for subsequent commands
     */
    private suspend fun authenticateCliUser() {
        val loginCommand = "login ${cliConfig.serverUrl} " +
                "--username ${cliConfig.adminCredentials.username} " +
                "--password ${cliConfig.adminCredentials.password}"
        
        val result = executeRealCliCommand(loginCommand)
        result.exitCode shouldBe 0
    }
    
    /**
     * Create temporary CLI configuration
     */
    private fun createTempCliConfig(clearAuth: Boolean = false): File {
        val tempDir = Files.createTempDirectory("hodei-cli-test").toFile()
        val configDir = File(tempDir, ".hodei")
        configDir.mkdirs()
        
        if (!clearAuth) {
            // Create basic config file
            val configFile = File(configDir, "config")
            val config = """
                {
                  "current-context": "default",
                  "contexts": {
                    "default": {
                      "server": "${cliConfig.serverUrl}",
                      "user": "",
                      "token": "",
                      "insecure": false
                    }
                  }
                }
            """.trimIndent()
            
            Files.write(configFile.toPath(), config.toByteArray(), StandardOpenOption.CREATE)
        }
        
        return tempDir
    }
    
    /**
     * Find CLI JAR file
     */
    private fun findCliJar(): File {
        val possiblePaths = listOf(
            "hodei-pipelines-cli/build/libs/hodei-pipelines-cli-all.jar",
            "build/libs/hodei-pipelines-cli-all.jar",
            "../hodei-pipelines-cli/build/libs/hodei-pipelines-cli-all.jar"
        )
        
        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists()) {
                return file
            }
        }
        
        throw IllegalStateException(
            "Could not find CLI JAR. Please run 'gradle :hodei-pipelines-cli:build' first.\n" +
            "Searched paths: ${possiblePaths.joinToString(", ")}"
        )
    }
    
    /**
     * Create test pipeline file
     */
    private fun createTestPipelineFile(): File {
        val pipelineContent = """
            #!/usr/bin/env pipeline-dsl
            
            pipeline {
                name = "CLI Integration Test"
                description = "Test pipeline for CLI integration testing"
                
                stage("test") {
                    step("hello") {
                        run {
                            println("Hello from CLI integration test!")
                            "CLI test completed"
                        }
                    }
                }
            }
        """.trimIndent()
        
        val tempFile = Files.createTempFile("test-pipeline", ".pipeline.kts").toFile()
        tempFile.writeText(pipelineContent)
        return tempFile
    }
}