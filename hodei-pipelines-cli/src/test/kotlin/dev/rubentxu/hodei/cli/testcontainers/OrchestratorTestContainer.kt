package dev.rubentxu.hodei.cli.testcontainers

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.utility.DockerImageName
import org.slf4j.LoggerFactory
import java.io.File
import java.time.Duration

/**
 * TestContainer for Hodei Orchestrator.
 * 
 * This container starts a complete Hodei orchestrator with:
 * - Bootstrap users (admin/admin123, user/user123, moderator/mod123)
 * - Docker discovery and resource pool registration
 * - Default worker templates
 * - REST API on port 8080
 * - gRPC server on port 9090
 */
class OrchestratorTestContainer : GenericContainer<OrchestratorTestContainer> {
    
    companion object {
        private val logger = LoggerFactory.getLogger(OrchestratorTestContainer::class.java)
        
        const val REST_API_PORT = 8080
        const val GRPC_PORT = 9090
        
        // Default credentials created by bootstrap
        const val ADMIN_USERNAME = "admin"
        const val ADMIN_PASSWORD = "admin123"
        const val USER_USERNAME = "user"
        const val USER_PASSWORD = "user123"
        const val MODERATOR_USERNAME = "moderator"
        const val MODERATOR_PASSWORD = "mod123"
        
        /**
         * Create container using pre-built JAR from orchestrator module
         */
        fun fromJar(): OrchestratorTestContainer {
            // Find the orchestrator JAR file
            val orchestratorJar = findOrchestratorJar()
            
            return OrchestratorTestContainer(
                ImageFromDockerfile()
                    .withDockerfileFromBuilder { builder ->
                        builder
                            .from("openjdk:17-jre-slim")
                            .run("apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*")
                            .copy("orchestrator.jar", "/app/orchestrator.jar")
                            .workDir("/app")
                            .expose(REST_API_PORT, GRPC_PORT)
                            .cmd("java", "-jar", "orchestrator.jar")
                    }
                    .withFileFromFile("orchestrator.jar", orchestratorJar)
            )
        }
        
        /**
         * Create container using Docker image (if available)
         */
        fun fromImage(imageName: String = "hodei/orchestrator:latest"): OrchestratorTestContainer {
            return OrchestratorTestContainer(DockerImageName.parse(imageName))
        }
        
        private fun findOrchestratorJar(): File {
            // Try to find the orchestrator JAR in build directory
            val possiblePaths = listOf(
                "orchestrator/build/libs/orchestrator-all.jar",
                "../orchestrator/build/libs/orchestrator-all.jar",
                "../../orchestrator/build/libs/orchestrator-all.jar",
                "build/libs/orchestrator-all.jar"
            )
            
            for (path in possiblePaths) {
                val file = File(path)
                if (file.exists()) {
                    logger.info("Found orchestrator JAR at: ${file.absolutePath}")
                    return file
                }
            }
            
            throw IllegalStateException(
                "Could not find orchestrator JAR. Please run 'gradle :orchestrator:build' first.\n" +
                "Searched paths: ${possiblePaths.joinToString(", ")}"
            )
        }
    }
    
    constructor(dockerImageName: DockerImageName) : super(dockerImageName)
    constructor(dockerImage: ImageFromDockerfile) : super(dockerImage)
    
    init {
        // Configure container
        withExposedPorts(REST_API_PORT, GRPC_PORT)
        
        // Wait for health endpoint to be ready
        waitingFor(
            Wait.forHttp("/v1/health")
                .forPort(REST_API_PORT)
                .withStartupTimeout(Duration.ofMinutes(3))
        )
        
        // Enable Docker socket access for Docker discovery
        withFileSystemBind("/var/run/docker.sock", "/var/run/docker.sock")
        
        // Configure logging
        withLogConsumer(Slf4jLogConsumer(logger).withPrefix("ORCHESTRATOR"))
        
        // Environment variables
        withEnv("HODEI_LOG_LEVEL", "INFO")
        withEnv("HODEI_INFRASTRUCTURE_TYPE", "docker")
    }
    
    /**
     * Get the base URL for REST API calls
     */
    fun getRestApiUrl(): String {
        return "http://${host}:${getMappedPort(REST_API_PORT)}"
    }
    
    /**
     * Get the gRPC endpoint
     */
    fun getGrpcEndpoint(): String {
        return "${host}:${getMappedPort(GRPC_PORT)}"
    }
    
    /**
     * Wait for the orchestrator to be fully ready with bootstrap completed
     */
    fun waitForBootstrap(): OrchestratorTestContainer {
        // Wait for basic health
        waitingFor(
            Wait.forHttp("/v1/health")
                .forPort(REST_API_PORT)
                .withStartupTimeout(Duration.ofMinutes(3))
        )
        
        // Additional wait for bootstrap completion
        waitingFor(
            Wait.forLogMessage(".*🔗 CLI Usage: hp login.*", 1)
                .withStartupTimeout(Duration.ofMinutes(2))
        )
        
        return this
    }
    
    /**
     * Get configuration for CLI testing
     */
    fun getCliConfig(): CliTestConfig {
        return CliTestConfig(
            serverUrl = getRestApiUrl(),
            grpcEndpoint = getGrpcEndpoint(),
            adminCredentials = Credentials(ADMIN_USERNAME, ADMIN_PASSWORD),
            userCredentials = Credentials(USER_USERNAME, USER_PASSWORD),
            moderatorCredentials = Credentials(MODERATOR_USERNAME, MODERATOR_PASSWORD)
        )
    }
    
    /**
     * Execute health check and return result
     */
    fun isHealthy(): Boolean {
        return try {
            val result = execInContainer("curl", "-f", "http://localhost:$REST_API_PORT/v1/health")
            result.exitCode == 0
        } catch (e: Exception) {
            logger.warn("Health check failed: ${e.message}")
            false
        }
    }
    
    /**
     * Get orchestrator logs
     */
    fun getOrchestratorLogs(): String {
        return logs
    }
    
    /**
     * Execute CLI command inside container (if CLI is available)
     */
    fun executeCommand(command: String): CommandResult {
        return try {
            val result = execInContainer("bash", "-c", command)
            CommandResult(
                exitCode = result.exitCode,
                stdout = result.stdout,
                stderr = result.stderr
            )
        } catch (e: Exception) {
            CommandResult(
                exitCode = -1,
                stdout = "",
                stderr = e.message ?: "Unknown error"
            )
        }
    }
}

/**
 * Configuration for CLI testing
 */
data class CliTestConfig(
    val serverUrl: String,
    val grpcEndpoint: String,
    val adminCredentials: Credentials,
    val userCredentials: Credentials,
    val moderatorCredentials: Credentials
)

/**
 * User credentials
 */
data class Credentials(
    val username: String,
    val password: String
)

/**
 * Command execution result
 */
data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccess: Boolean get() = exitCode == 0
    val output: String get() = if (stdout.isNotBlank()) stdout else stderr
}