package dev.rubentxu.hodei.templatemanagement.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import dev.rubentxu.hodei.templatemanagement.domain.entities.Template
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.concurrent.ConcurrentHashMap

private val logger = KotlinLogging.logger {}

class InMemoryTemplateRepository : TemplateRepository {
    private val templates = ConcurrentHashMap<String, Template>()

    override suspend fun findById(id: DomainId): Either<DomainError, Template?> {
        return try {
            templates[id.value].right()
        } catch (e: Exception) {
            logger.error(e) { "Error finding template by id: $id" }
            ValidationError(message = "Failed to find template: ${e.message}").left()
        }
    }

    override suspend fun findByName(name: String): Either<DomainError, List<Template>> {
        return try {
            templates.values
                .filter { it.name == name }
                .sortedByDescending { it.version.value }
                .right()
        } catch (e: Exception) {
            logger.error(e) { "Error finding templates by name: $name" }
            ValidationError(message = "Failed to find templates: ${e.message}").left()
        }
    }

    override suspend fun findByNameAndVersion(
        name: String, 
        version: String
    ): Either<DomainError, Template?> {
        return try {
            templates.values
                .find { it.name == name && it.version.value == version }
                .right()
        } catch (e: Exception) {
            logger.error(e) { "Error finding template by name and version: $name:$version" }
            ValidationError(message = "Failed to find template: ${e.message}").left()
        }
    }

    override suspend fun findByStatus(status: TemplateStatus): Flow<Template> = flow {
        try {
            templates.values
                .filter { it.status == status }
                .sortedByDescending { it.updatedAt }
                .forEach { emit(it) }
        } catch (e: Exception) {
            logger.error(e) { "Error finding templates by status: $status" }
            throw e
        }
    }

    override suspend fun search(query: String, status: TemplateStatus?): Flow<Template> = flow {
        try {
            templates.values
                .filter { template ->
                    val matchesQuery = template.name.contains(query, ignoreCase = true) ||
                        template.description?.contains(query, ignoreCase = true) == true
                    val matchesStatus = status == null || template.status == status
                    matchesQuery && matchesStatus
                }
                .sortedByDescending { it.updatedAt }
                .forEach { emit(it) }
        } catch (e: Exception) {
            logger.error(e) { "Error searching templates with query: $query" }
            throw e
        }
    }

    override suspend fun save(template: Template): Either<DomainError, Template> {
        return try {
            val existingTemplate = templates.values.find { 
                it.name == template.name && it.version.value == template.version.value 
            }
            
            if (existingTemplate != null) {
                ValidationError(
                    message = "Template with name '${template.name}' and version '${template.version.value}' already exists"
                ).left()
            } else {
                templates[template.id.value] = template
                template.right()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error saving template: ${template.name}" }
            ValidationError(message = "Failed to save template: ${e.message}").left()
        }
    }

    override suspend fun update(template: Template): Either<DomainError, Template> {
        return try {
            if (templates.containsKey(template.id.value)) {
                templates[template.id.value] = template
                template.right()
            } else {
                NotFoundError(
                    message = "Template not found",
                    entityType = "Template",
                    entityId = template.id.value
                ).left()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error updating template: ${template.id}" }
            ValidationError(message = "Failed to update template: ${e.message}").left()
        }
    }

    override suspend fun delete(id: DomainId): Either<DomainError, Unit> {
        return try {
            if (templates.remove(id.value) != null) {
                Unit.right()
            } else {
                NotFoundError(
                    message = "Template not found",
                    entityType = "Template",
                    entityId = id.value
                ).left()
            }
        } catch (e: Exception) {
            logger.error(e) { "Error deleting template: $id" }
            ValidationError(message = "Failed to delete template: ${e.message}").left()
        }
    }

    override suspend fun exists(id: DomainId): Either<DomainError, Boolean> {
        return try {
            templates.containsKey(id.value).right()
        } catch (e: Exception) {
            logger.error(e) { "Error checking template existence: $id" }
            ValidationError(message = "Failed to check template existence: ${e.message}").left()
        }
    }

    override suspend fun existsByNameAndVersion(
        name: String, 
        version: String
    ): Either<DomainError, Boolean> {
        return try {
            templates.values.any { 
                it.name == name && it.version.value == version 
            }.right()
        } catch (e: Exception) {
            logger.error(e) { "Error checking template existence by name and version: $name:$version" }
            ValidationError(message = "Failed to check template existence: ${e.message}").left()
        }
    }

    override suspend fun list(
        page: Int, 
        pageSize: Int
    ): Either<DomainError, Pair<List<Template>, Long>> {
        return try {
            val allTemplates = templates.values.sortedByDescending { it.updatedAt }
            val total = allTemplates.size.toLong()
            val startIndex = (page - 1) * pageSize
            val endIndex = minOf(startIndex + pageSize, allTemplates.size)
            
            val paginatedTemplates = if (startIndex < allTemplates.size) {
                allTemplates.subList(startIndex, endIndex)
            } else {
                emptyList()
            }
            
            (paginatedTemplates to total).right()
        } catch (e: Exception) {
            logger.error(e) { "Error listing templates" }
            ValidationError(message = "Failed to list templates: ${e.message}").left()
        }
    }

    // Utility methods for testing
    fun clear() {
        templates.clear()
    }

    fun size(): Int = templates.size
}