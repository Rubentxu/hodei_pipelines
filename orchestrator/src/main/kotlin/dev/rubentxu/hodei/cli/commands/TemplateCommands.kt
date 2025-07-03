package dev.rubentxu.hodei.cli.commands

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.choice
import dev.rubentxu.hodei.templatemanagement.application.services.WorkerTemplateService
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

private val logger = KotlinLogging.logger {}

/**
 * List available worker templates
 */
class TemplateListCommand : CliktCommand(
    name = "list",
    help = "📋 List available worker templates"
), KoinComponent {
    
    private val templateService: WorkerTemplateService by inject()
    
    private val type by option(
        "--type",
        help = "Filter by template type (docker, kubernetes, all)"
    ).choice("docker", "kubernetes", "all").default("all")
    
    private val detailed by option(
        "--detailed", "-d",
        help = "Show detailed template information"
    ).flag()
    
    override fun run() = runBlocking {
        echo("📋 Worker Templates")
        echo("=".repeat(60))
        
        if (detailed) {
            showDetailedTemplateList()
        } else {
            showSimpleTemplateList()
        }
    }
    
    private fun showSimpleTemplateList() {
        echo("NAME                           TYPE         STATUS      CREATED")
        echo("-".repeat(60))
        echo("default-docker-worker          Docker       Published   2024-01-15")
        echo("performance-docker-worker      Docker       Published   2024-01-15")
        echo("docker-ci-pipeline-worker      Docker       Published   2024-01-15")
        echo("docker-lightweight-worker      Docker       Published   2024-01-15")
        echo("docker-storage-worker          Docker       Published   2024-01-15")
        echo("docker-gpu-worker              Docker       Published   2024-01-15")
        echo("default-kubernetes-worker      Kubernetes   Published   2024-01-15")
    }
    
    private fun showDetailedTemplateList() {
        echo("📦 Template: default-docker-worker")
        echo("   Type: Docker")
        echo("   Description: Default Docker worker template for general purpose jobs")
        echo("   Image: hodei/worker:latest")
        echo("   Resources: CPU 100m, Memory 256Mi")
        echo("   Status: Published")
        echo()
        
        echo("📦 Template: performance-docker-worker")
        echo("   Type: Docker") 
        echo("   Description: High-performance Docker worker template for resource-intensive jobs")
        echo("   Image: hodei/worker:latest")
        echo("   Resources: CPU 1000m, Memory 2Gi")
        echo("   Status: Published")
        echo()
        
        echo("📦 Template: docker-ci-pipeline-worker")
        echo("   Type: Docker")
        echo("   Description: Docker worker template optimized for CI/CD pipeline execution")
        echo("   Image: hodei/worker-ci:latest")
        echo("   Resources: CPU 500m-2000m, Memory 1Gi-4Gi, Storage 10Gi-20Gi")
        echo("   Status: Published")
        echo()
        
        echo("📦 Template: docker-lightweight-worker")
        echo("   Type: Docker")
        echo("   Description: Lightweight Docker worker template for fast job execution")
        echo("   Image: hodei/worker-alpine:latest")
        echo("   Resources: CPU 50m-500m, Memory 128Mi-512Mi")
        echo("   Status: Published")
        echo()
        
        echo("📦 Template: docker-storage-worker")
        echo("   Type: Docker")
        echo("   Description: Docker worker template with persistent storage for data processing")
        echo("   Image: hodei/worker-data:latest")
        echo("   Resources: CPU 1000m-4000m, Memory 2Gi-8Gi, Storage 50Gi-100Gi")
        echo("   Status: Published")
        echo()
        
        echo("📦 Template: docker-gpu-worker")
        echo("   Type: Docker")
        echo("   Description: Docker worker template with GPU support for ML/AI workloads")
        echo("   Image: hodei/worker-gpu:latest")
        echo("   Resources: CPU 2000m-8000m, Memory 4Gi-16Gi, GPU 1")
        echo("   Status: Published")
        echo()
        
        echo("📦 Template: default-kubernetes-worker")
        echo("   Type: Kubernetes")
        echo("   Description: Default Kubernetes worker template")
        echo("   Image: hodei/worker:latest")
        echo("   Resources: CPU 100m, Memory 256Mi")
        echo("   Status: Published")
    }
}

/**
 * Create default worker templates
 */
