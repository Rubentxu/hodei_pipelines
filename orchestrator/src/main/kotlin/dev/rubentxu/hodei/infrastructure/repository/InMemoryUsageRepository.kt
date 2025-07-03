package dev.rubentxu.hodei.infrastructure.repository

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.domain.quota.ResourceUsage
import dev.rubentxu.hodei.domain.quota.UsageRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

class InMemoryUsageRepository : UsageRepository {
    private val usages = ConcurrentHashMap<String, ResourceUsage>()
    private val mutex = Mutex()

    override suspend fun save(usage: ResourceUsage): Either<DomainError, ResourceUsage> = mutex.withLock {
        try {
            usages[usage.poolId.value] = usage
            usage.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to save usage: ${e.message}").left()
        }
    }

    override suspend fun findByPoolId(poolId: DomainId): Either<DomainError, ResourceUsage?> = mutex.withLock {
        try {
            usages[poolId.value].right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find usage by pool id: ${e.message}").left()
        }
    }

    override suspend fun findAll(): Either<DomainError, List<ResourceUsage>> = mutex.withLock {
        try {
            usages.values.toList().right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to find all usages: ${e.message}").left()
        }
    }

    override suspend fun delete(poolId: DomainId): Either<DomainError, Unit> = mutex.withLock {
        try {
            val usage = usages.remove(poolId.value)
            if (usage != null) {
                Unit.right()
            } else {
                RepositoryError.NotFoundError(
                    message = "Usage not found",
                    entityType = "ResourceUsage",
                    entityId = poolId.value
                ).left()
            }
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to delete usage: ${e.message}").left()
        }
    }

    override suspend fun updateUsage(
        poolId: DomainId,
        updater: (ResourceUsage) -> ResourceUsage
    ): Either<DomainError, ResourceUsage> = mutex.withLock {
        try {
            val currentUsage = usages[poolId.value] ?: ResourceUsage.empty(poolId)
            val updatedUsage = updater(currentUsage)
            usages[poolId.value] = updatedUsage
            updatedUsage.right()
        } catch (e: Exception) {
            RepositoryError.OperationFailed(message = "Failed to update usage: ${e.message}").left()
        }
    }
}