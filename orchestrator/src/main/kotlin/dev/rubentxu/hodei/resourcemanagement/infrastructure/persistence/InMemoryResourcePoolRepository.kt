package dev.rubentxu.hodei.resourcemanagement.infrastructure.persistence

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.shared.domain.errors.RepositoryError
import dev.rubentxu.hodei.resourcemanagement.domain.repositories.ResourcePoolRepository
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import arrow.core.Either
import arrow.core.left
import arrow.core.right
import java.util.concurrent.ConcurrentHashMap
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

class InMemoryResourcePoolRepository : ResourcePoolRepository {
    private val pools = ConcurrentHashMap<String, ResourcePool>()
    private val poolsByName = ConcurrentHashMap<String, ResourcePool>()

    init {
        // Create default pool on startup
        val defaultPool = ResourcePool.createDefault("system")
        pools[defaultPool.id.value] = defaultPool
        poolsByName[defaultPool.name] = defaultPool
        logger.info { "Created default resource pool: ${defaultPool.name}" }
    }

    override suspend fun save(pool: ResourcePool): Either<RepositoryError, ResourcePool> {
        return try {
            // Check if name already exists (for new pools)
            if (!pools.containsKey(pool.id.value) && poolsByName.containsKey(pool.name)) {
                return RepositoryError.Conflict(message = "Resource pool with name '${pool.name}' already exists").left()
            }

            pools[pool.id.value] = pool
            poolsByName[pool.name] = pool
            logger.debug { "Saved resource pool: ${pool.name} (${pool.id})" }
            pool.right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to save resource pool ${pool.id}" }
            RepositoryError.Unknown(message = "Failed to save resource pool: ${e.message}").left()
        }
    }

    override suspend fun findById(id: DomainId): Either<RepositoryError, ResourcePool?> {
        return try {
            pools[id.value].right()
        } catch (e: Exception) {
            RepositoryError.Unknown(message = "Failed to find resource pool: ${e.message}").left()
        }
    }

    override suspend fun findByName(name: String): ResourcePool? {
        return poolsByName[name]
    }

    override suspend fun findAll(): List<ResourcePool> {
        return pools.values.toList().sortedBy { it.name }
    }

    override suspend fun findByStatus(status: PoolStatus): List<ResourcePool> {
        return pools.values.filter { it.status == status }.sortedBy { it.name }
    }
    
    override suspend fun findActive(): Either<RepositoryError, List<ResourcePool>> {
        return try {
            pools.values.filter { it.status == PoolStatus.ACTIVE }.sortedBy { it.name }.right()
        } catch (e: Exception) {
            RepositoryError.Unknown(message = "Failed to find active pools: ${e.message}").left()
        }
    }

    override suspend fun findByLabel(key: String, value: String): List<ResourcePool> {
        return pools.values.filter { pool ->
            pool.labels[key] == value
        }.sortedBy { it.name }
    }

    override suspend fun update(pool: ResourcePool): Either<RepositoryError, ResourcePool> {
        return try {
            val existing = pools[pool.id.value]
            if (existing == null) {
                return RepositoryError.NotFoundError(message = "Resource pool not found: ${pool.id}", entityType = "ResourcePool", entityId = pool.id.value).left()
            }

            // Check if name change conflicts with existing pool
            if (existing.name != pool.name && poolsByName.containsKey(pool.name)) {
                return RepositoryError.Conflict(message = "Resource pool with name '${pool.name}' already exists").left()
            }

            // Update both indexes if name changed
            if (existing.name != pool.name) {
                poolsByName.remove(existing.name)
                poolsByName[pool.name] = pool
            }

            pools[pool.id.value] = pool
            logger.debug { "Updated resource pool: ${pool.name} (${pool.id})" }
            pool.right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to update resource pool ${pool.id}" }
            RepositoryError.Unknown(message = "Failed to update resource pool: ${e.message}").left()
        }
    }

    override suspend fun delete(id: DomainId): Either<RepositoryError, Unit> {
        return try {
            val pool = pools[id.value]
            if (pool == null) {
                return RepositoryError.NotFoundError(message = "Resource pool not found: $id", entityType = "ResourcePool", entityId = id.value).left()
            }

            // Prevent deletion of default pool
            if (pool.name == ResourcePool.DEFAULT_POOL_NAME) {
                return RepositoryError.Conflict(message = "Cannot delete default resource pool").left()
            }

            pools.remove(id.value)
            poolsByName.remove(pool.name)
            logger.info { "Deleted resource pool: ${pool.name} (${pool.id})" }
            Unit.right()
        } catch (e: Exception) {
            logger.error(e) { "Failed to delete resource pool $id" }
            RepositoryError.Unknown(message = "Failed to delete resource pool: ${e.message}").left()
        }
    }

    override suspend fun exists(name: String): Boolean {
        return poolsByName.containsKey(name)
    }
}