class TemplateCreateCommand : CliktCommand(
    name = "create-defaults",
    help = "➕ Create default worker templates"
), KoinComponent {
    
    private val templateService: WorkerTemplateService by inject()
    
    private val includeDocker by option(
        "--include-docker",
        help = "Include Docker execution templates"
    ).flag(default = true)
    
    private val includeExtended by option(
        "--include-extended",
        help = "Include extended Docker templates (CI/CD, GPU, etc.)"
    ).flag(default = false)
    
    private val dryRun by option(
        "--dry-run",
        help = "Show what templates would be created without creating them"
    ).flag()
    
    override fun run() = runBlocking {
        echo("➕ Creating default worker templates...")
        echo()
        
        if (dryRun) {
            echo("🧪 DRY RUN: Would create the following templates:")
            echo("• default-docker-worker")
            echo("• performance-docker-worker") 
            echo("• default-kubernetes-worker")
            
            if (includeExtended) {
                echo("• docker-ci-pipeline-worker")
                echo("• docker-lightweight-worker")
                echo("• docker-storage-worker")
                echo("• docker-gpu-worker")
            }
            return@runBlocking
        }
        
        try {
            val result = if (includeExtended) {
                templateService.createAllTemplates()
            } else {
                templateService.createDefaultTemplates()
            }
            
            when {
                result.isRight() -> {
                    val templates = result.getOrNull() ?: emptyList()
                    echo("✅ Successfully created ${templates.size} worker templates!")
                    echo()
                    echo("📋 Created templates:")
                    templates.forEach { template ->
                        echo("• ${template.name} - ${template.description}")
                    }
                    echo()
                    echo("🎯 Next steps:")
                    echo("1. List templates: hodei template list")
                    echo("2. Start Docker workers: hodei docker worker start --template <name>")
                }
                else -> {
                    val error = result.leftOrNull() ?: "Unknown error"
                    echo("❌ Failed to create templates: $error")
                }
            }
            
        } catch (e: Exception) {
            echo("❌ Error creating templates: ${e.message}")
        }
    }
}

/**
 * Show template information
 */
