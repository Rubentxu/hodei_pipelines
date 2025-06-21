package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import kotlinx.coroutines.flow.Flow

/**
 * Port (Output) - Job Execution Service
 * Handles the actual execution of jobs on workers
 */
interface JobExecutor {
    suspend fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent>
    suspend fun sendSignal(jobId: JobId, signal: ExecutionSignal): Boolean
    suspend fun getJobOutput(jobId: JobId): Flow<JobOutputChunk>
}

interface ScriptExecutor {
    fun execute(job: Job, workerId: WorkerId): Flow<JobExecutionEvent>
}

/**
 * Job Execution Events
 */
sealed class JobExecutionEvent {
    data class Started(val jobId: JobId, val workerId: WorkerId) : JobExecutionEvent()
    data class OutputReceived(val jobId: JobId, val chunk: JobOutputChunk) : JobExecutionEvent()
    data class Completed(val jobId: JobId, val exitCode: Int, val output: String) : JobExecutionEvent()
    data class Failed(val jobId: JobId, val error: String, val exitCode: Int?) : JobExecutionEvent()
    data class Cancelled(val jobId: JobId) : JobExecutionEvent()
}

enum class ExecutionSignal {
    START,
    STOP,
    PAUSE,
    RESUME,
    CANCEL
}



/**
 * Job Output Chunk
 */
data class JobOutputChunk(
    val data: ByteArray,
    val isError: Boolean = false,
    val timestamp: java.time.Instant = java.time.Instant.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        
        other as JobOutputChunk
        
        if (!data.contentEquals(other.data)) return false
        if (isError != other.isError) return false
        if (timestamp != other.timestamp) return false
        
        return true
    }
    
    override fun hashCode(): Int {
        var result = data.contentHashCode()
        result = 31 * result + isError.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}