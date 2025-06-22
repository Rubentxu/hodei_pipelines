package dev.rubentxu.hodei.pipelines.infrastructure.extensions

import dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions.DefaultExtensionManager
import dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions.DockerExtension
import dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions.ExtensionBase
import dev.rubentxu.hodei.pipelines.domain.worker.ports.ExtensionManager
import dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions.GitExtension
import dev.rubentxu.hodei.pipelines.application.worker.dsl.extensions.NotificationExtension
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.ParameterType
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepDefinition
import dev.rubentxu.hodei.pipelines.domain.worker.model.dsl.StepResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ExtensionManagerTest {
    
    private lateinit var extensionManager: ExtensionManager
    
    @TempDir
    lateinit var tempDir: File
    
    @BeforeEach
    fun setup() {
        extensionManager = DefaultExtensionManager()
    }
    
    @Test
    fun `should register and retrieve extension`() {
        // Given
        val extension = TestExtension()
        
        // When
        extensionManager.registerExtension(extension)
        
        // Then
        assertEquals(extension, extensionManager.getExtension("test"))
        assertTrue(extensionManager.getAllExtensions().containsKey("test"))
    }
    
    @Test
    fun `should return null for non-existent extension`() {
        // When
        val result = extensionManager.getExtension("non-existent")
        
        // Then
        assertNull(result)
    }
    
    @Test
    fun `should collect steps from all extensions`() {
        // Given
        val extension1 = TestExtension()
        val extension2 = AnotherTestExtension()
        
        extensionManager.registerExtension(extension1)
        extensionManager.registerExtension(extension2)
        
        // When
        val availableSteps = extensionManager.getAvailableSteps()
        
        // Then
        assertTrue(availableSteps.containsKey("test.testStep"))
        assertTrue(availableSteps.containsKey("another.anotherStep"))
    }
    
    @Test
    fun `should collect global variables from all extensions`() {
        // Given
        val extension = TestExtension()
        extensionManager.registerExtension(extension)
        
        // When
        val globalVars = extensionManager.getGlobalVariables()
        
        // Then
        assertTrue(globalVars.containsKey("test.testVar"))
        assertEquals("testValue", globalVars["test.testVar"])
    }
    
    @Test
    fun `should unload extension`() {
        // Given
        val extension = TestExtension()
        extensionManager.registerExtension(extension)
        assertTrue(extensionManager.getAllExtensions().containsKey("test"))
        
        // When
        extensionManager.unloadExtension("test")
        
        // Then
        assertFalse(extensionManager.getAllExtensions().containsKey("test"))
        assertNull(extensionManager.getExtension("test"))
    }
    
    @Test
    fun `built-in git extension should provide checkout step`() {
        // Given
        val gitExtension = GitExtension()
        
        // When
        val steps = gitExtension.getSteps()
        
        // Then
        assertTrue(steps.containsKey("checkout"))
        assertTrue(steps.containsKey("tag"))
        
        val checkoutStep = steps["checkout"]!!
        assertEquals("checkout", checkoutStep.name)
        assertTrue(checkoutStep.parameters.containsKey("url"))
        assertTrue(checkoutStep.parameters["url"]!!.required)
    }
    
    @Test
    fun `built-in docker extension should provide build and push steps`() {
        // Given
        val dockerExtension = DockerExtension()
        
        // When
        val steps = dockerExtension.getSteps()
        
        // Then
        assertTrue(steps.containsKey("build"))
        assertTrue(steps.containsKey("push"))
        
        val buildStep = steps["build"]!!
        assertEquals("build", buildStep.name)
        assertTrue(buildStep.parameters.containsKey("tag"))
        assertTrue(buildStep.parameters["tag"]!!.required)
    }
    
    @Test
    fun `built-in notification extension should provide slack and email steps`() {
        // Given
        val notificationExtension = NotificationExtension()
        
        // When
        val steps = notificationExtension.getSteps()
        
        // Then
        assertTrue(steps.containsKey("slack"))
        assertTrue(steps.containsKey("email"))
        
        val slackStep = steps["slack"]!!
        assertEquals("slack", slackStep.name)
        assertTrue(slackStep.parameters.containsKey("channel"))
        assertTrue(slackStep.parameters.containsKey("message"))
    }
    
    // Test extension implementations
    private class TestExtension : ExtensionBase() {
        override val identifier = "test"
        override val version = "1.0.0"
        override val description = "Test extension"
        override val author = "Test Author"
        override val minimumPipelineVersion = "1.0.0"
        
        override fun getSteps(): Map<String, StepDefinition> = mapOf(
            "testStep" to StepDefinition(
                name = "testStep",
                description = "A test step",
                parameters = mapOf(
                    "param1" to ParameterDefinition("param1", ParameterType.STRING, required = true)
                ),
                executor = { _, _ -> StepResult.Success("Test step executed") }
            )
        )
        
        override fun getGlobalVariables(): Map<String, Any> = mapOf(
            "testVar" to "testValue"
        )
    }
    
    private class AnotherTestExtension : ExtensionBase() {
        override val identifier = "another"
        override val version = "1.0.0"
        override val description = "Another test extension"
        override val author = "Test Author"
        override val minimumPipelineVersion = "1.0.0"
        
        override fun getSteps(): Map<String, StepDefinition> = mapOf(
            "anotherStep" to StepDefinition(
                name = "anotherStep",
                description = "Another test step",
                executor = { _, _ -> StepResult.Success("Another step executed") }
            )
        )
    }
}