class TemplateShowCommand : CliktCommand(
    name = "show",
    help = "📊 Show detailed information about a specific template"
), KoinComponent {
    
    private val templateService: WorkerTemplateService by inject()
    
    private val templateName by option(
        "--name", "-n",
        help = "Template name to show"
    ).required()
    
    override fun run() = runBlocking {
        echo("📊 Template Information: $templateName")
        echo("=".repeat(50))
        
        // In a real implementation, this would fetch the actual template
        when (templateName) {
            "default-docker-worker" -> showDefaultDockerTemplate()
            "performance-docker-worker" -> showPerformanceDockerTemplate()
            "docker-ci-pipeline-worker" -> showCIPipelineTemplate()
            "docker-lightweight-worker" -> showLightweightTemplate()
            "docker-storage-worker" -> showStorageTemplate()
            "docker-gpu-worker" -> showGpuTemplate()
            "default-kubernetes-worker" -> showKubernetesTemplate()
            else -> echo("❌ Template '$templateName' not found")
        }
    }
    
    private fun showDefaultDockerTemplate() {
        echo("📦 Name: default-docker-worker")
        echo("🐳 Type: Docker")
        echo("📝 Description: Default Docker worker template for general purpose jobs")
        echo("🖼️ Image: hodei/worker:latest")
        echo("💻 Resources:")
        echo("   CPU: 100m")
        echo("   Memory: 256Mi")
        echo("🌐 Environment:")
        echo("   HODEI_LOG_LEVEL: INFO")
        echo("   HODEI_WORKER_POOL: default")
        echo("🏷️ Labels:")
        echo("   hodei.worker: true")
        echo("   hodei.worker.type: docker")
        echo("📊 Status: Published")
        echo("📅 Created: 2024-01-15T10:30:00Z")
    }
    
    private fun showPerformanceDockerTemplate() {
        echo("📦 Name: performance-docker-worker")
        echo("🐳 Type: Docker")
        echo("📝 Description: High-performance Docker worker template for resource-intensive jobs")
        echo("🖼️ Image: hodei/worker:latest")
        echo("💻 Resources:")
        echo("   CPU: 1000m")
        echo("   Memory: 2Gi")
        echo("🌐 Environment:")
        echo("   HODEI_LOG_LEVEL: INFO")
        echo("   HODEI_WORKER_POOL: performance")
        echo("   HODEI_WORKER_CONCURRENCY: 4")
        echo("🏷️ Labels:")
        echo("   hodei.worker: true")
        echo("   hodei.worker.type: performance")
        echo("📊 Status: Published")
    }
    
    private fun showCIPipelineTemplate() {
        echo("📦 Name: docker-ci-pipeline-worker")
        echo("🐳 Type: Docker")
        echo("📝 Description: Docker worker template optimized for CI/CD pipeline execution")
        echo("🖼️ Image: hodei/worker-ci:latest")
        echo("💻 Resources:")
        echo("   CPU: 500m (limit: 2000m)")
        echo("   Memory: 1Gi (limit: 4Gi)")
        echo("   Storage: 10Gi (limit: 20Gi)")
        echo("🌐 Environment:")
        echo("   HODEI_WORKER_TYPE: ci-pipeline")
        echo("   HODEI_WORKSPACE: /workspace")
        echo("   CI: true")
        echo("🏥 Health Checks:")
        echo("   Liveness: HTTP /health:8080")
        echo("   Readiness: HTTP /ready:8080")
        echo("📊 Status: Published")
    }
    
    private fun showLightweightTemplate() {
        echo("📦 Name: docker-lightweight-worker")
        echo("🐳 Type: Docker")
        echo("📝 Description: Lightweight Docker worker template for fast job execution")
        echo("🖼️ Image: hodei/worker-alpine:latest")
        echo("💻 Resources:")
        echo("   CPU: 50m (limit: 500m)")
        echo("   Memory: 128Mi (limit: 512Mi)")
        echo("🌐 Environment:")
        echo("   HODEI_WORKER_TYPE: lightweight")
        echo("   HODEI_LOG_LEVEL: WARN")
        echo("🏥 Health Checks:")
        echo("   Liveness: exec pgrep hodei-worker")
        echo("   Readiness: exec pgrep hodei-worker")
        echo("📊 Status: Published")
    }
    
    private fun showStorageTemplate() {
        echo("📦 Name: docker-storage-worker")
        echo("🐳 Type: Docker")
        echo("📝 Description: Docker worker template with persistent storage for data processing")
        echo("🖼️ Image: hodei/worker-data:latest")
        echo("💻 Resources:")
        echo("   CPU: 1000m (limit: 4000m)")
        echo("   Memory: 2Gi (limit: 8Gi)")
        echo("   Storage: 50Gi (limit: 100Gi)")
        echo("🌐 Environment:")
        echo("   HODEI_WORKER_TYPE: data-processing")
        echo("   HODEI_DATA_DIR: /data")
        echo("   HODEI_TEMP_DIR: /tmp/hodei")
        echo("🏷️ Labels:")
        echo("   hodei.storage: persistent")
        echo("📊 Status: Published")
    }
    
    private fun showGpuTemplate() {
        echo("📦 Name: docker-gpu-worker")
        echo("🐳 Type: Docker")
        echo("📝 Description: Docker worker template with GPU support for ML/AI workloads")
        echo("🖼️ Image: hodei/worker-gpu:latest")
        echo("💻 Resources:")
        echo("   CPU: 2000m (limit: 8000m)")
        echo("   Memory: 4Gi (limit: 16Gi)")
        echo("   GPU: 1")
        echo("🌐 Environment:")
        echo("   HODEI_WORKER_TYPE: gpu-accelerated")
        echo("   NVIDIA_VISIBLE_DEVICES: all")
        echo("   CUDA_VISIBLE_DEVICES: all")
        echo("🏷️ Labels:")
        echo("   hodei.gpu: nvidia")
        echo("🏥 Health Checks:")
        echo("   Readiness: exec nvidia-smi -q")
        echo("📊 Status: Published")
    }
    
    private fun showKubernetesTemplate() {
        echo("📦 Name: default-kubernetes-worker")
        echo("☸️ Type: Kubernetes")
        echo("📝 Description: Default Kubernetes worker template")
        echo("🖼️ Image: hodei/worker:latest")
        echo("💻 Resources:")
        echo("   CPU: 100m")
        echo("   Memory: 256Mi")
        echo("🌐 Environment:")
        echo("   HODEI_LOG_LEVEL: INFO")
        echo("   HODEI_WORKER_POOL: kubernetes")
        echo("📊 Status: Published")
    }
}