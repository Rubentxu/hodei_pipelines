package dev.rubentxu.hodei.pipelines.domain.worker

import java.time.Instant

@JvmInline
value class WorkerId(val value: String)

enum class WorkerStatus {
    IDLE,
    BUSY,
    OFFLINE
}

data class WorkerCapabilities(
    val os: String,
    val arch: String,
    val maxConcurrentJobs: Int
)

data class Worker(
    val id: WorkerId,
    val name: String,
    val capabilities: WorkerCapabilities,
    val status: WorkerStatus = WorkerStatus.IDLE,
    val activeJobs: Int = 0,
    val createdAt: Instant = Instant.now(),
    val lastHeartbeat: Instant = Instant.now()
) {
    
    fun updateHeartbeat(): Worker {
        return copy(lastHeartbeat = Instant.now())
    }
    
    fun assignJob(): Worker {
        validateCanAcceptJob()
        val newActiveJobs = activeJobs + 1
        return copy(
            activeJobs = newActiveJobs,
            status = WorkerStatus.BUSY
        )
    }
    
    fun completeJob(): Worker {
        require(activeJobs > 0) { "No active jobs to complete" }
        val newActiveJobs = activeJobs - 1
        return copy(
            activeJobs = newActiveJobs,
            status = if (newActiveJobs == 0) WorkerStatus.IDLE else WorkerStatus.BUSY
        )
    }
    
    fun canAcceptJob(requiredOS: String, requiredArch: String): Boolean {
        return capabilities.os == requiredOS && 
               capabilities.arch == requiredArch &&
               status != WorkerStatus.OFFLINE &&
               activeJobs < capabilities.maxConcurrentJobs
    }
    
    fun goOffline(): Worker {
        return copy(status = WorkerStatus.OFFLINE)
    }
    
    private fun validateCanAcceptJob() {
        when {
            status == WorkerStatus.OFFLINE -> 
                throw IllegalStateException("Cannot assign job to offline worker")
            activeJobs >= capabilities.maxConcurrentJobs -> 
                throw IllegalStateException("Worker has reached maximum concurrent jobs limit")
        }
    }
}