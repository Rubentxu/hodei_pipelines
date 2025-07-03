package dev.rubentxu.hodei.infrastructure.config

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import dev.rubentxu.hodei.infrastructure.api.routes.configureApiRoutes
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm

/**
 * Test application configuration that includes security modules for integration tests
 */
fun Application.configureTestModules() {
    // Configure Koin with both regular modules and test security modules
    install(Koin) {
        slf4jLogger()
        modules(appModule, testSecurityModule)
    }
    
    configureSerialization()
    configureHTTP()
    configureMonitoring()
    configureTestAuthentication()
    
    // Configure only API routes, skip gRPC and other complex services
    routing {
        configureApiRoutes()
    }
}

/**
 * Test authentication configuration - simplified version for testing
 */
fun Application.configureTestAuthentication() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Hodei Pipelines Test"
            verifier(
                JWT.require(Algorithm.HMAC256("default-jwt-secret-change-in-production"))
                    .withIssuer("hodei-pipelines")
                    .withAudience("hodei-pipelines-users")
                    .build()
            )
            validate { credential ->
                // Simple validation for tests
                val userId = credential.payload.subject
                if (userId != null) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}

/**
 * Simplified test configuration without security for basic controllers
 */
fun Application.configureTestModulesBasic() {
    // Configure Koin with only basic modules (no gRPC)
    install(Koin) {
        slf4jLogger()
        modules(dev.rubentxu.hodei.infrastructure.config.testBasicModule)
    }
    
    configureSerialization()
    configureHTTP()
    configureMonitoring()
    
    // Configure only API routes, skip gRPC and other complex services
    routing {
        configureApiRoutes()
    }
}