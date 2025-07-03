package dev.rubentxu.hodei.application.services

import arrow.core.Either
import arrow.core.flatMap
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.domain.quota.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.time.Duration.Companion.seconds

private val logger = KotlinLogging.logger {}

class ResourceMonitoringService(
    private val quotaService: QuotaService,
    private val usageRepository: UsageRepository,
    private val violationRepository: ViolationRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val alertChannel = Channel<ResourceAlert>(Channel.UNLIMITED)
    private val violationChannel = Channel<QuotaViolation>(Channel.UNLIMITED)
    
    // Monitoring state
    private val monitoringJobs = mutableMapOf<String, Job>()
    private val monitoringInterval = 30.seconds
    
    init {
        // Start alert and violation processors
        scope.launch { processAlerts() }
        scope.launch { processViolations() }
    }
    
    fun startMonitoring(poolId: DomainId): Either<DomainError, Unit> {
        logger.info { "Starting resource monitoring for pool $poolId" }
        
        return try {
            // Stop existing monitoring if any
            stopMonitoring(poolId)
            
            // Start new monitoring job
            val monitoringJob = scope.launch {
                monitorPool(poolId)
            }
            
            monitoringJobs[poolId.value] = monitoringJob
            Unit.right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to start monitoring for pool $poolId" }
            SystemError.InternalError(message = "Failed to start resource monitoring: ${e.message}").left()
        }
    }
    
    fun stopMonitoring(poolId: DomainId): Either<DomainError, Unit> {
        logger.info { "Stopping resource monitoring for pool $poolId" }
        
        return try {
            monitoringJobs[poolId.value]?.cancel()
            monitoringJobs.remove(poolId.value)
            Unit.right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to stop monitoring for pool $poolId" }
            SystemError.InternalError(message = "Failed to stop resource monitoring: ${e.message}").left()
        }
    }
    
    private suspend fun monitorPool(poolId: DomainId) {
        logger.debug { "Starting monitoring loop for pool $poolId" }
        
        while (currentCoroutineContext().isActive) {
            try {
                checkPoolQuotas(poolId)
                delay(monitoringInterval)
            } catch (e: CancellationException) {
                logger.debug { "Monitoring cancelled for pool $poolId" }
                throw e
            } catch (e: Exception) {
                logger.error(e) { "Error during monitoring for pool $poolId" }
                delay(monitoringInterval) // Continue monitoring even after errors
            }
        }
    }
    
    private suspend fun checkPoolQuotas(poolId: DomainId) {
        quotaService.getQuotaByPoolId(poolId).flatMap { quota ->
            if (quota?.enabled == true) {
                quotaService.getCurrentUsage(poolId).flatMap { usage ->
                    checkQuotaAlerts(quota, usage)
                    checkQuotaViolations(quota, usage)
                    Unit.right()
                }
            } else {
                Unit.right()
            }
        }.fold(
            { error -> logger.error { "Failed to check quotas for pool $poolId: $error" } },
            { /* Success */ }
        )
    }
    
    private suspend fun checkQuotaAlerts(quota: ResourceQuota, usage: ResourceUsage) {
        val alerts = usage.shouldTriggerAlert(quota.limits, quota.alertThresholds)
        alerts.forEach { alert ->
            logger.warn { "Resource alert for pool ${quota.poolId}: ${alert.resource} at ${alert.currentPercentage.toInt()}%" }
            alertChannel.trySend(alert)
        }
    }
    
    private suspend fun checkQuotaViolations(quota: ResourceQuota, usage: ResourceUsage) {
        val violations = usage.getViolations(quota.limits)
        
        violations.forEach { violation ->
            logger.error { "Quota violation detected for pool ${quota.poolId}: ${violation.resource} exceeded by ${violation.excessPercentage.toInt()}%" }
            
            // Create violation record
            val quotaViolation = QuotaViolation.create(
                poolId = quota.poolId,
                quotaId = quota.id,
                violationType = ViolationType.CRITICAL_USAGE,
                resource = violation.resource,
                limit = violation.limit,
                attempted = violation.current,
                current = violation.current,
                severity = violation.severity,
                action = ViolationAction.NOTIFICATION_SENT,
                context = ViolationContext(
                    operationType = "monitoring_check",
                    additionalData = mapOf(
                        "monitoring_timestamp" to Clock.System.now().toString(),
                        "excess_percentage" to violation.excessPercentage.toString()
                    )
                )
            )
            
            violationRepository.save(quotaViolation).fold(
                { error -> logger.error { "Failed to save violation: $error" } },
                { saved -> violationChannel.trySend(saved) }
            )
        }
    }
    
    private suspend fun processAlerts() = flow {
        while (currentCoroutineContext().isActive) {
            val alert = alertChannel.receive()
            emit(alert)
        }
    }.catch { e ->
        logger.error(e) { "Error in alert processor" }
    }.collect { alert ->
        handleAlert(alert)
    }
    
    private suspend fun processViolations() = flow {
        while (currentCoroutineContext().isActive) {
            val violation = violationChannel.receive()
            emit(violation)
        }
    }.catch { e ->
        logger.error(e) { "Error in violation processor" }
    }.collect { violation ->
        handleViolation(violation)
    }
    
    private suspend fun handleAlert(alert: ResourceAlert) {
        logger.info { "Processing resource alert: ${alert.resource} at ${alert.currentPercentage.toInt()}% (threshold: ${alert.thresholdPercentage.toInt()}%)" }
        
        // Here you could integrate with:
        // - Notification systems (email, Slack, etc.)
        // - Metrics systems (Prometheus, etc.)
        // - Auto-scaling systems
        
        when (alert.severity) {
            AlertSeverity.CRITICAL -> {
                logger.error { "CRITICAL alert: ${alert.resource} usage is at ${alert.currentPercentage.toInt()}%" }
                // Send immediate notifications
            }
            AlertSeverity.HIGH -> {
                logger.warn { "HIGH alert: ${alert.resource} usage is at ${alert.currentPercentage.toInt()}%" }
                // Send priority notifications
            }
            AlertSeverity.MEDIUM -> {
                logger.info { "MEDIUM alert: ${alert.resource} usage is at ${alert.currentPercentage.toInt()}%" }
                // Send standard notifications
            }
            AlertSeverity.LOW -> {
                logger.debug { "LOW alert: ${alert.resource} usage is at ${alert.currentPercentage.toInt()}%" }
                // Log only or send low-priority notifications
            }
        }
    }
    
    private suspend fun handleViolation(violation: QuotaViolation) {
        logger.error { "Processing quota violation: ${violation.resource} exceeded limit by ${((violation.attempted - violation.limit) / violation.limit * 100).toInt()}%" }
        
        // Here you could integrate with:
        // - Incident management systems
        // - Auto-remediation systems
        // - Capacity planning systems
        
        when (violation.severity) {
            ViolationSeverity.CRITICAL -> {
                logger.error { "CRITICAL violation: immediate attention required for ${violation.resource}" }
                // Trigger incident response
            }
            ViolationSeverity.HIGH -> {
                logger.error { "HIGH severity violation: ${violation.resource} needs attention" }
                // Alert on-call team
            }
            ViolationSeverity.MEDIUM -> {
                logger.warn { "MEDIUM severity violation: ${violation.resource} monitoring required" }
                // Standard alerting
            }
            ViolationSeverity.LOW -> {
                logger.info { "LOW severity violation: ${violation.resource} minor excess" }
                // Log and track
            }
        }
    }
    
    suspend fun getMonitoringStatus(): Either<DomainError, MonitoringStatus> {
        return try {
            val activeMonitoring = monitoringJobs.mapNotNull { (poolId, job) ->
                if (job.isActive) {
                    PoolMonitoringInfo(
                        poolId = DomainId(poolId),
                        isActive = true,
                        startedAt = Clock.System.now(), // In real implementation, track actual start times
                        lastCheck = Clock.System.now()
                    )
                } else {
                    null
                }
            }
            
            MonitoringStatus(
                totalPoolsMonitored = activeMonitoring.size,
                activeMonitoring = activeMonitoring,
                monitoringInterval = monitoringInterval
            ).right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to get monitoring status" }
            SystemError.InternalError(message = "Failed to get monitoring status: ${e.message}").left()
        }
    }
    
    fun getAlertStream(): Flow<ResourceAlert> = flow {
        while (currentCoroutineContext().isActive) {
            val alert = alertChannel.receive()
            emit(alert)
        }
    }
    
    fun getViolationStream(): Flow<QuotaViolation> = flow {
        while (currentCoroutineContext().isActive) {
            val violation = violationChannel.receive()
            emit(violation)
        }
    }
    
    fun shutdown() {
        logger.info { "Shutting down resource monitoring service" }
        scope.cancel()
        alertChannel.close()
        violationChannel.close()
    }
}

@kotlinx.serialization.Serializable
data class MonitoringStatus(
    val totalPoolsMonitored: Int,
    val activeMonitoring: List<PoolMonitoringInfo>,
    val monitoringInterval: kotlin.time.Duration
)

@kotlinx.serialization.Serializable
data class PoolMonitoringInfo(
    val poolId: DomainId,
    val isActive: Boolean,
    val startedAt: Instant,
    val lastCheck: Instant
)