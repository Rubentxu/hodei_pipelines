package dev.rubentxu.hodei.pipelines.infrastructure.script

import dev.rubentxu.hodei.pipelines.domain.job.Job
import dev.rubentxu.hodei.pipelines.domain.job.JobDefinition
import dev.rubentxu.hodei.pipelines.domain.job.JobId
import dev.rubentxu.hodei.pipelines.domain.worker.WorkerId
import dev.rubentxu.hodei.pipelines.port.JobExecutionEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PipelineScriptExecutorTest {
    
    private val scriptExecutor = PipelineScriptExecutor()
    
    @Test
    fun `should execute simple task in pipeline script`() = runTest {
        // Given - Pipeline script similar to Gradle DSL
        val script = """
            tasks.register("build") {
                doLast {
                    println("Building project...")
                    env["BUILD_STATUS"] = "success"
                }
            }
            
            tasks.register("test") {
                dependsOn("build")
                doLast {
                    println("Running tests...")
                    println("Build status: " + env["BUILD_STATUS"])
                }
            }
            
            // Execute specific task
            tasks.getByName("test").execute()
        """.trimIndent()
        
        val job = Job(
            id = JobId("pipeline-job-1"),
            definition = JobDefinition(
                name = "Pipeline Task Job",
                command = listOf("kotlin-script"),
                workingDirectory = "/tmp",
                environment = mapOf("SCRIPT_CONTENT" to script)
            )
        )
        
        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()
        
        // Then
        assertTrue(events.any { it is JobExecutionEvent.Started })
        assertTrue(events.any { it is JobExecutionEvent.Completed })
        
        val completedEvent = events.find { it is JobExecutionEvent.Completed } as? JobExecutionEvent.Completed
        assertNotNull(completedEvent)
        assertTrue(completedEvent!!.output.contains("Building project..."))
        assertTrue(completedEvent.output.contains("Running tests..."))
        assertTrue(completedEvent.output.contains("Build status: success"))
    }
    
    @Test
    fun `should support task dependencies like gradle`() = runTest {
        // Given
        val script = """
            tasks.register("compile") {
                doLast {
                    println("Compiling sources...")
                }
            }
            
            tasks.register("processResources") {
                doLast {
                    println("Processing resources...")
                }
            }
            
            tasks.register("classes") {
                dependsOn("compile", "processResources")
                doLast {
                    println("Classes ready!")
                }
            }
            
            tasks.register("test") {
                dependsOn("classes")
                doLast {
                    println("Running unit tests...")
                }
            }
            
            tasks.register("build") {
                dependsOn("classes", "test")
                doLast {
                    println("Build completed!")
                }
            }
            
            // Execute build - should run all dependencies in correct order
            tasks.getByName("build").execute()
        """.trimIndent()
        
        val job = Job(
            id = JobId("dependency-job-1"),
            definition = JobDefinition(
                name = "Dependency Task Job",
                command = listOf("kotlin-script"),
                workingDirectory = "/tmp",
                environment = mapOf("SCRIPT_CONTENT" to script)
            )
        )
        
        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()
        
        // Then
        val completedEvent = events.find { it is JobExecutionEvent.Completed } as? JobExecutionEvent.Completed
        assertNotNull(completedEvent)
        
        val output = completedEvent!!.output
        val lines = output.lines().filter { it.isNotBlank() }
        
        // Check that dependencies run before dependent tasks
        val compileIndex = lines.indexOfFirst { it.contains("Compiling sources") }
        val resourcesIndex = lines.indexOfFirst { it.contains("Processing resources") }
        val classesIndex = lines.indexOfFirst { it.contains("Classes ready") }
        val testIndex = lines.indexOfFirst { it.contains("Running unit tests") }
        val buildIndex = lines.indexOfFirst { it.contains("Build completed") }
        
        assertTrue(compileIndex < classesIndex)
        assertTrue(resourcesIndex < classesIndex)
        assertTrue(classesIndex < testIndex)
        assertTrue(testIndex < buildIndex)
    }
    
    @Test
    fun `should support task configuration blocks`() = runTest {
        // Given
        val script = """
            tasks.register("dockerBuild") {
                val imageName = "my-app:latest"
                val dockerFile = "Dockerfile"
                
                doFirst {
                    println("Preparing Docker build...")
                    println("Image: " + imageName)
                    println("Dockerfile: " + dockerFile)
                }
                
                doLast {
                    println("Docker build completed!")
                    env["DOCKER_IMAGE"] = imageName
                }
            }
            
            tasks.register("dockerPush") {
                dependsOn("dockerBuild")
                doLast {
                    val image = env["DOCKER_IMAGE"] ?: "unknown"
                    println("Pushing Docker image: " + image)
                }
            }
            
            tasks.getByName("dockerPush").execute()
        """.trimIndent()
        
        val job = Job(
            id = JobId("config-job-1"),
            definition = JobDefinition(
                name = "Configuration Task Job",
                command = listOf("kotlin-script"),
                workingDirectory = "/tmp",
                environment = mapOf("SCRIPT_CONTENT" to script)
            )
        )
        
        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()
        
        // Then
        val completedEvent = events.find { it is JobExecutionEvent.Completed } as? JobExecutionEvent.Completed
        assertNotNull(completedEvent)
        
        val output = completedEvent!!.output
        assertTrue(output.contains("Preparing Docker build..."))
        assertTrue(output.contains("Image: my-app:latest"))
        assertTrue(output.contains("Docker build completed!"))
        assertTrue(output.contains("Pushing Docker image: my-app:latest"))
    }
    
    @Test
    fun `should handle task execution failure`() = runTest {
        // Given
        val script = """
            tasks.register("failingTask") {
                doLast {
                    println("About to fail...")
                    throw RuntimeException("Task execution failed!")
                }
            }
            
            tasks.getByName("failingTask").execute()
        """.trimIndent()
        
        val job = Job(
            id = JobId("failing-job-1"),
            definition = JobDefinition(
                name = "Failing Task Job",
                command = listOf("kotlin-script"),
                workingDirectory = "/tmp",
                environment = mapOf("SCRIPT_CONTENT" to script)
            )
        )
        
        // When
        val events = scriptExecutor.execute(job, WorkerId("test-worker")).toList()
        
        // Then
        assertTrue(events.any { it is JobExecutionEvent.Started })
        assertTrue(events.any { it is JobExecutionEvent.Failed })
        
        val failedEvent = events.find { it is JobExecutionEvent.Failed } as? JobExecutionEvent.Failed
        assertNotNull(failedEvent)
        assertTrue(failedEvent!!.error.contains("Task execution failed!"))
    }
}