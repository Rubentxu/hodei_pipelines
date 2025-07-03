package dev.rubentxu.hodei.templatemanagement.application.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.templatemanagement.domain.entities.*
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.slf4j.LoggerFactory

/**
 * Application service for managing worker templates.
 * 
 * Provides operations for creating, updating, and querying worker templates
 * that define how worker instances should be created and configured.
 */
class WorkerTemplateService(
    private val templateRepository: TemplateRepository
) {
    
    private val logger = LoggerFactory.getLogger(WorkerTemplateService::class.java)
    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    
    /**
     * Create a new worker template
     */
    suspend fun createWorkerTemplate(
        name: String,
        description: String,
        spec: WorkerTemplateSpec,
        createdBy: String
    ): Either<String, Template> {
        return try {
            // Validate template name uniqueness
            val existingTemplatesResult = templateRepository.findByName(name)
            when (existingTemplatesResult) {
                is Either.Right -> {
                    if (existingTemplatesResult.value.isNotEmpty()) {
                        return "Worker template with name '$name' already exists".left()
                    }
                }
                is Either.Left -> {
                    logger.warn("Failed to check template existence: ${existingTemplatesResult.value}")
                    // Continue anyway - this is just a validation check
                }
            }
            
            // Validate the spec
            val validationResult = validateWorkerTemplateSpec(spec)
            if (validationResult.isLeft()) {
                return validationResult.fold({ it.left() }, { "Validation passed but should not reach here".left() })
            }
            
            val template = WorkerTemplateBuilder.createDockerTemplate(
                name = name,
                description = description,
                image = spec.runtime.image,
                tag = spec.runtime.tag,
                cpuRequest = spec.resources.requests.cpu,
                memoryRequest = spec.resources.requests.memory,
                environment = spec.environment,
                createdBy = createdBy
            )
            
            when (val saveResult = templateRepository.save(template)) {
                is Either.Right -> {
                    logger.info("Created worker template: ${template.name} (${template.id.value})")
                    saveResult.value.right()
                }
                is Either.Left -> {
                    logger.error("Failed to save template: ${saveResult.value}")
                    "Failed to save template: ${saveResult.value.message}".left()
                }
            }
            
        } catch (e: Exception) {
            logger.error("Failed to create worker template", e)
            "Failed to create worker template: ${e.message}".left()
        }
    }
    
    /**
     * Get a worker template by ID
     */
    suspend fun getWorkerTemplate(templateId: DomainId): Either<String, Template> {
        return try {
            when (val result = templateRepository.findById(templateId)) {
                is Either.Right -> {
                    val template = result.value
                    if (template == null) {
                        "Worker template not found: ${templateId.value}".left()
                    } else {
                        template.right()
                    }
                }
                is Either.Left -> {
                    "Failed to get worker template: ${result.value.message}".left()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get worker template", e)
            "Failed to get worker template: ${e.message}".left()
        }
    }
    
    /**
     * Get a worker template by name
     */
    suspend fun getWorkerTemplateByName(name: String): Either<String, Template> {
        return try {
            when (val result = templateRepository.findByName(name)) {
                is Either.Right -> {
                    val templates = result.value
                    if (templates.isEmpty()) {
                        "Worker template not found: $name".left()
                    } else {
                        templates.first().right() // Return first matching template
                    }
                }
                is Either.Left -> {
                    "Failed to get worker template: ${result.value.message}".left()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to get worker template by name", e)
            "Failed to get worker template: ${e.message}".left()
        }
    }
    
    /**
     * Parse worker template specification from template
     */
    suspend fun parseWorkerTemplateSpec(template: Template): Either<String, WorkerTemplateSpec> {
        return try {
            val spec = json.decodeFromJsonElement(WorkerTemplateSpec.serializer(), template.spec)
            spec.right()
        } catch (e: Exception) {
            logger.error("Failed to parse worker template spec", e)
            "Failed to parse worker template spec: ${e.message}".left()
        }
    }
    
    /**
     * List all worker templates
     */
    suspend fun listWorkerTemplates(): Either<String, List<Template>> {
        return try {
            when (val result = templateRepository.list(0, 1000)) { // Use reasonable page size
                is Either.Right -> {
                    val (templates, _) = result.value
                    val workerTemplates = templates.filter { isWorkerTemplate(it) }
                    workerTemplates.right()
                }
                is Either.Left -> {
                    "Failed to list worker templates: ${result.value.message}".left()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to list worker templates", e)
            "Failed to list worker templates: ${e.message}".left()
        }
    }
    
    /**
     * List worker templates by type
     */
    suspend fun listWorkerTemplatesByType(workerType: WorkerType): Either<String, List<Template>> {
        return try {
            when (val result = templateRepository.list(0, 1000)) { // Use reasonable page size
                is Either.Right -> {
                    val (templates, _) = result.value
                    val workerTemplates = templates.filter { template ->
                        isWorkerTemplate(template) && 
                        getWorkerTypeFromTemplate(template) == workerType
                    }
                    workerTemplates.right()
                }
                is Either.Left -> {
                    "Failed to list worker templates by type: ${result.value.message}".left()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to list worker templates by type", e)
            "Failed to list worker templates by type: ${e.message}".left()
        }
    }
    
    /**
     * Update a worker template
     */
    suspend fun updateWorkerTemplate(
        templateId: DomainId,
        spec: WorkerTemplateSpec,
        updatedBy: String
    ): Either<String, Template> {
        return try {
            when (val findResult = templateRepository.findById(templateId)) {
                is Either.Right -> {
                    val existingTemplate = findResult.value
                    if (existingTemplate == null) {
                        return "Worker template not found: ${templateId.value}".left()
                    }
                    
                    // Validate the spec
                    val validationResult = validateWorkerTemplateSpec(spec)
                    if (validationResult.isLeft()) {
                        return validationResult.fold({ it.left() }, { "Validation passed but should not reach here".left() })
                    }
                    
                    val updatedTemplate = existingTemplate.copy(
                        spec = json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec) as JsonObject,
                        updatedAt = kotlinx.datetime.Clock.System.now()
                    )
                    
                    when (val saveResult = templateRepository.save(updatedTemplate)) {
                        is Either.Right -> {
                            logger.info("Updated worker template: ${updatedTemplate.name} (${updatedTemplate.id.value})")
                            saveResult.value.right()
                        }
                        is Either.Left -> {
                            "Failed to save updated template: ${saveResult.value.message}".left()
                        }
                    }
                }
                is Either.Left -> {
                    "Failed to find template: ${findResult.value.message}".left()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to update worker template", e)
            "Failed to update worker template: ${e.message}".left()
        }
    }
    
    /**
     * Delete a worker template
     */
    suspend fun deleteWorkerTemplate(templateId: DomainId): Either<String, Unit> {
        return try {
            when (val findResult = templateRepository.findById(templateId)) {
                is Either.Right -> {
                    val template = findResult.value
                    if (template == null) {
                        return "Worker template not found: ${templateId.value}".left()
                    }
                    
                    when (val deleteResult = templateRepository.delete(templateId)) {
                        is Either.Right -> {
                            logger.info("Deleted worker template: ${template.name} (${template.id.value})")
                            Unit.right()
                        }
                        is Either.Left -> {
                            "Failed to delete template: ${deleteResult.value.message}".left()
                        }
                    }
                }
                is Either.Left -> {
                    "Failed to find template: ${findResult.value.message}".left()
                }
            }
        } catch (e: Exception) {
            logger.error("Failed to delete worker template", e)
            "Failed to delete worker template: ${e.message}".left()
        }
    }
    
    /**
     * Create default worker templates
     */
    suspend fun createDefaultTemplates(): Either<String, List<Template>> {
        return try {
            val templates = mutableListOf<Template>()
            
            // Default Docker worker template
            val dockerTemplate = WorkerTemplateBuilder.createDockerTemplate(
                name = "default-docker-worker",
                description = "Default Docker worker template for general purpose jobs",
                image = "hodei/worker",
                tag = "latest",
                cpuRequest = "100m",
                memoryRequest = "256Mi",
                environment = mapOf(
                    "HODEI_LOG_LEVEL" to "INFO",
                    "HODEI_WORKER_POOL" to "default"
                ),
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(dockerTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save docker template: ${saveResult.value}")
            }
            
            // High-performance Docker worker template
            val performanceTemplate = WorkerTemplateBuilder.createDockerTemplate(
                name = "performance-docker-worker",
                description = "High-performance Docker worker template for resource-intensive jobs",
                image = "hodei/worker",
                tag = "latest",
                cpuRequest = "1000m",
                memoryRequest = "2Gi",
                environment = mapOf(
                    "HODEI_LOG_LEVEL" to "INFO",
                    "HODEI_WORKER_POOL" to "performance",
                    "HODEI_WORKER_CONCURRENCY" to "4"
                ),
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(performanceTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save performance template: ${saveResult.value}")
            }
            
            // Kubernetes worker template
            val kubernetesTemplate = WorkerTemplateBuilder.createKubernetesTemplate(
                name = "default-kubernetes-worker",
                description = "Default Kubernetes worker template",
                image = "hodei/worker",
                tag = "latest",
                cpuRequest = "100m",
                memoryRequest = "256Mi",
                environment = mapOf(
                    "HODEI_LOG_LEVEL" to "INFO",
                    "HODEI_WORKER_POOL" to "kubernetes"
                ),
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(kubernetesTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save kubernetes template: ${saveResult.value}")
            }
            
            logger.info("Created ${templates.size} default worker templates")
            templates.right()
            
        } catch (e: Exception) {
            logger.error("Failed to create default worker templates", e)
            "Failed to create default worker templates: ${e.message}".left()
        }
    }
    
    /**
     * Create extended Docker execution templates for MVP
     */
    suspend fun createDockerExecutionTemplates(): Either<String, List<Template>> {
        return try {
            val templates = mutableListOf<Template>()
            
            // CI/CD Pipeline Docker template
            val ciPipelineTemplate = WorkerTemplateBuilder.createDockerCIPipelineTemplate(
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(ciPipelineTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save CI pipeline template: ${saveResult.value}")
            }
            
            // Lightweight Docker template
            val lightweightTemplate = WorkerTemplateBuilder.createDockerLightweightTemplate(
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(lightweightTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save lightweight template: ${saveResult.value}")
            }
            
            // Persistent storage Docker template
            val storageTemplate = WorkerTemplateBuilder.createDockerPersistentStorageTemplate(
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(storageTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save storage template: ${saveResult.value}")
            }
            
            // GPU-accelerated Docker template
            val gpuTemplate = WorkerTemplateBuilder.createDockerGpuTemplate(
                createdBy = "system"
            )
            
            when (val saveResult = templateRepository.save(gpuTemplate)) {
                is Either.Right -> templates.add(saveResult.value)
                is Either.Left -> logger.warn("Failed to save GPU template: ${saveResult.value}")
            }
            
            logger.info("Created ${templates.size} Docker execution templates for MVP")
            templates.right()
            
        } catch (e: Exception) {
            logger.error("Failed to create Docker execution templates", e)
            "Failed to create Docker execution templates: ${e.message}".left()
        }
    }
    
    /**
     * Create all templates (default + Docker execution)
     */
    suspend fun createAllTemplates(): Either<String, List<Template>> {
        return try {
            val allTemplates = mutableListOf<Template>()
            
            // Create default templates
            when (val defaultResult = createDefaultTemplates()) {
                is Either.Right -> allTemplates.addAll(defaultResult.value)
                is Either.Left -> return defaultResult
            }
            
            // Create Docker execution templates
            when (val dockerResult = createDockerExecutionTemplates()) {
                is Either.Right -> allTemplates.addAll(dockerResult.value)
                is Either.Left -> logger.warn("Failed to create Docker execution templates: ${dockerResult.value}")
            }
            
            logger.info("Created ${allTemplates.size} total worker templates")
            allTemplates.right()
            
        } catch (e: Exception) {
            logger.error("Failed to create all templates", e)
            "Failed to create all templates: ${e.message}".left()
        }
    }
    
    /**
     * Get the appropriate worker template for a resource pool
     */
    suspend fun getTemplateForResourcePool(
        resourcePoolType: String,
        requirements: Map<String, String> = emptyMap()
    ): Either<String, Template> {
        return try {
            val workerType = when (resourcePoolType.lowercase()) {
                "docker" -> WorkerType.DOCKER
                "kubernetes" -> WorkerType.KUBERNETES_POD
                else -> WorkerType.DOCKER // Default to Docker
            }
            
            when (val templates = listWorkerTemplatesByType(workerType)) {
                is Either.Right -> {
                    val availableTemplates = templates.value
                    if (availableTemplates.isEmpty()) {
                        return "No worker templates found for type: $workerType".left()
                    }
                    
                    // For now, return the first template
                    // In the future, this could implement more sophisticated selection logic
                    // based on requirements (CPU, memory, labels, etc.)
                    availableTemplates.first().right()
                }
                is Either.Left -> {
                    templates.value.left()
                }
            }
            
        } catch (e: Exception) {
            logger.error("Failed to get template for resource pool", e)
            return "Failed to get template for resource pool: ${e.message}".left()
        }
    }
    
    private fun validateWorkerTemplateSpec(spec: WorkerTemplateSpec): Either<String, Unit> {
        // Validate runtime spec
        if (spec.runtime.image.isBlank()) {
            return "Worker template image cannot be blank".left()
        }
        
        // Validate resource requirements
        if (spec.resources.requests.cpu.isBlank()) {
            return "Worker template CPU request cannot be blank".left()
        }
        
        if (spec.resources.requests.memory.isBlank()) {
            return "Worker template memory request cannot be blank".left()
        }
        
        // Additional validation can be added here
        return Unit.right()
    }
    
    private fun isWorkerTemplate(template: Template): Boolean {
        return try {
            val spec = json.decodeFromJsonElement(WorkerTemplateSpec.serializer(), template.spec)
            true
        } catch (e: Exception) {
            false
        }
    }
    
    private fun getWorkerTypeFromTemplate(template: Template): WorkerType? {
        return try {
            val spec = json.decodeFromJsonElement(WorkerTemplateSpec.serializer(), template.spec)
            spec.type
        } catch (e: Exception) {
            null
        }
    }
}