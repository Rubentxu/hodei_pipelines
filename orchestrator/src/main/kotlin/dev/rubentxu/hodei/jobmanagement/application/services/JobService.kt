package dev.rubentxu.hodei.jobmanagement.application.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.shared.domain.errors.NotFoundError
import dev.rubentxu.hodei.shared.domain.errors.ValidationError
import dev.rubentxu.hodei.shared.domain.errors.ConflictError
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonObject

private val logger = KotlinLogging.logger {}

class JobService(
    private val jobRepository: JobRepository
) {
    
    
    suspend fun createAdHocJob(
        name: String,
        spec: JsonObject,
        namespace: String = "default",
        parameters: JsonObject = JsonObject(emptyMap()),
        priority: Priority = Priority.MEDIUM,
        scheduledAt: kotlinx.datetime.Instant? = null,
        createdBy: String = "system"
    ): Either<DomainError, Job> {
        logger.debug { "Creating ad-hoc job: $name" }
        
        // Check if job with same name already exists
        return jobRepository.existsByName(name, namespace).flatMap { exists ->
            if (exists) {
                ConflictError(
                    message = "Job with name '$name' already exists in namespace '$namespace'"
                ).left()
            } else {
                val now = Clock.System.now()
                val job = Job(
                    id = DomainId.generate(),
                    name = name,
                    templateId = null,
                    templateVersion = null,
                    status = JobStatus.PENDING,
                    priority = priority,
                    parameters = parameters,
                    overrides = JsonObject(emptyMap()),
                    spec = spec,
                    namespace = namespace,
                    retryCount = 0,
                    maxRetries = 3,
                    latestExecutionId = null,
                    createdAt = now,
                    updatedAt = now,
                    createdBy = createdBy,
                    scheduledAt = scheduledAt,
                    completedAt = null
                )
                
                jobRepository.save(job)
            }
        }
    }
    
    suspend fun findById(jobId: DomainId): Either<DomainError, Job> {
        logger.debug { "Finding job with id: $jobId" }
        
        return jobRepository.findById(jobId).flatMap { job ->
            if (job == null) {
                NotFoundError(
                    message = "Job with id $jobId not found",
                    entityType = "Job",
                    entityId = jobId.value
                ).left()
            } else {
                job.right()
            }
        }
    }
    
    suspend fun findAll(): Either<DomainError, List<Job>> {
        logger.debug { "Finding all jobs" }
        return jobRepository.findAll()
    }
    
    
    suspend fun getJob(id: DomainId): Either<DomainError, Job?> =
        jobRepository.findById(id)
    
    suspend fun updateJobStatus(
        id: DomainId, 
        newStatus: JobStatus
    ): Either<DomainError, Job> {
        return jobRepository.findById(id).flatMap { job ->
            if (job == null) {
                NotFoundError(
                    message = "Job with id $id not found",
                    entityType = "Job",
                    entityId = id.value
                ).left()
            } else {
                try {
                    val updatedJob = job.updateStatus(newStatus)
                    jobRepository.update(updatedJob)
                } catch (e: IllegalArgumentException) {
                    ValidationError(message = e.message ?: "Invalid status transition").left()
                }
            }
        }
    }
    
    suspend fun cancelJob(id: DomainId): Either<DomainError, Job> =
        updateJobStatus(id, JobStatus.CANCELLED)
    
    suspend fun retryJob(id: DomainId): Either<DomainError, Job> {
        return jobRepository.findById(id).flatMap { job ->
            if (job == null) {
                NotFoundError(
                    message = "Job with id $id not found",
                    entityType = "Job",
                    entityId = id.value
                ).left()
            } else if (!job.canRetry()) {
                ValidationError(
                    message = "Job cannot be retried. Status: ${job.status}, Retries: ${job.retryCount}/${job.maxRetries}"
                ).left()
            } else {
                val retriedJob = job.retry()
                jobRepository.update(retriedJob)
            }
        }
    }
    
    suspend fun deleteJob(id: DomainId): Either<DomainError, Unit> {
        return jobRepository.findById(id).flatMap { job ->
            if (job == null) {
                NotFoundError(
                    message = "Job with id $id not found",
                    entityType = "Job",
                    entityId = id.value
                ).left()
            } else if (job.status == JobStatus.RUNNING) {
                ValidationError(
                    message = "Cannot delete a running job"
                ).left()
            } else {
                jobRepository.delete(id)
            }
        }
    }
    
    fun listJobs(
        page: Int = 1,
        pageSize: Int = 20,
        status: JobStatus? = null,
        namespace: String? = null
    ): Flow<Job> = jobRepository.list(page, pageSize, status, namespace)
    
    suspend fun getJobsByTemplate(templateId: DomainId): Flow<Job> =
        jobRepository.findByTemplateId(templateId)
    
    suspend fun getJobStatistics(namespace: String? = null): Either<DomainError, JobStatistics> {
        val statuses = JobStatus.values()
        val counts = mutableMapOf<JobStatus, Long>()
        
        statuses.forEach { status ->
            jobRepository.countByStatus(status, namespace).fold(
                { return it.left() },
                { count -> counts[status] = count }
            )
        }
        
        return JobStatistics(
            total = counts.values.sum(),
            pending = counts[JobStatus.PENDING] ?: 0,
            queued = counts[JobStatus.QUEUED] ?: 0,
            running = counts[JobStatus.RUNNING] ?: 0,
            completed = counts[JobStatus.COMPLETED] ?: 0,
            failed = counts[JobStatus.FAILED] ?: 0,
            cancelled = counts[JobStatus.CANCELLED] ?: 0
        ).right()
    }
}

data class JobStatistics(
    val total: Long,
    val pending: Long,
    val queued: Long,
    val running: Long,
    val completed: Long,
    val failed: Long,
    val cancelled: Long
)