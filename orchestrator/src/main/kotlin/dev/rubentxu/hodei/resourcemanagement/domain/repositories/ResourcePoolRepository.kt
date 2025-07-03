package dev.rubentxu.hodei.resourcemanagement.domain.repositories

import dev.rubentxu.hodei.shared.domain.errors.*
import dev.rubentxu.hodei.shared.domain.primitives.*
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import arrow.core.Either

interface ResourcePoolRepository {
    suspend fun save(pool: ResourcePool): Either<RepositoryError, ResourcePool>
    suspend fun findById(id: DomainId): Either<RepositoryError, ResourcePool?>
    suspend fun findByName(name: String): ResourcePool?
    suspend fun findAll(): List<ResourcePool>
    suspend fun findByStatus(status: PoolStatus): List<ResourcePool>
    suspend fun findActive(): Either<RepositoryError, List<ResourcePool>>
    suspend fun findByLabel(key: String, value: String): List<ResourcePool>
    suspend fun update(pool: ResourcePool): Either<RepositoryError, ResourcePool>
    suspend fun delete(id: DomainId): Either<RepositoryError, Unit>
    suspend fun exists(name: String): Boolean
}