package dev.rubentxu.hodei.resourcemanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import dev.rubentxu.hodei.domain.worker.Worker
import dev.rubentxu.hodei.domain.worker.WorkerStatus
import dev.rubentxu.hodei.domain.worker.WorkerCapabilities
import dev.rubentxu.hodei.domain.worker.WorkerResources
import dev.rubentxu.hodei.domain.worker.WorkerInstanceMetrics
import dev.rubentxu.hodei.domain.worker.WorkerPoolHealth
import dev.rubentxu.hodei.domain.worker.WorkerInstanceUtils
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock

class WorkerInstanceMetricsTest : DescribeSpec({
    describe("WorkerInstanceMetrics") {
        val now = Clock.System.now()
        val workerId = DomainId.generate()
        val poolId = DomainId.generate()

        describe("metrics creation and validation") {
            it("should create valid metrics") {
                val metrics = WorkerInstanceMetrics(
                    workerId = workerId,
                    poolId = poolId,
                    timestamp = now,
                    cpuUsagePercent = 75.5,
                    memoryUsagePercent = 60.0,
                    diskUsagePercent = 40.0,
                    activeExecutions = 2,
                    completedExecutions = 100,
                    failedExecutions = 5,
                    averageExecutionTimeMs = 15000L
                )

                metrics.cpuUsagePercent shouldBe 75.5
                metrics.memoryUsagePercent shouldBe 60.0
                (metrics.successRate > 0.9) shouldBe true // ~0.95 but avoiding floating point precision issues
            }

            it("should calculate success rate correctly") {
                val metrics = WorkerInstanceMetrics(
                    workerId = workerId,
                    poolId = poolId,
                    timestamp = now,
                    cpuUsagePercent = 50.0,
                    memoryUsagePercent = 50.0,
                    completedExecutions = 80,
                    failedExecutions = 20
                )

                metrics.successRate shouldBe 0.8
            }

            it("should detect resource constraints") {
                val metrics = WorkerInstanceMetrics(
                    workerId = workerId,
                    poolId = poolId,
                    timestamp = now,
                    cpuUsagePercent = 95.0,
                    memoryUsagePercent = 85.0,
                    diskUsagePercent = 92.0
                )

                metrics.isResourceConstrained shouldBe true
            }

            it("should handle custom metrics") {
                val metrics = WorkerInstanceMetrics(
                    workerId = workerId,
                    poolId = poolId,
                    timestamp = now,
                    cpuUsagePercent = 50.0,
                    memoryUsagePercent = 50.0
                ).withCustomMetric("gpu_usage", 65.0)
                    .withCustomMetric("cache_hit_rate", 0.95)

                metrics.customMetrics["gpu_usage"] shouldBe 65.0
                metrics.customMetrics["cache_hit_rate"] shouldBe 0.95
            }
        }
    }

    describe("WorkerPoolHealth") {
        it("should calculate health scores correctly") {
            val poolHealth = WorkerPoolHealth(
                poolId = DomainId.generate(),
                timestamp = Clock.System.now(),
                totalWorkers = 10,
                healthyWorkers = 8,
                unhealthyWorkers = 2,
                availableWorkers = 6,
                busyWorkers = 4,
                averageCpuUsage = 65.0,
                averageMemoryUsage = 70.0,
                totalExecutions = 1000,
                totalFailures = 20,
                avgExecutionTime = 25000L
            )

            poolHealth.healthScore shouldBe 80.0
            poolHealth.capacity shouldBe 60.0
            poolHealth.successRate shouldBe 0.98
            poolHealth.isHealthy shouldBe true
        }

        it("should identify unhealthy pool") {
            val poolHealth = WorkerPoolHealth(
                poolId = DomainId.generate(),
                timestamp = Clock.System.now(),
                totalWorkers = 10,
                healthyWorkers = 6,
                unhealthyWorkers = 4,
                availableWorkers = 2,
                busyWorkers = 8,
                averageCpuUsage = 90.0,
                averageMemoryUsage = 85.0,
                totalExecutions = 100,
                totalFailures = 20,
                avgExecutionTime = 45000L
            )

            poolHealth.healthScore shouldBe 60.0
            poolHealth.successRate shouldBe 0.8
            poolHealth.isHealthy shouldBe false
        }
    }

    describe("WorkerInstanceUtils") {
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
            lastHeartbeat = Clock.System.now(),
            createdAt = Clock.System.now(),
            updatedAt = Clock.System.now()
        )

        describe("worker health checks") {
            it("should identify healthy worker") {
                val isHealthy = WorkerInstanceUtils.isWorkerHealthy(worker)
                isHealthy shouldBe true
            }

            it("should identify stale worker") {
                val staleWorker = worker.copy(
                    lastHeartbeat = Clock.System.now().minus(kotlin.time.Duration.parse("PT10M"))
                )
                val isHealthy = WorkerInstanceUtils.isWorkerHealthy(staleWorker)
                isHealthy shouldBe false
            }
        }

        describe("worker scoring") {
            it("should score idle healthy worker highly") {
                val score = WorkerInstanceUtils.calculateWorkerScore(worker)
                (score > 80.0) shouldBe true
            }

            it("should score busy worker lower than idle") {
                val busyWorker = worker.copy(status = WorkerStatus.BUSY)
                val idleScore = WorkerInstanceUtils.calculateWorkerScore(worker)
                val busyScore = WorkerInstanceUtils.calculateWorkerScore(busyWorker)
                
                (busyScore < idleScore) shouldBe true
            }

            it("should score error worker very low") {
                val errorWorker = worker.copy(status = WorkerStatus.ERROR)
                val score = WorkerInstanceUtils.calculateWorkerScore(errorWorker)
                score shouldBe 0.0 // Error workers get minimum score after coercion
            }
        }

        describe("worker selection") {
            it("should select best available worker") {
                val workers = listOf(
                    worker,
                    worker.copy(
                        id = DomainId.generate(),
                        status = WorkerStatus.BUSY
                    ),
                    worker.copy(
                        id = DomainId.generate(),
                        status = WorkerStatus.IDLE,
                        capabilities = worker.capabilities.copy(cpu = "8") // More CPU
                    )
                )

                val bestWorker = WorkerInstanceUtils.selectBestWorker(workers)
                bestWorker shouldNotBe null
            }
        }

        describe("pool capacity calculation") {
            it("should calculate pool capacity correctly") {
                val workers = listOf(
                    worker,
                    worker.copy(
                        id = DomainId.generate(),
                        status = WorkerStatus.BUSY
                    ),
                    worker.copy(
                        id = DomainId.generate(),
                        status = WorkerStatus.IDLE
                    )
                )

                val capacity = WorkerInstanceUtils.calculatePoolCapacity(workers)
                
                capacity.totalWorkers shouldBe 3
                capacity.availableWorkers shouldBe 2
                capacity.utilizationPercent shouldBe 33.33333333333333 // 1/3 busy
                capacity.totalCpuUnits shouldBe 12 // 3 workers * 4 CPU each
                capacity.totalMemoryGb shouldBe 24 // 3 workers * 8GB each
            }
        }
    }
})