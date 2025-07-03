package dev.rubentxu.hodei.security.infrastructure

import dev.rubentxu.hodei.infrastructure.config.configureTestModules
import dev.rubentxu.hodei.security.infrastructure.api.AuthDto
import dev.rubentxu.hodei.infrastructure.api.dto.ErrorResponseDto
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json

class AuthControllerIntegrationTest : BehaviorSpec({
    
    given("a running application with Auth API") {
        
        `when`("registering a new user") {
            then("should register successfully") {
                testApplication {
                    application {
                        configureTestModules()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                            })
                        }
                    }
                    
                    val response = client.post("/v1/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "email" to "test@example.com",
                            "password" to "password123"
                        ))
                    }
                    
                    response.status shouldBe HttpStatusCode.Created
                    val responseBody = response.body<AuthDto.RegisterResponseDto>()
                    responseBody.user.username shouldBe "testuser"
                    responseBody.user.email shouldBe "test@example.com"
                }
            }
            
            then("should fail with duplicate username") {
                testApplication {
                    application {
                        configureTestModules()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                            })
                        }
                    }
                    
                    // Register first user
                    client.post("/v1/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "email" to "test@example.com",
                            "password" to "password123"
                        ))
                    }
                    
                    // Try to register second user with same username
                    val response = client.post("/v1/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "email" to "different@example.com",
                            "password" to "password456"
                        ))
                    }
                    
                    response.status shouldBe HttpStatusCode.BadRequest
                    val responseBody = response.body<ErrorResponseDto>()
                    responseBody.error shouldBe "REGISTRATION_FAILED"
                }
            }
        }
        
        `when`("logging in") {
            then("should login successfully with correct credentials") {
                testApplication {
                    application {
                        configureTestModules()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                            })
                        }
                    }
                    
                    // Register user first
                    client.post("/v1/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "email" to "test@example.com",
                            "password" to "password123"
                        ))
                    }
                    
                    // Login
                    val response = client.post("/v1/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "password" to "password123"
                        ))
                    }
                    
                    response.status shouldBe HttpStatusCode.OK
                    val responseBody = response.body<AuthDto.LoginResponseDto>()
                    responseBody.token.isNotBlank() shouldBe true
                    responseBody.user.username shouldBe "testuser"
                    responseBody.user.email shouldBe "test@example.com"
                    responseBody.expiresAt shouldNotBe null
                    
                    val token = responseBody.token
                    token.split(".").size shouldBe 3 // Valid JWT format
                }
            }
            
            then("should fail with wrong password") {
                testApplication {
                    application {
                        configureTestModules()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                            })
                        }
                    }
                    
                    // Register user first
                    client.post("/v1/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "email" to "test@example.com",
                            "password" to "password123"
                        ))
                    }
                    
                    // Login with wrong password
                    val response = client.post("/v1/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "password" to "wrongpassword"
                        ))
                    }
                    
                    response.status shouldBe HttpStatusCode.Unauthorized
                    val responseBody = response.body<ErrorResponseDto>()
                    responseBody.error shouldBe "AUTHENTICATION_FAILED"
                }
            }
        }
        
        `when`("accessing protected endpoints") {
            then("should access profile with valid token") {
                testApplication {
                    application {
                        configureTestModules()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                            })
                        }
                    }
                    
                    // Register and login user to get token
                    client.post("/v1/auth/register") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "email" to "test@example.com",
                            "password" to "password123"
                        ))
                    }
                    
                    val loginResponse = client.post("/v1/auth/login") {
                        contentType(ContentType.Application.Json)
                        setBody(mapOf(
                            "username" to "testuser",
                            "password" to "password123"
                        ))
                    }
                    
                    val loginBody = loginResponse.body<AuthDto.LoginResponseDto>()
                    val token = loginBody.token
                    
                    // Access profile
                    val response = client.get("/v1/auth/profile") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                    }
                    
                    response.status shouldBe HttpStatusCode.OK
                    val responseBody = response.body<AuthDto.UserDto>()
                    responseBody.username shouldBe "testuser"
                    responseBody.email shouldBe "test@example.com"
                }
            }
            
            then("should deny access without token") {
                testApplication {
                    application {
                        configureTestModules()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json(Json {
                                ignoreUnknownKeys = true
                            })
                        }
                    }
                    
                    val response = client.get("/v1/auth/profile")
                    response.status shouldBe HttpStatusCode.Unauthorized
                }
            }
        }
    }
})