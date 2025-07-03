package dev.rubentxu.hodei.templatemanagement.application.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.shared.domain.errors.NotFoundError
import dev.rubentxu.hodei.shared.domain.errors.ValidationError
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import dev.rubentxu.hodei.templatemanagement.domain.entities.Template
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

class TemplateService(
    private val templateRepository: TemplateRepository
) {
    
    suspend fun createTemplate(
        name: String,
        version: String,
        spec: JsonObject,
        description: String,
        parentTemplateId: DomainId? = null,
        createdBy: String
    ): Either<DomainError, Template> {
        logger.info { "Creating template: $name:$version" }
        
        return validateTemplateName(name)
            .flatMap { validateTemplateVersion(version) }
            .flatMap { validateSpecNotEmpty(spec) }
            .flatMap { validateTemplateDescription(description) }
            .flatMap { validateUniqueNameVersion(name, version) }
            .flatMap { validateParentTemplate(parentTemplateId) }
            .flatMap {
                val template = Template(
                    id = DomainId.generate(),
                    name = name,
                    description = description,
                    version = Version(version),
                    spec = spec,
                    status = TemplateStatus.DRAFT,
                    parentTemplateId = parentTemplateId,
                    createdAt = Clock.System.now(),
                    updatedAt = Clock.System.now(),
                    createdBy = createdBy
                )
                templateRepository.save(template)
            }
    }

    suspend fun updateTemplate(
        templateId: DomainId,
        description: String? = null,
        spec: JsonObject? = null
    ): Either<DomainError, Template> {
        logger.info { "Updating template: $templateId" }
        
        return templateRepository.findById(templateId)
            .flatMap { template ->
                if (template == null) {
                    NotFoundError(
                        message = "Template not found",
                        entityType = "Template",
                        entityId = templateId.value
                    ).left()
                } else {
                    if (template.status == TemplateStatus.PUBLISHED) {
                        ValidationError(
                            message = "Cannot update published template. Create a new version instead."
                        ).left()
                    } else {
                        val updatedTemplate = template.copy(
                            description = description ?: template.description,
                            spec = spec ?: template.spec,
                            updatedAt = Clock.System.now()
                        )
                        templateRepository.update(updatedTemplate)
                    }
                }
            }
    }

    suspend fun publishTemplate(
        templateId: DomainId,
        version: String
    ): Either<DomainError, Template> {
        logger.info { "Publishing template: $templateId as version $version" }
        
        return templateRepository.findById(templateId)
            .flatMap { template ->
                if (template == null) {
                    NotFoundError(
                        message = "Template not found",
                        entityType = "Template",
                        entityId = templateId.value
                    ).left()
                } else {
                    when (template.status) {
                        TemplateStatus.DRAFT -> {
                            validateUniqueNameVersion(template.name, version)
                                .flatMap {
                                    val publishedTemplate = template.copy(
                                        version = Version(version),
                                        status = TemplateStatus.PUBLISHED,
                                        updatedAt = Clock.System.now()
                                    )
                                    templateRepository.update(publishedTemplate)
                                }
                        }
                        TemplateStatus.PUBLISHED -> 
                            ValidationError(message = "Template is already published").left()
                        TemplateStatus.DEPRECATED -> 
                            ValidationError(message = "Cannot publish deprecated template").left()
                        else -> 
                            ValidationError(message = "Cannot publish template in current state").left()
                    }
                }
            }
    }

    suspend fun deprecateTemplate(templateId: DomainId): Either<DomainError, Template> {
        logger.info { "Deprecating template: $templateId" }
        
        return templateRepository.findById(templateId)
            .flatMap { template ->
                if (template == null) {
                    NotFoundError(
                        message = "Template not found",
                        entityType = "Template",
                        entityId = templateId.value
                    ).left()
                } else {
                    if (template.status == TemplateStatus.PUBLISHED) {
                        val deprecatedTemplate = template.updateStatus(TemplateStatus.DEPRECATED)
                        templateRepository.update(deprecatedTemplate)
                    } else {
                        ValidationError(message = "Only published templates can be deprecated").left()
                    }
                }
            }
    }

    suspend fun getTemplate(templateId: DomainId): Either<DomainError, Template> {
        logger.debug { "Getting template: $templateId" }
        return templateRepository.findById(templateId)
            .flatMap { template ->
                if (template == null) {
                    NotFoundError(
                        message = "Template not found",
                        entityType = "Template",
                        entityId = templateId.value
                    ).left()
                } else {
                    template.right()
                }
            }
    }

    suspend fun getTemplateByNameAndVersion(
        name: String, 
        version: String
    ): Either<DomainError, Template> {
        logger.debug { "Getting template by name and version: $name:$version" }
        return templateRepository.findByNameAndVersion(name, version)
            .flatMap { template ->
                if (template == null) {
                    NotFoundError(
                        message = "Template not found",
                        entityType = "Template",
                        entityId = "$name:$version"
                    ).left()
                } else {
                    template.right()
                }
            }
    }

    suspend fun listTemplates(
        page: Int = 1,
        pageSize: Int = 20,
        status: TemplateStatus? = null
    ): Either<DomainError, Pair<List<Template>, Long>> {
        logger.debug { "Listing templates: page=$page, pageSize=$pageSize, status=$status" }
        
        return if (status != null) {
            // If status filter is provided, use the flow-based method
            val templates = mutableListOf<Template>()
            try {
                templateRepository.findByStatus(status)
                    .catch { e -> throw e }
                    .collect { templates.add(it) }
                
                val startIndex = (page - 1) * pageSize
                val endIndex = minOf(startIndex + pageSize, templates.size)
                val paginatedTemplates = if (startIndex < templates.size) {
                    templates.subList(startIndex, endIndex)
                } else {
                    emptyList()
                }
                
                (paginatedTemplates to templates.size.toLong()).right()
            } catch (e: Exception) {
                logger.error(e) { "Error listing templates by status" }
                ValidationError(message = "Failed to list templates: ${e.message}").left()
            }
        } else {
            templateRepository.list(page, pageSize)
        }
    }

    suspend fun searchTemplates(
        query: String,
        status: TemplateStatus? = null
    ): Flow<Template> {
        logger.debug { "Searching templates: query=$query, status=$status" }
        return templateRepository.search(query, status)
    }

    suspend fun deleteTemplate(templateId: DomainId): Either<DomainError, Unit> {
        logger.info { "Deleting template: $templateId" }
        
        return templateRepository.findById(templateId)
            .flatMap { template ->
                if (template == null) {
                    NotFoundError(
                        message = "Template not found",
                        entityType = "Template",
                        entityId = templateId.value
                    ).left()
                } else {
                    if (template.status == TemplateStatus.PUBLISHED) {
                        ValidationError(message = "Cannot delete published template. Deprecate it instead.").left()
                    } else {
                        templateRepository.delete(templateId)
                    }
                }
            }
    }

    suspend fun getTemplateVersions(name: String): Either<DomainError, List<Template>> {
        logger.debug { "Getting template versions for: $name" }
        return templateRepository.findByName(name)
    }

    private suspend fun validateTemplateName(name: String): Either<DomainError, Unit> {
        return if (name.isBlank()) {
            ValidationError(message = "Template name cannot be blank").left()
        } else if (name.length > 255) {
            ValidationError(message = "Template name cannot exceed 255 characters").left()
        } else if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            ValidationError(message = "Template name can only contain alphanumeric characters, hyphens, and underscores").left()
        } else {
            Unit.right()
        }
    }

    private suspend fun validateTemplateVersion(version: String): Either<DomainError, Unit> {
        return if (version.isBlank()) {
            ValidationError(message = "Template version cannot be blank").left()
        } else if (!version.matches(Regex("^\\d+\\.\\d+\\.\\d+(-[a-zA-Z0-9.-]+)?$"))) {
            ValidationError(message = "Template version must follow semantic versioning (e.g., 1.0.0)").left()
        } else {
            Unit.right()
        }
    }

    private suspend fun validateSpecNotEmpty(spec: JsonObject): Either<DomainError, Unit> {
        return if (spec.isEmpty()) {
            ValidationError(message = "Template spec cannot be empty").left()
        } else {
            Unit.right()
        }
    }
    
    private suspend fun validateTemplateDescription(description: String): Either<DomainError, Unit> {
        return if (description.isBlank()) {
            ValidationError(message = "Template description cannot be blank").left()
        } else if (description.length > 1000) {
            ValidationError(message = "Template description cannot exceed 1000 characters").left()
        } else {
            Unit.right()
        }
    }

    private suspend fun validateUniqueNameVersion(
        name: String, 
        version: String
    ): Either<DomainError, Unit> {
        return templateRepository.existsByNameAndVersion(name, version)
            .flatMap { exists ->
                if (exists) {
                    ValidationError(message = "Template with name '$name' and version '$version' already exists").left()
                } else {
                    Unit.right()
                }
            }
    }

    private suspend fun validateParentTemplate(
        parentTemplateId: DomainId?
    ): Either<DomainError, Unit> {
        return if (parentTemplateId != null) {
            templateRepository.exists(parentTemplateId)
                .flatMap { exists ->
                    if (exists) {
                        Unit.right()
                    } else {
                        ValidationError(message = "Parent template does not exist").left()
                    }
                }
        } else {
            Unit.right()
        }
    }
}