package dev.rubentxu.hodei.security.application.services

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.security.domain.repositories.UserRepository
import dev.rubentxu.hodei.security.domain.repositories.RoleRepository
import dev.rubentxu.hodei.security.domain.repositories.AuditRepository
import dev.rubentxu.hodei.security.domain.entities.User
import dev.rubentxu.hodei.security.domain.entities.Role
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryUserRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryRoleRepository
import dev.rubentxu.hodei.security.infrastructure.persistence.InMemoryAuditRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("Auth Service Tests")
class AuthServiceTest {
    
    private lateinit var authService: dev.rubentxu.hodei.security.application.services.AuthService
    private lateinit var userRepository: UserRepository
    private lateinit var roleRepository: RoleRepository
    private lateinit var auditRepository: AuditRepository
    private lateinit var jwtService: dev.rubentxu.hodei.security.application.services.JwtService
    
    private val testUserId = DomainId.generate()
    private val testUsername = "testuser"
    private val testEmail = "test@example.com"
    private val testPassword = "password123"
    
    @BeforeEach
    fun setUp() {
        userRepository = InMemoryUserRepository()
        roleRepository = InMemoryRoleRepository()
        auditRepository = InMemoryAuditRepository()
        jwtService = dev.rubentxu.hodei.security.application.services.JwtService(
            dev.rubentxu.hodei.security.application.services.JwtConfig(
                secret = "test-secret-for-auth-service",
                issuer = "test-issuer",
                audience = "test-audience",
                tokenExpirationHours = 1
            )
        )
        authService = dev.rubentxu.hodei.security.application.services.AuthService(userRepository, roleRepository, auditRepository, jwtService)
    }
    
    @Nested
    @DisplayName("User Registration")
    inner class UserRegistration {
        
        @Test
        @DisplayName("Should register new user successfully")
        fun shouldRegisterNewUserSuccessfully() = runTest {
            // When
            val result = authService.register(testUsername, testEmail, testPassword)
            
            // Then
            assertTrue(result.isSuccess)
            val registrationResult = result.getOrThrow()
            assertEquals(testUsername, registrationResult.user.username)
            assertEquals(testEmail, registrationResult.user.email)
            assertTrue(registrationResult.user.isActive)
            assertEquals("User registered successfully", registrationResult.message)
            
            // Verify user was saved to repository
            val savedUser = userRepository.findByUsername(testUsername)
            assertNotNull(savedUser)
            assertEquals(testUsername, savedUser?.username)
        }
        
        @Test
        @DisplayName("Should assign default USER role when no roles specified")
        fun shouldAssignDefaultUserRoleWhenNoRolesSpecified() = runTest {
            // When
            val result = authService.register(testUsername, testEmail, testPassword)
            
            // Then
            assertTrue(result.isSuccess)
            val user = result.getOrThrow().user
            assertEquals(1, user.roles.size)
            
            // Verify the role is USER
            val userRole = roleRepository.findByName("USER")
            assertNotNull(userRole)
            assertTrue(user.roles.contains(userRole!!.id))
        }
        
        @Test
        @DisplayName("Should assign specified roles during registration")
        fun shouldAssignSpecifiedRolesDuringRegistration() = runTest {
            // When
            val result = authService.register(testUsername, testEmail, testPassword, setOf("ADMIN", "USER"))
            
            // Then
            assertTrue(result.isSuccess)
            val user = result.getOrThrow().user
            assertEquals(2, user.roles.size)
            
            // Verify roles are assigned correctly
            val adminRole = roleRepository.findByName("ADMIN")
            val userRole = roleRepository.findByName("USER")
            assertTrue(user.roles.contains(adminRole!!.id))
            assertTrue(user.roles.contains(userRole!!.id))
        }
        
        @Test
        @DisplayName("Should fail to register user with existing username")
        fun shouldFailToRegisterUserWithExistingUsername() = runTest {
            // Given
            authService.register(testUsername, testEmail, testPassword)
            
            // When
            val result = authService.register(testUsername, "another@example.com", "password456")
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Username already exists") == true)
        }
        
        @Test
        @DisplayName("Should fail to register user with existing email")
        fun shouldFailToRegisterUserWithExistingEmail() = runTest {
            // Given
            authService.register(testUsername, testEmail, testPassword)
            
            // When
            val result = authService.register("anotheruser", testEmail, "password456")
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Email already exists") == true)
        }
        
        @Test
        @DisplayName("Should fail to register user with non-existent role")
        fun shouldFailToRegisterUserWithNonExistentRole() = runTest {
            // When
            val result = authService.register(testUsername, testEmail, testPassword, setOf("NONEXISTENT_ROLE"))
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Some roles do not exist") == true)
        }
    }
    
