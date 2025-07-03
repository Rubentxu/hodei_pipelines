package dev.rubentxu.hodei.infrastructure.config

import dev.rubentxu.hodei.security.application.services.BootstrapUsersService
import dev.rubentxu.hodei.templatemanagement.application.services.WorkerTemplateService
import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.slf4j.LoggerFactory

/**
 * Bootstrap configuration for system initialization.
 * 
 * Handles initialization of default resources during application startup:
 * - Default users and roles (admin, user, moderator)
 * - Default resource pools (local Docker, Kubernetes if available)
 * - Default worker templates (basic pipeline templates)
 * 
 * This ensures the system is ready for immediate use after startup.
 */
class BootstrapConfiguration : KoinComponent {
    
    private val logger = LoggerFactory.getLogger(BootstrapConfiguration::class.java)
    private val bootstrapUsersService: BootstrapUsersService by inject()
    private val workerTemplateService: WorkerTemplateService by inject()
    private val resourcePoolService: ResourcePoolService by inject()
    
    /**
     * Initialize all default system resources
     */
    fun initialize() {
        logger.info("🚀 Starting system bootstrap...")
        
        runBlocking {
            try {
                // Initialize in logical order
                initializeDefaultUsers()
                initializeDefaultResourcePools()
                initializeDefaultTemplates()
                
                logger.info("✅ System bootstrap completed successfully")
                logBootstrapSummary()
                
            } catch (e: Exception) {
                logger.error("❌ System bootstrap failed: ${e.message}", e)
                throw e
            }
        }
    }
    
    /**
     * Initialize default users and roles
     */
    private suspend fun initializeDefaultUsers() {
        logger.info("🔐 Initializing default users...")
        
        val result = bootstrapUsersService.createDefaultUsers()
        result.fold(
            onSuccess = { users ->
                logger.info("✅ Default users initialized: ${users.size} users created/verified")
            },
            onFailure = { error ->
                logger.error("❌ Failed to initialize default users: ${error.message}")
                throw error
            }
        )
    }
    
    /**
     * Initialize default resource pools
     */
    private suspend fun initializeDefaultResourcePools() {
        logger.info("🏗️ Initializing default resource pools...")
        
        try {
            // Resource pool initialization will be handled by the Docker environment bootstrap
            // This is a placeholder for future resource pool management features
            logger.info("✅ Resource pools will be managed by Docker environment bootstrap")
            
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to initialize default resource pools: ${e.message}")
            // Don't throw - resource pools are optional for basic functionality
        }
    }
    
    /**
     * Initialize default worker templates
     */
    private suspend fun initializeDefaultTemplates() {
        logger.info("📋 Initializing default worker templates...")
        
        try {
            // Check if we already have templates using the correct API
            val existingTemplatesResult = workerTemplateService.listWorkerTemplates()
            
            existingTemplatesResult.fold(
                { error ->
                    logger.warn("⚠️ Failed to check existing templates: $error")
                    // Continue anyway
                },
                { existingTemplates ->
                    if (existingTemplates.isEmpty()) {
                        logger.info("📝 Creating default worker templates...")
                        
                        // Use the service's built-in method to create all templates
                        runBlocking {
                            val result = workerTemplateService.createAllTemplates()
                            result.fold(
                                { error ->
                                    logger.warn("⚠️ Failed to create default templates: $error")
                                },
                                { templates ->
                                    logger.info("✅ Default worker templates created: ${templates.size} templates")
                                }
                            )
                        }
                    } else {
                        logger.info("⏭️ Worker templates already exist (${existingTemplates.size}), skipping creation")
                    }
                }
            )
            
        } catch (e: Exception) {
            logger.warn("⚠️ Failed to initialize default worker templates: ${e.message}")
            // Don't throw - templates are optional for basic functionality
        }
    }
    
    /**
     * Log bootstrap summary
     */
    private suspend fun logBootstrapSummary() {
        logger.info("")
        logger.info("🎯 Bootstrap Summary:")
        logger.info("=" .repeat(50))
        
        // Users summary
        val userInfo = bootstrapUsersService.getDefaultUsersInfo()
        logger.info("👥 Users: ${userInfo.size} default users available")
        userInfo.forEach { (username, info) ->
            logger.info("   - $username (${info.roles.joinToString(", ")})")
        }
        
        // Resource pools summary (placeholder)
        logger.info("🏗️ Resource Pools: Managed by Docker environment bootstrap")
        
        // Templates summary
        try {
            runBlocking {
                val templatesResult = workerTemplateService.listWorkerTemplates()
                templatesResult.fold(
                    { error ->
                        logger.info("📋 Templates: Unable to list templates ($error)")
                    },
                    { templates ->
                        logger.info("📋 Templates: ${templates.size} templates available")
                        templates.forEach { template ->
                            logger.info("   - ${template.name}")
                        }
                    }
                )
            }
        } catch (e: Exception) {
            logger.info("📋 Templates: Unable to list templates")
        }
        
        logger.info("=" .repeat(50))
        logger.info("🔗 CLI Usage: hp login http://localhost:8080")
        logger.info("🔗 Health Check: curl http://localhost:8080/v1/health")
        logger.info("")
    }
}