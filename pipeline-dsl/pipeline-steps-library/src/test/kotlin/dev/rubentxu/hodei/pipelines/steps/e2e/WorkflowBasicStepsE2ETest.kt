package dev.rubentxu.hodei.pipelines.steps.e2e

import dev.rubentxu.hodei.pipelines.dsl.extensions.ExtensionStep
import dev.rubentxu.hodei.pipelines.dsl.model.PipelineExecutionEvent
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests E2E para workflow-basic-steps.
 * Verifica que todos los steps funcionan correctamente de extremo a extremo.
 */
class WorkflowBasicStepsE2ETest : E2ETestBase() {
    
    @Test
    fun `echo step should output message`() = runBlocking {
        // Given
        val message = "Hello from E2E test!"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "echo",
            parameters = mapOf("message" to message)
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains(message)
        assertNoErrors()
    }
    
    @Test
    fun `writeFile and readFile should work together`() = runBlocking {
        // Given
        val fileName = "test.txt"
        val content = "This is test content\nWith multiple lines"
        
        val writeStep = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "writeFile",
            parameters = mapOf(
                "file" to fileName,
                "text" to content,
                "encoding" to "UTF-8"
            )
        )
        
        val readStep = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "readFile",
            parameters = mapOf(
                "file" to fileName,
                "encoding" to "UTF-8"
            ),
            name = "readFileTest"
        )
        
        // When
        executeStep(writeStep)
        executeStep(readStep)
        waitForCompletion()
        
        // Then
        assertFileExists(fileName)
        assertFileContent(fileName, content)
        assertOutputContains("Writing file: $fileName")
        assertOutputContains("Reading file: $fileName")
        
        val readContent = getVariable<String>("readFileTest.content")
        assertEquals(content, readContent)
    }
    
    @Test
    fun `fileExists should detect existing and non-existing files`() = runBlocking {
        // Given
        val existingFile = "existing.txt"
        val nonExistingFile = "not-found.txt"
        createTestFile(existingFile, "test content")
        
        val existsStep = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "fileExists",
            parameters = mapOf("file" to existingFile),
            name = "existsCheck"
        )
        
        val notExistsStep = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "fileExists",
            parameters = mapOf("file" to nonExistingFile),
            name = "notExistsCheck"
        )
        
        // When
        executeStep(existsStep)
        executeStep(notExistsStep)
        waitForCompletion()
        
        // Then
        assertVariable("existsCheck.result", true)
        assertVariable("notExistsCheck.result", false)
        assertOutputContains("Checking file: $existingFile")
        assertOutputContains("File exists: true")
        assertOutputContains("File exists: false")
    }
    
    @Test
    fun `isUnix should detect operating system`() = runBlocking {
        // Given
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "isUnix",
            parameters = emptyMap(),
            name = "osCheck"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        val isUnix = getVariable<Boolean>("osCheck.result")
        assertNotNull(isUnix)
        assertOutputContains("Operating system check:")
        assertOutputContains("Is Unix-like: $isUnix")
        
        // Verify it matches the actual OS
        val expectedUnix = !System.getProperty("os.name").lowercase().contains("windows")
        assertEquals(expectedUnix, isUnix)
    }
    
    @Test
    fun `pwd should return current directory`() = runBlocking {
        // Given
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "pwd",
            parameters = mapOf("tmp" to false),
            name = "pwdCheck"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        val currentDir = getVariable<String>("pwdCheck.result")
        assertEquals(workingDirectory.absolutePath, currentDir)
        assertOutputContains("Current directory: ${workingDirectory.absolutePath}")
    }
    
    @Test
    fun `pwd with tmp should return temp directory`() = runBlocking {
        // Given
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "pwd",
            parameters = mapOf("tmp" to true),
            name = "tmpCheck"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        val tmpDir = getVariable<String>("tmpCheck.result")
        assertEquals(System.getProperty("java.io.tmpdir"), tmpDir)
        assertOutputContains("Current directory: $tmpDir")
    }
    
    @Test
    fun `sleep should pause execution`() = runBlocking {
        // Given
        val sleepTime = 1L // 1 second
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "sleep",
            parameters = mapOf(
                "time" to sleepTime,
                "unit" to "SECONDS"
            )
        )
        
        // When
        val startTime = System.currentTimeMillis()
        executeStep(step)
        waitForCompletion()
        val endTime = System.currentTimeMillis()
        
        // Then
        val elapsed = endTime - startTime
        assertTrue(elapsed >= sleepTime * 1000, "Should have slept for at least $sleepTime seconds")
        assertOutputContains("Sleeping for $sleepTime SECONDS")
        assertOutputContains("Sleep completed")
    }
    
    @Test
    fun `error step should fail with message`() = runBlocking {
        // Given
        val errorMessage = "Test error message"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "error",
            parameters = mapOf("message" to errorMessage)
        )
        
        // When & Then
        assertThrows(errorMessage) {
            executeStep(step)
        }
    }
    
    @Test
    fun `unstable step should mark build as unstable`() = runBlocking {
        // Given
        val message = "Build marked as unstable for testing"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "unstable",
            parameters = mapOf("message" to message)
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("UNSTABLE: $message")
        assertVariable("currentBuild.result", "UNSTABLE")
    }
    
    @Test
    fun `milestone should set milestone information`() = runBlocking {
        // Given
        val ordinal = 1
        val label = "Test Milestone"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "milestone",
            parameters = mapOf(
                "ordinal" to ordinal,
                "label" to label
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Milestone reached:")
        assertOutputContains("Ordinal: $ordinal")
        assertOutputContains("Label: $label")
        assertVariable("milestone.ordinal", ordinal)
        assertVariable("milestone.label", label)
    }
    
    @Test
    fun `mail step should send notification`() = runBlocking {
        // Given
        val to = "test@example.com"
        val subject = "Test Email"
        val body = "This is a test email body"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "mail",
            parameters = mapOf(
                "to" to to,
                "subject" to subject,
                "body" to body,
                "attachLog" to true
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Sending email:")
        assertOutputContains("To: $to")
        assertOutputContains("Subject: $subject")
        assertOutputContains("Body: $body")
        assertOutputContains("Build log will be attached")
        assertOutputContains("Email sent successfully")
    }
    
    @Test
    fun `retry step should attempt multiple times`() = runBlocking {
        // Given
        val retryCount = 3
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "retry",
            parameters = mapOf(
                "count" to retryCount,
                "simulateFailure" to true // Simulate failure on first attempts
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Retry configuration: max $retryCount attempts")
        assertOutputContains("Operation succeeded on attempt $retryCount")
    }
    
    @Test
    fun `catchError should handle exceptions and continue`() = runBlocking {
        // Given
        val buildResult = "UNSTABLE"
        val message = "Caught test error"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "catchError",
            parameters = mapOf(
                "buildResult" to buildResult,
                "message" to message,
                "simulateError" to true
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Executing with error catching...")
        assertOutputContains("Caught error:")
        assertOutputContains("Message: $message")
        assertOutputContains("Continuing execution after error")
        assertVariable("currentBuild.result", buildResult)
    }
    
    @Test
    fun `warnError should warn but continue on error`() = runBlocking {
        // Given
        val message = "Test warning message"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "warnError",
            parameters = mapOf(
                "message" to message,
                "simulateError" to true
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("WARNING: $message")
        assertOutputContains("Continuing after warning")
        assertVariable("currentBuild.result", "UNSTABLE")
    }
    
    @Test
    fun `withEnv should set environment variables`() = runBlocking {
        // Given
        val envVars = listOf("TEST_VAR=test_value", "BUILD_TYPE=release")
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "withEnv",
            parameters = mapOf("env" to envVars)
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Setting environment variables:")
        assertOutputContains("TEST_VAR = test_value")
        assertOutputContains("BUILD_TYPE = release")
        assertOutputContains("Environment configured for nested steps")
    }
    
    @Test
    fun `tool step should load tool`() = runBlocking {
        // Given
        val toolName = "maven"
        val toolType = "build"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "tool",
            parameters = mapOf(
                "name" to toolName,
                "type" to toolType
            ),
            name = "toolLoad"
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Loading tool: $toolName")
        assertOutputContains("Tool type: $toolType")
        assertOutputContains("Tool loaded at: /tools/$toolName")
        assertVariable("toolLoad.path", "/tools/$toolName")
    }
    
    @Test
    fun `waitUntil should wait for condition`() = runBlocking {
        // Given
        val condition = "service_ready"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "waitUntil",
            parameters = mapOf(
                "condition" to condition,
                "initialRecurrencePeriod" to 100L,
                "quiet" to false
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Waiting until condition is met: $condition")
        assertOutputContains("Checking condition")
        assertOutputContains("Condition met after")
    }
    
    @Test
    fun `deleteDir should clean workspace`() = runBlocking {
        // Given
        createTestFile("file1.txt", "content1")
        createTestFile("subdir/file2.txt", "content2")
        
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "deleteDir",
            parameters = emptyMap()
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Deleting workspace directory...")
        assertOutputContains("Directory to delete: ${workingDirectory.absolutePath}")
        assertOutputContains("Directory deleted")
    }
    
    @Test
    fun `node step should allocate node`() = runBlocking {
        // Given
        val label = "linux"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "node",
            parameters = mapOf("label" to label)
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Allocating node with label: $label")
        assertOutputContains("Node allocated: worker-")
    }
    
    @Test
    fun `script step should execute groovy script`() = runBlocking {
        // Given
        val script = "println 'Hello from Groovy script!'"
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "script",
            parameters = mapOf("script" to script)
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Executing script block")
        assertOutputContains("Script: $script")
        assertOutputContains("Script executed successfully")
    }
    
    @Test
    fun `build step should trigger downstream job`() = runBlocking {
        // Given
        val jobName = "downstream-pipeline"
        val parameters = mapOf("VERSION" to "1.0.0", "ENVIRONMENT" to "staging")
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "build",
            parameters = mapOf(
                "job" to jobName,
                "parameters" to parameters,
                "wait" to true,
                "propagate" to true
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Triggering build for job: $jobName")
        assertOutputContains("Parameter: VERSION = 1.0.0")
        assertOutputContains("Parameter: ENVIRONMENT = staging")
        assertOutputContains("Waiting for build to complete...")
        assertOutputContains("Build completed successfully")
    }
    
    @Test
    fun `parallel step should execute branches concurrently`() = runBlocking {
        // Given
        val branches = mapOf(
            "branch1" to listOf("echo 'Branch 1'"),
            "branch2" to listOf("echo 'Branch 2'"),
            "branch3" to listOf("echo 'Branch 3'")
        )
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "parallel",
            parameters = mapOf(
                "branches" to branches,
                "failFast" to true
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Executing ${branches.size} branches in parallel")
        assertOutputContains("Fail fast: true")
        branches.keys.forEach { branchName ->
            assertOutputContains("Branch: $branchName")
        }
        assertOutputContains("Parallel execution completed")
    }
    
    @Test
    fun `timeout step should configure timeout settings`() = runBlocking {
        // Given
        val time = 15L
        val unit = "MINUTES"
        val activity = true
        val step = ExtensionStep(
            extensionName = "workflow-basic-steps",
            action = "timeout",
            parameters = mapOf(
                "time" to time,
                "unit" to unit,
                "activity" to activity
            )
        )
        
        // When
        executeStep(step)
        waitForCompletion()
        
        // Then
        assertOutputContains("Setting timeout: $time $unit")
        assertOutputContains("Activity-based timeout enabled")
        assertVariable("timeout.time", time)
        assertVariable("timeout.unit", unit)
        assertVariable("timeout.activity", activity)
    }
}