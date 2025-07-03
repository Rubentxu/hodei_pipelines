package dev.rubentxu.hodei.domain.quota

import arrow.core.Either
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*

interface QuotaRepository {
    suspend fun save(quota: ResourceQuota): Either<DomainError, ResourceQuota>
    suspend fun findById(id: DomainId): Either<DomainError, ResourceQuota?>
    suspend fun findByPoolId(poolId: DomainId): Either<DomainError, ResourceQuota?>
    suspend fun findAll(): Either<DomainError, List<ResourceQuota>>
    suspend fun findByEnabled(enabled: Boolean): Either<DomainError, List<ResourceQuota>>
    suspend fun delete(id: DomainId): Either<DomainError, Unit>
    suspend fun exists(poolId: DomainId): Either<DomainError, Boolean>
}

interface UsageRepository {
    suspend fun save(usage: ResourceUsage): Either<DomainError, ResourceUsage>
    suspend fun findByPoolId(poolId: DomainId): Either<DomainError, ResourceUsage?>
    suspend fun findAll(): Either<DomainError, List<ResourceUsage>>
    suspend fun delete(poolId: DomainId): Either<DomainError, Unit>
    suspend fun updateUsage(poolId: DomainId, updater: (ResourceUsage) -> ResourceUsage): Either<DomainError, ResourceUsage>
}

interface ViolationRepository {
    suspend fun save(violation: QuotaViolation): Either<DomainError, QuotaViolation>
    suspend fun findById(id: DomainId): Either<DomainError, QuotaViolation?>
    suspend fun findByPoolId(poolId: DomainId): Either<DomainError, List<QuotaViolation>>
    suspend fun findByPoolIdAndUnresolved(poolId: DomainId): Either<DomainError, List<QuotaViolation>>
    suspend fun findBySeverity(severity: ViolationSeverity): Either<DomainError, List<QuotaViolation>>
    suspend fun findRecentViolations(poolId: DomainId, limitHours: Long = 24): Either<DomainError, List<QuotaViolation>>
    suspend fun findAll(): Either<DomainError, List<QuotaViolation>>
    suspend fun markResolved(id: DomainId, resolvedBy: String): Either<DomainError, QuotaViolation>
}