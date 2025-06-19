package dev.rubentxu.hodei.packages.api.features.auth.routes

import org.junit.jupiter.api.AfterEach
import org.koin.core.context.stopKoin

import dev.rubentxu.hodei.packages.app.features.auth.model.*
import dev.rubentxu.hodei.packages.app.features.auth.routes.authRoutes
import dev.rubentxu.hodei.packages.application.auth.*
import dev.rubentxu.hodei.packages.application.identityaccess.dto.AuthenticationResult
import dev.rubentxu.hodei.packages.application.identityaccess.dto.LoginCommand
import dev.rubentxu.hodei.packages.application.identityaccess.dto.RegisterAdminCommand
import dev.rubentxu.hodei.packages.application.identityaccess.service.AuthService
import dev.rubentxu.hodei.packages.application.identityaccess.service.AuthServiceError
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.config.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.*


class AuthRoutesTest {

    @AfterEach
    fun tearDown() {
        stopKoin()
    }
    private val authService = mockk<AuthService>()
    private val jsonConfig = Json {
        prettyPrint = true
        isLenient = true
    }

    @Test
    fun testRegisterFirstAdmin_success_when_no_admin_exists_and_conditions_met() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            // install(Authentication) // Add if needed for specific auth features being tested beyond basic routing
            routing { authRoutes(authService) }
        }

        coEvery { authService.registerFirstAdmin(any<RegisterAdminCommand>()) } returns
                Result.success(
                    AuthenticationResult(
                        username = "adminUser",
                        email = "admin@example.com",
                        token = "admin_token",
                        message = "Admin registered successfully"
                    )
                )

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/register-first-admin") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterFirstAdminRequest(
                    username = "adminUser",
                    email = "admin@example.com",
                    password = "Password123"
                )
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("admin_token", response.body<AuthResponse>().token)
        assertEquals("admin@example.com", response.body<AuthResponse>().email)
        assertEquals("adminUser", response.body<AuthResponse>().username)
    }

    @Test
    fun testRegisterFirstAdmin_failure_when_payload_is_invalid() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            install(RequestValidation) {
                validate<RegisterFirstAdminRequest> { request ->
                    if (request.username.length < 3) {
                        ValidationResult.Invalid("username must be at least 3 characters long")
                    } else {
                        ValidationResult.Valid
                    }
                }
            }
            install(StatusPages) {
                exception<RequestValidationException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons.joinToString()))
                }
            }
            routing { authRoutes(authService) }
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/register-first-admin") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterFirstAdminRequest(
                    username = "ad", // Too short
                    email = "invalid-email", // Invalid format
                    password = "short" // Too short
                )
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.body<ErrorResponse>().error.contains("username must be at least 3 characters long"))
    }

    @Test
    fun testRegisterFirstAdmin_failure_when_admin_already_exists() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            routing { authRoutes(authService) }
        }

        coEvery { authService.registerFirstAdmin(any<RegisterAdminCommand>()) } returns
                Result.failure(AuthServiceError.AdminAlreadyExists)

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/register-first-admin") {
            contentType(ContentType.Application.Json)
            setBody(
                RegisterFirstAdminRequest(
                    username = "existingAdmin",
                    email = "existing@example.com",
                    password = "ExistingPassword123"
                )
            )
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        assertEquals("Admin already exists", response.body<ErrorResponse>().error)
    }

    @Test
    fun testLogin_success_with_correct_credentials() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            routing { authRoutes(authService) }
        }

        coEvery { authService.login(any<LoginCommand>()) } returns
                Result.success(
                    AuthenticationResult(
                        username = "testUser",
                        email = "user@example.com",
                        token = "user_token",
                        message = "Login successful"
                    )
                )

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequest(
                    usernameOrEmail = "testUser",
                    password = "CorrectPassword"
                )
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Login successful", response.body<AuthResponse>().message)
    }

    @Test
    fun testLogin_failure_when_payload_is_invalid() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            install(RequestValidation) {
                validate<LoginRequest> { request ->
                    if (request.usernameOrEmail.isBlank() || request.password.isBlank()) {
                        ValidationResult.Invalid("Username or email and password cannot be empty")
                    } else {
                        ValidationResult.Valid
                    }
                }
            }
            install(StatusPages) {
                exception<RequestValidationException> { call, cause ->
                    call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons.joinToString()))
                }
            }
            routing { authRoutes(authService) }
        }

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest("", "")) // Invalid payload
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("Username or email and password cannot be empty", response.body<ErrorResponse>().error)
    }

    @Test
    fun testLogin_failure_with_incorrect_password() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            routing { authRoutes(authService) }
        }

        coEvery { authService.login(any<LoginCommand>()) } returns
                Result.failure(AuthServiceError.InvalidCredentials)

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequest(
                    usernameOrEmail = "admin@example.com",
                    password = "WrongPassword"
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Invalid credentials", response.body<ErrorResponse>().error)
    }

    @Test
    fun testLogin_failure_when_user_does_not_exist() = testApplication {
        environment {
            config = MapApplicationConfig("ktor.environment" to "test")
        }
        application {
            install(ContentNegotiation) { json(jsonConfig) }
            install(Koin) { modules(module { single { authService } }) }
            routing { authRoutes(authService) }
        }

        coEvery { authService.login(any<LoginCommand>()) } returns
                Result.failure(AuthServiceError.UserNotFound)

        val client = createClient {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(jsonConfig)
            }
        }

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(
                LoginRequest(
                    usernameOrEmail = "nonexistent@example.com",
                    password = "SomePassword123"
                )
            )
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals("Invalid credentials", response.body<ErrorResponse>().error)
    }
}
