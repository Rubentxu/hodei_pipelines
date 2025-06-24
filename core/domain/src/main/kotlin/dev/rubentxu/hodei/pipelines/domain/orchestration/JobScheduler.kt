package dev.rubentxu.hodei.pipelines.domain.orchestration

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.Worker
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import java.time.Duration
import java.time.Instant
import java.util.*

/**
 * Job Priority levels
 */
enum class JobPriority(val value: Int) {
    CRITICAL(1000),  // System-critical jobs (security patches, critical fixes)
    HIGH(800),       // High-priority business jobs (releases, hotfixes)
    NORMAL(500),     // Normal priority jobs (regular builds, tests)
    LOW(200),        // Low priority jobs (cleanup, maintenance)
    BACKGROUND(100); // Background jobs (analytics, reporting)
    
    companion object {
        fun fromValue(value: Int): JobPriority {
            return values().find { it.value <= value } ?: BACKGROUND
        }
    }
}

/**
 * Scheduling Strategy for job assignment
 */
enum class SchedulingStrategy {
    FIFO,                    // First In, First Out
    PRIORITY_BASED,          // Based on job priority
    SHORTEST_JOB_FIRST,     // Shortest estimated time first
    FAIR_SHARE,             // Fair distribution across users/projects
    RESOURCE_OPTIMIZED,     // Optimize for resource utilization
    DEADLINE_AWARE,         // Consider job deadlines
    DEPENDENCY_AWARE        // Consider job dependencies
}

/**
 * Job Queue with priority and scheduling capabilities
 */
