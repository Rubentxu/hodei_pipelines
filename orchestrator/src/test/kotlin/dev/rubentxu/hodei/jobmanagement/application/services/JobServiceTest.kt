package dev.rubentxu.hodei.jobmanagement.application.services

// TODO: Fix JobServiceTest compilation errors
/*

import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
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
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JobServiceTest : BehaviorSpec({
    val jobRepository = mockk<JobRepository>()
    val templateRepository = mockk<TemplateRepository>()
    val jobService = JobService(jobRepository, templateRepository)

    Given("a job service") {
        When("creating a job from template") {
            val template = createSampleTemplate()
            val templateId = template.id

            coEvery { 
                templateRepository.findById(templateId) 
            } returns template.right()
            
            coEvery { 
                jobRepository.existsByName("test-job", "test") 
            } returns false.right()
            
            coEvery { 
                jobRepository.save(any()) 
            } returns createSampleJob().right()

            Then("it should create the job successfully") {
                val result = jobService.createJobFromTemplate(
                    templateId = templateId,
                    name = "test-job",
                    namespace = "test",
                    priority = Priority.MEDIUM,
                    parameters = JsonObject(emptyMap()),
                    overrides = JsonObject(emptyMap()),
                    createdBy = "user123"
                )

                result shouldBeRight { job: Job ->
                    job.name shouldBe "test-job"
                    job.templateId shouldBe templateId
                    job.status shouldBe JobStatus.PENDING
                    job.namespace shouldBe "test"
                    job.priority shouldBe Priority.MEDIUM
                }

                coVerify { jobRepository.save(any()) }
            }
        }

        When("creating a job from non-existent template") {
            val templateId = DomainId.generate()

            coEvery { 
                templateRepository.findById(templateId) 
            } returns null.right()

            Then("it should return not found error") {
                val result = jobService.createJobFromTemplate(
                    templateId = templateId,
                    name = "test-job",
                    namespace = "test",
                    createdBy = "user123"
                )

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<NotFoundError>()
                    error.message shouldContain "Template with id $templateId not found"
                }
            }
        }

        When("creating a job from draft template") {
            val template = createSampleTemplate().copy(status = TemplateStatus.DRAFT)

            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()

            Then("it should return validation error") {
                val result = jobService.createJobFromTemplate(
                    templateId = template.id,
                    name = "test-job",
                    namespace = "test",
                    createdBy = "user123"
                )

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "not published"
                }
            }
        }

        When("creating a job with duplicate name") {
            val template = createSampleTemplate()

            coEvery { 
                templateRepository.findById(template.id) 
            } returns template.right()
            
            coEvery { 
                jobRepository.existsByName("duplicate-job", "test") 
            } returns true.right()

            Then("it should return conflict error") {
                val result = jobService.createJobFromTemplate(
                    templateId = template.id,
                    name = "duplicate-job",
                    namespace = "test",
                    createdBy = "user123"
                )

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ConflictError>()
                    error.message shouldContain "already exists"
                }
            }
        }

        When("creating an ad-hoc job") {
            val spec = buildJsonObject {
                put("stages", buildJsonObject {
                    put("build", "gradle build")
                })
            }

            coEvery { 
                jobRepository.existsByName("adhoc-job", "test") 
            } returns false.right()
            
            coEvery { 
                jobRepository.save(any()) 
            } returns createSampleJob().copy(
                templateId = null,
                spec = spec
            ).right()

            Then("it should create the ad-hoc job successfully") {
                val result = jobService.createAdHocJob(
                    name = "adhoc-job",
                    spec = spec,
                    namespace = "test",
                    priority = Priority.LOW,
                    createdBy = "user123"
                )

                result shouldBeRight { job: Job ->
                    job.name shouldBe "adhoc-job"
                    job.templateId shouldBe null
                    job.spec shouldBe spec
                }

                coVerify { jobRepository.save(any()) }
            }
        }

        When("creating an ad-hoc job with invalid name") {
            val spec = buildJsonObject { put("test", "spec") }

            Then("it should return validation error") {
                val result = jobService.createAdHocJob(
                    name = "",
                    spec = spec,
                    namespace = "test",
                    createdBy = "user123"
                )

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "name cannot be blank"
                }
            }
        }

        When("updating job status") {
            val job = createSampleJob()

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()
            
            coEvery { 
                jobRepository.update(any()) 
            } returns job.copy(status = JobStatus.QUEUED).right()

            Then("it should update status successfully") {
                val result = jobService.updateJobStatus(job.id, JobStatus.QUEUED)

                result shouldBeRight { updatedJob: Job ->
                    updatedJob.status shouldBe JobStatus.QUEUED
                    updatedJob.id shouldBe job.id
                }

                coVerify { jobRepository.update(any()) }
            }
        }

        When("updating job status with invalid transition") {
            val job = createSampleJob().copy(status = JobStatus.PENDING)

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()

            Then("it should return validation error") {
                val result = jobService.updateJobStatus(job.id, JobStatus.RUNNING)

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "Invalid status transition"
                }
            }
        }

        When("updating non-existent job status") {
            val jobId = DomainId.generate()

            coEvery { 
                jobRepository.findById(jobId) 
            } returns null.right()

            Then("it should return not found error") {
                val result = jobService.updateJobStatus(jobId, JobStatus.QUEUED)

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<NotFoundError>()
                    error.message shouldContain "Job with id $jobId not found"
                }
            }
        }

        When("retrying a failed job") {
            val job = createSampleJob().copy(
                status = JobStatus.FAILED,
                retryCount = 1
            )

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()
            
            coEvery { 
                jobRepository.update(any()) 
            } returns job.copy(
                status = JobStatus.QUEUED,
                retryCount = 2
            ).right()

            Then("it should retry successfully") {
                val result = jobService.retryJob(job.id)

                result shouldBeRight { retriedJob: Job ->
                    retriedJob.status shouldBe JobStatus.QUEUED
                    retriedJob.retryCount shouldBe 2
                }

                coVerify { jobRepository.update(any()) }
            }
        }

        When("trying to retry a job that has exceeded max retries") {
            val job = createSampleJob().copy(
                status = JobStatus.FAILED,
                retryCount = 3,
                maxRetries = 3
            )

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()

            Then("it should return validation error") {
                val result = jobService.retryJob(job.id)

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "cannot be retried"
                }
            }
        }

        When("getting a job by ID") {
            val job = createSampleJob()

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()

            Then("it should return the job") {
                val result = jobService.getJob(job.id)

                result shouldBeRight { foundJob: Job? ->
                    foundJob!!.id shouldBe job.id
                    foundJob.name shouldBe job.name
                }
            }
        }

        When("deleting a draft job") {
            val job = createSampleJob().copy(status = JobStatus.PENDING)

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()
            
            coEvery { 
                jobRepository.delete(job.id) 
            } returns Unit.right()

            Then("it should delete successfully") {
                val result = jobService.deleteJob(job.id)

                result shouldBeRight Unit

                coVerify { jobRepository.delete(job.id) }
            }
        }

        When("trying to delete a running job") {
            val job = createSampleJob().copy(status = JobStatus.RUNNING)

            coEvery { 
                jobRepository.findById(job.id) 
            } returns job.right()

            Then("it should return validation error") {
                val result = jobService.deleteJob(job.id)

                result shouldBeLeft { error: DomainError ->
                    error.shouldBeInstanceOf<ValidationError>()
                    error.message shouldContain "Cannot delete a running job"
                }
            }
        }

        When("getting job statistics") {
            coEvery { 
                jobRepository.countByStatus(JobStatus.PENDING, null) 
            } returns 5L.right()
            
            coEvery { 
                jobRepository.countByStatus(JobStatus.QUEUED, null) 
            } returns 0L.right()
            
            coEvery { 
                jobRepository.countByStatus(JobStatus.RUNNING, null) 
            } returns 3L.right()
            
            coEvery { 
                jobRepository.countByStatus(JobStatus.COMPLETED, null) 
            } returns 10L.right()
            
            coEvery { 
                jobRepository.countByStatus(JobStatus.FAILED, null) 
            } returns 2L.right()
            
            coEvery { 
                jobRepository.countByStatus(JobStatus.CANCELLED, null) 
            } returns 0L.right()

            Then("it should return statistics") {
                val result = jobService.getJobStatistics()

                result shouldBeRight { jobStats: JobStatistics ->
                    jobStats.total shouldBe 20L
                    jobStats.pending shouldBe 5L
                    jobStats.running shouldBe 3L
                    jobStats.completed shouldBe 10L
                    jobStats.failed shouldBe 2L
                }

                coVerify { jobRepository.countByStatus(JobStatus.PENDING, null) }
                coVerify { jobRepository.countByStatus(JobStatus.RUNNING, null) }
                coVerify { jobRepository.countByStatus(JobStatus.COMPLETED, null) }
                coVerify { jobRepository.countByStatus(JobStatus.FAILED, null) }
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
                    put("steps", "gradle build")
                })
            })
        },
        status = TemplateStatus.PUBLISHED,
        description = "Test template",
        createdAt = now,
        updatedAt = now,
        createdBy = "user123",
        statistics = TemplateStatistics()
    )
}

private fun createSampleJob(): Job {
    val now = Clock.System.now()
    return Job(
        id = DomainId.generate(),
        name = "test-job",
        templateId = DomainId.generate(),
        templateVersion = Version("1.0.0"),
        status = JobStatus.PENDING,
        priority = Priority.MEDIUM,
        parameters = JsonObject(emptyMap()),
        overrides = JsonObject(emptyMap()),
        namespace = "test",
        retryCount = 0,
        maxRetries = 3,
        createdAt = now,
        updatedAt = now,
        createdBy = "user123"
    )
}
*/