    @Nested
    @DisplayName("User Login")
    inner class UserLogin {
        
        @BeforeEach
        fun setUpUser() = runTest {
            // Create a test user
            authService.register(testUsername, testEmail, testPassword)
        }
        
        @Test
        @DisplayName("Should login user successfully with correct credentials")
        fun shouldLoginUserSuccessfullyWithCorrectCredentials() = runTest {
            // When
            val result = authService.login(testUsername, testPassword)
            
            // Then
            assertTrue(result.isSuccess)
            val loginResult = result.getOrThrow()
            assertEquals(testUsername, loginResult.user.username)
            assertNotNull(loginResult.token)
            assertNotNull(loginResult.expiresAt)
            
            // Verify token is valid
            val tokenValidation = jwtService.verifyToken(loginResult.token)
            assertTrue(tokenValidation.isSuccess)
        }
        
        @Test
        @DisplayName("Should fail login with wrong password")
        fun shouldFailLoginWithWrongPassword() = runTest {
            // When
            val result = authService.login(testUsername, "wrongpassword")
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Invalid credentials") == true)
        }
        
        @Test
        @DisplayName("Should fail login with non-existent username")
        fun shouldFailLoginWithNonExistentUsername() = runTest {
            // When
            val result = authService.login("nonexistentuser", testPassword)
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Invalid credentials") == true)
        }
        
        @Test
        @DisplayName("Should fail login with deactivated user")
        fun shouldFailLoginWithDeactivatedUser() = runTest {
            // Given - deactivate the user
            val user = userRepository.findByUsername(testUsername)!!
            val deactivatedUser = user.deactivate()
            userRepository.update(deactivatedUser)
            
            // When
            val result = authService.login(testUsername, testPassword)
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("Account is deactivated") == true)
        }
        
        @Test
        @DisplayName("Should update last login timestamp on successful login")
        fun shouldUpdateLastLoginTimestampOnSuccessfulLogin() = runTest {
            // Given
            val originalUser = userRepository.findByUsername(testUsername)!!
            assertNull(originalUser.lastLoginAt)
            
            // When
            authService.login(testUsername, testPassword)
            
            // Then
            val updatedUser = userRepository.findByUsername(testUsername)!!
            assertNotNull(updatedUser.lastLoginAt)
            assertTrue(updatedUser.lastLoginAt!! > originalUser.createdAt)
        }
        
        @Test
        @DisplayName("Should include IP address and user agent in audit log")
        fun shouldIncludeIpAddressAndUserAgentInAuditLog() = runTest {
            // Given
            val ipAddress = "192.168.1.100"
            val userAgent = "Mozilla/5.0"
            
            // When
            authService.login(testUsername, testPassword, ipAddress, userAgent)
            
            // Then
            val auditLogs = auditRepository.findAll()
            val loginLog = auditLogs.find { it.action == "LOGIN_SUCCESS" }
            assertNotNull(loginLog)
            assertEquals(ipAddress, loginLog?.ipAddress)
            assertEquals(userAgent, loginLog?.userAgent)
        }
    }
    
    @Nested
    @DisplayName("Token Validation")
    inner class TokenValidation {
        
        private lateinit var validToken: String
        private lateinit var testUser: User
        
        @BeforeEach
        fun setUpUserAndToken() = runTest {
            // Create user and login to get valid token
            authService.register(testUsername, testEmail, testPassword)
            val loginResult = authService.login(testUsername, testPassword).getOrThrow()
            validToken = loginResult.token
            testUser = loginResult.user
        }
        
        @Test
        @DisplayName("Should validate valid token successfully")
        fun shouldValidateValidTokenSuccessfully() = runTest {
            // When
            val result = authService.validateToken(validToken)
            
            // Then
            assertTrue(result.isSuccess)
            val user = result.getOrThrow()
            assertEquals(testUsername, user.username)
            assertEquals(testEmail, user.email)
        }
        
        @Test
        @DisplayName("Should fail to validate invalid token")
        fun shouldFailToValidateInvalidToken() = runTest {
            // When
            val result = authService.validateToken("invalid.token.here")
            
            // Then
            assertTrue(result.isFailure)
        }
        
        @Test
        @DisplayName("Should fail to validate token for deactivated user")
        fun shouldFailToValidateTokenForDeactivatedUser() = runTest {
            // Given - deactivate the user
            val deactivatedUser = testUser.deactivate()
            userRepository.update(deactivatedUser)
            
            // When
            val result = authService.validateToken(validToken)
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("User account is deactivated") == true)
        }
        
        @Test
        @DisplayName("Should fail to validate token for deleted user")
        fun shouldFailToValidateTokenForDeletedUser() = runTest {
            // Given - delete the user
            userRepository.delete(testUser.id)
            
            // When
            val result = authService.validateToken(validToken)
            
            // Then
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("User not found") == true)
        }
    }
    
    @Nested
    @DisplayName("User Logout")
    inner class UserLogout {
        
        @Test
        @DisplayName("Should logout user and create audit log")
        fun shouldLogoutUserAndCreateAuditLog() = runTest {
            // Given
            val userId = DomainId.generate()
            val ipAddress = "192.168.1.100"
            val userAgent = "Mozilla/5.0"
            
            // When
            authService.logout(userId, ipAddress, userAgent)
            
            // Then
            val auditLogs = auditRepository.findByUserId(userId)
            val logoutLog = auditLogs.find { it.action == "LOGOUT" }
            assertNotNull(logoutLog)
            assertEquals(userId, logoutLog?.userId)
            assertEquals(ipAddress, logoutLog?.ipAddress)
            assertEquals(userAgent, logoutLog?.userAgent)
        }
        
        // Note: logout method is designed to not fail even if audit logging fails
    }
}