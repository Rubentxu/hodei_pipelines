package dev.rubentxu.hodei.security.infrastructure.api

import dev.rubentxu.hodei.security.application.services.AuthService
import dev.rubentxu.hodei.security.infrastructure.api.AuthDto
import dev.rubentxu.hodei.infrastructure.api.dto.ErrorResponseDto
import io.ktor.http.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.Clock

class AuthController(private val authService: AuthService) {
    
    fun Route.authRoutes() {
        route("/auth") {
            // Public routes (no authentication required)
            post("/login") {
                val request = call.receive<AuthDto.LoginRequestDto>()
                
                val ipAddress = call.request.headers["X-Forwarded-For"] 
                    ?: call.request.local.remoteHost
                val userAgent = call.request.headers["User-Agent"]
                
                authService.login(
                    username = request.username,
                    password = request.password,
                    ipAddress = ipAddress,
                    userAgent = userAgent
                ).fold(
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponseDto(
                                error = "AUTHENTICATION_FAILED",
                                message = error.message ?: "Authentication failed",
                                timestamp = Clock.System.now()
                            )
                        )
                    },
                    onSuccess = { loginResult ->
                        call.respond(
                            HttpStatusCode.OK,
                            AuthDto.LoginResponseDto(
                                token = loginResult.token,
                                user = AuthDto.UserDto(
                                    id = loginResult.user.id.value,
                                    username = loginResult.user.username,
                                    email = loginResult.user.email,
                                    roles = loginResult.user.roles.map { it.value },
                                    isActive = loginResult.user.isActive,
                                    lastLoginAt = loginResult.user.lastLoginAt
                                ),
                                expiresAt = loginResult.expiresAt
                            )
                        )
                    }
                )
            }
            
            post("/register") {
                val request = call.receive<AuthDto.RegisterRequestDto>()
                
                authService.register(
                    username = request.username,
                    email = request.email,
                    password = request.password,
                    roleNames = request.roles?.toSet() ?: setOf("USER")
                ).fold(
                    onFailure = { error ->
                        call.respond(
                            HttpStatusCode.BadRequest,
                            ErrorResponseDto(
                                error = "REGISTRATION_FAILED",
                                message = error.message ?: "Registration failed",
                                timestamp = Clock.System.now()
                            )
                        )
                    },
                    onSuccess = { registrationResult ->
                        call.respond(
                            HttpStatusCode.Created,
                            AuthDto.RegisterResponseDto(
                                user = AuthDto.UserDto(
                                    id = registrationResult.user.id.value,
                                    username = registrationResult.user.username,
                                    email = registrationResult.user.email,
                                    roles = registrationResult.user.roles.map { it.value },
                                    isActive = registrationResult.user.isActive,
                                    lastLoginAt = registrationResult.user.lastLoginAt
                                ),
                                message = registrationResult.message
                            )
                        )
                    }
                )
            }
            
            // Protected routes (authentication required)
            authenticate("auth-jwt") {
                post("/logout") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject
                    
                    if (userId != null) {
                        val ipAddress = call.request.headers["X-Forwarded-For"] 
                            ?: call.request.local.remoteHost
                        val userAgent = call.request.headers["User-Agent"]
                        
                        authService.logout(
                            userId = dev.rubentxu.hodei.shared.domain.primitives.DomainId(userId),
                            ipAddress = ipAddress,
                            userAgent = userAgent
                        )
                    }
                    
                    call.respond(
                        HttpStatusCode.OK,
                        AuthDto.LogoutResponseDto(message = "Logged out successfully")
                    )
                }
                
                get("/profile") {
                    val principal = call.principal<JWTPrincipal>()
                    val userId = principal?.payload?.subject
                    
                    if (userId == null) {
                        call.respond(
                            HttpStatusCode.Unauthorized,
                            ErrorResponseDto(
                                error = "INVALID_TOKEN",
                                message = "Invalid or missing token",
                                timestamp = Clock.System.now()
                            )
                        )
                        return@get
                    }
                    
                    authService.validateToken(
                        call.request.authorization()?.removePrefix("Bearer ") ?: ""
                    ).fold(
                        onFailure = { error ->
                            call.respond(
                                HttpStatusCode.Unauthorized,
                                ErrorResponseDto(
                                    error = "TOKEN_VALIDATION_FAILED",
                                    message = error.message ?: "Token validation failed",
                                    timestamp = Clock.System.now()
                                )
                            )
                        },
                        onSuccess = { user ->
                            call.respond(
                                HttpStatusCode.OK,
                                AuthDto.UserDto(
                                    id = user.id.value,
                                    username = user.username,
                                    email = user.email,
                                    roles = user.roles.map { it.value },
                                    isActive = user.isActive,
                                    lastLoginAt = user.lastLoginAt
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}