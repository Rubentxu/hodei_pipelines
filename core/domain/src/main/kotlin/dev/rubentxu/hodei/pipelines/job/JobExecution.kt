package dev.rubentxu.hodei.pipelines.job

import java.time.Instant

class JobExecution(
    val definition: JobDefinition
) {
    var status: JobStatus = JobStatus.QUEUED
        private set
    var exitCode: Int? = null
        private set

    val createdAt: Instant = Instant.now()
    var startedAt: Instant? = null
        private set
    var completedAt: Instant? = null
        private set

    private val outputLog: MutableList<LogEntry> = mutableListOf()

    fun getOutputLog(): List<LogEntry> = outputLog.toList()

    fun start() {
        check(status == JobStatus.QUEUED) { "Solo se puede iniciar un job encolado." }
        status = JobStatus.RUNNING
        startedAt = Instant.now()
    }

    fun complete(exitCode: Int) {
        this.exitCode = exitCode
        status = if (exitCode == 0) JobStatus.SUCCESS else JobStatus.FAILED
        completedAt = Instant.now()
    }

    fun cancel() {
        if (status == JobStatus.RUNNING || status == JobStatus.QUEUED) {
            status = JobStatus.CANCELLED
            completedAt = Instant.now()
        }
    }

    fun addLogEntry(entry: LogEntry) {
        if (status == JobStatus.RUNNING) {
            outputLog.add(entry)
        }
    }
}

data class JobOutputAndStatus(
    val data: ByteArray?,
    val isStderr: Boolean?,
    val timestamp: Instant?,
    val status: JobStatus?,
    val exitCode: Int?,
    val message: String?
)