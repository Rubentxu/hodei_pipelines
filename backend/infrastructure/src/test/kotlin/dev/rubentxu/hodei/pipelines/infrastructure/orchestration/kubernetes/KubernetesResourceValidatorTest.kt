package dev.rubentxu.hodei.pipelines.infrastructure.orchestration.kubernetes

import dev.rubentxu.hodei.pipelines.domain.orchestration.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KubernetesResourceValidatorTest {

    private lateinit var validator: KubernetesResourceValidator
    private lateinit var configuration: KubernetesOrchestratorConfiguration

    @BeforeEach
    fun setup() {
        validator = KubernetesResourceValidator()
        configuration = KubernetesOrchestratorConfiguration.create(
            maxCpuPerWorker = "4",
            maxMemoryPerWorker = "8Gi"
        )
    }

    @Test
    fun `should validate valid worker template successfully`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("valid-template"),
            name = "Valid Template",
            image = "nginx:latest",
            resources = ResourceRequirements(
                cpu = "1",
                memory = "2Gi"
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Valid)
    }

    @Test
    fun `should reject template with blank ID`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId(""),
            name = "Invalid Template",
            image = "nginx:latest",
            resources = ResourceRequirements()
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Template ID cannot be blank") })
    }

    @Test
    fun `should reject template with invalid ID format`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("Invalid_Template_ID"),
            name = "Invalid Template",
            image = "nginx:latest",
            resources = ResourceRequirements()
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Template ID must follow Kubernetes naming convention") })
    }

    @Test
    fun `should reject template with blank image`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("valid-template"),
            name = "Valid Template",
            image = "",
            resources = ResourceRequirements()
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Container image cannot be blank") })
    }

    @Test
    fun `should reject template with invalid image format`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("valid-template"),
            name = "Valid Template",
            image = "INVALID_IMAGE_NAME",
            resources = ResourceRequirements()
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid container image format") })
    }

    @Test
    fun `should reject template with excessive CPU request`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("cpu-heavy-template"),
            name = "CPU Heavy Template",
            image = "nginx:latest",
            resources = ResourceRequirements(
                cpu = "8", // Exceeds max of 4
                memory = "2Gi"
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("CPU request") && it.contains("exceeds maximum") })
    }

    @Test
    fun `should reject template with excessive memory request`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("memory-heavy-template"),
            name = "Memory Heavy Template",
            image = "nginx:latest",
            resources = ResourceRequirements(
                cpu = "1",
                memory = "16Gi" // Exceeds max of 8Gi
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Memory request") && it.contains("exceeds maximum") })
    }

    @Test
    fun `should reject template with invalid CPU format`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("invalid-cpu-template"),
            name = "Invalid CPU Template",
            image = "nginx:latest",
            resources = ResourceRequirements(
                cpu = "invalid-cpu",
                memory = "2Gi"
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid CPU format") })
    }

    @Test
    fun `should reject template with invalid memory format`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("invalid-memory-template"),
            name = "Invalid Memory Template",
            image = "nginx:latest",
            resources = ResourceRequirements(
                cpu = "1",
                memory = "invalid-memory"
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid memory format") })
    }

    @Test
    fun `should reject template with dangerous security capabilities`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("dangerous-template"),
            name = "Dangerous Template",
            image = "nginx:latest",
            resources = ResourceRequirements(),
            containerSecurityContext = ContainerSecurityContext(
                allowPrivilegeEscalation = true,
                capabilities = ContainerCapabilities(
                    add = listOf("SYS_ADMIN")
                )
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Privilege escalation is not allowed") })
        assertTrue(errors.any { it.contains("Dangerous capability not allowed: SYS_ADMIN") })
    }

    @Test
    fun `should reject template with running as root`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("root-template"),
            name = "Root Template",
            image = "nginx:latest",
            resources = ResourceRequirements(),
            containerSecurityContext = ContainerSecurityContext(
                runAsUser = 0L
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Running as root") && it.contains("not recommended") })
    }

    @Test
    fun `should reject template with too many volumes`() = runTest {
        // Given
        val volumes = (1..15).map { 
            VolumeSpec(
                name = "volume-$it",
                type = "emptyDir",
                source = ""
            )
        }
        
        val template = WorkerTemplate(
            id = WorkerTemplateId("volume-heavy-template"),
            name = "Volume Heavy Template", 
            image = "nginx:latest",
            resources = ResourceRequirements(),
            volumes = volumes
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Too many volumes") })
    }

    @Test
    fun `should reject template with dangerous host path`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("dangerous-hostpath-template"),
            name = "Dangerous HostPath Template",
            image = "nginx:latest",
            resources = ResourceRequirements(),
            volumes = listOf(
                VolumeSpec(
                    name = "docker-socket",
                    type = "hostPath",
                    source = "/var/run/docker.sock"
                )
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Host path not allowed for security reasons") })
    }

    @Test
    fun `should reject template with invalid port numbers`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("invalid-port-template"),
            name = "Invalid Port Template",
            image = "nginx:latest",
            resources = ResourceRequirements(),
            ports = listOf(
                ContainerPort(containerPort = 0),
                ContainerPort(containerPort = 80000),
                ContainerPort(containerPort = 22) // System port
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Invalid container port: 0") })
        assertTrue(errors.any { it.contains("Invalid container port: 80000") })
        assertTrue(errors.any { it.contains("Container port 22 is in reserved range") })
    }

    @Test
    fun `should reject template with missing volume mount reference`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("missing-volume-template"),
            name = "Missing Volume Template",
            image = "nginx:latest",
            resources = ResourceRequirements(),
            volumeMounts = listOf(
                VolumeMountSpec(
                    name = "non-existent-volume",
                    mountPath = "/data"
                )
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Volume mount references non-existent volume") })
    }

    @Test
    fun `should reject template with invalid volume mount path`() = runTest {
        // Given
        val template = WorkerTemplate(
            id = WorkerTemplateId("invalid-mount-template"),
            name = "Invalid Mount Template",
            image = "nginx:latest",
            resources = ResourceRequirements(),
            volumeMounts = listOf(
                VolumeMountSpec(
                    name = "data-volume",
                    mountPath = "relative/path" // Should be absolute
                )
            ),
            volumes = listOf(
                VolumeSpec(
                    name = "data-volume",
                    type = "emptyDir",
                    source = ""
                )
            )
        )

        // When
        val result = validator.validateTemplate(template, configuration)

        // Then
        assertTrue(result is TemplateValidationResult.Invalid)
        val errors = (result as TemplateValidationResult.Invalid).errors
        assertTrue(errors.any { it.contains("Volume mount path must be absolute") })
    }
}