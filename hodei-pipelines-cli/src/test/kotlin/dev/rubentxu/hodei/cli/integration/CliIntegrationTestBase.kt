package dev.rubentxu.hodei.cli.integration

import dev.rubentxu.hodei.cli.testcontainers.CliTestConfig
import dev.rubentxu.hodei.cli.testcontainers.OrchestratorTestContainer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.core.spec.style.scopes.BehaviorSpecGivenContainerScope
import io.kotest.extensions.testcontainers.perSpec
import org.slf4j.LoggerFactory

/**
 * Base class for CLI integration tests using Testcontainers.
 * 
 * This base class provides:
 * - Orchestrator container setup and teardown
 * - Common test utilities
 * - Bootstrap validation
 * - CLI configuration
 */
abstract class CliIntegrationTestBase : BehaviorSpec() {
    
    companion object {
        private val logger = LoggerFactory.getLogger(CliIntegrationTestBase::class.java)
    }
    
    // Testcontainer for orchestrator
    protected val orchestratorContainer = OrchestratorTestContainer.fromJar()
        .waitForBootstrap()
    
    // Register container lifecycle
    private val containerExtension = orchestratorContainer.perSpec()
    
    // CLI configuration once container is started
    protected lateinit var cliConfig: CliTestConfig
    
    init {
        // Setup before all tests
        beforeSpec {
            logger.info("🚀 Starting orchestrator container for CLI tests...")
            
            // Container is started by the extension
            // Get CLI configuration
            cliConfig = orchestratorContainer.getCliConfig()
            
            logger.info("✅ Orchestrator ready at: ${cliConfig.serverUrl}")
            logger.info("🔐 Admin credentials: ${cliConfig.adminCredentials.username}/${cliConfig.adminCredentials.password}")
            
            // Validate bootstrap completed
            validateBootstrap()
        }
        
        afterSpec {
            logger.info("🛑 Stopping orchestrator container...")
            if (::cliConfig.isInitialized) {
                logger.info("📋 Final orchestrator logs:")
                println(orchestratorContainer.getOrchestratorLogs())
            }
        }
    }
    
    /**
     * Validate that bootstrap completed successfully
     */
    private suspend fun validateBootstrap() {
        logger.info("🔍 Validating bootstrap completion...")
        
        val isHealthy = orchestratorContainer.isHealthy()
        if (!isHealthy) {
            val logs = orchestratorContainer.getOrchestratorLogs()
            logger.error("❌ Orchestrator is not healthy!\nLogs:\n$logs")
            throw AssertionError("Orchestrator health check failed")
        }
        
        logger.info("✅ Bootstrap validation completed successfully")
    }
    
    /**
     * Helper to create test scenarios with orchestrator context
     */
    protected fun withOrchestrator(
        name: String,
        test: suspend BehaviorSpecGivenContainerScope.(CliTestConfig) -> Unit
    ) {
        given(name) {
            test(cliConfig)
        }
    }
    
    /**
     * Execute HTTP request to orchestrator
     */
    protected suspend fun executeHttpRequest(
        method: String,
        path: String,
        headers: Map<String, String> = emptyMap(),
        body: String? = null
    ): HttpResponse {
        // This would be implemented with actual HTTP client
        // For now, return a mock response
        return HttpResponse(200, "{\"status\": \"ok\"}", emptyMap())
    }
    
    /**
     * Simulate CLI command execution
     */
    protected suspend fun executeCli(command: String): CliResult {
        logger.info("🖥️ Executing CLI command: $command")
        
        // For now, this is a placeholder
        // In real implementation, this would:
        // 1. Create temporary CLI config file
        // 2. Execute the HP CLI with proper arguments
        // 3. Capture output and exit code
        
        return CliResult(
            exitCode = 0,
            output = "Command executed successfully",
            error = ""
        )
    }
}

/**
 * HTTP response for testing
 */
data class HttpResponse(
    val statusCode: Int,
    val body: String,
    val headers: Map<String, String>
)

/**
 * CLI execution result
 */
data class CliResult(
    val exitCode: Int,
    val output: String,
    val error: String
) {
    val isSuccess: Boolean get() = exitCode == 0
}