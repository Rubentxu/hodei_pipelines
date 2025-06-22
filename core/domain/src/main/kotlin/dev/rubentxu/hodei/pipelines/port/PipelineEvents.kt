package dev.rubentxu.hodei.pipelines.port

import dev.rubentxu.hodei.pipelines.domain.job.JobId
import java.time.Instant

/**
 * Pipeline-specific events that extend the base JobExecutionEvent
 */
sealed class PipelineEvent : JobExecutionEvent() {
    
    /**
     * Stage-related events
     */
    data class StageStarted(
        val jobId: JobId,
        val stageName: String,
        val stageType: StageType,
        val metadata: Map<String, Any> = emptyMap(),
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class StageCompleted(
        val jobId: JobId,
        val stageName: String,
        val duration: Long,
        val status: StageStatus,
        val output: String? = null,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class StageFailed(
        val jobId: JobId,
        val stageName: String,
        val error: String,
        val cause: Throwable? = null,
        val duration: Long,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class StageSkipped(
        val jobId: JobId,
        val stageName: String,
        val reason: String,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    /**
     * Task-related events
     */
    data class TaskStarted(
        val jobId: JobId,
        val taskName: String,
        val stageName: String? = null,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class TaskCompleted(
        val jobId: JobId,
        val taskName: String,
        val duration: Long,
        val stageName: String? = null,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class TaskFailed(
        val jobId: JobId,
        val taskName: String,
        val error: String,
        val duration: Long,
        val stageName: String? = null,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    /**
     * Progress and status events
     */
    data class ProgressUpdate(
        val jobId: JobId,
        val currentStep: Int,
        val totalSteps: Int,
        val message: String,
        val percentage: Double = (currentStep.toDouble() / totalSteps.toDouble()) * 100.0,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class StatusUpdate(
        val jobId: JobId,
        val status: String,
        val message: String,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    /**
     * Parallel execution events
     */
    data class ParallelGroupStarted(
        val jobId: JobId,
        val groupName: String,
        val parallelStages: List<String>,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class ParallelGroupCompleted(
        val jobId: JobId,
        val groupName: String,
        val duration: Long,
        val successfulStages: List<String>,
        val failedStages: List<String>,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    /**
     * Resource and environment events
     */
    data class EnvironmentPrepared(
        val jobId: JobId,
        val environment: Map<String, String>,
        val workingDirectory: String,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class ArtifactGenerated(
        val jobId: JobId,
        val artifactName: String,
        val artifactPath: String,
        val artifactSize: Long,
        val artifactType: String,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class DependencyResolved(
        val jobId: JobId,
        val dependencyName: String,
        val version: String,
        val source: String,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    /**
     * Custom and extensible events
     */
    data class CustomEvent(
        val jobId: JobId,
        val eventType: String,
        val eventName: String,
        val data: Map<String, Any>,
        val source: String = "pipeline",
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    /**
     * Checkpoint and recovery events
     */
    data class CheckpointCreated(
        val jobId: JobId,
        val checkpointId: String,
        val stageName: String,
        val state: Map<String, Any>,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
    
    data class RecoveryInitiated(
        val jobId: JobId,
        val checkpointId: String,
        val reason: String,
        val timestamp: Instant = Instant.now()
    ) : PipelineEvent()
}

/**
 * Stage types
 */
enum class StageType {
    BUILD,
    TEST,
    DEPLOY,
    SETUP,
    CLEANUP,
    VALIDATION,
    SECURITY_SCAN,
    QUALITY_GATE,
    INTEGRATION,
    PACKAGING,
    NOTIFICATION,
    CUSTOM
}

/**
 * Stage execution status
 */
enum class StageStatus {
    SUCCESS,
    FAILURE,
    SKIPPED,
    UNSTABLE,
    ABORTED
}

/**
 * Pipeline execution context for DSL
 */
data class PipelineExecutionContext(
    val jobId: JobId,
    val workerId: dev.rubentxu.hodei.pipelines.domain.worker.WorkerId,
    val environment: Map<String, String>,
    val workingDirectory: String,
    val startTime: Instant = Instant.now(),
    val currentStage: String? = null,
    val executedStages: MutableList<String> = mutableListOf(),
    val artifacts: MutableMap<String, String> = mutableMapOf(),
    val checkpoints: MutableMap<String, Map<String, Any>> = mutableMapOf()
)