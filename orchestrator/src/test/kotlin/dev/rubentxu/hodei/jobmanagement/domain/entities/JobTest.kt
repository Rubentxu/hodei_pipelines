package dev.rubentxu.hodei.jobmanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
import dev.rubentxu.hodei.shared.domain.primitives.Version
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class JobTest : BehaviorSpec({
    
    val now = Clock.System.now()
    
    given("a Job") {
        val job = Job(
            id = DomainId.generate(),
            name = "Test Job",
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
            createdBy = "test-user"
        )
        
        `when`("updating status from PENDING to QUEUED") {
            val updatedJob = job.updateStatus(JobStatus.QUEUED)
            
            then("it should update the status") {
                updatedJob.status shouldBe JobStatus.QUEUED
                updatedJob.id shouldBe job.id
                updatedJob.name shouldBe job.name
            }
        }
        
        `when`("updating status from PENDING to RUNNING (invalid transition)") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    job.updateStatus(JobStatus.RUNNING)
                }
            }
        }
        
        `when`("assigning an execution") {
            val executionId = DomainId.generate()
            val updatedJob = job.assignExecution(executionId)
            
            then("it should assign the execution ID") {
                updatedJob.latestExecutionId shouldBe executionId
                updatedJob.id shouldBe job.id
            }
        }
        
        `when`("checking if job can be retried") {
            val failedJob = job.copy(status = JobStatus.FAILED, retryCount = 1)
            
            then("it should allow retry when under max retries") {
                failedJob.canRetry() shouldBe true
            }
            
            val exhaustedJob = failedJob.copy(retryCount = 3)
            then("it should not allow retry when at max retries") {
                exhaustedJob.canRetry() shouldBe false
            }
        }
        
        `when`("retrying a failed job") {
            val failedJob = job.copy(status = JobStatus.FAILED, retryCount = 1)
            val retriedJob = failedJob.retry()
            
            then("it should increment retry count and reset status") {
                retriedJob.status shouldBe JobStatus.QUEUED
                retriedJob.retryCount shouldBe 2
                retriedJob.completedAt shouldBe null
            }
        }
        
        `when`("checking job type") {
            val templateJob = job.copy(templateId = DomainId.generate(), spec = null)
            val adHocJob = job.copy(
                templateId = null, 
                spec = buildJsonObject { put("test", "spec") }
            )
            
            then("template job should be identified correctly") {
                templateJob.isFromTemplate shouldBe true
                templateJob.isAdHoc shouldBe false
            }
            
            then("ad-hoc job should be identified correctly") {
                adHocJob.isFromTemplate shouldBe false
                adHocJob.isAdHoc shouldBe true
            }
        }
    }
    
    given("Job validation") {
        `when`("creating a job with blank name") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    Job(
                        id = DomainId.generate(),
                        name = "",
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
                        createdBy = "test-user"
                    )
                }
            }
        }
        
        `when`("creating a job without template or spec") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    Job(
                        id = DomainId.generate(),
                        name = "Test Job",
                        templateId = null,
                        templateVersion = null,
                        status = JobStatus.PENDING,
                        priority = Priority.MEDIUM,
                        parameters = JsonObject(emptyMap()),
                        overrides = JsonObject(emptyMap()),
                        spec = null,
                        namespace = "test",
                        retryCount = 0,
                        maxRetries = 3,
                        createdAt = now,
                        updatedAt = now,
                        createdBy = "test-user"
                    )
                }
            }
        }
        
        `when`("creating a job with negative retry count") {
            then("it should throw an exception") {
                shouldThrow<IllegalArgumentException> {
                    Job(
                        id = DomainId.generate(),
                        name = "Test Job",
                        templateId = DomainId.generate(),
                        templateVersion = Version("1.0.0"),
                        status = JobStatus.PENDING,
                        priority = Priority.MEDIUM,
                        parameters = JsonObject(emptyMap()),
                        overrides = JsonObject(emptyMap()),
                        namespace = "test",
                        retryCount = -1,
                        maxRetries = 3,
                        createdAt = now,
                        updatedAt = now,
                        createdBy = "test-user"
                    )
                }
            }
        }
    }
})