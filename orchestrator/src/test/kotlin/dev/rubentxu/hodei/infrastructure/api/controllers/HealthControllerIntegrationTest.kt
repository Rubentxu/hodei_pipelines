package dev.rubentxu.hodei.infrastructure.api.controllers

import dev.rubentxu.hodei.infrastructure.api.dto.HealthCheckResponse
import dev.rubentxu.hodei.infrastructure.api.dto.ReadinessCheckResponse
import dev.rubentxu.hodei.infrastructure.api.dto.LivenessCheckResponse
import dev.rubentxu.hodei.infrastructure.api.dto.SystemMetricsResponse
import dev.rubentxu.hodei.infrastructure.api.dto.AdminStatusResponse
import dev.rubentxu.hodei.infrastructure.api.dto.SystemInfoResponse
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.CreateAdHocJobRequest
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobContentDto
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobContentTypeDto
import dev.rubentxu.hodei.infrastructure.config.configureTestModulesBasic
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*

class HealthControllerIntegrationTest : BehaviorSpec({
    
    Given("a running application with Health API") {
        
        When("checking health") {
            then("should return health status") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/health")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val health = response.body<HealthCheckResponse>()
                    
                    health.overall shouldNotBe null
                    health.timestamp shouldNotBe null
                    health.checks shouldNotBe null
                    health.checks["api"] shouldNotBe null
                    health.checks["api"]?.status shouldBe "healthy"
                }
            }
        }
        
        When("checking readiness") {
            then("should return readiness status") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/health/ready")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val readiness = response.body<ReadinessCheckResponse>()
                    
                    readiness.ready shouldBe true
                    readiness.timestamp shouldNotBe null
                    readiness.message shouldNotBe null
                }
            }
        }
        
        When("checking liveness") {
            then("should return liveness status") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/health/live")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val liveness = response.body<LivenessCheckResponse>()
                    
                    liveness.alive shouldBe true
                    liveness.timestamp shouldNotBe null
                    liveness.message shouldBe "System is alive"
                }
            }
        }
        
        When("getting system metrics") {
            then("should return metrics data") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/metrics")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val metrics = response.body<SystemMetricsResponse>()
                    
                    metrics.timestamp shouldNotBe null
                    metrics.jobs shouldNotBe null
                    metrics.executions shouldNotBe null
                    metrics.workers shouldNotBe null
                    
                    // Check job metrics structure
                    metrics.jobs.totalJobs shouldBe 0
                    metrics.jobs.pendingJobs shouldBe 0
                    metrics.jobs.runningJobs shouldBe 0
                    metrics.jobs.completedJobs shouldBe 0
                    metrics.jobs.failedJobs shouldBe 0
                    
                    // Check execution metrics structure
                    metrics.executions.activeExecutions shouldBe 0
                    metrics.executions.totalExecutionsToday shouldBe 0
                    metrics.executions.avgExecutionTime shouldBe 0.0
                    
                    // Check worker metrics structure
                    metrics.workers.connectedWorkers shouldBe 0
                    metrics.workers.activeWorkers shouldBe 0
                    metrics.workers.idleWorkers shouldBe 0
                }
            }
        }
        
        When("getting admin status") {
            then("should return system status") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/admin/status")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val status = response.body<AdminStatusResponse>()
                    
                    status.version shouldBe "1.0.0-MVP"
                    status.environment shouldBe "development"
                    status.timestamp shouldNotBe null
                    status.features shouldNotBe null
                    status.features shouldBe listOf("jobs", "executions", "templates", "pools", "workers", "grpc")
                    status.limits shouldNotBe null
                    status.limits["maxConcurrentJobs"] shouldBe "100"
                    status.limits["maxWorkers"] shouldBe "50"
                    status.limits["maxPools"] shouldBe "10"
                }
            }
        }
        
        When("getting system info") {
            then("should return system information") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/admin/info")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val info = response.body<SystemInfoResponse>()
                    
                    info.applicationName shouldBe "Hodei Pipelines"
                    info.version shouldBe "1.0.0-MVP"
                    info.timestamp shouldNotBe null
                    info.jvmVersion shouldNotBe null
                    info.properties shouldNotBe null
                    info.properties["processors"] shouldNotBe null
                    info.properties["maxMemory"] shouldNotBe null
                    info.properties["freeMemory"] shouldNotBe null
                }
            }
        }
        
        When("creating jobs and checking metrics") {
            then("should reflect job creation in metrics") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    // Create a few jobs
                    repeat(3) { i ->
                        val createRequest = CreateAdHocJobRequest(
                            name = "metrics-test-job-$i",
                            content = JobContentDto(
                                type = JobContentTypeDto.SHELL_COMMANDS,
                                shellCommands = listOf("echo 'Metrics test $i'")
                            )
                        )
                        
                        client.post("/v1/jobs") {
                            contentType(ContentType.Application.Json)
                            setBody(createRequest)
                        }
                    }
                    
                    // Check metrics reflect the created jobs
                    val metricsResponse = client.get("/v1/metrics")
                    val metrics = metricsResponse.body<SystemMetricsResponse>()
                    
                    // JobAPIService and metrics are integrated, so metrics show created jobs
                    metrics.jobs.totalJobs shouldBe 3
                    metrics.jobs.pendingJobs shouldBe 3
                }
            }
        }
    }
})