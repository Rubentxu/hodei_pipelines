package dev.rubentxu.hodei.infrastructure.config

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.metrics.micrometer.*
import io.ktor.server.plugins.calllogging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.routing.*
import io.ktor.serialization.kotlinx.json.*
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.json.*
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import java.util.UUID
import dev.rubentxu.hodei.templatemanagement.infrastructure.api.routes.templateRoutes
import dev.rubentxu.hodei.infrastructure.api.routes.jobRoutes
import dev.rubentxu.hodei.infrastructure.api.routes.configureApiRoutes
import dev.rubentxu.hodei.infrastructure.api.controllers.resourcePoolRoutes
import dev.rubentxu.hodei.execution.infrastructure.api.streaming.configureExecutionStreaming
import dev.rubentxu.hodei.infrastructure.grpc.GrpcServerManager
import dev.rubentxu.hodei.execution.application.services.ExecutionEngineService
import dev.rubentxu.hodei.resourcemanagement.application.services.ResourcePoolService
import org.koin.ktor.ext.inject

fun Application.configureModules() {
    configureDependencyInjection()
    configureDockerEnvironment()
    configureBootstrap()
    configureSerialization()
    configureHTTP()
    configureMonitoring()
    configureAuthentication()
    configureRouting()
    configureDatabase()
    configureGrpcServer()
}

fun Application.configureDependencyInjection() {
    install(Koin) {
        slf4jLogger()
        modules(listOf(appModule))
    }
}

fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            isLenient = true
            ignoreUnknownKeys = true
        })
    }
}

fun Application.configureHTTP() {
    install(CORS) {
        anyHost()
        allowCredentials = true
        allowNonSimpleContentTypes = true
        allowHeader(io.ktor.http.HttpHeaders.Authorization)
        allowHeader(io.ktor.http.HttpHeaders.ContentType)
    }
    
    install(Compression) {
        gzip {
            priority = 1.0
        }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }
}

fun Application.configureMonitoring() {
    install(CallLogging) {
        format { call ->
            val status = call.response.status()
            val httpMethod = call.request.local.method.value
            val userAgent = call.request.headers["User-Agent"]
            "Status: $status, HTTP method: $httpMethod, User agent: $userAgent"
        }
        mdc("correlation-id") {
            it.request.headers["X-Correlation-ID"] ?: generateCorrelationId()
        }
    }
    
    install(MicrometerMetrics) {
        registry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    }
}

fun Application.configureAuthentication() {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = "Hodei Pipelines"
            verifier(
                com.auth0.jwt.JWT
                    .require(com.auth0.jwt.algorithms.Algorithm.HMAC256("default-jwt-secret-change-in-production"))
                    .withIssuer("hodei-pipelines")
                    .withAudience("hodei-pipelines-users")
                    .build()
            )
            validate { credential ->
                // Extract user ID and validate
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

fun Application.configureRouting() {
    val executionEngineService by inject<ExecutionEngineService>()
    val resourcePoolService by inject<ResourcePoolService>()
    
    // Configure execution streaming endpoints
    configureExecutionStreaming(executionEngineService)
    
    routing {
        // Configure all API routes with the new centralized approach
        configureApiRoutes()
        
        // Keep legacy resource pool routes for compatibility (if needed)
        // resourcePoolRoutes(resourcePoolService)
    }
}

fun Application.configureDatabase() {
    // Database configuration not needed for MVP with in-memory repository
}

fun Application.configureGrpcServer() {
    val grpcServerManager by inject<GrpcServerManager>()
    
    // Start gRPC server
    grpcServerManager.start()
    
    // Add shutdown hook to properly close gRPC server
    environment.monitor.subscribe(ApplicationStopped) {
        grpcServerManager.shutdown()
    }
}

fun Application.configureDockerEnvironment() {
    val dockerStartup = DockerStartupConfiguration()
    dockerStartup.initialize()
}

fun Application.configureBootstrap() {
    val bootstrap = BootstrapConfiguration()
    bootstrap.initialize()
}

private fun generateCorrelationId(): String = UUID.randomUUID().toString()