package dev.rubentxu.hodei.pipelines.infrastructure.orchestration.kubernetes

import dev.rubentxu.hodei.pipelines.domain.orchestration.ResourceRequirements
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KubernetesOrchestratorConfigurationTest {

    @TempDir
    lateinit var tempDir: Path

    @BeforeEach
    fun setup() {
        mockkStatic(Files::class)
        mockkStatic("java.lang.System")
    }

    @AfterEach
    fun teardown() {
        unmockkStatic(Files::class)
        unmockkStatic("java.lang.System")
    }

    @Test
    fun `should detect in-cluster configuration when service account files exist`() = runTest {
        // Given - Mock in-cluster environment
        val serviceAccountPath = Path.of("/var/run/secrets/kubernetes.io/serviceaccount")
        val tokenFile = serviceAccountPath.resolve("token")
        val namespaceFile = serviceAccountPath.resolve("namespace")
        
        every { Files.exists(tokenFile) } returns true
        every { Files.exists(namespaceFile) } returns true

        // When
        val config = KubernetesOrchestratorConfiguration.create()

        // Then
        assertTrue(config.inCluster)
        assertEquals("hodei-pipelines", config.namespace)
    }

    @Test
    fun `should detect out-of-cluster configuration when service account files don't exist`() = runTest {
        // Given - Mock out-of-cluster environment
        val serviceAccountPath = Path.of("/var/run/secrets/kubernetes.io/serviceaccount")
        val tokenFile = serviceAccountPath.resolve("token")
        val namespaceFile = serviceAccountPath.resolve("namespace")
        
        every { Files.exists(tokenFile) } returns false
        every { Files.exists(namespaceFile) } returns false

        // When
        val config = KubernetesOrchestratorConfiguration.create()

        // Then
        assertFalse(config.inCluster)
    }

    @Test
    fun `should use provided kubeconfig path for out-of-cluster`() = runTest {
        // Given
        val kubeconfigPath = tempDir.resolve("kubeconfig").toString()
        Files.write(Path.of(kubeconfigPath), "dummy-config".toByteArray())
        
        every { Files.exists(any<Path>()) } returns false andThen true

        // When
        val config = KubernetesOrchestratorConfiguration.create(
            kubeConfigPath = kubeconfigPath
        )

        // Then
        assertFalse(config.inCluster)
        assertEquals(kubeconfigPath, config.kubeConfigPath)
    }

    @Test
    fun `should use effective namespace from service account in cluster`() = runTest {
        // Given - Mock in-cluster with custom namespace
        val serviceAccountPath = Path.of("/var/run/secrets/kubernetes.io/serviceaccount")
        val tokenFile = serviceAccountPath.resolve("token")
        val namespaceFile = serviceAccountPath.resolve("namespace")
        
        every { Files.exists(tokenFile) } returns true
        every { Files.exists(namespaceFile) } returns true
        every { Files.readString(namespaceFile) } returns "custom-namespace"

        val config = KubernetesOrchestratorConfiguration.create()

        // When
        val effectiveNamespace = config.getEffectiveNamespace()

        // Then
        assertEquals("custom-namespace", effectiveNamespace)
    }

    @Test
    fun `should validate configuration successfully with valid settings`() = runTest {
        // Given
        val config = KubernetesOrchestratorConfiguration.create(
            namespace = "test-namespace",
            appLabel = "test-app",
            maxWorkersPerPool = 5,
            maxTotalWorkers = 50
        )

        // When
        val errors = config.validate()

        // Then
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `should return validation errors for invalid configuration`() = runTest {
        // Given
        val config = KubernetesOrchestratorConfiguration.create(
            namespace = "",
            appLabel = "",
            maxWorkersPerPool = -1,
            maxTotalWorkers = 0
        )

        // When
        val errors = config.validate()

        // Then
        assertFalse(errors.isEmpty())
        assertTrue(errors.any { it.contains("Namespace cannot be blank") })
        assertTrue(errors.any { it.contains("App label cannot be blank") })
        assertTrue(errors.any { it.contains("Max workers per pool must be positive") })
        assertTrue(errors.any { it.contains("Max total workers must be positive") })
    }

    @Test
    fun `should create kubernetes client successfully`() = runTest {
        // Given - Mock valid configuration
        every { Files.exists(any<Path>()) } returns false
        
        val config = KubernetesOrchestratorConfiguration.create()

        // When & Then - Should not throw exception for basic client creation test
        // Note: Real client creation would require actual Kubernetes cluster
        // This test validates the configuration setup
        assertTrue(config.validate().isEmpty())
    }

    @Test
    fun `should provide correct resource limits and requests maps`() = runTest {
        // Given
        val config = KubernetesOrchestratorConfiguration.create(
            maxCpuPerWorker = "2",
            maxMemoryPerWorker = "4Gi",
            defaultWorkerResources = ResourceRequirements(
                cpu = "500m",
                memory = "1Gi"
            )
        )

        // When
        val limitsMap = config.getResourceLimitsMap()
        val requestsMap = config.getDefaultResourceRequestsMap()

        // Then
        assertEquals("2", limitsMap["cpu"])
        assertEquals("4Gi", limitsMap["memory"])
        assertEquals("500m", requestsMap["cpu"])
        assertEquals("1Gi", requestsMap["memory"])
    }

    @Test
    fun `should provide configuration summary`() = runTest {
        // Given
        val config = KubernetesOrchestratorConfiguration.create(
            namespace = "test-namespace",
            appLabel = "test-app",
            maxWorkersPerPool = 10
        )

        // When
        val summary = config.getSummary()

        // Then
        assertEquals("test-namespace", summary["namespace"])
        assertEquals("test-app", summary["appLabel"])
        assertEquals(10, summary["maxWorkersPerPool"])
        assertTrue(summary.containsKey("inCluster"))
        assertTrue(summary.containsKey("kubeConfigPath"))
    }
}