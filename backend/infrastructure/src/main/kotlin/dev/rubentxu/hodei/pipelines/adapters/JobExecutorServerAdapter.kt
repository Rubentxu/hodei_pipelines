package dev.rubentxu.hodei.pipelines.adapters

import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.job.JobDefinition
import dev.rubentxu.hodei.pipelines.job.JobExecution
import dev.rubentxu.hodei.pipelines.proto.*
import io.grpc.Status

class JobExecutorServerAdapter(private val jobExecutor: JobExecutor) :
    JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineImplBase() {

    override suspend fun executeJob(request: ServerWorker.JobRequest): ServerWorker.JobResponse {
        try {
            val jobDefinition = JobDefinition(request.jobId, request.script)
            val execution: JobExecution = jobExecutor.execute(jobDefinition)
            return ServerWorker.JobResponse.newBuilder()
                .setJobId(execution.jobId)
                .setStatus(execution.status.name)
                .setOutput(execution.output)
                .build()
        } catch (e: Exception) {
            throw Status.INTERNAL.withDescription(e.message).asRuntimeException()
        }
    }
}