package dev.rubentxu.hodei.resourcemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.domain.worker.Worker
import dev.rubentxu.hodei.domain.worker.WorkerStatus
import dev.rubentxu.hodei.domain.worker.WorkerCapabilities
import dev.rubentxu.hodei.domain.worker.WorkerResources
import dev.rubentxu.hodei.domain.worker.ResourceUsage
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.datetime.Clock

class WorkerEnhancementsTest : DescribeSpec({
    describe("Worker domain enhancements") {
        val now = Clock.System.now()
        
        describe("Worker health checks") {
            it("should identify healthy worker with recent heartbeat") {
                val worker = Worker(
                    id = DomainId.generate(),
                    poolId = DomainId.generate(),
                    status = WorkerStatus.IDLE,
                    capabilities = WorkerCapabilities(
                        cpu = "4",
                        memory = "8Gi",
                        storage = "100Gi"
                    ),
                    resourceAllocation = WorkerResources(
                        cpuCores = 4.0,
                        memoryGB = 8.0
                    ),
                    lastHeartbeat = now,
                    createdAt = now,
                    updatedAt = now
                )

                worker.isHealthy() shouldBe true
                worker.isAvailable shouldBe true
            }

            it("should identify unhealthy worker with old heartbeat") {
                val oldHeartbeat = now.minus(kotlin.time.Duration.parse("PT10M"))
                val worker = Worker(
                    id = DomainId.generate(),
                    poolId = DomainId.generate(),
                    status = WorkerStatus.IDLE,
                    capabilities = WorkerCapabilities(
                        cpu = "4",
                        memory = "8Gi", 
                        storage = "100Gi"
                    ),
                    resourceAllocation = WorkerResources(
                        cpuCores = 4.0,
                        memoryGB = 8.0
                    ),
                    lastHeartbeat = oldHeartbeat,
                    createdAt = now,
                    updatedAt = now
                )

                worker.isHealthy(heartbeatTimeoutSeconds = 300) shouldBe false
            }
        }

        describe("Worker capabilities") {
            it("should check platform support") {
                val capabilities = WorkerCapabilities(
                    cpu = "8",
                    memory = "16Gi",
                    storage = "200Gi",
                    supportedPlatforms = listOf("linux", "darwin")
                )

                capabilities.supports("linux") shouldBe true
                capabilities.supports("windows") shouldBe false
            }

            it("should check custom capabilities") {
                val capabilities = WorkerCapabilities(
                    cpu = "8",
                    memory = "16Gi",
                    storage = "200Gi",
                    customCapabilities = mapOf(
                        "gpu" to "nvidia-v100",
                        "cuda" to "11.0"
                    )
                )

                capabilities.hasCapability("gpu") shouldBe true
                capabilities.getCapabilityValue("gpu") shouldBe "nvidia-v100"
                capabilities.hasCapability("tpu") shouldBe false
            }
        }

        describe("Resource usage") {
            it("should track resource usage") {
                val usage = ResourceUsage(
                    cpuUsagePercent = 75.5,
                    memoryUsageBytes = 4_294_967_296L, // 4GB
                    storageUsageBytes = 10_737_418_240L // 10GB
                )

                usage.isHighCpuUsage shouldBe false // 75.5% < 80%
                usage.cpuUsagePercent shouldBe 75.5
            }

            it("should handle custom metrics") {
                val usage = ResourceUsage()
                    .withCustomMetric("gpu_usage", 45.0)
                    .withCustomMetric("gpu_memory", 80.0)

                usage.getCustomMetric("gpu_usage") shouldBe 45.0
                usage.getCustomMetric("gpu_memory") shouldBe 80.0
                usage.customMetrics.size shouldBe 2
            }
        }
    }
})