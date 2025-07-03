package dev.rubentxu.hodei.jobmanagement.application.services

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.*
import dev.rubentxu.hodei.shared.domain.primitives.Version
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

/**
 * Simplified JobService for API operations using JobForAPI model
 */
class JobAPIService {
    
    private val jobs = ConcurrentHashMap<String, JobForAPI>()
    
    suspend fun findAll(): Either<DomainError, List<JobForAPI>> {
        return jobs.values.toList().right()
    }
    
    suspend fun findById(id: DomainId): Either<DomainError, JobForAPI> {
        val job = jobs[id.value]
        return if (job != null) {
            job.right()
        } else {
            NotFoundError(message = "Job not found", entityType = "Job", entityId = id.value).left()
        }
    }
    
    suspend fun createAdHocJob(
        name: String,
        description: String? = null,
        content: JobContent,
        parameters: Map<String, String> = emptyMap(),
        poolId: DomainId? = null,
        priority: Int = 50,
        retryPolicy: RetryPolicy? = null,
        labels: Map<String, String> = emptyMap(),
        createdBy: String = "api-user",
        scheduledAt: kotlinx.datetime.Instant? = null
    ): Either<DomainError, JobForAPI> {
        if (name.isBlank()) {
            return ValidationError(message = "Job name cannot be blank").left()
        }
        
        val now = Clock.System.now()
        val job = JobForAPI(
            id = DomainId.generate(),
            name = name,
            description = description,
            status = JobStatus.PENDING,
            content = content,
            parameters = parameters,
            poolId = poolId,
            priority = priority,
            retryPolicy = retryPolicy,
            labels = labels,
            metadata = Metadata(
                createdAt = now,
                updatedAt = now,
                createdBy = createdBy
            ),
            scheduledAt = scheduledAt
        )
        
        jobs[job.id.value] = job
        return job.right()
    }
    
    suspend fun createJobFromTemplate(
        templateId: DomainId,
        templateVersion: Version? = null,
        name: String,
        description: String? = null,
        parameters: Map<String, String> = emptyMap(),
        poolId: DomainId? = null,
        priority: Int = 50,
        retryPolicy: RetryPolicy? = null,
        labels: Map<String, String> = emptyMap(),
        createdBy: String = "api-user",
        scheduledAt: kotlinx.datetime.Instant? = null
    ): Either<DomainError, JobForAPI> {
        if (name.isBlank()) {
            return ValidationError(message = "Job name cannot be blank").left()
        }
        
        // For now, create a simple shell job from template
        val now = Clock.System.now()
        val job = JobForAPI(
            id = DomainId.generate(),
            name = name,
            description = description,
            status = JobStatus.PENDING,
            templateId = templateId,
            templateVersion = templateVersion,
            content = JobContent.ShellCommands(listOf("echo 'Template job: $name'")),
            parameters = parameters,
            poolId = poolId,
            priority = priority,
            retryPolicy = retryPolicy,
            labels = labels,
            metadata = Metadata(
                createdAt = now,
                updatedAt = now,
                createdBy = createdBy
            ),
            scheduledAt = scheduledAt
        )
        
        jobs[job.id.value] = job
        return job.right()
    }
    
    suspend fun cancel(
        id: DomainId,
        reason: String? = null,
        force: Boolean = false
    ): Either<DomainError, JobForAPI> {
        val job = jobs[id.value]
        return if (job != null) {
            if (job.status in listOf(JobStatus.COMPLETED, JobStatus.FAILED, JobStatus.CANCELLED)) {
                BusinessRuleError(code = "BUSINESS_LOGIC_ERROR", message = "Job cannot be cancelled in current state").left()
            } else {
                val cancelledJob = job.cancel(reason ?: "User requested")
                jobs[id.value] = cancelledJob
                cancelledJob.right()
            }
        } else {
            NotFoundError(message = "Job not found", entityType = "Job", entityId = "unknown").left()
        }
    }
    
    suspend fun retry(id: DomainId): Either<DomainError, JobForAPI> {
        val job = jobs[id.value]
        return if (job != null) {
            if (job.status != JobStatus.FAILED) {
                BusinessRuleError(code = "BUSINESS_LOGIC_ERROR", message = "Job can only be retried if it failed").left()
            } else {
                val retriedJob = job.copy(
                    status = JobStatus.QUEUED,
                    metadata = job.metadata.copy(updatedAt = Clock.System.now())
                )
                jobs[id.value] = retriedJob
                retriedJob.right()
            }
        } else {
            NotFoundError(message = "Job not found", entityType = "Job", entityId = "unknown").left()
        }
    }
}