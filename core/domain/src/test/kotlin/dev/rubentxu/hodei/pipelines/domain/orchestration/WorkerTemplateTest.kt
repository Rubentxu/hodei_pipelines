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
        // Corregir para usar los campos correctos de WorkerRequirements
        val requirements = WorkerRequirements(
            resources = ResourceRequirements(cpu = "500m", memory = "1Gi")
        )

        assertTrue(template.matches(requirements))
    }

    @Test
    fun `should generate worker name from template`() {
        val template = createTestTemplate()

        val workerName = template.generateWorkerName()

        // La implementación actual usa name.lowercase().replace(" ", "-")
        assertTrue(workerName.startsWith("test-template-"))
        assertTrue(workerName.contains(System.currentTimeMillis().toString().take(8)))
    }

    @Test
    fun `should generate worker name with sanitized template name`() {
        val template = WorkerTemplate(
            id = WorkerTemplateId("test-template"),
            name = "Build Worker Template", // Contiene espacios
            image = "test:latest",
            resources = ResourceRequirements()
        )

        val workerName = template.generateWorkerName()

        assertTrue(workerName.startsWith("build-worker-template-"))
        assertFalse(workerName.contains(" ")) // No debe contener espacios
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

        // Usar env en lugar de environment
        assertTrue(mergedTemplate.env.containsKey("NEW_VAR"))
        assertEquals("value", mergedTemplate.env["NEW_VAR"])

        assertTrue(mergedTemplate.labels.containsKey("version"))
        assertEquals("1.0", mergedTemplate.labels["version"])

        // Las etiquetas originales deberían estar presentes
        assertEquals("test", mergedTemplate.labels["env"])

        assertEquals("2000m", mergedTemplate.resources.cpu)
        assertEquals("4Gi", mergedTemplate.resources.memory)
    }
}