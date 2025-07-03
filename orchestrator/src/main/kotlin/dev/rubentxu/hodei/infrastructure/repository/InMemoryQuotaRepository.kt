package dev.rubentxu.hodei.infrastructure.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.domain.quota.QuotaRepository
import dev.rubentxu.hodei.domain.quota.ResourceQuota
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class InMemoryQuotaRepository : QuotaRepository {
    private val quotas = ConcurrentHashMap<String, ResourceQuota>()
    private val poolQuotaIndex = ConcurrentHashMap<String, String>() // poolId -> quotaId
    private val mutex = Mutex()

    override suspend fun save(quota: ResourceQuota): Either<DomainError, ResourceQuota> = mutex.withLock {
        try {
            quotas[quota.id.value] = quota
            poolQuotaIndex[quota.poolId.value] = quota.id.value
            quota.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to save quota: ${e.message}").left()
        }
    }

    override suspend fun findById(id: DomainId): Either<DomainError, ResourceQuota?> = mutex.withLock {
        try {
            quotas[id.value].right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find quota by id: ${e.message}").left()
        }
    }

    override suspend fun findByPoolId(poolId: DomainId): Either<DomainError, ResourceQuota?> = mutex.withLock {
        try {
            val quotaId = poolQuotaIndex[poolId.value]
            if (quotaId != null) {
                quotas[quotaId].right()
            } else {
                null.right()
            }
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find quota by pool id: ${e.message}").left()
        }
    }

    override suspend fun findAll(): Either<DomainError, List<ResourceQuota>> = mutex.withLock {
        try {
            quotas.values.toList().right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find all quotas: ${e.message}").left()
        }
    }

    override suspend fun findByEnabled(enabled: Boolean): Either<DomainError, List<ResourceQuota>> = mutex.withLock {
        try {
            quotas.values.filter { it.enabled == enabled }.toList().right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find quotas by enabled status: ${e.message}").left()
        }
    }

    override suspend fun delete(id: DomainId): Either<DomainError, Unit> = mutex.withLock {
        try {
            val quota = quotas.remove(id.value)
            if (quota != null) {
                poolQuotaIndex.remove(quota.poolId.value)
                Unit.right()
            } else {
                RepositoryError.NotFoundError(
                    message = "Quota not found",
                    entityType = "ResourceQuota",
                    entityId = id.value
                ).left()
            }
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to delete quota: ${e.message}").left()
        }
    }

    override suspend fun exists(poolId: DomainId): Either<DomainError, Boolean> = mutex.withLock {
        try {
            poolQuotaIndex.containsKey(poolId.value).right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to check quota existence: ${e.message}").left()
        }
    }
}