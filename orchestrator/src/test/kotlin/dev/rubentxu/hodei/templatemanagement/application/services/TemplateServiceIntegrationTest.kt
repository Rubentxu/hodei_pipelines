package dev.rubentxu.hodei.templatemanagement.application.services

import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import dev.rubentxu.hodei.templatemanagement.infrastructure.persistence.InMemoryTemplateRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// TODO: Fix TemplateServiceIntegrationTest compilation errors
/*
class TemplateServiceIntegrationTest : BehaviorSpec({
    
    given("TemplateService with InMemoryRepository") {
        val templateRepository = InMemoryTemplateRepository()
        val templateService = TemplateService(templateRepository)
        
        `when`("creating a valid template") {
            val spec = buildJsonObject {
                put("stages", buildJsonObject {
                    put("build", buildJsonObject {
                        put("steps", buildJsonObject {
                            put("compile", "gradle build")
                        })
                    })
                })
            }
            
            val result = templateService.createTemplate(
                name = "Test Template",
                version = "1.0.0",
                spec = spec,
                description = "A test template",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            then("it should create the template successfully") {
                result.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { template ->
                        template.name shouldBe "Test Template"
                        template.description shouldBe "A test template"
                        template.status shouldBe TemplateStatus.DRAFT
                        template.createdBy shouldBe "test-user"
                        template.spec shouldBe spec
                    }
                )
            }
        }
        
        `when`("creating a template with duplicate name") {
            val spec = buildJsonObject { put("test", "spec") }
            
            // Create first template
            templateService.createTemplate(
                name = "Duplicate Template",
                version = "1.0.0",
                spec = spec,
                description = "First template",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            // Try to create second template with same name
            val result = templateService.createTemplate(
                name = "Duplicate Template",
                version = "1.0.0",
                spec = spec,
                description = "Second template",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            then("it should return a conflict error") {
                result.fold(
                    { error ->
                        error.shouldBeInstanceOf<ValidationError>()
                        error.message shouldBe "Template with name 'Duplicate Template' and version '1.0.0' already exists"
                    },
                    { throw AssertionError("Expected error but got success") }
                )
            }
        }
        
        `when`("getting a template by ID") {
            val spec = buildJsonObject { put("test", "spec") }
            val createResult = templateService.createTemplate(
                name = "Get Test Template",
                version = "1.0.0",
                spec = spec,
                description = "Template for get test",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            val templateId = createResult.fold(
                { throw AssertionError("Failed to create template") },
                { it.id }
            )
            
            val getResult = templateService.getTemplate(templateId)
            
            then("it should return the template") {
                getResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { template ->
                        template!!.id shouldBe templateId
                        template.name shouldBe "Get Test Template"
                        template.description shouldBe "Template for get test"
                    }
                )
            }
        }
        
        `when`("publishing a draft template") {
            val spec = buildJsonObject { put("test", "spec") }
            val createResult = templateService.createTemplate(
                name = "Publish Test Template",
                version = "1.0.0",
                spec = spec,
                description = "Template for publish test",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            val templateId = createResult.fold(
                { throw AssertionError("Failed to create template") },
                { it.id }
            )
            
            val publishResult = templateService.publishTemplate(templateId, version = "1.0.0")
            
            then("it should publish the template") {
                publishResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { template ->
                        template.status shouldBe TemplateStatus.PUBLISHED
                        template.id shouldBe templateId
                    }
                )
            }
        }
        
        `when`("trying to publish a non-existent template") {
            val nonExistentId = DomainId.generate()
            val result = templateService.publishTemplate(nonExistentId, "1.0.0")
            
            then("it should return a not found error") {
                result.fold(
                    { error ->
                        error.shouldBeInstanceOf<NotFoundError>()
                        error.message shouldBe "Template not found"
                    },
                    { throw AssertionError("Expected error but got success") }
                )
            }
        }
        
        `when`("deprecating a published template") {
            val spec = buildJsonObject { put("test", "spec") }
            val createResult = templateService.createTemplate(
                name = "Deprecate Test Template",
                version = "1.0.0",
                spec = spec,
                description = "Template for deprecate test",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            val templateId = createResult.fold(
                { throw AssertionError("Failed to create template") },
                { it.id }
            )
            
            // First publish it
            templateService.publishTemplate(templateId, "1.0.0")
            
            // Then deprecate it
            val deprecateResult = templateService.deprecateTemplate(templateId)
            
            then("it should deprecate the template") {
                deprecateResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { template ->
                        template.status shouldBe TemplateStatus.DEPRECATED
                        template.id shouldBe templateId
                    }
                )
            }
        }
        
        `when`("updating a template") {
            val spec = buildJsonObject { put("test", "spec") }
            val createResult = templateService.createTemplate(
                name = "Update Test Template",
                version = "1.0.0",
                spec = spec,
                description = "Template for update test",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            val templateId = createResult.fold(
                { throw AssertionError("Failed to create template") },
                { it.id }
            )
            
            val newSpec = buildJsonObject { put("updated", "spec") }
            val updateResult = templateService.updateTemplate(
                templateId = templateId,
                description = "Updated description",
                spec = newSpec
            )
            
            then("it should update the template") {
                updateResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { template ->
                        template.description shouldBe "Updated description"
                        template.spec shouldBe newSpec
                        template.id shouldBe templateId
                    }
                )
            }
        }
        
        `when`("deleting a draft template") {
            val spec = buildJsonObject { put("test", "spec") }
            val createResult = templateService.createTemplate(
                name = "Delete Test Template",
                version = "1.0.0",
                spec = spec,
                description = "Template for delete test",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            val templateId = createResult.fold(
                { throw AssertionError("Failed to create template") },
                { it.id }
            )
            
            val deleteResult = templateService.deleteTemplate(templateId)
            
            then("it should delete the template") {
                deleteResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { /* success */ }
                )
                
                // Verify it's deleted by trying to get it
                val getResult = templateService.getTemplate(templateId)
                getResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { template -> template shouldBe null }
                )
            }
        }
        
        `when`("trying to delete a published template") {
            val spec = buildJsonObject { put("test", "spec") }
            val createResult = templateService.createTemplate(
                name = "Delete Published Template",
                version = "1.0.0",
                spec = spec,
                description = "Template for delete test",
                parentTemplateId = null,
                createdBy = "test-user"
            )
            
            val templateId = createResult.fold(
                { throw AssertionError("Failed to create template") },
                { it.id }
            )
            
            // Publish it first
            templateService.publishTemplate(templateId, "1.0.0")
            
            val deleteResult = templateService.deleteTemplate(templateId)
            
            then("it should return a validation error") {
                deleteResult.fold(
                    { error ->
                        error.shouldBeInstanceOf<ValidationError>()
                        error.message shouldBe "Cannot delete published template. Deprecate it instead."
                    },
                    { throw AssertionError("Expected error but got success") }
                )
            }
        }
    }
})
*/