package dev.rubentxu.hodei.packages.app.plugins

import dev.rubentxu.hodei.packages.application.artifactmanagement.service.ArtifactPublicationService
import dev.rubentxu.hodei.packages.application.identityaccess.service.AuthService
import dev.rubentxu.hodei.packages.app.features.auth.model.ErrorResponse
import dev.rubentxu.hodei.packages.app.features.auth.routes.authRoutes
import dev.rubentxu.hodei.packages.app.features.packages.routes.commonRoutes
import dev.rubentxu.hodei.packages.app.features.packages.routes.mavenRoutes
import dev.rubentxu.hodei.packages.app.features.packages.routes.npmRoutes
import dev.rubentxu.hodei.packages.app.features.packages.routes.pypiRoutes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
// import io.ktor.server.auth.jwt.JWTPrincipal // Uncomment if using JWTs for bearer tokens and have a JWTPrincipal implementation
import io.ktor.server.plugins.requestvalidation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject
// import com.auth0.jwt.JWT // Example for decoding, replace with your JWT library/logic
// import com.auth0.jwt.algorithms.Algorithm // Example
// import java.util.* // Example for JWT Expiry

fun Application.configureRouting() {
    val authService by inject<AuthService>()
    val artifactPublicationService by inject<ArtifactPublicationService>()

    install(Authentication) {
        basic("basicAuth") {
            realm = "Hodei Artifact Repository"
            validate { credentials ->
                // TODO: Replace with actual credential validation using authService.
                // Example: val user = authService.authenticateBasic(credentials.name, credentials.password)
                // if (user != null) UserIdPrincipal(user.id.value) else null
                if (credentials.name == "user" && credentials.password == "password") { // Placeholder
                    UserIdPrincipal(credentials.name)
                } else {
                    null
                }
            }
        }
        bearer("bearerAuth") {
            realm = "Hodei Artifact Repository"
            authenticate { tokenCredential ->
                // TODO: Replace with actual token validation (e.g., JWT or opaque token lookup via authService).
                // Example: val user = authService.authenticateBearer(tokenCredential.token)
                // if (user != null) JWTPrincipal(payload) or UserIdPrincipal(user.id.value) else null

                // Placeholder for a simple token check (NOT JWT validation)
                if (tokenCredential.token.startsWith("npm_validtoken")) { // Placeholder
                     // For a real JWT, you would verify its signature and claims.
                     // For non-JWT bearer tokens, you might just use UserIdPrincipal
                    UserIdPrincipal("npmUserFromToken") // Placeholder
                } else {
                    null
                }
            }
        }
    }

    install(StatusPages) {
        exception<RequestValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(cause.reasons.joinToString()))
        }
        // Handles 401 Unauthorized responses globally
        status(HttpStatusCode.Unauthorized) { call, status ->
            val requestPath = call.request.path()
            // val principal = call.principal<Principal>() // null if auth failed or no auth attempted
            // val failures = call.authentication.allFailures // List of causes for auth failure

            when {
                requestPath.startsWith("/api/maven/") -> {
                    val xmlError = """
                        <Error>
                          <code>401</code>
                          <message>Authentication failed</message>
                        </Error>
                    """.trimIndent()
                    call.respondText(xmlError, ContentType.Application.Xml, status)
                }
                requestPath.startsWith("/api/npm/") || requestPath.startsWith("/api/-/npm/v1/login") -> {
                    call.respond(status, mapOf("error" to "Unauthorized"))
                }
                requestPath.startsWith("/api/pypi/") -> {
                    call.respondText("Invalid credentials", ContentType.Text.Plain, status)
                }
                else -> {
                    // Default unauthorized response for other paths
                    call.respond(status, mapOf("error" to "Unauthorized", "message" to "Access to this resource is denied."))
                }
            }
        }
        // You might want to add handlers for other status codes or specific exceptions.
    }

    install(RequestValidation) {
        validate<dev.rubentxu.hodei.packages.app.features.auth.model.RegisterFirstAdminRequest> { request ->
            if (request.username.length < 3) ValidationResult.Invalid("Username must be at least 3 characters long")
            else if (!request.email.contains("@")) ValidationResult.Invalid("Email must be valid")
            else if (request.password.length < 8) ValidationResult.Invalid("Password must be at least 8 characters long")
            else ValidationResult.Valid
        }
        validate<dev.rubentxu.hodei.packages.app.features.auth.model.LoginRequest> { request ->
            if (request.usernameOrEmail.isEmpty()) ValidationResult.Invalid("Username or email cannot be empty")
            else if (request.password.isEmpty()) ValidationResult.Invalid("Password cannot be empty")
            else ValidationResult.Valid
        }
    }

    routing {
        get("/") {
            call.respondText("Hello Hodei Packages!")
        }
        authRoutes(authService) // Your existing authentication routes (e.g., for web UI login)

        // API routes for Maven, npm, PyPI, and common endpoints
        // Grouped under /api to match OpenAPI spec: servers: - url: .../api
        route("/api") {
            mavenRoutes(artifactPublicationService)
            npmRoutes(artifactPublicationService) // Consider passing authService if npm login logic is complex
            pypiRoutes(artifactPublicationService)
            commonRoutes() // Pass services like searchService if needed
        }
    }
}
