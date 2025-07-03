package dev.rubentxu.hodei.templatemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Version
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class TemplateTest : BehaviorSpec({
    
    val now = Clock.System.now()
    
    given("a Template") {
        val template = Template(
            id = DomainId.generate(),
            name = "Test Template",
            description = "A test template",
            version = Version("1.0.0"),
            status = TemplateStatus.DRAFT,
            spec = buildJsonObject {
                put("stages", buildJsonObject {
                    put("build", buildJsonObject {
                        put("steps", buildJsonObject {
                            put("compile", "gradle build")
                        })
                    })
                })
            },
            createdAt = now,
            updatedAt = now,
            createdBy = "test-user"
        )
        
        `when`("updating status from DRAFT to VALIDATING") {
            val updatedTemplate = template.updateStatus(TemplateStatus.VALIDATING)
            
            then("it should update the status and timestamp") {
                updatedTemplate.status shouldBe TemplateStatus.VALIDATING
                updatedTemplate.id shouldBe template.id
                updatedTemplate.name shouldBe template.name
                updatedTemplate.updatedAt shouldBe updatedTemplate.updatedAt
            }
        }
        
        `when`("updating status with invalid transition") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    template.updateStatus(TemplateStatus.PUBLISHED)
                }
            }
        }
        
        `when`("updating statistics") {
            val stats = TemplateStatistics(
                totalExecutions = 10,
                successfulExecutions = 8,
                failedExecutions = 2,
                averageDurationSeconds = 120.0
            )
            val updatedTemplate = template.updateStatistics(stats)
            
            then("it should update the statistics") {
                updatedTemplate.statistics shouldBe stats
                updatedTemplate.id shouldBe template.id
            }
        }
        
        `when`("checking if template can create jobs") {
            val draftTemplate = template.copy(status = TemplateStatus.DRAFT)
            val publishedTemplate = template.copy(status = TemplateStatus.PUBLISHED)
            val archivedTemplate = template.copy(status = TemplateStatus.ARCHIVED)
            
            then("draft template should not be usable") {
                draftTemplate.isUsableForJobs() shouldBe false
            }
            
            then("published template should be usable") {
                publishedTemplate.isUsableForJobs() shouldBe true
            }
            
            then("archived template should not be usable") {
                archivedTemplate.isUsableForJobs() shouldBe false
            }
        }
        
        `when`("checking computed properties") {
            val stats = TemplateStatistics(
                totalExecutions = 10,
                successfulExecutions = 8,
                failedExecutions = 2,
                averageDurationSeconds = 120.0
            )
            val templateWithStats = template.copy(statistics = stats)
            
            then("success rate should be calculated correctly") {
                templateWithStats.statistics.successRate shouldBe 0.8
            }
        }
    }
    
    given("Template validation") {
        `when`("creating a template with blank name") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    Template(
                        id = DomainId.generate(),
                        name = "",
                        description = "A test template",
                        version = Version("1.0.0"),
                        status = TemplateStatus.DRAFT,
                        spec = buildJsonObject { put("test", "spec") },
                        createdAt = now,
                        updatedAt = now,
                        createdBy = "test-user"
                    )
                }
            }
        }
        
        `when`("creating a template with empty spec") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    Template(
                        id = DomainId.generate(),
                        name = "Test Template",
                        description = "A test template",
                        version = Version("1.0.0"),
                        status = TemplateStatus.DRAFT,
                        spec = buildJsonObject { },
                        createdAt = now,
                        updatedAt = now,
                        createdBy = "test-user"
                    )
                }
            }
        }
        
        `when`("creating a template with blank description") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    Template(
                        id = DomainId.generate(),
                        name = "Test Template",
                        description = "",
                        version = Version("1.0.0"),
                        status = TemplateStatus.DRAFT,
                        spec = buildJsonObject { put("test", "spec") },
                        createdAt = now,
                        updatedAt = now,
                        createdBy = "test-user"
                    )
                }
            }
        }
    }
})