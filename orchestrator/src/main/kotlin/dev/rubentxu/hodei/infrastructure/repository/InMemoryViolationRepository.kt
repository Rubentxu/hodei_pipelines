package dev.rubentxu.hodei.infrastructure.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.domain.quota.QuotaViolation
import dev.rubentxu.hodei.domain.quota.ViolationRepository
import dev.rubentxu.hodei.domain.quota.ViolationSeverity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import java.util.concurrent.ConcurrentHashMap

class InMemoryViolationRepository : ViolationRepository {
    private val violations = ConcurrentHashMap<String, QuotaViolation>()
    private val poolViolationsIndex = ConcurrentHashMap<String, MutableSet<String>>() // poolId -> Set<violationId>
    private val mutex = Mutex()

    override suspend fun save(violation: QuotaViolation): Either<DomainError, QuotaViolation> = mutex.withLock {
        try {
            violations[violation.id.value] = violation
            poolViolationsIndex.computeIfAbsent(violation.poolId.value) { mutableSetOf() }
                .add(violation.id.value)
            violation.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to save violation: ${e.message}").left()
        }
    }

    override suspend fun findById(id: DomainId): Either<DomainError, QuotaViolation?> = mutex.withLock {
        try {
            violations[id.value].right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find violation by id: ${e.message}").left()
        }
    }

    override suspend fun findByPoolId(poolId: DomainId): Either<DomainError, List<QuotaViolation>> = mutex.withLock {
        try {
            val violationIds = poolViolationsIndex[poolId.value] ?: emptySet()
            val poolViolations = violationIds.mapNotNull { violations[it] }.sortedByDescending { it.timestamp }
            poolViolations.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find violations by pool id: ${e.message}").left()
        }
    }

    override suspend fun findByPoolIdAndUnresolved(poolId: DomainId): Either<DomainError, List<QuotaViolation>> = mutex.withLock {
        try {
            val violationIds = poolViolationsIndex[poolId.value] ?: emptySet()
            val unresolvedViolations = violationIds
                .mapNotNull { violations[it] }
                .filter { !it.isResolved }
                .sortedByDescending { it.timestamp }
            unresolvedViolations.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find unresolved violations: ${e.message}").left()
        }
    }

    override suspend fun findBySeverity(severity: ViolationSeverity): Either<DomainError, List<QuotaViolation>> = mutex.withLock {
        try {
            val severityViolations = violations.values
                .filter { it.severity == severity }
                .sortedByDescending { it.timestamp }
            severityViolations.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find violations by severity: ${e.message}").left()
        }
    }

    override suspend fun findRecentViolations(poolId: DomainId, limitHours: Long): Either<DomainError, List<QuotaViolation>> = mutex.withLock {
        try {
            val now = Clock.System.now()
            val cutoffMillis = now.toEpochMilliseconds() - (limitHours * 60 * 60 * 1000)
            val cutoffTime = kotlinx.datetime.Instant.fromEpochMilliseconds(cutoffMillis)
            val violationIds = poolViolationsIndex[poolId.value] ?: emptySet()
            val recentViolations = violationIds
                .mapNotNull { violations[it] }
                .filter { it.timestamp >= cutoffTime }
                .sortedByDescending { it.timestamp }
            recentViolations.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find recent violations: ${e.message}").left()
        }
    }

    override suspend fun findAll(): Either<DomainError, List<QuotaViolation>> = mutex.withLock {
        try {
            violations.values.sortedByDescending { it.timestamp }.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find all violations: ${e.message}").left()
        }
    }

    override suspend fun markResolved(id: DomainId, resolvedBy: String): Either<DomainError, QuotaViolation> = mutex.withLock {
        try {
            val violation = violations[id.value]
            if (violation != null) {
                val resolvedViolation = violation.resolve(resolvedBy)
                violations[id.value] = resolvedViolation
                resolvedViolation.right()
            } else {
                RepositoryError.NotFoundError(
                    message = "Violation not found",
                    entityType = "QuotaViolation",
                    entityId = id.value
                ).left()
            }
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to mark violation as resolved: ${e.message}").left()
        }
    }
}