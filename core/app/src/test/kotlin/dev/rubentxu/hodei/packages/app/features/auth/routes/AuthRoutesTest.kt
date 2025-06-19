package dev.rubentxu.hodei.packages.app.features.auth.routes

import dev.rubentxu.hodei.packages.app.features.auth.model.AuthResponse
import dev.rubentxu.hodei.packages.app.features.auth.model.LoginRequest
import dev.rubentxu.hodei.packages.app.features.auth.model.RegisterFirstAdminRequest
import dev.rubentxu.hodei.packages.application.identityaccess.dto.AuthenticationResult
import dev.rubentxu.hodei.packages.application.identityaccess.dto.LoginCommand
import dev.rubentxu.hodei.packages.application.identityaccess.dto.RegisterAdminCommand
import dev.rubentxu.hodei.packages.application.identityaccess.service.AuthService
import dev.rubentxu.hodei.packages.application.identityaccess.service.AuthServiceError
import dev.rubentxu.hodei.packages.application.shared.Result
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals

class AuthRoutesTest {

    private val mockAuthService: AuthService = mock()

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `should register first admin successfully`() = testApplication {
        application {
            authRoutes(mockAuthService)
        }
        val request = RegisterFirstAdminRequest("testuser", "test@example.com", "SecureP@ss123")
        val expectedAuthResult = AuthenticationResult("User registered successfully", "some-token", "test@example.com", "testuser")

        whenever(mockAuthService.registerFirstAdmin(any<RegisterAdminCommand>()))
            .thenReturn(Result.Success(expectedAuthResult))

        val response = client.post("/api/auth/register-first-admin") {
            setBody(request)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        val authResponse = response.body<AuthResponse>()
        assertEquals(expectedAuthResult.message, authResponse.message)
        assertEquals(expectedAuthResult.token, authResponse.token)
        assertEquals(expectedAuthResult.email, authResponse.email)
        assertEquals(expectedAuthResult.username, authResponse.username)

        verify(mockAuthService).registerFirstAdmin(RegisterAdminCommand("testuser", "test@example.com", "SecureP@ss123"))
    }

    @Test
    fun `should return conflict when admin already exists during registration`() = testApplication {
        application {
            authRoutes(mockAuthService)
        }
        val request = RegisterFirstAdminRequest("existingadmin", "existing@example.com", "Password123")

        whenever(mockAuthService.registerFirstAdmin(any<RegisterAdminCommand>()))
            .thenReturn(Result.Failure(AuthServiceError.AdminAlreadyExists))

        val response = client.post("/api/auth/register-first-admin") {
            setBody(request)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Conflict, response.status)
        val errorResponse = response.body<Map<String, String>>()
        assertEquals("Admin already exists", errorResponse["message"])

        verify(mockAuthService).registerFirstAdmin(RegisterAdminCommand("existingadmin", "existing@example.com", "Password123"))
    }

    @Test
    fun `should login successfully with valid credentials`() = testApplication {
        application {
            authRoutes(mockAuthService)
        }
        val request = LoginRequest("user@example.com", "SecureP@ss123")
        val expectedAuthResult = AuthenticationResult("Login successful", "another-token", "user@example.com", "testuser")

        whenever(mockAuthService.login(any<LoginCommand>()))
            .thenReturn(Result.Success(expectedAuthResult))

        val response = client.post("/api/auth/login") {
            setBody(request)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val authResponse = response.body<AuthResponse>()
        assertEquals("Login successful", authResponse.message)
        // Note: The current AuthResponse in login only returns message, not token/email/username
        // This might need adjustment based on desired API behavior.

        verify(mockAuthService).login(LoginCommand("user@example.com", "SecureP@ss123"))
    }

    @Test
    fun `should return unauthorized for invalid login credentials`() = testApplication {
        application {
            authRoutes(mockAuthService)
        }
        val request = LoginRequest("user@example.com", "WrongPassword")

        whenever(mockAuthService.login(any<LoginCommand>()))
            .thenReturn(Result.Failure(AuthServiceError.InvalidCredentials))

        val response = client.post("/api/auth/login") {
            setBody(request)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
        val errorResponse = response.body<Map<String, String>>()
        assertEquals("Invalid credentials", errorResponse["message"])

        verify(mockAuthService).login(LoginCommand("user@example.com", "WrongPassword"))
    }

    @Test
    fun `should return bad request for validation failed during login`() = testApplication {
        application {
            authRoutes(mockAuthService)
        }
        val request = LoginRequest("invalid-email", "password")

        whenever(mockAuthService.login(any<LoginCommand>()))
            .thenReturn(Result.Failure(AuthServiceError.ValidationFailed("Invalid email format")))

        val response = client.post("/api/auth/login") {
            setBody(request)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val errorResponse = response.body<Map<String, String>>()
        assertEquals("Invalid email format", errorResponse["message"])

        verify(mockAuthService).login(LoginCommand("invalid-email", "password"))
    }

    @Test
    fun `should return internal server error for unexpected error during login`() = testApplication {
        application {
            authRoutes(mockAuthService)
        }
        val request = LoginRequest("user@example.com", "password")

        whenever(mockAuthService.login(any<LoginCommand>()))
            .thenReturn(Result.Failure(AuthServiceError.UnexpectedError("Database connection lost")))

        val response = client.post("/api/auth/login") {
            setBody(request)
            contentType(ContentType.Application.Json)
        }

        assertEquals(HttpStatusCode.InternalServerError, response.status)
        val errorResponse = response.body<Map<String, String>>()
        assertEquals("An unexpected error occurred", errorResponse["message"])

        verify(mockAuthService).login(LoginCommand("user@example.com", "password"))
    }

    private fun ApplicationTestBuilder.application(configure: suspend Application.() -> Unit) {
        this.application {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            routing {
                configure()
            }
        }
    }
}