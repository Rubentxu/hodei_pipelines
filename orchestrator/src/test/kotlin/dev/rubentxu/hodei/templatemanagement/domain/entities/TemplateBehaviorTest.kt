package dev.rubentxu.hodei.templatemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Version
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.*
import io.kotest.property.checkAll
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

class TemplateBehaviorTest : BehaviorSpec({
    
    Given("a template lifecycle scenario") {
        When("creating a new template in draft status") {
            val template = createValidTemplate()
            
            Then("it should be created with draft status") {
                template.status shouldBe TemplateStatus.DRAFT
                template.status.canCreateJobs shouldBe false
                template.isUsableForJobs() shouldBe false
                template.statistics.totalExecutions shouldBe 0
                template.statistics.successRate shouldBe 0.0
            }
        }
        
        When("publishing a draft template") {
            val draftTemplate = createValidTemplate()
            val validatingTemplate = draftTemplate.updateStatus(TemplateStatus.VALIDATING)
            val publishedTemplate = validatingTemplate.updateStatus(TemplateStatus.PUBLISHED)
            
            Then("it should be published and usable for jobs") {
                publishedTemplate.status shouldBe TemplateStatus.PUBLISHED
                publishedTemplate.status.canCreateJobs shouldBe true
                publishedTemplate.isUsableForJobs() shouldBe true
                publishedTemplate.updatedAt shouldNotBe draftTemplate.updatedAt
            }
        }
        
        When("deprecating a published template") {
            val publishedTemplate = createValidTemplate()
                .updateStatus(TemplateStatus.VALIDATING)
                .updateStatus(TemplateStatus.PUBLISHED)
            val deprecatedTemplate = publishedTemplate.updateStatus(TemplateStatus.DEPRECATED)
            
            Then("it should be deprecated but still usable") {
                deprecatedTemplate.status shouldBe TemplateStatus.DEPRECATED
                deprecatedTemplate.status.canCreateJobs shouldBe false
                deprecatedTemplate.status.isActive shouldBe true
            }
        }
        
        When("archiving a template") {
            val template = createValidTemplate()
                .updateStatus(TemplateStatus.VALIDATING)
                .updateStatus(TemplateStatus.PUBLISHED)
            val archivedTemplate = template.updateStatus(TemplateStatus.ARCHIVED)
            
            Then("it should be archived and unusable") {
                archivedTemplate.status shouldBe TemplateStatus.ARCHIVED
                archivedTemplate.status.canCreateJobs shouldBe false
                archivedTemplate.status.isActive shouldBe false
            }
        }
        
        When("attempting invalid status transition") {
            val draftTemplate = createValidTemplate()
            
            Then("it should throw validation error") {
                shouldThrow<IllegalArgumentException> {
                    draftTemplate.updateStatus(TemplateStatus.DEPRECATED) // DRAFT -> DEPRECATED is invalid
                }.message shouldBe "Cannot transition from DRAFT to DEPRECATED"
            }
        }
    }
    
    Given("a template validation scenario") {
        When("creating a template") {
            val validTemplate = createValidTemplate()
            
            Then("it should have all required fields") {
                validTemplate.name shouldBe "test-template"
                validTemplate.version shouldBe Version("1.0.0")
                validTemplate.spec.isNotEmpty() shouldBe true
                validTemplate.parentTemplateId shouldBe null
            }
        }
        
        When("creating a template with inheritance") {
            val parentTemplateId = DomainId.generate()
            val childTemplate = createValidTemplate().copy(parentTemplateId = parentTemplateId)
            
            Then("it should reference parent template") {
                childTemplate.parentTemplateId shouldBe parentTemplateId
            }
        }
        
        When("creating template with invalid name") {
            Then("it should throw validation error") {
                shouldThrow<IllegalArgumentException> {
                    createValidTemplate().copy(name = "")
                }.message shouldBe "Template name cannot be blank"
            }
        }
        
        When("creating template with empty spec") {
            Then("it should throw validation error") {
                shouldThrow<IllegalArgumentException> {
                    createValidTemplate().copy(spec = JsonObject(emptyMap()))
                }.message shouldBe "Template spec cannot be empty"
            }
        }
    }
    
    Given("a template statistics scenario") {
        val template = createValidTemplate()
        
        When("updating template statistics") {
            val newStats = TemplateStatistics(
                totalExecutions = 10,
                successfulExecutions = 8,
                failedExecutions = 2,
                averageDurationSeconds = 120.5
            )
            val updatedTemplate = template.updateStatistics(newStats)
            
            Then("statistics should be updated correctly") {
                updatedTemplate.statistics.totalExecutions shouldBe 10
                updatedTemplate.statistics.successfulExecutions shouldBe 8
                updatedTemplate.statistics.averageDurationSeconds shouldBe 120.5
                updatedTemplate.statistics.successRate shouldBe 0.8
                updatedTemplate.updatedAt shouldNotBe template.updatedAt
            }
        }
        
        When("calculating success rate with no executions") {
            val emptyStats = TemplateStatistics()
            
            Then("success rate should be zero") {
                emptyStats.successRate shouldBe 0.0
            }
        }
    }
})

// Property-based tests for Template validation
class TemplatePropertyTest : BehaviorSpec({
    
    Given("property-based template validation") {
        When("testing template name validation") {
            Then("template names should not be blank") {
                checkAll(Arb.string(0..10)) { name ->
                    if (name.isBlank()) {
                        shouldThrow<IllegalArgumentException> {
                            createValidTemplate().copy(name = name)
                        }
                    }
                }
            }
        }
        
        When("testing version validation") {
            Then("versions should follow semantic versioning") {
                checkAll(
                    Arb.choice(
                        Arb.constant(""),
                        Arb.constant("invalid"),
                        Arb.constant("1.2"),
                        Arb.constant("v1.2.3"),
                        Arb.constant("1.2.3.4")
                    )
                ) { invalidVersion ->
                    shouldThrow<IllegalArgumentException> {
                        Version(invalidVersion)
                    }
                }
            }
        }
        
        When("testing valid semantic versions") {
            Then("valid versions should be accepted") {
                checkAll(
                    Arb.choice(
                        Arb.constant("1.0.0"),
                        Arb.constant("2.1.3"),
                        Arb.constant("1.0.0-alpha"),
                        Arb.constant("1.0.0-beta.1"),
                        Arb.constant("1.0.0+build.1")
                    )
                ) { validVersion ->
                    val version = Version(validVersion)
                    version.value shouldBe validVersion
                }
            }
        }
        
        When("testing template status transitions") {
            Then("valid transitions should be allowed") {
                checkAll(Arb.enum<TemplateStatus>(), Arb.enum<TemplateStatus>()) { from, to ->
                    val canTransition = from.canTransitionTo(to)
                    val template = createValidTemplate().copy(status = from)
                    
                    if (canTransition) {
                        template.updateStatus(to).status shouldBe to
                    } else {
                        shouldThrow<IllegalArgumentException> {
                            template.updateStatus(to)
                        }
                    }
                }
            }
        }
    }
})

private fun createValidTemplate(): Template {
    val now = Clock.System.now()
    val spec = JsonObject(
        mapOf(
            "stages" to JsonPrimitive("build,test,deploy"),
            "image" to JsonPrimitive("ubuntu:22.04")
        )
    )
    
    return Template(
        id = DomainId.generate(),
        name = "test-template",
        description = "A test template",
        version = Version("1.0.0"),
        spec = spec,
        status = TemplateStatus.DRAFT,
        createdAt = now,
        updatedAt = now,
        createdBy = "test-user"
    )
}