class JobQueue(
    private val schedulingStrategy: SchedulingStrategy = SchedulingStrategy.PRIORITY_BASED,
    private val maxQueueSize: Int = 1000
) {
    private val queue = PriorityQueue<QueuedJob>(compareByDescending { it.effectivePriority })
    private val queuedJobs = mutableMapOf<JobId, QueuedJob>()
    private val schedulingMetrics = SchedulingMetrics()
    
    /**
     * Add job to queue
     */
    fun enqueue(job: Job, priority: JobPriority, requirements: WorkerRequirements, deadline: Instant? = null): QueueResult {
        if (queue.size >= maxQueueSize) {
            return QueueResult.QueueFull(maxQueueSize)
        }
        
        if (queuedJobs.containsKey(job.id)) {
            return QueueResult.AlreadyQueued(job.id)
        }
        
        val queuedJob = QueuedJob(
            job = job,
            priority = priority,
            requirements = requirements,
            queuedAt = Instant.now(),
            deadline = deadline,
            estimatedDuration = estimateJobDuration(job),
            userId = extractUserId(job),
            projectId = extractProjectId(job)
        )
        
        queue.offer(queuedJob)
        queuedJobs[job.id] = queuedJob
        schedulingMetrics.recordJobQueued(queuedJob)
        
        return QueueResult.Success(queue.size)
    }
    
    /**
     * Remove job from queue
     */
    fun dequeue(jobId: JobId): QueuedJob? {
        val queuedJob = queuedJobs.remove(jobId) ?: return null
        queue.remove(queuedJob)
        schedulingMetrics.recordJobDequeued(queuedJob)
        return queuedJob
    }
    
    /**
     * Get next job based on scheduling strategy and available workers
     */
    fun getNextJob(availableWorkers: List<Worker>): QueuedJob? {
        if (queue.isEmpty() || availableWorkers.isEmpty()) {
            return null
        }
        
        return when (schedulingStrategy) {
            SchedulingStrategy.FIFO -> getNextFIFO()
            SchedulingStrategy.PRIORITY_BASED -> getNextByPriority(availableWorkers)
            SchedulingStrategy.SHORTEST_JOB_FIRST -> getNextShortestJob(availableWorkers)
            SchedulingStrategy.FAIR_SHARE -> getNextFairShare(availableWorkers)
            SchedulingStrategy.RESOURCE_OPTIMIZED -> getNextResourceOptimized(availableWorkers)
            SchedulingStrategy.DEADLINE_AWARE -> getNextDeadlineAware(availableWorkers)
            SchedulingStrategy.DEPENDENCY_AWARE -> getNextDependencyAware(availableWorkers)
        }
    }
    
    /**
     * Peek at next jobs without removing them
     */
    fun peekNext(count: Int): List<QueuedJob> {
        return queue.take(count)
    }
    
    /**
     * Get queue statistics
     */
    fun getQueueStats(): QueueStats {
        val waitTimes = queuedJobs.values.map { Duration.between(it.queuedAt, Instant.now()) }
        
        return QueueStats(
            totalJobs = queue.size,
            priorityBreakdown = queue.groupBy { it.priority }.mapValues { it.value.size },
            averageWaitTime = if (waitTimes.isNotEmpty()) Duration.ofMillis(waitTimes.map { it.toMillis() }.average().toLong()) else Duration.ZERO,
            oldestJob = queue.minByOrNull { it.queuedAt },
            criticalJobsCount = queue.count { it.priority == JobPriority.CRITICAL },
            expiredJobsCount = queue.count { it.isExpired() }
        )
    }
    
    private fun getNextFIFO(): QueuedJob? {
        return queue.minByOrNull { it.queuedAt }
    }
    
    private fun getNextByPriority(availableWorkers: List<Worker>): QueuedJob? {
        return queue.firstOrNull { queuedJob ->
            availableWorkers.any { worker -> canWorkerExecuteJob(worker, queuedJob) }
        }
    }
    
    private fun getNextShortestJob(availableWorkers: List<Worker>): QueuedJob? {
        return queue
            .filter { queuedJob -> availableWorkers.any { worker -> canWorkerExecuteJob(worker, queuedJob) } }
            .minByOrNull { it.estimatedDuration }
    }
    
    private fun getNextFairShare(availableWorkers: List<Worker>): QueuedJob? {
        val userJobCounts = schedulingMetrics.getUserJobCounts()
        
        return queue
            .filter { queuedJob -> availableWorkers.any { worker -> canWorkerExecuteJob(worker, queuedJob) } }
            .minByOrNull { userJobCounts.getOrDefault(it.userId, 0) }
    }
    
    private fun getNextResourceOptimized(availableWorkers: List<Worker>): QueuedJob? {
        // Find job that best matches available worker capabilities
        return availableWorkers.mapNotNull { worker ->
            queue.filter { canWorkerExecuteJob(worker, it) }
                .maxByOrNull { calculateResourceMatch(worker, it) }
        }.maxByOrNull { it.effectivePriority }
    }
    
    private fun getNextDeadlineAware(availableWorkers: List<Worker>): QueuedJob? {
        val now = Instant.now()
        
        return queue
            .filter { queuedJob -> availableWorkers.any { worker -> canWorkerExecuteJob(worker, queuedJob) } }
            .sortedWith(compareBy<QueuedJob> { 
                it.deadline?.let { deadline -> Duration.between(now, deadline) } ?: Duration.ofDays(365)
            }.thenByDescending { it.priority })
            .firstOrNull()
    }
    
    private fun getNextDependencyAware(availableWorkers: List<Worker>): QueuedJob? {
        // For now, same as priority-based - can be enhanced with dependency graph
        return getNextByPriority(availableWorkers)
    }
    
    private fun canWorkerExecuteJob(worker: Worker, queuedJob: QueuedJob): Boolean {
        // Check if worker has required languages
        val hasLanguages = queuedJob.requirements.requiredLanguages.all { language ->
            worker.capabilities.languages.contains(language)
        }
        
        // Check if worker has required tools
        val hasTools = queuedJob.requirements.requiredTools.all { tool ->
            worker.capabilities.tools.contains(tool)
        }
        
        // Check if worker has required features
        val hasFeatures = queuedJob.requirements.requiredFeatures.all { feature ->
            worker.capabilities.features.contains(feature)
        }
        
        // Check if worker has required labels
        val hasLabels = queuedJob.requirements.labels.all { (key, value) ->
            worker.capabilities.matches(key, value)
        }
        
        // Check if worker meets resource requirements (simplified)
        val hasResources = true // Assume worker can handle the resources for now
        
        return hasLanguages && hasTools && hasFeatures && hasLabels && hasResources && 
               worker.status == dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY
    }
    
    private fun calculateResourceMatch(worker: Worker, queuedJob: QueuedJob): Double {
        // Calculate how well worker capabilities match job requirements
        val totalRequirements = queuedJob.requirements.requiredLanguages.size +
                              queuedJob.requirements.requiredTools.size +
                              queuedJob.requirements.requiredFeatures.size
        
        if (totalRequirements == 0) return 1.0
        
        val languageMatches = queuedJob.requirements.requiredLanguages.count { language ->
            worker.capabilities.languages.contains(language)
        }
        
        val toolMatches = queuedJob.requirements.requiredTools.count { tool ->
            worker.capabilities.tools.contains(tool)
        }
        
        val featureMatches = queuedJob.requirements.requiredFeatures.count { feature ->
            worker.capabilities.features.contains(feature)
        }
        
        val totalMatches = languageMatches + toolMatches + featureMatches
        
        return if (totalRequirements > 0) {
            totalMatches.toDouble() / totalRequirements
        } else {
            1.0
        }
    }
    
    private fun estimateJobDuration(job: Job): Duration {
        // Simple estimation - can be enhanced with ML/historical data
        return Duration.ofMinutes(10) // Default 10 minutes
    }
    
    private fun extractUserId(job: Job): String {
        return job.definition.environment["USER_ID"] ?: "unknown"
    }
    
    private fun extractProjectId(job: Job): String {
        return job.definition.environment["PROJECT_ID"] ?: "default"
    }
}

/**
 * Queued Job with scheduling metadata
 */
