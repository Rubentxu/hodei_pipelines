package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus
import kotlinx.coroutines.flow.Flow

/**
 * Port (Output) - Job Repository for data persistence
 * Follows hexagonal architecture principles - domain defines the contract
 */
interface JobRepository {
    suspend fun save(job: Job): Result<JobId> // Result<JobId> instead of Job
    suspend fun findById(id: JobId): Result<Job> // Result<Job> instead of Job?
    suspend fun findByStatus(status: JobStatus): Result<List<Job>>
    suspend fun findAll(): Flow<Job>
    suspend fun delete(id: JobId): Boolean
    suspend fun existsById(id: JobId): Boolean
}