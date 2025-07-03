package dev.rubentxu.hodei.jobmanagement.infrastructure.persistence

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import dev.rubentxu.hodei.jobmanagement.domain.repositories.JobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryJobRepository : JobRepository {
    private val jobs = mutableMapOf<DomainId, Job>()
    private val mutex = Mutex()
    
    override suspend fun save(job: Job): Either<DomainError, Job> = mutex.withLock {
        jobs[job.id] = job
        job.right()
    }
    
    override suspend fun findById(id: DomainId): Either<DomainError, Job?> = mutex.withLock {
        jobs[id].right()
    }
    
    override suspend fun findByName(name: String, namespace: String): Either<DomainError, Job?> = mutex.withLock {
        jobs.values.find { it.name == name && it.namespace == namespace }.right()
    }
    
    override suspend fun findAll(): Either<DomainError, List<Job>> = mutex.withLock {
        jobs.values.toList().right()
    }
    
    override suspend fun update(job: Job): Either<DomainError, Job> = mutex.withLock {
        if (jobs.containsKey(job.id)) {
            jobs[job.id] = job
            job.right()
        } else {
            NotFoundError(
                message = "Job with id ${job.id} not found",
                entityType = "Job",
                entityId = job.id.value
            ).left()
        }
    }
    
    override suspend fun delete(id: DomainId): Either<DomainError, Unit> = mutex.withLock {
        if (jobs.containsKey(id)) {
            jobs.remove(id)
            Unit.right()
        } else {
            NotFoundError(
                message = "Job with id $id not found",
                entityType = "Job",
                entityId = id.value
            ).left()
        }
    }
    
    override fun list(
        page: Int,
        pageSize: Int,
        status: JobStatus?,
        namespace: String?
    ): Flow<Job> {
        val allJobs = jobs.values.asSequence()
        
        val filteredJobs = allJobs
            .filter { job -> status == null || job.status == status }
            .filter { job -> namespace == null || job.namespace == namespace }
            .sortedByDescending { it.createdAt }
        
        val paginatedJobs = filteredJobs
            .drop((page - 1) * pageSize)
            .take(pageSize)
            .toList()
        
        return paginatedJobs.asFlow()
    }
    
    override suspend fun existsByName(name: String, namespace: String): Either<DomainError, Boolean> = mutex.withLock {
        jobs.values.any { it.name == name && it.namespace == namespace }.right()
    }
    
    override suspend fun countByStatus(status: JobStatus, namespace: String?): Either<DomainError, Long> = mutex.withLock {
        jobs.values
            .filter { job -> job.status == status }
            .filter { job -> namespace == null || job.namespace == namespace }
            .count()
            .toLong()
            .right()
    }
    
    override fun findByTemplateId(templateId: DomainId): Flow<Job> {
        return jobs.values
            .filter { it.templateId == templateId }
            .sortedByDescending { it.createdAt }
            .asFlow()
    }
}