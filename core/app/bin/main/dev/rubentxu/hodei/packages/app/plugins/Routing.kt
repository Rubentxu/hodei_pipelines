package dev.rubentxu.hodei.packages.app.plugins

import dev.rubentxu.hodei.packages.application.auth.AuthService
import dev.rubentxu.hodei.packages.application.auth.AuthServiceError
import dev.rubentxu.hodei.packages.application.auth.LoginCommand
import dev.rubentxu.hodei.packages.application.auth.RegisterAdminCommand
import dev.rubentxu.hodei.packages.application.shared.Result
import dev.rubentxu.hodei.packages.app.features.auth.routes.authRoutes
import dev.rubentxu.hodei.packages.app.features.auth.routes.loginRouting
import dev.rubentxu.hodei.packages.app.features.auth.routes.registerAdminRouting
import io.ktor.server.application.*
import dev.rubentxu.hodei.packages.app.features.auth.model.ErrorResponse
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val authService by inject<AuthService>()

    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons.joinToString()))
        }
    }

    install(RequestValidation) {
        validate<dev.rubentxu.hodei.packages.app.features.auth.model.RegisterFirstAdminRequest> { request ->
            if (request.username.length < 3) {
                ValidationResult.Invalid("Username must be at least 3 characters long")
            }
            if (!request.email.contains("@")) {
                ValidationResult.Invalid("Email must be valid")
            }
            if (request.password.length < 8) {
                ValidationResult.Invalid("Password must be at least 8 characters long")
            }
            ValidationResult.Valid
        }
        validate<dev.rubentxu.hodei.packages.app.features.auth.model.LoginRequest> { request ->
            if (request.usernameOrEmail.isEmpty()) {
                ValidationResult.Invalid("Username or email cannot be empty")
            }
            if (request.password.isEmpty()) {
                ValidationResult.Invalid("Password cannot be empty")
            }
            ValidationResult.Valid
        }
    }

    routing {
        get("/") {
            call.respondText("Hello Hodei Packages!")
        }
        authRoutes(authService)
    }
}
