package dev.rubentxu.hodei.pipelines.infrastructure.orchestration.kubernetes

import dev.rubentxu.hodei.pipelines.domain.orchestration.ResourceRequirements
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.util.ClientBuilder
import io.kubernetes.client.util.KubeConfig
import java.io.FileReader
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Configuration for Kubernetes Worker Orchestrator
 * 
 * Handles both in-cluster and out-of-cluster deployment scenarios.
 * Automatically detects the environment and configures the Kubernetes client accordingly.
 */
class KubernetesOrchestratorConfiguration private constructor(
    val namespace: String,
    val appLabel: String,
    val podNamePrefix: String,
    val serviceAccountName: String,
    val deleteGracePeriodSeconds: Int,
    val allowForceDelete: Boolean,
    val maxWorkersPerPool: Int,
    val maxTotalWorkers: Int,
    val maxCpuPerWorker: String,
    val maxMemoryPerWorker: String,
    val maxVolumesPerWorker: Int,
    val maxConcurrentCreations: Int,
    val defaultWorkerResources: ResourceRequirements,
    val inCluster: Boolean,
    val kubeConfigPath: String?
) {
    
    companion object {
        /**
         * Create configuration with automatic cluster detection
         */
        fun create(
            namespace: String = "hodei-pipelines",
            appLabel: String = "hodei-worker",
            podNamePrefix: String = "hodei-worker",
            serviceAccountName: String = "hodei-pipelines",
            deleteGracePeriodSeconds: Int = 30,
            allowForceDelete: Boolean = true,
            maxWorkersPerPool: Int = 100,
            maxTotalWorkers: Int = 1000,
            maxCpuPerWorker: String = "8",
            maxMemoryPerWorker: String = "16Gi",
            maxVolumesPerWorker: Int = 10,
            maxConcurrentCreations: Int = 10,
            defaultWorkerResources: ResourceRequirements = ResourceRequirements(
                cpu = "1",
                memory = "2Gi",
                storage = "5Gi"
            ),
            kubeConfigPath: String? = null
        ): KubernetesOrchestratorConfiguration {
            
            val inCluster = isRunningInCluster()
            
            return KubernetesOrchestratorConfiguration(
                namespace = namespace,
                appLabel = appLabel,
                podNamePrefix = podNamePrefix,
                serviceAccountName = serviceAccountName,
                deleteGracePeriodSeconds = deleteGracePeriodSeconds,
                allowForceDelete = allowForceDelete,
                maxWorkersPerPool = maxWorkersPerPool,
                maxTotalWorkers = maxTotalWorkers,
                maxCpuPerWorker = maxCpuPerWorker,
                maxMemoryPerWorker = maxMemoryPerWorker,
                maxVolumesPerWorker = maxVolumesPerWorker,
                maxConcurrentCreations = maxConcurrentCreations,
                defaultWorkerResources = defaultWorkerResources,
                inCluster = inCluster,
                kubeConfigPath = kubeConfigPath ?: getDefaultKubeConfigPath()
            )
        }
        
        /**
         * Detect if we're running inside a Kubernetes cluster
         */
        private fun isRunningInCluster(): Boolean {
            // Check for service account token file (standard Kubernetes in-cluster detection)
            val serviceAccountPath = "/var/run/secrets/kubernetes.io/serviceaccount"
            val tokenFile = Paths.get(serviceAccountPath, "token")
            val namespaceFile = Paths.get(serviceAccountPath, "namespace")
            
            return Files.exists(tokenFile) && Files.exists(namespaceFile)
        }
        
        /**
         * Get default kubeconfig path for out-of-cluster usage
         */
        private fun getDefaultKubeConfigPath(): String? {
            // Try environment variable first
            System.getenv("KUBECONFIG")?.let { return it }
            
            // Try default location
            val homeDir = System.getProperty("user.home")
            val defaultPath = "$homeDir/.kube/config"
            
            return if (Files.exists(Paths.get(defaultPath))) defaultPath else null
        }
    }
    
    /**
     * Create Kubernetes ApiClient based on configuration
     */
    fun createKubernetesClient(): ApiClient {
        return if (inCluster) {
            // In-cluster configuration
            ClientBuilder.cluster().build()
        } else {
            // Out-of-cluster configuration
            kubeConfigPath?.let { configPath ->
                val config = KubeConfig.loadKubeConfig(FileReader(configPath))
                ClientBuilder.kubeconfig(config).build()
            } ?: throw IllegalStateException(
                "Cannot create Kubernetes client: not running in cluster and no kubeconfig found"
            )
        }.also { client ->
            // Set default configuration
            io.kubernetes.client.openapi.Configuration.setDefaultApiClient(client)
        }
    }
    
    /**
     * Get the actual namespace to use (may be overridden in-cluster)
     */
    fun getEffectiveNamespace(): String {
        return if (inCluster) {
            // Try to read namespace from service account
            try {
                val namespaceFile = "/var/run/secrets/kubernetes.io/serviceaccount/namespace"
                if (Files.exists(Paths.get(namespaceFile))) {
                    Files.readString(Paths.get(namespaceFile)).trim()
                } else {
                    namespace
                }
            } catch (e: Exception) {
                namespace
            }
        } else {
            namespace
        }
    }
    
    /**
     * Get resource limits as a map for Kubernetes pod spec
     */
    fun getResourceLimitsMap(): Map<String, String> {
        return mapOf(
            "cpu" to maxCpuPerWorker,
            "memory" to maxMemoryPerWorker
        )
    }
    
    /**
     * Get default resource requests as a map for Kubernetes pod spec
     */
    fun getDefaultResourceRequestsMap(): Map<String, String> {
        return mapOf(
            "cpu" to defaultWorkerResources.cpu,
            "memory" to defaultWorkerResources.memory
        )
    }
    
    /**
     * Validate configuration
     */
    fun validate(): List<String> {
        val errors = mutableListOf<String>()
        
        if (namespace.isBlank()) {
            errors.add("Namespace cannot be blank")
        }
        
        if (appLabel.isBlank()) {
            errors.add("App label cannot be blank")
        }
        
        if (maxWorkersPerPool <= 0) {
            errors.add("Max workers per pool must be positive")
        }
        
        if (maxTotalWorkers <= 0) {
            errors.add("Max total workers must be positive")
        }
        
        if (maxConcurrentCreations <= 0) {
            errors.add("Max concurrent creations must be positive")
        }
        
        if (!inCluster && kubeConfigPath == null) {
            errors.add("Kubeconfig path is required for out-of-cluster usage")
        }
        
        if (!inCluster && kubeConfigPath != null && !Files.exists(Paths.get(kubeConfigPath))) {
            errors.add("Kubeconfig file does not exist: $kubeConfigPath")
        }
        
        return errors
    }
    
    /**
     * Get configuration summary for logging
     */
    fun getSummary(): Map<String, Any> {
        return mapOf(
            "namespace" to getEffectiveNamespace(),
            "appLabel" to appLabel,
            "inCluster" to inCluster,
            "kubeConfigPath" to (kubeConfigPath ?: "not set"),
            "maxWorkersPerPool" to maxWorkersPerPool,
            "maxTotalWorkers" to maxTotalWorkers,
            "maxConcurrentCreations" to maxConcurrentCreations,
            "serviceAccountName" to serviceAccountName
        )
    }
}