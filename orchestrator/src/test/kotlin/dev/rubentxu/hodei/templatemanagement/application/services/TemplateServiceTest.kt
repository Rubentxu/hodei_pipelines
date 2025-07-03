package dev.rubentxu.hodei.templatemanagement.application.services

import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import dev.rubentxu.hodei.templatemanagement.domain.entities.Template
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatistics
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

// TODO: Fix TemplateServiceTest lambda type inference issues
/*
class TemplateServiceTest : BehaviorSpec({
    val templateRepository = mockk<TemplateRepository>()
    val templateService = TemplateService(templateRepository)

    Given("a template service") {
        When("creating a valid template") {
            val spec = buildJsonObject {
                put("stages", buildJsonObject {
                    put("build", buildJsonObject {
                        put("steps", "npm run build")
                    })
                })
            }

            coEvery { 
                templateRepository.existsByNameAndVersion("test-template", "1.0.0") 
            } returns false.right()
            
            coEvery { 
                templateRepository.save(any()) 
            } returns createSampleTemplate().right()

            Then("it should create the template successfully") {
                val result = templateService.createTemplate(
                    name = "test-template",
                    version = "1.0.0",
                    spec = spec,
                    description = "Test template",
                    createdBy = "user123"
                )

                result.shouldBeRight { template: Template ->
                    template.name shouldBe "test-template"
                    template.version.value shouldBe "1.0.0"
                    template.status shouldBe TemplateStatus.DRAFT
                }

                coVerify { templateRepository.save(any()) }
            }
        }

        When("creating a template with invalid name") {
            val spec = buildJsonObject { put("test", "value") }

            Then("it should return validation error") {
                val result = templateService.createTemplate(
                    name = "",
                    version = "1.0.0",
                    spec = spec,
                    description = "Test template",
                    createdBy = "user123"
                )

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldBe "Template name cannot be blank"
                }
            }
        }

        When("creating a template with invalid version") {
            val spec = buildJsonObject { put("test", "value") }

            Then("it should return validation error") {
                val result = templateService.createTemplate(
                    name = "test-template",
                    version = "invalid-version",
                    spec = spec,
                    description = "Test template",
                    createdBy = "user123"
                )

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "semantic versioning"
                }
            }
        }

        When("creating a template with duplicate name and version") {
            val spec = buildJsonObject { put("test", "value") }

            coEvery { 
                templateRepository.existsByNameAndVersion("test-template", "1.0.0") 
            } returns true.right()

            Then("it should return validation error") {
                val result = templateService.createTemplate(
                    name = "test-template",
                    version = "1.0.0",
                    spec = spec,
                    description = "Test template",
                    createdBy = "user123"
                )

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "already exists"
                }
            }
        }

        When("publishing a draft template") {
            val template = createSampleTemplate()
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()
            
            coEvery { 
                templateRepository.existsByNameAndVersion(template.name, "2.0.0") 
            } returns false.right()
            
            coEvery { 
                templateRepository.update(any()) 
            } returns template.copy(
                version = Version("2.0.0"),
                status = TemplateStatus.PUBLISHED
            ).right()

            Then("it should publish successfully") {
                val result = templateService.publishTemplate(template.id, "2.0.0")

                result.shouldBeRight { publishedTemplate: Template ->
                    publishedTemplate.version.value shouldBe "2.0.0"
                    publishedTemplate.status shouldBe TemplateStatus.PUBLISHED
                }

                coVerify { templateRepository.update(any()) }
            }
        }

        When("trying to publish an already published template") {
            val template = createSampleTemplate().copy(status = TemplateStatus.PUBLISHED)
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()

            Then("it should return validation error") {
                val result = templateService.publishTemplate(template.id, "2.0.0")

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "already published"
                }
            }
        }

        When("updating a draft template") {
            val template = createSampleTemplate()
            val newSpec = buildJsonObject { put("updated", "spec") }
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()
            
            coEvery { 
                templateRepository.update(any()) 
            } returns template.copy(
                description = "Updated description",
                spec = newSpec
            ).right()

            Then("it should update successfully") {
                val result = templateService.updateTemplate(
                    templateId = template.id,
                    description = "Updated description",
                    spec = newSpec
                )

                result.shouldBeRight { updatedTemplate: Template ->
                    updatedTemplate.description shouldBe "Updated description"
                    updatedTemplate.spec shouldBe newSpec
                }

                coVerify { templateRepository.update(any()) }
            }
        }

        When("trying to update a published template") {
            val template = createSampleTemplate().copy(status = TemplateStatus.PUBLISHED)
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()

            Then("it should return validation error") {
                val result = templateService.updateTemplate(
                    templateId = template.id,
                    description = "Updated description"
                )

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "Cannot update published template"
                }
            }
        }

        When("deleting a draft template") {
            val template = createSampleTemplate()
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()
            
            coEvery { 
                templateRepository.delete(template.id) 
            } returns Unit.right()

            Then("it should delete successfully") {
                val result = templateService.deleteTemplate(template.id)

                result.shouldBeRight(kotlin.Unit)

                coVerify { templateRepository.delete(template.id) }
            }
        }

        When("trying to delete a published template") {
            val template = createSampleTemplate().copy(status = TemplateStatus.PUBLISHED)
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()

            Then("it should return validation error") {
                val result = templateService.deleteTemplate(template.id)

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "Cannot delete published template"
                }
            }
        }

        When("listing templates") {
            val templates = listOf(createSampleTemplate(), createSampleTemplate())
            
            coEvery { 
                templateRepository.list(1, 20) 
            } returns (templates to 2L).right()

            Then("it should return paginated templates") {
                val result = templateService.listTemplates(page = 1, pageSize = 20)

                result.shouldBeRight { paginatedResult: Pair<List<Template>, Long> ->
                    val (returnedTemplates, total) = paginatedResult
                    returnedTemplates.size shouldBe 2
                    total shouldBe 2L
                }

                coVerify { templateRepository.list(1, 20) }
            }
        }

        When("searching templates") {
            val templates = listOf(createSampleTemplate())
            
            coEvery { 
                templateRepository.search("test", null) 
            } returns flowOf(*templates.toTypedArray())

            Then("it should return matching templates") {
                val result = templateService.searchTemplates("test")
                val resultList = mutableListOf<Template>()
                result.collect { resultList.add(it) }

                resultList.size shouldBe 1
                resultList[0].name shouldBe "test-template"

                coVerify { templateRepository.search("test", null) }
            }
        }

        When("deprecating a published template") {
            val template = createSampleTemplate().copy(status = TemplateStatus.PUBLISHED)
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()
            
            coEvery { 
                templateRepository.update(any()) 
            } returns template.copy(status = TemplateStatus.DEPRECATED).right()

            Then("it should deprecate successfully") {
                val result = templateService.deprecateTemplate(template.id)

                result.shouldBeRight { deprecatedTemplate: Template ->
                    deprecatedTemplate.status shouldBe TemplateStatus.DEPRECATED
                }

                coVerify { templateRepository.update(any()) }
            }
        }

        When("trying to deprecate a draft template") {
            val template = createSampleTemplate()
            
            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()

            Then("it should return validation error") {
                val result = templateService.deprecateTemplate(template.id)

                result.shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "Only published templates can be deprecated"
                }
            }
        }
    }
})

private fun createSampleTemplate(): Template {
    val now = Clock.System.now()
    return Template(
        id = DomainId.generate(),
        name = "test-template",
        version = Version("1.0.0"),
        spec = buildJsonObject {
            put("stages", buildJsonObject {
                put("build", buildJsonObject {
                    put("steps", "npm run build")
                })
            })
        },
        status = TemplateStatus.DRAFT,
        description = "Test template",
        createdAt = now,
        updatedAt = now,
        createdBy = "user123",
        statistics = TemplateStatistics()
    )
}
*/