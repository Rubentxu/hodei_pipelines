package dev.rubentxu.hodei.templatemanagement.infrastructure.api.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class TemplateDto(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val status: String,
    val spec: JsonObject,
    val parentTemplateId: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String,
    val statistics: JsonObject
)

@Serializable
data class CreateTemplateRequest(
    val name: String,
    val description: String,
    val spec: JsonObject,
    val parentTemplateId: String? = null
)

@Serializable
data class UpdateTemplateRequest(
    val description: String? = null,
    val spec: JsonObject? = null
)

@Serializable
data class PublishTemplateRequest(
    val version: String,
    val changelog: String? = null
)

