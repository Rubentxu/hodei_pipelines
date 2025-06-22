package dev.rubentxu.hodei.pipelines.infrastructure.grpc.mappers

import com.google.protobuf.Timestamp
import dev.rubentxu.hodei.pipelines.application.CreateAndExecuteJobRequest
import dev.rubentxu.hodei.pipelines.application.JobExecutionResult as DomainJobResult
import dev.rubentxu.hodei.pipelines.domain.job.*
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition as DomainJobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobStatus as DomainJobStatus
import dev.rubentxu.hodei.pipelines.port.ExecutionSignal as DomainExecutionSignal
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import dev.rubentxu.hodei.pipelines.proto.*
import dev.rubentxu.hodei.pipelines.proto.JobDefinition as GrpcJobDefinition
import dev.rubentxu.hodei.pipelines.proto.JobStatus as GrpcJobStatus
import java.time.Instant

/**
 * Mappers for converting between gRPC protobuf messages and domain objects
 * for Job-related operations
 */
object JobMappers {
    
    /**
     * Convert gRPC ExecuteJobRequest to domain CreateAndExecuteJobRequest
     */
    fun toDomain(request: ExecuteJobRequest): CreateAndExecuteJobRequest {
        val jobDef = request.jobDefinition
        val domainPayload = when (jobDef.payloadCase) {
            GrpcJobDefinition.PayloadCase.COMMAND -> JobPayload.Command(jobDef.command.commandLineList)
            GrpcJobDefinition.PayloadCase.SCRIPT -> JobPayload.Script(jobDef.script.content)
            else -> throw IllegalArgumentException("Unsupported payload type")
        }

        val jobDefinition = DomainJobDefinition(
            name = jobDef.name,
            payload = domainPayload,
            workingDirectory = jobDef.workingDirectory.takeIf { it.isNotEmpty() } ?: "/tmp",
            environment = jobDef.environmentMap.toMap()
        )
        return CreateAndExecuteJobRequest(jobDefinition)
    }
    
    /**
     * Convert domain Job to gRPC JobDefinition
     */
    fun toGrpcJobDefinition(job: Job): GrpcJobDefinition {
        val builder = GrpcJobDefinition.newBuilder()
            .setId(JobIdentifier.newBuilder().setValue(job.id.value).build())
            .setName(job.definition.name)
            .setWorkingDirectory(job.definition.workingDirectory)
            .putAllEnvironment(job.definition.environment)

        when (val payload = job.definition.payload) {
            is JobPayload.Command -> {
                val commandPayload = CommandPayload.newBuilder().addAllCommandLine(payload.commandLine).build()
                builder.setCommand(commandPayload)
            }
            is JobPayload.Script -> {
                val scriptPayload = ScriptPayload.newBuilder().setContent(payload.content).build()
                builder.setScript(scriptPayload)
            }
            is JobPayload.CompiledScript -> {
                val scriptPayload = ScriptPayload.newBuilder().setContent(payload.content).build()
                builder.setScript(scriptPayload)
            }
            else -> {
                // For new payload types, default to script for now
                val scriptPayload = ScriptPayload.newBuilder().setContent("// Unsupported payload type").build()
                builder.setScript(scriptPayload)
            }
        }
        return builder.build()
    }
    
    /**
     * Convert domain JobStatus to gRPC JobStatus
     */
    fun toGrpcStatus(status: DomainJobStatus): GrpcJobStatus {
        return when (status) {
            DomainJobStatus.QUEUED -> GrpcJobStatus.JOB_STATUS_QUEUED
            DomainJobStatus.RUNNING -> GrpcJobStatus.JOB_STATUS_RUNNING
            DomainJobStatus.COMPLETED -> GrpcJobStatus.JOB_STATUS_SUCCESS
            DomainJobStatus.FAILED -> GrpcJobStatus.JOB_STATUS_FAILED
            DomainJobStatus.CANCELLED -> GrpcJobStatus.JOB_STATUS_CANCELLED
        }
    }
    
    /**
     * Convert domain JobExecutionResult to gRPC JobOutputAndStatus
     */
    fun toGrpcOutput(result: DomainJobResult): JobOutputAndStatus {
        return when (result) {
            is DomainJobResult.JobCreated -> {
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(JobExecutionStatus.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue(result.jobId.value).build())
                        .setStatus(GrpcJobStatus.JOB_STATUS_QUEUED)
                        .setMessage("Job created and queued")
                        .build())
                    .build()
            }
            
            is DomainJobResult.JobAssigned -> {
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(JobExecutionStatus.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue(result.jobId.value).build())
                        .setStatus(GrpcJobStatus.JOB_STATUS_QUEUED)
                        .setMessage("Job assigned to worker ${result.workerId.value}")
                        .build())
                    .build()
            }
            
            is DomainJobResult.JobStarted -> {
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(JobExecutionStatus.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue(result.jobId.value).build())
                        .setStatus(GrpcJobStatus.JOB_STATUS_RUNNING)
                        .setMessage("Job started")
                        .build())
                    .build()
            }
            
            is DomainJobResult.JobOutput -> {
                JobOutputAndStatus.newBuilder()
                    .setOutputChunk(JobOutputChunk.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue(result.jobId.value).build())
                        .setData(com.google.protobuf.ByteString.copyFromUtf8(result.output))
                        .setIsStderr(false)
                        .setTimestamp(toGrpcTimestamp(Instant.now()))
                        .build())
                    .build()
            }
            
            is DomainJobResult.JobCompleted -> {
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(JobExecutionStatus.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue(result.jobId.value).build())
                        .setStatus(GrpcJobStatus.JOB_STATUS_SUCCESS)
                        .setExitCode(result.exitCode)
                        .setMessage("Job completed successfully")
                        .build())
                    .build()
            }
            
            is DomainJobResult.JobFailed -> {
                JobOutputAndStatus.newBuilder()
                    .setStatusUpdate(JobExecutionStatus.newBuilder()
                        .setJobId(JobIdentifier.newBuilder().setValue(result.jobId.value).build())
                        .setStatus(GrpcJobStatus.JOB_STATUS_FAILED)
                        .setExitCode(1)
                        .setMessage("Job failed: ${result.error}")
                        .build())
                    .build()
            }
        }
    }
    
    /**
     * Convert gRPC ControlSignal to domain ExecutionSignal
     */
    fun toDomainSignal(signal: ControlSignal): DomainExecutionSignal {
        return when (signal.type) {
            ControlSignal.SignalType.SIGNAL_TYPE_CANCEL -> DomainExecutionSignal.CANCEL
            ControlSignal.SignalType.SIGNAL_TYPE_PAUSE -> DomainExecutionSignal.PAUSE
            ControlSignal.SignalType.SIGNAL_TYPE_RESUME -> DomainExecutionSignal.RESUME
            else -> DomainExecutionSignal.CANCEL
        }
    }
    
    /**
     * Convert Instant to gRPC Timestamp
     */
    private fun toGrpcTimestamp(instant: Instant): Timestamp {
        return Timestamp.newBuilder()
            .setSeconds(instant.epochSecond)
            .setNanos(instant.nano)
            .build()
    }
}