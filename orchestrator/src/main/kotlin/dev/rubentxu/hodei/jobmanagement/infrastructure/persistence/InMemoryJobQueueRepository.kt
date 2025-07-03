package dev.rubentxu.hodei.jobmanagement.infrastructure.persistence

import arrow.core.Either
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.jobmanagement.domain.entities.*
import dev.rubentxu.hodei.jobmanagement.domain.repositories.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class InMemoryJobQueueRepository : JobQueueRepository {
    private val queues = mutableMapOf<DomainId, JobQueue>()
    private val queuesByName = mutableMapOf<String, JobQueue>()
    private val mutex = Mutex()

    override suspend fun save(queue: JobQueue): Either<RepositoryError, JobQueue> = mutex.withLock {
        queues[queue.id] = queue
        queuesByName[queue.name] = queue
        queue.right()
    }

    override suspend fun findById(id: DomainId): Either<RepositoryError, JobQueue?> = mutex.withLock {
        queues[id].right()
    }

    override suspend fun findByName(name: String): Either<RepositoryError, JobQueue?> = mutex.withLock {
        queuesByName[name].right()
    }

    override suspend fun findByResourcePool(resourcePoolId: DomainId): Either<RepositoryError, List<JobQueue>> = mutex.withLock {
        queues.values.filter { it.resourcePoolId == resourcePoolId }.right()
    }

    override suspend fun findAll(): Either<RepositoryError, List<JobQueue>> = mutex.withLock {
        queues.values.toList().right()
    }

    override suspend fun findActive(): Either<RepositoryError, List<JobQueue>> = mutex.withLock {
        queues.values.filter { it.isActive }.right()
    }

    override suspend fun delete(id: DomainId): Either<RepositoryError, Unit> = mutex.withLock {
        queues[id]?.let { queue ->
            queues.remove(id)
            queuesByName.remove(queue.name)
        }
        Unit.right()
    }

    fun clear() {
        queues.clear()
        queuesByName.clear()
    }
}

class InMemoryQueuedJobRepository : QueuedJobRepository {
    private val queuedJobs = mutableMapOf<DomainId, QueuedJob>()
    private val jobsByJobId = mutableMapOf<DomainId, QueuedJob>()
    private val mutex = Mutex()

    override suspend fun save(queuedJob: QueuedJob): Either<RepositoryError, QueuedJob> = mutex.withLock {
        queuedJobs[queuedJob.id] = queuedJob
        jobsByJobId[queuedJob.jobId] = queuedJob
        queuedJob.right()
    }

    override suspend fun findById(id: DomainId): Either<RepositoryError, QueuedJob?> = mutex.withLock {
        queuedJobs[id].right()
    }

    override suspend fun findByJobId(jobId: DomainId): Either<RepositoryError, QueuedJob?> = mutex.withLock {
        jobsByJobId[jobId].right()
    }

    override suspend fun findByQueue(queueId: DomainId): Either<RepositoryError, List<QueuedJob>> = mutex.withLock {
        queuedJobs.values.filter { it.queueId == queueId }.right()
    }

    override suspend fun findByStatus(status: QueuedJobStatus): Either<RepositoryError, List<QueuedJob>> = mutex.withLock {
        queuedJobs.values.filter { it.status == status }.right()
    }

    override suspend fun findReadyJobs(): Either<RepositoryError, List<QueuedJob>> = mutex.withLock {
        queuedJobs.values.filter { it.isReady() }.right()
    }

    override suspend fun findRetryableJobs(): Either<RepositoryError, List<QueuedJob>> = mutex.withLock {
        queuedJobs.values.filter { it.canRetry() }.right()
    }

    override suspend fun countByQueueAndStatus(queueId: DomainId, status: QueuedJobStatus): Either<RepositoryError, Int> = mutex.withLock {
        queuedJobs.values.count { it.queueId == queueId && it.status == status }.right()
    }

    override suspend fun delete(id: DomainId): Either<RepositoryError, Unit> = mutex.withLock {
        queuedJobs[id]?.let { queuedJob ->
            queuedJobs.remove(id)
            jobsByJobId.remove(queuedJob.jobId)
        }
        Unit.right()
    }

    fun clear() {
        queuedJobs.clear()
        jobsByJobId.clear()
    }
}