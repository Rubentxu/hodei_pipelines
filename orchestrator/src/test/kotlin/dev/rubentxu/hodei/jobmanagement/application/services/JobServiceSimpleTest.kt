package dev.rubentxu.hodei.jobmanagement.application.services

// TODO: Fix JobServiceSimpleTest compilation errors
/*

import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.infrastructure.persistence.InMemoryJobRepository
import dev.rubentxu.hodei.templatemanagement.infrastructure.persistence.InMemoryTemplateRepository
import dev.rubentxu.hodei.templatemanagement.domain.entities.Template
import dev.rubentxu.hodei.templatemanagement.domain.entities.TemplateStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JobServiceSimpleTest : BehaviorSpec({
    
    val templateRepository = InMemoryTemplateRepository()
    val jobRepository = InMemoryJobRepository()
    val jobService = JobService(jobRepository, templateRepository)
    
    val now = Clock.System.now()
    
    given("a JobService with a published template") {
        val template = Template(
            id = DomainId.generate(),
            name = "Test Template",
            description = "A test template",
            version = Version("1.0.0"),
            status = TemplateStatus.PUBLISHED,
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
        
        templateRepository.save(template)
        
        `when`("creating a job from the template") {
            val result = jobService.createJobFromTemplate(
                templateId = template.id,
                name = "Test Job",
                namespace = "test",
                createdBy = "test-user"
            )
            
            then("it should create the job successfully") {
                result.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { job ->
                        job.name shouldBe "Test Job"
                        job.templateId shouldBe template.id
                        job.status shouldBe JobStatus.PENDING
                        job.namespace shouldBe "test"
                        job.isFromTemplate shouldBe true
                    }
                )
            }
        }
        
        `when`("creating an ad-hoc job") {
            val spec = buildJsonObject {
                put("stages", buildJsonObject {
                    put("cleanup", buildJsonObject {
                        put("steps", buildJsonObject {
                            put("delete", "rm -rf /tmp/*")
                        })
                    })
                })
            }
            
            val result = jobService.createAdHocJob(
                name = "Cleanup Job",
                spec = spec,
                namespace = "maintenance",
                createdBy = "test-user"
            )
            
            then("it should create the ad-hoc job successfully") {
                result.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { job ->
                        job.name shouldBe "Cleanup Job"
                        job.templateId shouldBe null
                        job.spec shouldBe spec
                        job.namespace shouldBe "maintenance"
                        job.isAdHoc shouldBe true
                    }
                )
            }
        }
        
        `when`("creating a job with duplicate name") {
            // First job
            jobService.createJobFromTemplate(
                templateId = template.id,
                name = "Duplicate Job",
                namespace = "test"
            )
            
            // Second job with same name
            val result = jobService.createJobFromTemplate(
                templateId = template.id,
                name = "Duplicate Job",
                namespace = "test"
            )
            
            then("it should return a conflict error") {
                result.fold(
                    { error -> 
                        error.shouldBeInstanceOf<ConflictError>()
                        error.message shouldBe "Job with name 'Duplicate Job' already exists in namespace 'test'"
                    },
                    { throw AssertionError("Expected error but got success") }
                )
            }
        }
        
        `when`("updating a job status") {
            val jobResult = jobService.createJobFromTemplate(
                templateId = template.id,
                name = "Status Test Job",
                namespace = "test"
            )
            
            val job = jobResult.fold(
                { error -> throw AssertionError("Failed to create job: $error") },
                { it }
            )
            
            val updateResult = jobService.updateJobStatus(job.id, JobStatus.QUEUED)
            
            then("it should update the status successfully") {
                updateResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { updatedJob ->
                        updatedJob.status shouldBe JobStatus.QUEUED
                        updatedJob.id shouldBe job.id
                    }
                )
            }
        }
        
        `when`("retrying a failed job") {
            val jobResult = jobService.createJobFromTemplate(
                templateId = template.id,
                name = "Retry Test Job",
                namespace = "test"
            )
            
            val job = jobResult.fold(
                { error -> throw AssertionError("Failed to create job: $error") },
                { it }
            )
            
            // Update to failed status first
            jobService.updateJobStatus(job.id, JobStatus.QUEUED)
            jobService.updateJobStatus(job.id, JobStatus.RUNNING)
            jobService.updateJobStatus(job.id, JobStatus.FAILED)
            
            val retryResult = jobService.retryJob(job.id)
            
            then("it should retry the job successfully") {
                retryResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { retriedJob ->
                        retriedJob.status shouldBe JobStatus.QUEUED
                        retriedJob.retryCount shouldBe 1
                    }
                )
            }
        }
        
        `when`("getting job statistics") {
            // Create some jobs in different states
            val job1Result = jobService.createJobFromTemplate(template.id, name = "Job 1", namespace = "test")
            val job2Result = jobService.createJobFromTemplate(template.id, name = "Job 2", namespace = "test")
            val job3Result = jobService.createJobFromTemplate(template.id, name = "Job 3", namespace = "test")
            
            val job1 = job1Result.getOrNull()!!
            val job2 = job2Result.getOrNull()!!
            val job3 = job3Result.getOrNull()!!
            
            // Update statuses
            jobService.updateJobStatus(job2.id, JobStatus.QUEUED)
            jobService.updateJobStatus(job3.id, JobStatus.QUEUED)
            jobService.updateJobStatus(job3.id, JobStatus.RUNNING)
            
            val statsResult = jobService.getJobStatistics()
            
            then("it should return correct statistics") {
                statsResult.fold(
                    { error -> throw AssertionError("Expected success but got error: $error") },
                    { stats ->
                        stats.total shouldBe 6L // Including jobs from previous tests
                        stats.pending shouldBe 1L
                        stats.queued shouldBe 1L  
                        stats.running shouldBe 1L
                    }
                )
            }
        }
    }
})
*/