data class QueuedJob(
    val job: Job,
    val priority: JobPriority,
    val requirements: WorkerRequirements,
    val queuedAt: Instant,
    val deadline: Instant? = null,
    val estimatedDuration: Duration = Duration.ofMinutes(10),
    val userId: String = "unknown",
    val projectId: String = "default",
    val dependencies: List<JobId> = emptyList(),
    val retryCount: Int = 0,
    val maxRetries: Int = 3
) {
    
    /**
     * Calculate effective priority considering wait time and deadline
     */
    val effectivePriority: Double
        get() {
            var basePriority = priority.value.toDouble()
            
            // Increase priority based on wait time (aging)
            val waitTime = Duration.between(queuedAt, Instant.now())
            val agingBonus = (waitTime.toMinutes() * 0.1).coerceAtMost(100.0)
            basePriority += agingBonus
            
            // Increase priority if deadline is approaching
            deadline?.let { deadline ->
                val timeToDeadline = Duration.between(Instant.now(), deadline)
                if (timeToDeadline.isNegative) {
                    basePriority += 500 // Expired jobs get high priority
                } else if (timeToDeadline < estimatedDuration.multipliedBy(2)) {
                    basePriority += 200 // Urgent jobs get priority boost
                }
            }
            
            return basePriority
        }
    
    /**
     * Check if job has expired past its deadline
     */
    fun isExpired(): Boolean {
        return deadline?.let { Instant.now().isAfter(it) } ?: false
    }
    
    /**
     * Check if job can be retried
     */
    fun canRetry(): Boolean {
        return retryCount < maxRetries
    }
    
    /**
     * Create retry job
     */
    fun retry(): QueuedJob {
        return copy(
            retryCount = retryCount + 1,
            queuedAt = Instant.now()
        )
    }
}

/**
 * Queue operation results
 */
sealed class QueueResult {
    data class Success(val queueSize: Int) : QueueResult()
    data class QueueFull(val maxSize: Int) : QueueResult()
    data class AlreadyQueued(val jobId: JobId) : QueueResult()
    data class InvalidJob(val reason: String) : QueueResult()
}

/**
 * Queue statistics
 */
data class QueueStats(
    val totalJobs: Int,
    val priorityBreakdown: Map<JobPriority, Int>,
    val averageWaitTime: Duration,
    val oldestJob: QueuedJob?,
    val criticalJobsCount: Int,
    val expiredJobsCount: Int
)

/**
 * Scheduling metrics for monitoring and optimization
 */
class SchedulingMetrics {
    private val userJobCounts = mutableMapOf<String, Int>()
    private val projectJobCounts = mutableMapOf<String, Int>()
    private val queuedJobs = mutableListOf<QueuedJob>()
    private val completedJobs = mutableListOf<CompletedJobMetric>()
    
    fun recordJobQueued(job: QueuedJob) {
        queuedJobs.add(job)
        userJobCounts[job.userId] = userJobCounts.getOrDefault(job.userId, 0) + 1
        projectJobCounts[job.projectId] = projectJobCounts.getOrDefault(job.projectId, 0) + 1
    }
    
    fun recordJobDequeued(job: QueuedJob) {
        queuedJobs.remove(job)
        userJobCounts[job.userId] = userJobCounts.getOrDefault(job.userId, 1) - 1
        projectJobCounts[job.projectId] = projectJobCounts.getOrDefault(job.projectId, 1) - 1
    }
    
    fun recordJobCompleted(job: QueuedJob, workerId: WorkerId, duration: Duration, success: Boolean) {
        completedJobs.add(CompletedJobMetric(
            jobId = job.job.id,
            priority = job.priority,
            queueTime = Duration.between(job.queuedAt, Instant.now()),
            executionTime = duration,
            workerId = workerId,
            success = success,
            completedAt = Instant.now()
        ))
    }
    
    fun getUserJobCounts(): Map<String, Int> = userJobCounts.toMap()
    fun getProjectJobCounts(): Map<String, Int> = projectJobCounts.toMap()
    
    fun getAverageQueueTime(): Duration {
        return if (completedJobs.isNotEmpty()) {
            Duration.ofMillis(completedJobs.map { it.queueTime.toMillis() }.average().toLong())
        } else {
            Duration.ZERO
        }
    }
    
    fun getAverageExecutionTime(): Duration {
        return if (completedJobs.isNotEmpty()) {
            Duration.ofMillis(completedJobs.map { it.executionTime.toMillis() }.average().toLong())
        } else {
            Duration.ZERO
        }
    }
    
    fun getSuccessRate(): Double {
        return if (completedJobs.isNotEmpty()) {
            completedJobs.count { it.success }.toDouble() / completedJobs.size
        } else {
            0.0
        }
    }
}

/**
 * Completed job metric for analysis
 */
data class CompletedJobMetric(
    val jobId: JobId,
    val priority: JobPriority,
    val queueTime: Duration,
    val executionTime: Duration,
    val workerId: WorkerId,
    val success: Boolean,
    val completedAt: Instant
)