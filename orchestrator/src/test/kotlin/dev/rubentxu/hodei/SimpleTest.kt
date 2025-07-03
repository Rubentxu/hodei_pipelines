package dev.rubentxu.hodei

import dev.rubentxu.hodei.resourcemanagement.application.services.WorkerManagerService
import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals

class SimpleTest {

    @Test
    fun `should create worker manager`() = runTest {
        val workerManager = WorkerManagerService()
        val workers = workerManager.getAllWorkers()
        assertEquals(0, workers.size)
        println("✅ Simple test passed: WorkerManager created successfully")
    }
}