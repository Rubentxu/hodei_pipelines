package dev.rubentxu.hodei.jobmanagement.domain.entities

import dev.rubentxu.hodei.shared.domain.primitives.DomainId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.datetime.Clock

class JobQueueTest : DescribeSpec({
    describe("JobQueue") {
        val now = Clock.System.now()
        val resourcePoolId = DomainId.generate()

        describe("creation") {
            it("should create a valid job queue") {
                val queue = JobQueue(
                    id = DomainId.generate(),
                    name = "test-queue",
                    resourcePoolId = resourcePoolId,
                    queueType = QueueType.FIFO,
                    priority = QueuePriority.NORMAL,
                    maxConcurrentJobs = 10,
                    maxQueuedJobs = 100,
                    createdAt = now,
                    updatedAt = now
                )

                queue.name shouldBe "test-queue"
                queue.resourcePoolId shouldBe resourcePoolId
                queue.queueType shouldBe QueueType.FIFO
                queue.priority shouldBe QueuePriority.NORMAL
                queue.maxConcurrentJobs shouldBe 10
                queue.maxQueuedJobs shouldBe 100
                queue.isActive shouldBe true
            }

            it("should fail with blank name") {
                shouldThrow<IllegalArgumentException> {
                    JobQueue(
                        id = DomainId.generate(),
                        name = "",
                        resourcePoolId = resourcePoolId,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }

            it("should fail with zero or negative concurrent jobs limit") {
                shouldThrow<IllegalArgumentException> {
                    JobQueue(
                        id = DomainId.generate(),
                        name = "test-queue",
                        resourcePoolId = resourcePoolId,
                        maxConcurrentJobs = 0,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }

            it("should fail with zero or negative queued jobs limit") {
                shouldThrow<IllegalArgumentException> {
                    JobQueue(
                        id = DomainId.generate(),
                        name = "test-queue",
                        resourcePoolId = resourcePoolId,
                        maxQueuedJobs = -1,
                        createdAt = now,
                        updatedAt = now
                    )
                }
            }
        }

        describe("behavior") {
            val queue = JobQueue(
                id = DomainId.generate(),
                name = "test-queue",
                resourcePoolId = resourcePoolId,
                createdAt = now,
                updatedAt = now
            )

            it("should update priority") {
                val updatedQueue = queue.updatePriority(QueuePriority.HIGH)
                
                updatedQueue.priority shouldBe QueuePriority.HIGH
                updatedQueue.updatedAt shouldNotBe queue.updatedAt
            }

            it("should update limits") {
                val updatedQueue = queue.updateLimits(maxConcurrent = 5, maxQueued = 50)
                
                updatedQueue.maxConcurrentJobs shouldBe 5
                updatedQueue.maxQueuedJobs shouldBe 50
                updatedQueue.updatedAt shouldNotBe queue.updatedAt
            }

            it("should activate and deactivate") {
                val deactivated = queue.deactivate()
                deactivated.isActive shouldBe false
                deactivated.updatedAt shouldNotBe queue.updatedAt

                val activated = deactivated.activate()
                activated.isActive shouldBe true
                activated.updatedAt shouldNotBe deactivated.updatedAt
            }
        }
    }

    describe("QueuePriority") {
        it("should have correct priority values") {
            QueuePriority.CRITICAL.value shouldBe 100
            QueuePriority.HIGH.value shouldBe 75
            QueuePriority.NORMAL.value shouldBe 50
            QueuePriority.LOW.value shouldBe 25
            QueuePriority.BACKGROUND.value shouldBe 0
        }
    }

    describe("JobPriority") {
        it("should have correct priority values") {
            JobPriority.URGENT.value shouldBe 100
            JobPriority.HIGH.value shouldBe 75
            JobPriority.NORMAL.value shouldBe 50
            JobPriority.LOW.value shouldBe 25
            JobPriority.DEFERRED.value shouldBe 0
        }
    }
})