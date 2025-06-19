package dev.rubentxu.hodei.packages.app.features.auth.routes

import dev.rubentxu.hodei.packages.app.features.auth.model.AuthResponse
import dev.rubentxu.hodei.packages.app.features.auth.model.ErrorResponse
import dev.rubentxu.hodei.packages.app.features.auth.model.RegisterFirstAdminRequest
import dev.rubentxu.hodei.packages.application.auth.AuthService
import dev.rubentxu.hodei.packages.application.auth.AuthServiceError
import dev.rubentxu.hodei.packages.application.auth.RegisterAdminCommand
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRoutes(authService: AuthService) {
    route("/api/auth") {
        registerAdminRouting(authService)
        loginRouting(authService)
    }
}

fun Route.registerAdminRouting(authService: AuthService) {
    post("/register-first-admin") {
        val request = call.receive<RegisterFirstAdminRequest>()
        when (val result = authService.registerFirstAdmin(request.toCommand())) {
            is dev.rubentxu.hodei.packages.application.shared.Result.Success -> {
                val authResult = result.value
                call.respond(
                    HttpStatusCode.Created,
                    AuthResponse(
                        message = authResult.message,
                        token = authResult.token,
                        email = authResult.email,
                        username = authResult.username
                    )
                )
            }
            is dev.rubentxu.hodei.packages.application.shared.Result.Failure -> {
                when (result.error) {
                    AuthServiceError.AdminAlreadyExists -> {
                        call.respond(HttpStatusCode.Conflict, ErrorResponse("Admin already exists"))
                    }
                    else -> {
                        call.respond(HttpStatusCode.BadRequest, ErrorResponse("Invalid request"))
                    }
                }
            }
        }
    }
}

fun Route.loginRouting(authService: AuthService) {
    post("/login") {
        val request = call.receive<dev.rubentxu.hodei.packages.app.features.auth.model.LoginRequest>()
        when (val result = authService.login(request.toCommand())) {
            is dev.rubentxu.hodei.packages.application.shared.Result.Success<dev.rubentxu.hodei.packages.application.auth.AuthenticationResult> -> {
                call.respond(HttpStatusCode.OK, AuthResponse("Login successful"))
            }
            is dev.rubentxu.hodei.packages.application.shared.Result.Failure<dev.rubentxu.hodei.packages.application.auth.AuthServiceError> -> {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Invalid credentials"))
            }
        }
    }
}

fun RegisterFirstAdminRequest.toCommand(): RegisterAdminCommand {
    return RegisterAdminCommand(username, email, password)
}

fun dev.rubentxu.hodei.packages.app.features.auth.model.LoginRequest.toCommand(): dev.rubentxu.hodei.packages.application.auth.LoginCommand {
    return dev.rubentxu.hodei.packages.application.auth.LoginCommand(usernameOrEmail, password)
}