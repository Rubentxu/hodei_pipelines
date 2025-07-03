package dev.rubentxu.hodei.security.application.services

import dev.rubentxu.hodei.security.application.services.JwtService
import dev.rubentxu.hodei.security.application.services.JwtConfig
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested

@DisplayName("JWT Service Tests")
class JwtServiceTest {
    
    private lateinit var jwtService: JwtService
    private val testUserId = DomainId.generate()
    private val testUsername = "testuser"
    private val testRoles = setOf("USER", "ADMIN")
    
    @BeforeEach
    fun setUp() {
        jwtService = JwtService(
            JwtConfig(
                secret = "test-secret-key-for-jwt-testing",
                issuer = "test-issuer",
                audience = "test-audience",
                tokenExpirationHours = 1
            )
        )
    }
    
    @Nested
    @DisplayName("Token Generation")
    inner class TokenGeneration {
        
        @Test
        @DisplayName("Should generate valid JWT token successfully")
        fun shouldGenerateValidTokenSuccessfully() {
            // When
            val result = jwtService.generateToken(testUserId, testUsername, testRoles)
            
            // Then
            assertTrue(result.isSuccess)
            val token = result.getOrThrow()
            assertNotNull(token)
            assertTrue(token.isNotBlank())
            // JWT tokens should have 3 parts separated by dots
            assertEquals(3, token.split(".").size)
        }
        
        @Test
        @DisplayName("Should generate token with correct claims")
        fun shouldGenerateTokenWithCorrectClaims() {
            // When
            val tokenResult = jwtService.generateToken(testUserId, testUsername, testRoles)
            val token = tokenResult.getOrThrow()
            
            val verifyResult = jwtService.verifyToken(token)
            
            // Then
            assertTrue(verifyResult.isSuccess)
            val claims = verifyResult.getOrThrow()
            assertEquals(testUserId, claims.userId)
            assertEquals(testUsername, claims.username)
            assertEquals(testRoles, claims.roles)
        }
        
        @Test
        @DisplayName("Should generate different tokens for different users")
        fun shouldGenerateDifferentTokensForDifferentUsers() {
            // Given
            val userId1 = DomainId.generate()
            val userId2 = DomainId.generate()
            
            // When
            val token1 = jwtService.generateToken(userId1, "user1", setOf("USER")).getOrThrow()
            val token2 = jwtService.generateToken(userId2, "user2", setOf("USER")).getOrThrow()
            
            // Then
            assertNotEquals(token1, token2)
        }
    }
    
    @Nested
    @DisplayName("Token Verification")
    inner class TokenVerification {
        
        @Test
        @DisplayName("Should verify valid token successfully")
        fun shouldVerifyValidTokenSuccessfully() {
            // Given
            val token = jwtService.generateToken(testUserId, testUsername, testRoles).getOrThrow()
            
            // When
            val result = jwtService.verifyToken(token)
            
            // Then
            assertTrue(result.isSuccess)
            val claims = result.getOrThrow()
            assertEquals(testUserId, claims.userId)
            assertEquals(testUsername, claims.username)
            assertEquals(testRoles, claims.roles)
        }
        
        @Test
        @DisplayName("Should fail to verify invalid token")
        fun shouldFailToVerifyInvalidToken() {
            // Given
            val invalidToken = "invalid.jwt.token"
            
            // When
            val result = jwtService.verifyToken(invalidToken)
            
            // Then
            assertTrue(result.isFailure)
            assertNotNull(result.exceptionOrNull())
        }
        
        @Test
        @DisplayName("Should fail to verify token with wrong signature")
        fun shouldFailToVerifyTokenWithWrongSignature() {
            // Given
            val otherJwtService = JwtService(
                JwtConfig(secret = "different-secret")
            )
            val tokenFromOtherService = otherJwtService.generateToken(testUserId, testUsername, testRoles).getOrThrow()
            
            // When
            val result = jwtService.verifyToken(tokenFromOtherService)
            
            // Then
            assertTrue(result.isFailure)
        }
        
        @Test
        @DisplayName("Should fail to verify empty token")
        fun shouldFailToVerifyEmptyToken() {
            // When
            val result = jwtService.verifyToken("")
            
            // Then
            assertTrue(result.isFailure)
        }
    }
    
    @Nested
    @DisplayName("Token Expiration")
    inner class TokenExpiration {
        
        @Test
        @DisplayName("Should detect non-expired token correctly")
        fun shouldDetectNonExpiredTokenCorrectly() {
            // Given
            val token = jwtService.generateToken(testUserId, testUsername, testRoles).getOrThrow()
            
            // When
            val isExpired = jwtService.isTokenExpired(token)
            
            // Then
            assertFalse(isExpired)
        }
        
        @Test
        @DisplayName("Should handle malformed token when checking expiration")
        fun shouldHandleMalformedTokenWhenCheckingExpiration() {
            // Given
            val malformedToken = "malformed.token"
            
            // When
            val isExpired = jwtService.isTokenExpired(malformedToken)
            
            // Then
            assertTrue(isExpired) // Should consider malformed tokens as expired
        }
    }
    
    @Nested
    @DisplayName("User ID Extraction")
    inner class UserIdExtraction {
        
        @Test
        @DisplayName("Should extract user ID from valid token")
        fun shouldExtractUserIdFromValidToken() {
            // Given
            val token = jwtService.generateToken(testUserId, testUsername, testRoles).getOrThrow()
            
            // When
            val result = jwtService.extractUserId(token)
            
            // Then
            assertTrue(result.isSuccess)
            assertEquals(testUserId, result.getOrThrow())
        }
        
        @Test
        @DisplayName("Should fail to extract user ID from invalid token")
        fun shouldFailToExtractUserIdFromInvalidToken() {
            // Given
            val invalidToken = "invalid.token"
            
            // When
            val result = jwtService.extractUserId(invalidToken)
            
            // Then
            assertTrue(result.isFailure)
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    inner class EdgeCases {
        
        @Test
        @DisplayName("Should handle empty username")
        fun shouldHandleEmptyUsername() {
            // When
            val result = jwtService.generateToken(testUserId, "", testRoles)
            
            // Then
            assertTrue(result.isSuccess) // Should still generate token
            val token = result.getOrThrow()
            val claims = jwtService.verifyToken(token).getOrThrow()
            assertEquals("", claims.username)
        }
        
        @Test
        @DisplayName("Should handle empty roles")
        fun shouldHandleEmptyRoles() {
            // When
            val result = jwtService.generateToken(testUserId, testUsername, emptySet())
            
            // Then
            assertTrue(result.isSuccess)
            val token = result.getOrThrow()
            val claims = jwtService.verifyToken(token).getOrThrow()
            assertEquals(emptySet<String>(), claims.roles)
        }
        
        @Test
        @DisplayName("Should handle null token gracefully")
        fun shouldHandleNullTokenGracefully() {
            // When/Then - these should not throw exceptions
            assertTrue(jwtService.isTokenExpired(""))
            assertTrue(jwtService.extractUserId("").isFailure)
            assertTrue(jwtService.verifyToken("").isFailure)
        }
    }
}