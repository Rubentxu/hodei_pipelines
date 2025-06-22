package dev.rubentxu.hodei.pipelines.infrastructure.grpc.mappers

import com.google.protobuf.Timestamp
import dev.rubentxu.hodei.pipelines.application.RegisterWorkerRequest
import dev.rubentxu.hodei.pipelines.application.RegisterWorkerResponse
import dev.rubentxu.hodei.pipelines.domain.worker.*
import dev.rubentxu.hodei.pipelines.proto.*
import java.time.Instant

/**
 * Mappers for converting between gRPC protobuf messages and domain objects
 * for Worker-related operations
 */
object WorkerMappers {
    
    /**
     * Convert gRPC WorkerRegistrationRequest to domain RegisterWorkerRequest
     */
    fun toDomain(request: WorkerRegistrationRequest): RegisterWorkerRequest {
        val capabilities = request.capabilitiesMap
        return RegisterWorkerRequest(
            name = request.workerName,
            os = capabilities["os"] ?: "unknown",
            arch = capabilities["arch"] ?: "unknown", 
            maxConcurrentJobs = request.maxConcurrentJobs.takeIf { it > 0 } ?: 5
        )
    }
    
    /**
     * Convert domain RegisterWorkerResponse to gRPC WorkerRegistrationResponse
     */
    fun toGrpcResponse(response: RegisterWorkerResponse): WorkerRegistrationResponse {
        return WorkerRegistrationResponse.newBuilder()
            .setSuccess(response.success)
            .setMessage(response.errorMessage ?: "Worker registered successfully")
            .setSessionToken(response.sessionToken ?: "")
            .setHeartbeatIntervalSeconds(30) // Default heartbeat interval
            .build()
    }
    
    /**
     * Convert domain Worker to gRPC WorkerInfo
     */
    fun toGrpcWorkerInfo(worker: Worker): WorkerInfo {
        return WorkerInfo.newBuilder()
            .setId(WorkerIdentifier.newBuilder().setValue(worker.id.value).build())
            .setName(worker.name)
            .setStatus(toGrpcStatus(worker.status))
            .putAllCapabilities(worker.capabilities.toMap())
            .setRegisteredAt(toGrpcTimestamp(worker.createdAt))
            .setLastHeartbeat(toGrpcTimestamp(worker.lastHeartbeat))
            .setActiveJobsCount(worker.activeJobs)
            .setMaxConcurrentJobs(worker.capabilities.toMap()["maxConcurrentJobs"]?.toIntOrNull() ?: 5)
            .build()
    }
    
    /**
     * Convert domain WorkerStatus to gRPC WorkerStatus
     */
    fun toGrpcStatus(status: dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus): dev.rubentxu.hodei.pipelines.proto.WorkerStatus {
        return when (status) {
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.PROVISIONING -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_PROVISIONING
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_READY
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.IDLE -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_READY
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.BUSY -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_BUSY
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.TERMINATING -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_TERMINATING
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.FAILED -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_FAILED
            dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.OFFLINE -> dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_FAILED
        }
    }
    
    /**
     * Convert gRPC WorkerStatus to domain WorkerStatus
     */
    fun toDomainStatus(status: dev.rubentxu.hodei.pipelines.proto.WorkerStatus): dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus {
        return when (status) {
            dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_PROVISIONING -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.PROVISIONING
            dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_READY -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.READY
            dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_BUSY -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.BUSY
            dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_TERMINATING -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.TERMINATING
            dev.rubentxu.hodei.pipelines.proto.WorkerStatus.WORKER_STATUS_FAILED -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.FAILED
            else -> dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus.OFFLINE
        }
    }
    
    /**
     * Create WorkerHeartbeat update from gRPC message
     */
    fun createHeartbeatUpdate(heartbeat: WorkerHeartbeat): WorkerHeartbeatUpdate {
        return WorkerHeartbeatUpdate(
            workerId = WorkerId(heartbeat.workerId.value),
            status = toDomainStatus(heartbeat.status),
            activeJobsCount = heartbeat.activeJobsCount,
            timestamp = fromGrpcTimestamp(heartbeat.timestamp)
        )
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
    
    /**
     * Convert gRPC Timestamp to Instant
     */
    private fun fromGrpcTimestamp(timestamp: Timestamp): Instant {
        return Instant.ofEpochSecond(timestamp.seconds, timestamp.nanos.toLong())
    }
}

/**
 * Data class for worker heartbeat updates
 */
data class WorkerHeartbeatUpdate(
    val workerId: WorkerId,
    val status: dev.rubentxu.hodei.pipelines.domain.worker.WorkerStatus,
    val activeJobsCount: Int,
    val timestamp: Instant
)