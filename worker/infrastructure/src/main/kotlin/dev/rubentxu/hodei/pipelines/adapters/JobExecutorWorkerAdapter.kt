package dev.rubentxu.hodei.pipelines.adapters

import dev.rubentxu.hodei.pipelines.port.JobExecutor
import dev.rubentxu.hodei.pipelines.job.JobDefinition
import dev.rubentxu.hodei.pipelines.job.JobExecution
import dev.rubentxu.hodei.pipelines.proto.*
import io.grpc.ManagedChannel
import java.util.concurrent.TimeUnit

class JobExecutorWorkerAdapter(private val channel: ManagedChannel) : JobExecutor {
    private val stub: JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub =
        JobExecutorServiceGrpcKt.JobExecutorServiceCoroutineStub(channel)

    override fun execute(job: JobDefinition): JobExecution {
        val request = ServerWorker.JobRequest.newBuilder()
            .setJobId(job.id)
            .setScript(job.script)
            .build()

        val response = try {
            stub.executeJob(request)
        } catch (e: Exception) {
            return JobExecution(job.id, JobExecution.Status.FAILED, e.message ?: "Unknown error")
        }

        return JobExecution(
            job.id,
            JobExecution.Status.valueOf(response.status),
            response.output
        )
    }

    fun shutdown() {
        channel.shutdown().awaitTermination(5, TimeUnit.SECONDS)
    }
}