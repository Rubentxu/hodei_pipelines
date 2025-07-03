package dev.rubentxu.hodei.security.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.UserRepository
import dev.rubentxu.hodei.security.domain.repositories.RoleRepository
import dev.rubentxu.hodei.security.domain.repositories.AuditRepository
import dev.rubentxu.hodei.security.domain.entities.User
import dev.rubentxu.hodei.security.domain.entities.AuditLog
import org.mindrot.jbcrypt.BCrypt
import kotlinx.datetime.Clock

data class LoginResult(
    val user: User,
    val token: String,
    val expiresAt: kotlinx.datetime.Instant
)

data class RegistrationResult(
    val user: User,
    val message: String
)

class AuthService(
    private val userRepository: UserRepository,
    private val roleRepository: RoleRepository,
    private val auditRepository: AuditRepository,
    private val jwtService: JwtService
) {
    
    suspend fun login(
        username: String, 
        password: String,
        ipAddress: String? = null,
        userAgent: String? = null
    ): Result<LoginResult> {
        return try {
            // Find user by username
            val user = userRepository.findByUsername(username)
                ?: return Result.failure(Exception("Invalid credentials"))
            
            // Check if user is active
            if (!user.isActive) {
                auditLogin(user.id, "LOGIN_FAILED_INACTIVE", ipAddress, userAgent)
                return Result.failure(Exception("Account is deactivated"))
            }
            
            // Verify password
            if (!verifyPassword(password, getStoredPassword(username))) {
                auditLogin(user.id, "LOGIN_FAILED_INVALID_PASSWORD", ipAddress, userAgent)
                return Result.failure(Exception("Invalid credentials"))
            }
            
            // Get user roles
            val userRoles = getUserRoles(user)
            
            // Generate JWT token
            val tokenResult = jwtService.generateToken(
                userId = user.id,
                username = user.username,
                roles = userRoles
            )
            
            if (tokenResult.isFailure) {
                auditLogin(user.id, "LOGIN_FAILED_TOKEN_GENERATION", ipAddress, userAgent)
                return Result.failure(tokenResult.exceptionOrNull() ?: Exception("Token generation failed"))
            }
            
            val token = tokenResult.getOrThrow()
            
            // Update user login timestamp
            val updatedUser = user.recordLogin()
            userRepository.update(updatedUser)
            
            // Audit successful login
            auditLogin(user.id, "LOGIN_SUCCESS", ipAddress, userAgent)
            
            val expiresAt = Clock.System.now().plus(kotlin.time.Duration.parse("24h"))
            
            Result.success(LoginResult(
                user = updatedUser,
                token = token,
                expiresAt = expiresAt
            ))
            
        } catch (e: Exception) {
            Result.failure(Exception("Login failed: ${e.message}", e))
        }
    }
    
    suspend fun register(
        username: String,
        email: String,
        password: String,
        roleNames: Set<String> = setOf("USER")
    ): Result<RegistrationResult> {
        return try {
            // Check if username already exists
            if (userRepository.findByUsername(username) != null) {
                return Result.failure(Exception("Username already exists"))
            }
            
            // Check if email already exists
            if (userRepository.findByEmail(email) != null) {
                return Result.failure(Exception("Email already exists"))
            }
            
            // Get role IDs
            val roleIds = roleNames.mapNotNull { roleName ->
                roleRepository.findByName(roleName)?.id
            }.toSet()
            
            if (roleIds.size != roleNames.size) {
                return Result.failure(Exception("Some roles do not exist"))
            }
            
            // Create user
            val now = Clock.System.now()
            val user = User(
                id = DomainId.generate(),
                username = username,
                email = email,
                roles = roleIds,
                isActive = true,
                createdAt = now,
                updatedAt = now
            )
            
            // Store user and password
            userRepository.save(user)
            storePassword(username, hashPassword(password))
            
            // Audit registration
            auditRepository.save(AuditLog(
                id = DomainId.generate(),
                userId = user.id,
                action = "USER_REGISTERED",
                resource = "USER",
                resourceId = user.id,
                timestamp = now
            ))
            
            Result.success(RegistrationResult(
                user = user,
                message = "User registered successfully"
            ))
            
        } catch (e: Exception) {
            Result.failure(Exception("Registration failed: ${e.message}", e))
        }
    }
    
    suspend fun validateToken(token: String): Result<User> {
        return try {
            val claimsResult = jwtService.verifyToken(token)
            
            if (claimsResult.isFailure) {
                return Result.failure(claimsResult.exceptionOrNull() ?: Exception("Token validation failed"))
            }
            
            val claims = claimsResult.getOrThrow()
            val user = userRepository.findById(claims.userId)
                ?: return Result.failure(Exception("User not found"))
            
            if (!user.isActive) {
                return Result.failure(Exception("User account is deactivated"))
            }
            
            Result.success(user)
            
        } catch (e: Exception) {
            Result.failure(Exception("Token validation failed: ${e.message}", e))
        }
    }
    
    suspend fun logout(userId: DomainId, ipAddress: String? = null, userAgent: String? = null) {
        try {
            auditRepository.save(AuditLog(
                id = DomainId.generate(),
                userId = userId,
                action = "LOGOUT",
                resource = "USER",
                resourceId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                timestamp = Clock.System.now()
            ))
        } catch (e: Exception) {
            // Log error but don't fail logout
            println("Failed to audit logout: ${e.message}")
        }
    }
    
    private fun hashPassword(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }
    
    private fun verifyPassword(password: String, hashedPassword: String): Boolean {
        return try {
            BCrypt.checkpw(password, hashedPassword)
        } catch (e: Exception) {
            false
        }
    }
    
    private suspend fun getUserRoles(user: User): Set<String> {
        return user.roles.mapNotNull { roleId ->
            roleRepository.findById(roleId)?.name
        }.toSet()
    }
    
    private suspend fun auditLogin(
        userId: DomainId, 
        action: String, 
        ipAddress: String?, 
        userAgent: String?
    ) {
        try {
            auditRepository.save(AuditLog(
                id = DomainId.generate(),
                userId = userId,
                action = action,
                resource = "USER",
                resourceId = userId,
                ipAddress = ipAddress,
                userAgent = userAgent,
                timestamp = Clock.System.now()
            ))
        } catch (e: Exception) {
            // Log error but don't fail the main operation
            println("Failed to audit login: ${e.message}")
        }
    }
    
    // Simple in-memory password storage for MVP (replace with proper storage in production)
    private val passwordStore = mutableMapOf<String, String>()
    
    private fun storePassword(username: String, hashedPassword: String) {
        passwordStore[username] = hashedPassword
    }
    
    private fun getStoredPassword(username: String): String {
        return passwordStore[username] ?: ""
    }
}