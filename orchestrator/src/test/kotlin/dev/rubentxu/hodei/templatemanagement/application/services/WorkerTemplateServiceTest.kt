package dev.rubentxu.hodei.templatemanagement.application.services

import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.DomainError
import dev.rubentxu.hodei.templatemanagement.domain.entities.*
import dev.rubentxu.hodei.templatemanagement.domain.repositories.TemplateRepository
import io.kotest.assertions.arrow.core.shouldBeLeft
import io.kotest.assertions.arrow.core.shouldBeRight
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.collections.shouldContain
import io.mockk.*
import kotlinx.serialization.json.Json

class WorkerTemplateServiceTest : DescribeSpec({
    
    describe("WorkerTemplateService") {
        val templateRepository = mockk<TemplateRepository>()
        val service = WorkerTemplateService(templateRepository)
        
        describe("createWorkerTemplate") {
            it("should create a valid Docker worker template") {
                // Given
                val name = "test-docker-worker"
                val description = "Test Docker worker template"
                val spec = WorkerTemplateSpec(
                    type = WorkerType.DOCKER,
                    runtime = RuntimeSpec(
                        image = "hodei/worker",
                        tag = "latest",
                        pullPolicy = ImagePullPolicy.IF_NOT_PRESENT
                    ),
                    resources = ResourceSpec(
                        requests = ResourceRequirements(
                            cpu = "100m",
                            memory = "256Mi"
                        )
                    ),
                    environment = mapOf("TEST_ENV" to "value")
                )
                val createdBy = "test-user"
                
                val templateSlot = slot<Template>()
                coEvery { templateRepository.findByName(name) } returns emptyList<Template>().right()
                coEvery { templateRepository.save(capture(templateSlot)) } answers { templateSlot.captured.right() }
                
                // When
                val result = service.createWorkerTemplate(name, description, spec, createdBy)
                
                // Then
                result.shouldBeRight()
                
                coVerify { templateRepository.save(any()) }
            }
            
            it("should fail when template name already exists") {
                // Given
                val name = "existing-template"
                val existingTemplate = createTestTemplate(DomainId.generate(), name)
                val spec = WorkerTemplateSpec(
                    type = WorkerType.DOCKER,
                    runtime = RuntimeSpec(image = "test", tag = "latest"),
                    resources = ResourceSpec(
                        requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
                    )
                )
                
                coEvery { templateRepository.findByName(name) } returns listOf(existingTemplate).right()
                
                // When
                val result = service.createWorkerTemplate(name, "description", spec, "user")
                
                // Then
                result.shouldBeLeft()
            }
            
            it("should fail when worker template spec is invalid") {
                // Given
                val name = "invalid-template"
                val spec = WorkerTemplateSpec(
                    type = WorkerType.DOCKER,
                    runtime = RuntimeSpec(image = "", tag = "latest"), // Invalid: empty image
                    resources = ResourceSpec(
                        requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
                    )
                )
                
                coEvery { templateRepository.findByName(name) } returns emptyList<Template>().right()
                
                // When
                val result = service.createWorkerTemplate(name, "description", spec, "user")
                
                // Then
                result.shouldBeLeft()
            }
        }
        
        describe("getWorkerTemplate") {
            it("should return template when it exists") {
                // Given
                val templateId = DomainId.generate()
                val template = createTestTemplate(templateId, "test-template")
                
                coEvery { templateRepository.findById(templateId) } returns template.right()
                
                // When
                val result = service.getWorkerTemplate(templateId)
                
                // Then
                result.shouldBeRight()
            }
            
            it("should return error when template does not exist") {
                // Given
                val templateId = DomainId.generate()
                
                coEvery { templateRepository.findById(templateId) } returns null.right()
                
                // When
                val result = service.getWorkerTemplate(templateId)
                
                // Then
                result.shouldBeLeft()
            }
        }
        
        describe("parseWorkerTemplateSpec") {
            it("should parse valid worker template spec") {
                // Given
                val spec = WorkerTemplateSpec(
                    type = WorkerType.DOCKER,
                    runtime = RuntimeSpec(image = "test", tag = "latest"),
                    resources = ResourceSpec(
                        requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
                    )
                )
                val template = createTestTemplate(DomainId.generate(), "test", spec)
                
                // When
                val result = service.parseWorkerTemplateSpec(template)
                
                // Then
                result.shouldBeRight()
            }
        }
        
        describe("listWorkerTemplatesByType") {
            it("should return templates of specified type") {
                // Given
                val dockerTemplate = createTestTemplate(
                    DomainId.generate(), 
                    "docker-template",
                    WorkerTemplateSpec(
                        type = WorkerType.DOCKER,
                        runtime = RuntimeSpec(image = "docker", tag = "latest"),
                        resources = ResourceSpec(
                            requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
                        )
                    )
                )
                val kubernetesTemplate = createTestTemplate(
                    DomainId.generate(), 
                    "k8s-template",
                    WorkerTemplateSpec(
                        type = WorkerType.KUBERNETES_POD,
                        runtime = RuntimeSpec(image = "k8s", tag = "latest"),
                        resources = ResourceSpec(
                            requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
                        )
                    )
                )
                
                coEvery { templateRepository.list(any(), any()) } returns (listOf(dockerTemplate, kubernetesTemplate) to 2L).right()
                
                // When
                val result = service.listWorkerTemplatesByType(WorkerType.DOCKER)
                
                // Then
                result.shouldBeRight()
            }
        }
        
        describe("getTemplateForResourcePool") {
            it("should return appropriate template for Docker resource pool") {
                // Given
                val dockerTemplate = createTestTemplate(
                    DomainId.generate(), 
                    "docker-template",
                    WorkerTemplateSpec(
                        type = WorkerType.DOCKER,
                        runtime = RuntimeSpec(image = "docker", tag = "latest"),
                        resources = ResourceSpec(
                            requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
                        )
                    )
                )
                
                coEvery { templateRepository.list(any(), any()) } returns (listOf(dockerTemplate) to 1L).right()
                
                // When
                val result = service.getTemplateForResourcePool("docker")
                
                // Then
                result.shouldBeRight()
            }
            
            it("should return error when no templates found for resource pool type") {
                // Given
                coEvery { templateRepository.list(any(), any()) } returns (emptyList<Template>() to 0L).right()
                
                // When
                val result = service.getTemplateForResourcePool("docker")
                
                // Then
                result.shouldBeLeft()
            }
        }
        
        describe("createDefaultTemplates") {
            it("should create default Docker and Kubernetes templates") {
                // Given
                clearMocks(templateRepository) // Clear previous interactions
                val capturedTemplates = mutableListOf<Template>()
                coEvery { templateRepository.save(any()) } answers { 
                    val template = firstArg<Template>()
                    capturedTemplates.add(template)
                    template.right()
                }
                
                // When
                val result = service.createDefaultTemplates()
                
                // Then
                result.shouldBeRight()
                val resultTemplates = result.getOrNull()
                resultTemplates?.shouldHaveSize(3)
                
                // Verify all 3 templates were created with correct names
                capturedTemplates shouldHaveSize 3
                val templateNames = capturedTemplates.map { it.name }
                templateNames shouldContain "default-docker-worker"
                templateNames shouldContain "performance-docker-worker"
                templateNames shouldContain "default-kubernetes-worker"
                
                // Verify repository save was called exactly 3 times
                coVerify(exactly = 3) { templateRepository.save(any()) }
            }
        }
    }
})

// Helper functions for creating test data
private fun createTestTemplate(
    id: DomainId, 
    name: String, 
    spec: WorkerTemplateSpec? = null
): Template {
    val defaultSpec = WorkerTemplateSpec(
        type = WorkerType.DOCKER,
        runtime = RuntimeSpec(image = "test", tag = "latest"),
        resources = ResourceSpec(
            requests = ResourceRequirements(cpu = "100m", memory = "256Mi")
        )
    )
    
    val now = kotlinx.datetime.Clock.System.now()
    return Template(
        id = id,
        name = name,
        description = "Test template",
        version = dev.rubentxu.hodei.shared.domain.primitives.Version("1.0.0"),
        spec = Json.encodeToJsonElement(WorkerTemplateSpec.serializer(), spec ?: defaultSpec) as kotlinx.serialization.json.JsonObject,
        status = TemplateStatus.PUBLISHED,
        createdAt = now,
        updatedAt = now,
        createdBy = "test"
    )
}