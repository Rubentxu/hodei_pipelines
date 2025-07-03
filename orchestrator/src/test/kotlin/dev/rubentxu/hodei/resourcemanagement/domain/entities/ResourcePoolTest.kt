package dev.rubentxu.hodei.resourcemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourcePool
import dev.rubentxu.hodei.resourcemanagement.domain.entities.PoolStatus
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceQuotas
import dev.rubentxu.hodei.resourcemanagement.domain.entities.ResourceLimit
import kotlinx.datetime.Clock
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.assertThrows

class ResourcePoolTest {

    @Test
    fun `should create valid resource pool`() {
        val now = Clock.System.now()
        val pool = ResourcePool(
            id = DomainId.generate(),
            name = "test-pool",
            displayName = "Test Pool",
            description = "A test resource pool",
            resourceQuotas = ResourceQuotas.basic(),
            createdAt = now,
            updatedAt = now,
            createdBy = "test-user"
        )

        assertEquals("test-pool", pool.name)
        assertEquals("Test Pool", pool.displayName)
        assertEquals("A test resource pool", pool.description)
        assertEquals(PoolStatus.ACTIVE, pool.status)
        assertTrue(pool.isActive())
        assertFalse(pool.isTerminating())
    }

    @Test
    fun `should enforce DNS-1123 naming rules`() {
        val now = Clock.System.now()
        
        // Valid names
        listOf("test", "test-pool", "pool123", "a", "test-pool-123").forEach { validName ->
            assertDoesNotThrow {
                ResourcePool(
                    id = DomainId.generate(),
                    name = validName,
                    resourceQuotas = ResourceQuotas.unlimited(),
                    createdAt = now,
                    updatedAt = now,
                    createdBy = "test-user"
                )
            }
        }

        // Invalid names
        listOf("TEST", "test_pool", "-test", "test-", "test..pool", "").forEach { invalidName ->
            assertThrows<IllegalArgumentException> {
                ResourcePool(
                    id = DomainId.generate(),
                    name = invalidName,
                    resourceQuotas = ResourceQuotas.unlimited(),
                    createdAt = now,
                    updatedAt = now,
                    createdBy = "test-user"
                )
            }
        }
    }

    @Test
    fun `should enforce name length limit`() {
        val now = Clock.System.now()
        val longName = "a".repeat(64) // 64 characters, exceeds limit

        assertThrows<IllegalArgumentException> {
            ResourcePool(
                id = DomainId.generate(),
                name = longName,
                resourceQuotas = ResourceQuotas.unlimited(),
                createdAt = now,
                updatedAt = now,
                createdBy = "test-user"
            )
        }
    }

    @Test
    fun `should create default pool correctly`() {
        val defaultPool = ResourcePool.createDefault("system")

        assertEquals(ResourcePool.DEFAULT_POOL_NAME, defaultPool.name)
        assertEquals("Default Pool", defaultPool.displayName)
        assertEquals("Default resource pool for workers and jobs", defaultPool.description)
        assertEquals(PoolStatus.ACTIVE, defaultPool.status)
        assertEquals("system", defaultPool.createdBy)
        assertFalse(defaultPool.resourceQuotas.hasLimits())
    }

    @Test
    fun `should update quotas correctly`() {
        val pool = ResourcePool.createDefault("test-user")
        val newQuotas = ResourceQuotas.basic()

        val updatedPool = pool.updateQuotas(newQuotas)

        assertEquals(newQuotas, updatedPool.resourceQuotas)
        assertTrue(updatedPool.updatedAt > pool.updatedAt)
        assertTrue(updatedPool.resourceQuotas.hasLimits())
    }

    @Test
    fun `should update status correctly`() {
        val pool = ResourcePool.createDefault("test-user")

        val terminatingPool = pool.updateStatus(PoolStatus.TERMINATING)

        assertEquals(PoolStatus.TERMINATING, terminatingPool.status)
        assertTrue(terminatingPool.isTerminating())
        assertFalse(terminatingPool.isActive())
        assertTrue(terminatingPool.updatedAt > pool.updatedAt)
    }

    @Test
    fun `should manage labels correctly`() {
        val pool = ResourcePool.createDefault("test-user")

        val withLabel = pool.addLabel("environment", "production")
        assertEquals("production", withLabel.labels["environment"])
        assertTrue(withLabel.updatedAt > pool.updatedAt)

        val withoutLabel = withLabel.removeLabel("environment")
        assertNull(withoutLabel.labels["environment"])
        assertTrue(withoutLabel.updatedAt > withLabel.updatedAt)
    }

    @Test
    fun `should manage annotations correctly`() {
        val pool = ResourcePool.createDefault("test-user")

        val withAnnotation = pool.addAnnotation("description", "Main production pool")
        assertEquals("Main production pool", withAnnotation.annotations["description"])
        assertTrue(withAnnotation.updatedAt > pool.updatedAt)
    }
}

class ResourceQuotasTest {

    @Test
    fun `should create unlimited quotas`() {
        val quotas = ResourceQuotas.unlimited()

        assertNull(quotas.cpu)
        assertNull(quotas.memory)
        assertNull(quotas.storage)
        assertNull(quotas.maxWorkers)
        assertNull(quotas.maxJobs)
        assertNull(quotas.maxConcurrentJobs)
        assertFalse(quotas.hasLimits())
    }

    @Test
    fun `should create basic quotas`() {
        val quotas = ResourceQuotas.basic()

        assertNotNull(quotas.cpu)
        assertNotNull(quotas.memory)
        assertNotNull(quotas.storage)
        assertEquals(10, quotas.maxWorkers)
        assertEquals(100, quotas.maxJobs)
        assertEquals(10, quotas.maxConcurrentJobs)
        assertTrue(quotas.hasLimits())
    }

    @Test
    fun `should check worker limits`() {
        val quotas = ResourceQuotas(
            cpu = null,
            memory = null,
            storage = null,
            maxWorkers = 5,
            maxJobs = null,
            maxConcurrentJobs = null
        )

        assertFalse(quotas.exceedsWorkerLimit(3))
        assertFalse(quotas.exceedsWorkerLimit(5))
        assertTrue(quotas.exceedsWorkerLimit(6))
    }

    @Test
    fun `should check concurrent job limits`() {
        val quotas = ResourceQuotas(
            cpu = null,
            memory = null,
            storage = null,
            maxWorkers = null,
            maxJobs = null,
            maxConcurrentJobs = 3
        )

        assertFalse(quotas.exceedsConcurrentJobLimit(2))
        assertFalse(quotas.exceedsConcurrentJobLimit(3))
        assertTrue(quotas.exceedsConcurrentJobLimit(4))
    }
}

class ResourceLimitTest {

    @Test
    fun `should create valid resource limit`() {
        val limit = ResourceLimit("100m", "1")

        assertEquals("100m", limit.requests)
        assertEquals("1", limit.limits)
    }

    @Test
    fun `should reject blank values`() {
        assertThrows<IllegalArgumentException> {
            ResourceLimit("", "1")
        }

        assertThrows<IllegalArgumentException> {
            ResourceLimit("100m", "")
        }
    }
}