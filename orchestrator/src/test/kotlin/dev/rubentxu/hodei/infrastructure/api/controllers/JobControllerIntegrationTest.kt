package dev.rubentxu.hodei.infrastructure.api.controllers

import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobListResponse
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.CreateAdHocJobRequest
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobContentDto
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobContentTypeDto
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobDto
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.JobStatusDto
import dev.rubentxu.hodei.jobmanagement.infrastructure.api.dto.CancelJobRequest
import dev.rubentxu.hodei.infrastructure.api.dto.ErrorResponseDto
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

class JobControllerIntegrationTest : BehaviorSpec({
    
    Given("a running application with Job API") {
        
        When("listing jobs") {
            then("should return empty list initially") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/jobs")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val jobList = response.body<JobListResponse>()
                    jobList.data.size shouldBe 0
                    jobList.meta.totalElements shouldBe 0
                }
            }
        }
        
        When("creating an ad-hoc job") {
            then("should create job successfully") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val createRequest = CreateAdHocJobRequest(
                        name = "test-job-1",
                        description = "Test job for integration test",
                        content = JobContentDto(
                            type = JobContentTypeDto.SHELL_COMMANDS,
                            shellCommands = listOf("echo 'Hello World'", "sleep 1"),
                            timeout = "5m"
                        ),
                        priority = 75,
                        labels = mapOf("test" to "true", "environment" to "integration")
                    )
                    
                    val response = client.post("/v1/jobs") {
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                    
                    response.status shouldBe HttpStatusCode.Created
                    val createdJob = response.body<JobDto>()
                    
                    createdJob.name shouldBe "test-job-1"
                    createdJob.description shouldBe "Test job for integration test"
                    createdJob.status shouldBe JobStatusDto.PENDING
                    createdJob.priority shouldBe 75
                    createdJob.content.type shouldBe JobContentTypeDto.SHELL_COMMANDS
                    createdJob.content.shellCommands shouldBe listOf("echo 'Hello World'", "sleep 1")
                    createdJob.labels["test"] shouldBe "true"
                    createdJob.id shouldNotBe null
                }
            }
        }
        
        When("creating a job with Kotlin script") {
            then("should create Kotlin job successfully") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val kotlinScript = """
                        stage("Build") {
                            step("Compile") {
                                sh("./gradlew build")
                            }
                        }
                        
                        stage("Test") {
                            step("Unit Tests") {
                                sh("./gradlew test")
                            }
                        }
                    """.trimIndent()
                    
                    val createRequest = CreateAdHocJobRequest(
                        name = "kotlin-pipeline-job",
                        description = "Kotlin DSL pipeline job",
                        content = JobContentDto(
                            type = JobContentTypeDto.KOTLIN_SCRIPT,
                            kotlinScript = kotlinScript,
                            timeout = "15m"
                        ),
                        priority = 50
                    )
                    
                    val response = client.post("/v1/jobs") {
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                    
                    response.status shouldBe HttpStatusCode.Created
                    val createdJob = response.body<JobDto>()
                    
                    createdJob.name shouldBe "kotlin-pipeline-job"
                    createdJob.content.type shouldBe JobContentTypeDto.KOTLIN_SCRIPT
                    createdJob.content.kotlinScript shouldBe kotlinScript
                }
            }
        }
        
        When("getting a specific job") {
            then("should return job details") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    // First create a job
                    val createRequest = CreateAdHocJobRequest(
                        name = "job-to-fetch",
                        content = JobContentDto(
                            type = JobContentTypeDto.SHELL_COMMANDS,
                            shellCommands = listOf("echo 'test'")
                        )
                    )
                    
                    val createResponse = client.post("/v1/jobs") {
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                    val createdJob = createResponse.body<JobDto>()
                    
                    // Then get the job
                    val getResponse = client.get("/v1/jobs/${createdJob.id}")
                    
                    getResponse.status shouldBe HttpStatusCode.OK
                    val fetchedJob = getResponse.body<JobDto>()
                    
                    fetchedJob.id shouldBe createdJob.id
                    fetchedJob.name shouldBe "job-to-fetch"
                }
            }
        }
        
        When("getting non-existent job") {
            then("should return 404") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    val response = client.get("/v1/jobs/non-existent-id")
                    
                    response.status shouldBe HttpStatusCode.NotFound
                    val error = response.body<ErrorResponseDto>()
                    error.error shouldBe "NotFoundError"
                }
            }
        }
        
        When("cancelling a job") {
            then("should cancel job successfully") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    // Create a job first
                    val createRequest = CreateAdHocJobRequest(
                        name = "job-to-cancel",
                        content = JobContentDto(
                            type = JobContentTypeDto.SHELL_COMMANDS,
                            shellCommands = listOf("sleep 30")
                        )
                    )
                    
                    val createResponse = client.post("/v1/jobs") {
                        contentType(ContentType.Application.Json)
                        setBody(createRequest)
                    }
                    val createdJob = createResponse.body<JobDto>()
                    
                    // Cancel the job
                    val cancelRequest = CancelJobRequest(
                        reason = "Testing cancellation",
                        force = false
                    )
                    
                    val cancelResponse = client.delete("/v1/jobs/${createdJob.id}") {
                        contentType(ContentType.Application.Json)
                        setBody(cancelRequest)
                    }
                    
                    cancelResponse.status shouldBe HttpStatusCode.OK
                    val cancelledJob = cancelResponse.body<JobDto>()
                    cancelledJob.status shouldBe JobStatusDto.CANCELLED
                }
            }
        }
        
        When("listing jobs with pagination") {
            then("should paginate results correctly") {
                testApplication {
                    application {
                        configureTestModulesBasic()
                    }
                    
                    val client = createClient {
                        install(ContentNegotiation) {
                            json()
                        }
                    }
                    
                    // Create multiple jobs
                    repeat(5) { i ->
                        val createRequest = CreateAdHocJobRequest(
                            name = "pagination-job-$i",
                            content = JobContentDto(
                                type = JobContentTypeDto.SHELL_COMMANDS,
                                shellCommands = listOf("echo 'Job $i'")
                            )
                        )
                        
                        client.post("/v1/jobs") {
                            contentType(ContentType.Application.Json)
                            setBody(createRequest)
                        }
                    }
                    
                    // Test pagination
                    val response = client.get("/v1/jobs?page=0&size=2")
                    
                    response.status shouldBe HttpStatusCode.OK
                    val jobList = response.body<JobListResponse>()
                    
                    jobList.data.size shouldBe 2
                    jobList.meta.page shouldBe 0
                    jobList.meta.size shouldBe 2
                    jobList.meta.totalElements shouldBe 5
                    jobList.meta.totalPages shouldBe 3
                    jobList.meta.hasNext shouldBe true
                    jobList.meta.hasPrevious shouldBe false
                }
            }
        }
    }
})