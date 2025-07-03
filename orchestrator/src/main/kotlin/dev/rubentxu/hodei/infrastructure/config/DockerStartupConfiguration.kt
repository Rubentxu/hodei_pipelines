package dev.rubentxu.hodei.infrastructure.config

import arrow.core.Either
import dev.rubentxu.hodei.resourcemanagement.infrastructure.docker.DockerEnvironmentBootstrap
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Startup configuration for Docker environment.
 * 
 * Handles Docker environment discovery and registration during application startup.
 * This enables zero-configuration setup for local Docker environments.
 */
class DockerStartupConfiguration : KoinComponent {
    
    private val logger = LoggerFactory.getLogger(DockerStartupConfiguration::class.java)
    private val dockerBootstrap: DockerEnvironmentBootstrap by inject()
    
    /**
     * Initialize Docker environment during application startup
     */
    fun initialize() {
        val infraType = System.getProperty("hodei.infrastructure.type", "docker")
        
        if (infraType.lowercase() == "docker") {
            logger.info("Initializing Docker environment...")
            
            runBlocking {
                // First check Docker health
                val healthResult = dockerBootstrap.performHealthCheck()
                if (healthResult.isSuccess) {
                    val health = healthResult.getOrThrow()
                    if (health.isHealthy) {
                        logger.info("Docker daemon is healthy: ${health.version} (API: ${health.apiVersion})")
                        logger.info("Containers: ${health.containersRunning} running")
                        
                        // Proceed with discovery and registration
                        discoverAndRegisterDocker()
                    } else {
                        logger.warn("Docker daemon is not healthy: ${health.version}")
                    }
                } else {
                    logger.warn("Docker health check failed: ${healthResult.exceptionOrNull()?.message}")
                    logger.info("Docker environment will not be registered as a resource pool")
                }
            }
        } else {
            logger.info("Infrastructure type is set to '$infraType', skipping Docker initialization")
        }
    }
    
    private suspend fun discoverAndRegisterDocker() {
        try {
            // Get Docker environment info
            val envInfoResult = dockerBootstrap.getDockerEnvironmentInfo()
            if (envInfoResult.isSuccess) {
                val envInfo = envInfoResult.getOrThrow()
                logger.info("Docker environment successfully discovered")
                logger.info("Version: ${envInfo.version}")
                logger.info("OS: ${envInfo.operatingSystem}")
                logger.info("Architecture: ${envInfo.architecture}")
                
                // Register as resource pool
                val poolId = dev.rubentxu.hodei.shared.domain.primitives.DomainId.generate()
                val registrationResult = dockerBootstrap.registerAsResourcePool(
                    poolId = poolId,
                    poolName = "auto-discovered-docker",
                    maxWorkers = 2
                )
                
                if (registrationResult.isSuccess) {
                    logger.info("Docker environment registered as resource pool: ${poolId.value}")
                } else {
                    logger.warn("Failed to register Docker as resource pool: ${registrationResult.exceptionOrNull()?.message}")
                }
                
                // Log compatibility info
                checkCompatibility()
                
            } else {
                logger.error("Failed to discover Docker environment: ${envInfoResult.exceptionOrNull()?.message}")
                logger.info("The orchestrator will start without Docker support")
            }
        } catch (e: Exception) {
            logger.error("Failed to discover Docker environment: ${e.message}")
            logger.info("The orchestrator will start without Docker support")
        }
    }
    
    private suspend fun checkCompatibility() {
        val compatibilityResult = dockerBootstrap.validateDockerCompatibility()
        if (compatibilityResult.isSuccess) {
            val compatibility = compatibilityResult.getOrThrow()
            if (compatibility.isCompatible) {
                logger.info("Docker environment is fully compatible")
                logger.info("Recommendation: ${compatibility.recommendation}")
            } else {
                logger.warn("Docker environment has compatibility issues:")
                compatibility.warnings.forEach { warning ->
                    logger.warn("  - $warning")
                }
                compatibility.issues.forEach { issue ->
                    logger.error("  - $issue")
                }
            }
        } else {
            logger.warn("Could not validate Docker compatibility: ${compatibilityResult.exceptionOrNull()?.message}")
        }
    }
}