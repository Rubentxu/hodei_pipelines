package dev.rubentxu.hodei.pipelines.infrastructure.repository

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus
import dev.rubentxu.hodei.pipelines.port.JobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory implementation of JobRepository for MVP
 * Not suitable for production - data is lost on restart
 */
class InMemoryJobRepository : JobRepository {
    
    private val jobs = ConcurrentHashMap<String, Job>()
    
    override suspend fun save(job: Job): Result<JobId> {
        jobs[job.id.value] = job
        return Result.success(job.id)
    }
    
    override suspend fun findById(id: JobId): Result<Job> {
        val job = jobs[id.value]
        return if (job != null) {
            Result.success(job)
        } else {
            Result.failure(Exception("Job not found: ${id.value}"))
        }
    }
    
    override suspend fun findByStatus(status: JobStatus): Result<List<Job>> {
        val jobList = jobs.values.filter { it.status == status }
        return Result.success(jobList)
    }
    
    override suspend fun findAll(): Flow<Job> {
        return jobs.values.asFlow()
    }
    
    override suspend fun delete(id: JobId): Boolean {
        return jobs.remove(id.value) != null
    }
    
    override suspend fun existsById(id: JobId): Boolean {
        return jobs.containsKey(id.value)
    }
    
    // Helper methods for testing/debugging
    fun clear() {
        jobs.clear()
    }
    
    fun size(): Int = jobs.size
}