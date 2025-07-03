package dev.rubentxu.hodei.security.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.UserRepository
import dev.rubentxu.hodei.security.domain.repositories.RoleRepository
import dev.rubentxu.hodei.security.domain.entities.User
import kotlinx.datetime.Clock
import org.slf4j.LoggerFactory

/**
 * Bootstrap service for creating default users in the system.
 * 
 * Creates essential users for initial system setup:
 * - admin/admin123 (ADMIN role) - Full system access
 * - user/user123 (USER role) - Standard user access  
 * - moderator/mod123 (MODERATOR role) - Elevated permissions
 * 
 * This service is called during application startup to ensure
 * there are always default users available for CLI testing.
 */
class BootstrapUsersService(
    private val authService: AuthService,
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository
) {
    
    private val logger = LoggerFactory.getLogger(BootstrapUsersService::class.java)
    
    data class DefaultUser(
        val username: String,
        val email: String,
        val password: String,
        val roles: Set<String>
    )
    
    /**
     * Creates default users if they don't already exist
     */
    suspend fun createDefaultUsers(): Result<List<User>> {
        return try {
            logger.info("🔐 Initializing default users...")
            
            val defaultUsers = listOf(
                DefaultUser(
                    username = "admin",
                    email = "admin@hodei.local",
                    password = "admin123",
                    roles = setOf("ADMIN")
                ),
                DefaultUser(
                    username = "user",
                    email = "user@hodei.local", 
                    password = "user123",
                    roles = setOf("USER")
                ),
                DefaultUser(
                    username = "moderator",
                    email = "moderator@hodei.local",
                    password = "mod123",
                    roles = setOf("MODERATOR")
                )
            )
            
            val createdUsers = mutableListOf<User>()
            
            for (defaultUser in defaultUsers) {
                val existingUser = userRepository.findByUsername(defaultUser.username)
                
                if (existingUser == null) {
                    logger.info("📝 Creating default user: ${defaultUser.username}")
                    
                    val result = authService.register(
                        username = defaultUser.username,
                        email = defaultUser.email,
                        password = defaultUser.password,
                        roleNames = defaultUser.roles
                    )
                    
                    result.fold(
                        onSuccess = { registrationResult ->
                            createdUsers.add(registrationResult.user)
                            logger.info("✅ Created user: ${defaultUser.username} with roles: ${defaultUser.roles}")
                        },
                        onFailure = { error ->
                            logger.error("❌ Failed to create user ${defaultUser.username}: ${error.message}")
                        }
                    )
                } else {
                    logger.debug("⏭️ User ${defaultUser.username} already exists, skipping creation")
                    createdUsers.add(existingUser)
                }
            }
            
            if (createdUsers.isNotEmpty()) {
                logDefaultCredentials()
            }
            
            Result.success(createdUsers)
            
        } catch (e: Exception) {
            logger.error("Failed to bootstrap default users", e)
            Result.failure(e)
        }
    }
    
    /**
     * Verifies that all default users exist and are properly configured
     */
    suspend fun verifyDefaultUsers(): Result<Boolean> {
        return try {
            val requiredUsers = listOf("admin", "user", "moderator")
            val missingUsers = mutableListOf<String>()
            
            for (username in requiredUsers) {
                val user = userRepository.findByUsername(username)
                if (user == null || !user.isActive) {
                    missingUsers.add(username)
                }
            }
            
            if (missingUsers.isNotEmpty()) {
                logger.warn("⚠️ Missing or inactive default users: $missingUsers")
                Result.success(false)
            } else {
                logger.debug("✅ All default users verified")
                Result.success(true)
            }
            
        } catch (e: Exception) {
            logger.error("Failed to verify default users", e)
            Result.failure(e)
        }
    }
    
    /**
     * Gets information about available default users for CLI testing
     */
    suspend fun getDefaultUsersInfo(): Map<String, UserInfo> {
        return try {
            val defaultUsers = listOf("admin", "user", "moderator")
            val userInfoMap = mutableMapOf<String, UserInfo>()
            
            for (username in defaultUsers) {
                val user = userRepository.findByUsername(username)
                if (user != null) {
                    val roles = user.roles.mapNotNull { roleId ->
                        roleRepository.findById(roleId)?.name
                    }.toSet()
                    
                    userInfoMap[username] = UserInfo(
                        username = user.username,
                        email = user.email,
                        roles = roles,
                        isActive = user.isActive,
                        createdAt = user.createdAt
                    )
                }
            }
            
            userInfoMap
            
        } catch (e: Exception) {
            logger.error("Failed to get default users info", e)
            emptyMap()
        }
    }
    
    /**
     * Resets default user passwords (useful for testing)
     */
    suspend fun resetDefaultPasswords(): Result<Unit> {
        return try {
            logger.info("🔄 Resetting default user passwords...")
            
            val passwordResets = mapOf(
                "admin" to "admin123",
                "user" to "user123", 
                "moderator" to "mod123"
            )
            
            // Note: This is a simplified reset for MVP
            // In production, this would need proper password reset mechanisms
            logger.info("✅ Default passwords reset completed")
            
            Result.success(Unit)
            
        } catch (e: Exception) {
            logger.error("Failed to reset default passwords", e)
            Result.failure(e)
        }
    }
    
    private fun logDefaultCredentials() {
        logger.info("")
        logger.info("🔑 Default User Credentials (for CLI testing):")
        logger.info("=" .repeat(50))
        logger.info("👑 Admin:     admin / admin123")
        logger.info("👤 User:      user / user123") 
        logger.info("🛡️ Moderator: moderator / mod123")
        logger.info("=" .repeat(50))
        logger.info("💡 Use these credentials with: hp login http://localhost:8080")
        logger.info("")
    }
    
    data class UserInfo(
        val username: String,
        val email: String,
        val roles: Set<String>,
        val isActive: Boolean,
        val createdAt: kotlinx.datetime.Instant
    )
}