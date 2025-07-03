package dev.rubentxu.hodei.security.application.services

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.toJavaInstant
import java.util.*

data class JwtClaims(
    val userId: DomainId,
    val username: String,
    val roles: Set<String>,
    val issuedAt: Instant,
    val expiresAt: Instant
)

data class JwtConfig(
    val secret: String = "default-jwt-secret-change-in-production",
    val issuer: String = "hodei-pipelines",
    val audience: String = "hodei-pipelines-users",
    val tokenExpirationHours: Long = 24
)

class JwtService(private val config: JwtConfig = JwtConfig()) {
    private val algorithm = Algorithm.HMAC256(config.secret)
    
    fun generateToken(
        userId: DomainId,
        username: String,
        roles: Set<String>
    ): Result<String> {
        return try {
            val now = Clock.System.now()
            val expiresAt = now.plus(kotlin.time.Duration.parse("${config.tokenExpirationHours}h"))
            
            val token = JWT.create()
                .withIssuer(config.issuer)
                .withAudience(config.audience)
                .withSubject(userId.value)
                .withClaim("username", username)
                .withClaim("roles", roles.toList())
                .withIssuedAt(Date.from(now.toJavaInstant()))
                .withExpiresAt(Date.from(expiresAt.toJavaInstant()))
                .sign(algorithm)
                
            Result.success(token)
        } catch (e: Exception) {
            Result.failure(Exception("Failed to generate JWT token: ${e.message}", e))
        }
    }
    
    fun verifyToken(token: String): Result<JwtClaims> {
        return try {
            val verifier = JWT.require(algorithm)
                .withIssuer(config.issuer)
                .withAudience(config.audience)
                .build()
                
            val decodedJWT = verifier.verify(token)
            
            val userId = DomainId(decodedJWT.subject)
            val username = decodedJWT.getClaim("username").asString()
            val roles = decodedJWT.getClaim("roles").asList(String::class.java).toSet()
            val issuedAt = kotlinx.datetime.Instant.fromEpochMilliseconds(decodedJWT.issuedAt.time)
            val expiresAt = kotlinx.datetime.Instant.fromEpochMilliseconds(decodedJWT.expiresAt.time)
            
            val claims = JwtClaims(
                userId = userId,
                username = username,
                roles = roles,
                issuedAt = issuedAt,
                expiresAt = expiresAt
            )
            
            Result.success(claims)
        } catch (e: JWTVerificationException) {
            Result.failure(Exception("Invalid JWT token: ${e.message}", e))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to verify JWT token: ${e.message}", e))
        }
    }
    
    fun isTokenExpired(token: String): Boolean {
        return try {
            val decodedJWT = JWT.decode(token)
            decodedJWT.expiresAt.before(Date())
        } catch (e: Exception) {
            true // Consider invalid tokens as expired
        }
    }
    
    fun extractUserId(token: String): Result<DomainId> {
        return try {
            val decodedJWT = JWT.decode(token)
            Result.success(DomainId(decodedJWT.subject))
        } catch (e: Exception) {
            Result.failure(Exception("Failed to extract user ID from token: ${e.message}", e))
        }
    }
}