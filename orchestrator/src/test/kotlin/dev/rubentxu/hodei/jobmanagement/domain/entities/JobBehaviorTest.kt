package dev.rubentxu.hodei.jobmanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.primitives.Priority
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

class JobBehaviorTest : BehaviorSpec({
    
    Given("a job creation scenario") {
        When("creating a job from template with valid parameters") {
            val templateId = DomainId.generate()
            val templateVersion = Version("1.0.0")
            val now = Clock.System.now()
            
            val job = Job(
                id = DomainId.generate(),
                name = "test-job",
                templateId = templateId,
                templateVersion = templateVersion,
                status = JobStatus.PENDING,
                priority = Priority.NORMAL,
                createdAt = now,
                updatedAt = now,
                createdBy = "test-user"
            )
            
            Then("it should be created successfully") {
                job.name shouldBe "test-job"
                job.templateId shouldBe templateId
                job.templateVersion shouldBe templateVersion
                job.status shouldBe JobStatus.PENDING
                job.priority shouldBe Priority.NORMAL
                job.isFromTemplate shouldBe true
                job.isAdHoc shouldBe false
            }
        }
        
        When("creating an ad-hoc job with inline spec") {
            val spec = JsonObject(mapOf("command" to kotlinx.serialization.json.JsonPrimitive("echo hello")))
            val now = Clock.System.now()
            
            val job = Job(
                id = DomainId.generate(),
                name = "adhoc-job",
                status = JobStatus.PENDING,
                priority = Priority.HIGH,
                spec = spec,
                createdAt = now,
                updatedAt = now,
                createdBy = "test-user"
            )
            
            Then("it should be created as ad-hoc job") {
                job.name shouldBe "adhoc-job"
                job.templateId shouldBe null
                job.spec shouldBe spec
                job.isFromTemplate shouldBe false
                job.isAdHoc shouldBe true
            }
        }
        
        When("creating a job without template or spec") {
            Then("it should throw validation error") {
                shouldThrow<IllegalArgumentException> {
                    Job(
                        id = DomainId.generate(),
                        name = "invalid-job",
                        status = JobStatus.PENDING,
                        priority = Priority.NORMAL,
                        createdAt = Clock.System.now(),
                        updatedAt = Clock.System.now(),
                        createdBy = "test-user"
                    )
                }.message shouldBe "Job must have either a template reference or an inline spec"
            }
        }
    }
    
    Given("a job status transition scenario") {
        val job = createValidJob()
        
        When("transitioning from PENDING to QUEUED") {
            val updatedJob = job.updateStatus(JobStatus.QUEUED)
            
            Then("status should be updated successfully") {
                updatedJob.status shouldBe JobStatus.QUEUED
                updatedJob.updatedAt shouldNotBe job.updatedAt
                updatedJob.completedAt shouldBe null
            }
        }
        
        When("transitioning from RUNNING to COMPLETED") {
            val runningJob = job.updateStatus(JobStatus.QUEUED).updateStatus(JobStatus.RUNNING)
            val completedJob = runningJob.updateStatus(JobStatus.COMPLETED)
            
            Then("job should be marked as completed with timestamp") {
                completedJob.status shouldBe JobStatus.COMPLETED
                completedJob.completedAt shouldNotBe null
                completedJob.status.isTerminal shouldBe true
            }
        }
        
        When("attempting invalid status transition") {
            Then("it should throw validation error") {
                shouldThrow<IllegalArgumentException> {
                    job.updateStatus(JobStatus.RUNNING) // PENDING -> RUNNING is invalid
                }.message shouldBe "Cannot transition from PENDING to RUNNING"
            }
        }
    }
    
    Given("a job retry scenario") {
        When("a failed job has retries remaining") {
            val failedJob = createValidJob()
                .updateStatus(JobStatus.QUEUED)
                .updateStatus(JobStatus.RUNNING)
                .updateStatus(JobStatus.FAILED)
            
            val retriedJob = failedJob.retry()
            
            Then("it should be queued again with incremented retry count") {
                retriedJob.status shouldBe JobStatus.QUEUED
                retriedJob.retryCount shouldBe failedJob.retryCount + 1
                retriedJob.completedAt shouldBe null
                retriedJob.retryCount shouldBe 1
                (retriedJob.retryCount < retriedJob.maxRetries) shouldBe true
            }
        }
        
        When("a failed job has exhausted retries") {
            val exhaustedJob = createValidJob().copy(retryCount = 3, maxRetries = 3)
                .updateStatus(JobStatus.QUEUED)
                .updateStatus(JobStatus.RUNNING)
                .updateStatus(JobStatus.FAILED)
            
            Then("it should not be retryable") {
                exhaustedJob.canRetry() shouldBe false
                shouldThrow<IllegalArgumentException> {
                    exhaustedJob.retry()
                }.message shouldBe "Job cannot be retried"
            }
        }
    }
    
    Given("a job execution assignment scenario") {
        val job = createValidJob()
        val executionId = DomainId.generate()
        
        When("assigning an execution to a job") {
            val assignedJob = job.assignExecution(executionId)
            
            Then("execution should be linked to job") {
                assignedJob.latestExecutionId shouldBe executionId
                assignedJob.updatedAt shouldNotBe job.updatedAt
            }
        }
    }
})

// Property-based tests for Job validation
class JobPropertyTest : BehaviorSpec({
    
    Given("property-based job validation") {
        When("testing job name validation") {
            Then("job names should not be blank") {
                checkAll(Arb.string(0..10)) { name ->
                    if (name.isBlank()) {
                        shouldThrow<IllegalArgumentException> {
                            createValidJob().copy(name = name)
                        }
                    }
                }
            }
        }
        
        When("testing retry count validation") {
            Then("retry counts should be non-negative") {
                checkAll(Arb.int(Int.MIN_VALUE..-1)) { negativeRetryCount ->
                    shouldThrow<IllegalArgumentException> {
                        createValidJob().copy(retryCount = negativeRetryCount)
                    }
                }
            }
        }
        
        When("testing max retries validation") {
            Then("max retries should be non-negative") {
                checkAll(Arb.int(Int.MIN_VALUE..-1)) { negativeMaxRetries ->
                    shouldThrow<IllegalArgumentException> {
                        createValidJob().copy(maxRetries = negativeMaxRetries)
                    }
                }
            }
        }
        
        When("testing namespace validation") {
            Then("namespace should not be blank") {
                checkAll(Arb.string(0..5).filter { it.isBlank() }) { blankNamespace ->
                    shouldThrow<IllegalArgumentException> {
                        createValidJob().copy(namespace = blankNamespace)
                    }
                }
            }
        }
    }
})

private fun createValidJob(): Job {
    val now = Clock.System.now()
    return Job(
        id = DomainId.generate(),
        name = "test-job",
        templateId = DomainId.generate(),
        templateVersion = Version("1.0.0"),
        status = JobStatus.PENDING,
        priority = Priority.NORMAL,
        createdAt = now,
        updatedAt = now,
        createdBy = "test-user"
    )
}