package dev.rubentxu.hodei.pipelines.domain.orchestration

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkerTemplateTest {
    
    private fun createTestTemplate(): WorkerTemplate {
        return WorkerTemplate(
            id = WorkerTemplateId("test-template"),
            name = "Test Template",
            image = "test:latest",
            resources = ResourceRequirements(cpu = "1000m", memory = "2Gi"),
            capabilities = mapOf("build" to "true", "test" to "enabled"),
            labels = mapOf("env" to "test", "team" to "platform")
        )
    }
    
    @Test
    fun `should create worker template with valid configuration`() {
        val template = createTestTemplate()
        
        assertEquals("test-template", template.id.value)
        assertEquals("Test Template", template.name)
        assertEquals("test:latest", template.image)
        assertEquals("1000m", template.resources.cpu)
        assertEquals("2Gi", template.resources.memory)
    }
    
    @Test
    fun `should reject invalid template id`() {
        assertThrows<IllegalArgumentException> {
            WorkerTemplateId("")
        }
    }
    
    @Test
    fun `should match worker requirements with exact capabilities`() {
        val template = createTestTemplate()
        val requirements = WorkerRequirements(
            capabilities = mapOf("build" to "true"),
            resources = ResourceRequirements(cpu = "500m", memory = "1Gi")
        )
        
        assertTrue(template.matches(requirements))
    }
    
    @Test
    fun `should not match worker requirements with missing capabilities`() {
        val template = createTestTemplate()
        val requirements = WorkerRequirements(
            capabilities = mapOf("deploy" to "true") // Template doesn't have this capability
        )
        
        assertFalse(template.matches(requirements))
    }
    
    @Test
    fun `should not match worker requirements with mismatched capability values`() {
        val template = createTestTemplate()
        val requirements = WorkerRequirements(
            capabilities = mapOf("build" to "false") // Template has "build" to "true"
        )
        
        assertFalse(template.matches(requirements))
    }
    
    @Test
    fun `should generate worker name from template`() {
        val template = createTestTemplate()
        
        val workerName = template.generateWorkerName()
        
        assertTrue(workerName.startsWith("test-template-"))
        assertTrue(workerName.contains(System.currentTimeMillis().toString().take(8))) // Check timestamp portion
    }
    
    @Test
    fun `should generate worker name with sanitized template name`() {
        val template = WorkerTemplate(
            id = WorkerTemplateId("test-template"),
            name = "Build Worker Template", // Contains spaces
            image = "test:latest",
            resources = ResourceRequirements()
        )
        
        val workerName = template.generateWorkerName()
        
        assertTrue(workerName.startsWith("build-worker-template-"))
        assertFalse(workerName.contains(" ")) // Should not contain spaces
    }
    
    @Test
    fun `should merge template with overrides`() {
        val template = createTestTemplate()
        val additionalEnv = mapOf("NEW_VAR" to "value")
        val additionalLabels = mapOf("version" to "1.0")
        val resourceOverrides = ResourceRequirements(cpu = "2000m", memory = "4Gi")
        
        val mergedTemplate = template.withOverrides(
            additionalEnv = additionalEnv,
            additionalLabels = additionalLabels,
            resourceOverrides = resourceOverrides
        )
        
        assertTrue(mergedTemplate.environment.containsKey("NEW_VAR"))
        assertEquals("value", mergedTemplate.environment["NEW_VAR"])
        
        assertTrue(mergedTemplate.labels.containsKey("version"))
        assertEquals("1.0", mergedTemplate.labels["version"])
        
        // Original labels should still be present
        assertEquals("test", mergedTemplate.labels["env"])
        
        assertEquals("2000m", mergedTemplate.resources.cpu)
        assertEquals("4Gi", mergedTemplate.resources.memory)
    }
}

class ResourceRequirementsTest {
    
    @Test
    fun `should compare CPU requirements correctly`() {
        val resources1 = ResourceRequirements(cpu = "1000m", memory = "1Gi")
        val resources2 = ResourceRequirements(cpu = "500m", memory = "512Mi")
        
        assertTrue(resources1.canSatisfy(resources2))
        assertFalse(resources2.canSatisfy(resources1))
    }
    
    @Test
    fun `should compare CPU requirements with different units`() {
        val resources1 = ResourceRequirements(cpu = "1", memory = "1Gi") // 1 CPU = 1000m
        val resources2 = ResourceRequirements(cpu = "500m", memory = "512Mi")
        
        assertTrue(resources1.canSatisfy(resources2))
        assertFalse(resources2.canSatisfy(resources1))
    }
    
    @Test
    fun `should compare memory requirements correctly`() {
        val resources1 = ResourceRequirements(cpu = "500m", memory = "2Gi")
        val resources2 = ResourceRequirements(cpu = "500m", memory = "1Gi")
        
        assertTrue(resources1.canSatisfy(resources2))
        assertFalse(resources2.canSatisfy(resources1))
    }
    
    @Test
    fun `should compare memory requirements with different units`() {
        val resources1 = ResourceRequirements(cpu = "500m", memory = "2048Mi") // 2048Mi = 2Gi
        val resources2 = ResourceRequirements(cpu = "500m", memory = "1Gi")
        
        assertTrue(resources1.canSatisfy(resources2))
    }
    
    @Test
    fun `should compare GPU requirements correctly`() {
        val resources1 = ResourceRequirements(cpu = "500m", memory = "1Gi", gpu = 2)
        val resources2 = ResourceRequirements(cpu = "500m", memory = "1Gi", gpu = 1)
        val resources3 = ResourceRequirements(cpu = "500m", memory = "1Gi", gpu = 3)
        
        assertTrue(resources1.canSatisfy(resources2))
        assertFalse(resources1.canSatisfy(resources3))
    }
    
    @Test
    fun `should handle resource requirements with same values`() {
        val resources1 = ResourceRequirements(cpu = "1000m", memory = "1Gi")
        val resources2 = ResourceRequirements(cpu = "1000m", memory = "1Gi")
        
        assertTrue(resources1.canSatisfy(resources2))
        assertTrue(resources2.canSatisfy(resources1))
    }
}