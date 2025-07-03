package dev.rubentxu.hodei.jobmanagement.domain.repositories

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.JobQueue
import dev.rubentxu.hodei.jobmanagement.domain.entities.QueuedJob
import dev.rubentxu.hodei.jobmanagement.domain.entities.QueuedJobStatus

interface JobQueueRepository {
    suspend fun save(queue: JobQueue): Either<RepositoryError, JobQueue>
    suspend fun findById(id: DomainId): Either<RepositoryError, JobQueue?>
    suspend fun findByName(name: String): Either<RepositoryError, JobQueue?>
    suspend fun findByResourcePool(resourcePoolId: DomainId): Either<RepositoryError, List<JobQueue>>
    suspend fun findAll(): Either<RepositoryError, List<JobQueue>>
    suspend fun findActive(): Either<RepositoryError, List<JobQueue>>
    suspend fun delete(id: DomainId): Either<RepositoryError, Unit>
}

interface QueuedJobRepository {
    suspend fun save(queuedJob: QueuedJob): Either<RepositoryError, QueuedJob>
    suspend fun findById(id: DomainId): Either<RepositoryError, QueuedJob?>
    suspend fun findByJobId(jobId: DomainId): Either<RepositoryError, QueuedJob?>
    suspend fun findByQueue(queueId: DomainId): Either<RepositoryError, List<QueuedJob>>
    suspend fun findByStatus(status: QueuedJobStatus): Either<RepositoryError, List<QueuedJob>>
    suspend fun findReadyJobs(): Either<RepositoryError, List<QueuedJob>>
    suspend fun findRetryableJobs(): Either<RepositoryError, List<QueuedJob>>
    suspend fun countByQueueAndStatus(queueId: DomainId, status: QueuedJobStatus): Either<RepositoryError, Int>
    suspend fun delete(id: DomainId): Either<RepositoryError, Unit>
}