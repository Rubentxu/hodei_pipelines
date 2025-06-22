package dev.rubentxu.hodei.pipelines.domain.orchestration

/**
 * Worker Pool Identifier - Strong typing for worker pool IDs
 */
@JvmInline
value class WorkerPoolId(val value: String) {
    init {
        require(value.isNotBlank()) { "WorkerPoolId cannot be blank" }
        require(value.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?$"))) { 
            "WorkerPoolId must be valid Kubernetes name: lowercase alphanumeric or '-', start/end with alphanumeric" 
        }
    }
    
    companion object {
        fun ephemeral(): WorkerPoolId = WorkerPoolId("ephemeral")
        fun persistent(): WorkerPoolId = WorkerPoolId("persistent")
        fun fromTemplate(templateId: WorkerTemplateId): WorkerPoolId = 
            WorkerPoolId("pool-${templateId.value}")
    }
}