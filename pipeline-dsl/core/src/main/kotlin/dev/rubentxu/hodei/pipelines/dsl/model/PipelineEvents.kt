package dev.rubentxu.hodei.pipelines.dsl.model

import kotlinx.serialization.Serializable

/**
 * Eventos coherentes del Pipeline DSL (equivalente a JobExecutionEvent).
 */
@Serializable
sealed class PipelineExecutionEvent {
    abstract val timestamp: Long
    
    @Serializable
    data class PipelineStarted(
        val pipelineName: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class PipelineCompleted(
        val pipelineName: String,
        val success: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StageStarted(
        val pipelineName: String,
        val stageName: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StageCompleted(
        val pipelineName: String,
        val stageName: String,
        val success: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StepStarted(
        val pipelineName: String,
        val stageName: String,
        val stepType: String,
        val stepId: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StepCompleted(
        val pipelineName: String,
        val stageName: String,
        val stepType: String,
        val stepId: String,
        val success: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StepFailed(
        val pipelineName: String,
        val stageName: String,
        val stepType: String,
        val stepId: String,
        val error: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StepExecuted(
        val pipelineName: String,
        val stageName: String,
        val stepType: String,
        val success: Boolean,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StatusUpdate(
        val jobId: String,
        val status: String,
        val message: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class EnvironmentPrepared(
        val jobId: String,
        val environment: Map<String, String>,
        val workingDirectory: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
    
    @Serializable
    data class StageSkipped(
        val jobId: String,
        val stageName: String,
        val reason: String,
        override val timestamp: Long = System.currentTimeMillis()
    ) : PipelineExecutionEvent()
}