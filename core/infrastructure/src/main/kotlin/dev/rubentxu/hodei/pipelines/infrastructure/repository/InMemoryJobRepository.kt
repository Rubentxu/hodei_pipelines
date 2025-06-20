package dev.rubentxu.hodei.pipelines.infrastructure.repository

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus
import dev.rubentxu.hodei.pipelines.port.JobRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import mu.KotlinLogging
import java.util.concurrent.ConcurrentHashMap

/**
 * In-Memory implementation of JobRepository for MVP
 * Not suitable for production - data is lost on restart
 */
class InMemoryJobRepository : JobRepository {

    private val logger = KotlinLogging.logger {}

    private val jobs = ConcurrentHashMap<String, Job>()
    
    override suspend fun save(job: Job): Result<JobId> {
        logger.debug { "Saving job with ID: ${job.id.value} and status: ${job.status}" }
        jobs[job.id.value] = job
        logger.trace { "Job ${job.id.value} saved successfully." }
        return Result.success(job.id)
    }
    
    override suspend fun findById(id: JobId): Result<Job> {
        logger.debug { "Finding job by ID: ${id.value}" }
        val job = jobs[id.value]
        return if (job != null) {
            logger.trace { "Job ${id.value} found." }
            Result.success(job)
        } else {
            logger.warn { "Job ${id.value} not found." }
            Result.failure(Exception("Job not found: ${id.value}"))
        }
    }
    
    override suspend fun findByStatus(status: JobStatus): Result<List<Job>> {
        logger.debug { "Finding jobs by status: $status" }
        val jobList = jobs.values.filter { it.status == status }
        logger.debug { "Found ${jobList.size} jobs with status: $status" }
        return Result.success(jobList)
    }
    
    override suspend fun findAll(): Flow<Job> {
        logger.debug { "Finding all jobs" }
        return jobs.values.asFlow()
    }
    
    override suspend fun delete(id: JobId): Boolean {
        logger.info { "Deleting job with ID: ${id.value}" }
        val removed = jobs.remove(id.value) != null
        if (removed) {
            logger.debug { "Job ${id.value} deleted successfully." }
        } else {
            logger.warn { "Job ${id.value} not found for deletion." }
        }
        return removed
    }
    
    override suspend fun existsById(id: JobId): Boolean {
        logger.debug { "Checking if job exists with ID: ${id.value}" }
        val exists = jobs.containsKey(id.value)
        logger.debug { "Job ${id.value} exists: $exists" }
        return exists
    }
    
    // Helper methods for testing/debugging
    fun clear() {
        logger.info { "Clearing all jobs from the repository" }
        jobs.clear()
    }
    
    fun size(): Int {
        val size = jobs.size
        logger.debug { "Job repository size: $size" }
        return size
    }
}