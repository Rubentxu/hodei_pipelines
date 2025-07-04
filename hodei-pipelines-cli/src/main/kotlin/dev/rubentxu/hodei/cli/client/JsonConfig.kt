package dev.rubentxu.hodei.cli.client

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

/**
 * Centralized JSON configuration for the CLI.
 * This ensures serializers are properly registered for GraalVM Native Image.
 */
object JsonConfig {
    val json = Json {
        prettyPrint = true
        isLenient = true
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        
        serializersModule = SerializersModule {
            // Explicitly register all our model classes
            contextual(LoginRequest.serializer())
            contextual(LoginResponse.serializer())
            contextual(AuthToken.serializer())
            contextual(User.serializer())
            contextual(UserInfo.serializer())
            contextual(HealthResponse.serializer())
            contextual(ComponentHealth.serializer())
            contextual(VersionResponse.serializer())
            contextual(ResourcePool.serializer())
            contextual(CreatePoolRequest.serializer())
            contextual(PoolCapacity.serializer())
            contextual(PoolUtilization.serializer())
            contextual(Job.serializer())
            contextual(JobSubmissionRequest.serializer())
            contextual(JobSubmissionResponse.serializer())
            contextual(JobLogsResponse.serializer())
            contextual(LogEntry.serializer())
            contextual(Worker.serializer())
            contextual(Template.serializer())
            contextual(CreateTemplateRequest.serializer())
            contextual(CliConfig.serializer())
            contextual(Context.serializer())
        }
    }
}