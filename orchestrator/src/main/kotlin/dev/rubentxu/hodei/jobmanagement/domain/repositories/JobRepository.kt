package dev.rubentxu.hodei.jobmanagement.domain.repositories

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.Job
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobStatus
import kotlinx.coroutines.flow.Flow

interface JobRepository {
    suspend fun save(job: Job): Either<DomainError, Job>
    suspend fun findById(id: DomainId): Either<DomainError, Job?>
    suspend fun findByName(name: String, namespace: String = "default"): Either<DomainError, Job?>
    suspend fun findAll(): Either<DomainError, List<Job>>
    suspend fun update(job: Job): Either<DomainError, Job>
    suspend fun delete(id: DomainId): Either<DomainError, Unit>
    fun list(
        page: Int = 1,
        pageSize: Int = 20,
        status: JobStatus? = null,
        namespace: String? = null
    ): Flow<Job>
    suspend fun existsByName(name: String, namespace: String = "default"): Either<DomainError, Boolean>
    suspend fun countByStatus(status: JobStatus, namespace: String? = null): Either<DomainError, Long>
    fun findByTemplateId(templateId: DomainId): Flow<Job>
}