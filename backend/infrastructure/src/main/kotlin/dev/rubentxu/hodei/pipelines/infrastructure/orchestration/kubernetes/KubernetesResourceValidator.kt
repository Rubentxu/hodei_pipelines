package dev.rubentxu.hodei.pipelines.infrastructure.orchestration.kubernetes

import dev.rubentxu.hodei.pipelines.domain.orchestration.TemplateValidationResult
import dev.rubentxu.hodei.pipelines.domain.orchestration.WorkerTemplate

/**
 * Validates WorkerTemplate for Kubernetes compatibility
 * 
 * Following Single Responsibility Principle:
 * - Only responsible for template validation logic
 * - Kubernetes-specific validation rules
 * - Stateless and reusable
 */
class KubernetesResourceValidator {
    
    /**
     * Validate WorkerTemplate against Kubernetes constraints
     */
    fun validateTemplate(
        template: WorkerTemplate,
        config: KubernetesOrchestratorConfiguration
    ): TemplateValidationResult {
        val errors = mutableListOf<String>()
        
        // Basic validation
        errors.addAll(validateBasicTemplate(template))
        
        // Resource validation
        errors.addAll(validateResources(template, config))
        
        // Kubernetes-specific validation
        errors.addAll(validateKubernetesConstraints(template, config))
        
        // Security validation
        errors.addAll(validateSecurity(template))
        
        // Volume validation
        errors.addAll(validateVolumes(template, config))
        
        // Networking validation
        errors.addAll(validateNetworking(template))
        
        return if (errors.isEmpty()) {
            TemplateValidationResult.Valid
        } else {
            TemplateValidationResult.Invalid(errors)
        }
    }
    
    private fun validateBasicTemplate(template: WorkerTemplate): List<String> {
        val errors = mutableListOf<String>()
        
        // Template ID validation
        if (template.id.value.isBlank()) {
            errors.add("Template ID cannot be blank")
        }
        
        if (!template.id.value.matches(Regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"))) {
            errors.add("Template ID must follow Kubernetes naming convention (lowercase, alphanumeric, hyphens)")
        }
        
        // Name validation
        if (template.name.isBlank()) {
            errors.add("Template name cannot be blank")
        }
        
        // Image validation
        if (template.image.isBlank()) {
            errors.add("Container image cannot be blank")
        }
        
        if (!isValidImageName(template.image)) {
            errors.add("Invalid container image format: ${template.image}")
        }
        
        // Version validation
        if (template.version.isBlank()) {
            errors.add("Template version cannot be blank")
        }
        
        return errors
    }
    
    private fun validateResources(
        template: WorkerTemplate,
        config: KubernetesOrchestratorConfiguration
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // CPU validation
        try {
            val requestedCpu = parseCpuToMillicores(template.resources.cpu)
            val maxCpu = parseCpuToMillicores(config.maxCpuPerWorker)
            
            if (requestedCpu <= 0) {
                errors.add("CPU request must be positive")
            }
            
            if (requestedCpu > maxCpu) {
                errors.add("CPU request (${template.resources.cpu}) exceeds maximum (${config.maxCpuPerWorker})")
            }
        } catch (e: Exception) {
            errors.add("Invalid CPU format: ${template.resources.cpu}")
        }
        
        // Memory validation
        try {
            val requestedMemory = parseMemoryToBytes(template.resources.memory)
            val maxMemory = parseMemoryToBytes(config.maxMemoryPerWorker)
            
            if (requestedMemory <= 0) {
                errors.add("Memory request must be positive")
            }
            
            if (requestedMemory > maxMemory) {
                errors.add("Memory request (${template.resources.memory}) exceeds maximum (${config.maxMemoryPerWorker})")
            }
        } catch (e: Exception) {
            errors.add("Invalid memory format: ${template.resources.memory}")
        }
        
        // Storage validation
        if (template.resources.storage.isNotBlank()) {
            try {
                val requestedStorage = parseMemoryToBytes(template.resources.storage) // Same parsing logic
                if (requestedStorage <= 0) {
                    errors.add("Storage request must be positive")
                }
            } catch (e: Exception) {
                errors.add("Invalid storage format: ${template.resources.storage}")
            }
        }
        
        return errors
    }
    
    private fun validateKubernetesConstraints(
        template: WorkerTemplate,
        config: KubernetesOrchestratorConfiguration
    ): List<String> {
        val errors = mutableListOf<String>()
        
        // Labels validation
        template.labels.forEach { (key, value) ->
            if (!isValidLabelKey(key)) {
                errors.add("Invalid label key format: $key")
            }
            if (!isValidLabelValue(value)) {
                errors.add("Invalid label value format: $value")
            }
        }
        
        // Environment variables validation
        template.env.forEach { (key, _) ->
            if (!isValidEnvVarName(key)) {
                errors.add("Invalid environment variable name: $key")
            }
        }
        
        // DNS policy validation
        template.dnsPolicy?.let { policy ->
            val validPolicies = setOf("ClusterFirst", "ClusterFirstWithHostNet", "Default", "None")
            if (policy !in validPolicies) {
                errors.add("Invalid DNS policy: $policy. Must be one of: ${validPolicies.joinToString()}")
            }
        }
        
        // Service account validation
        template.serviceAccountName?.let { serviceAccount ->
            if (!serviceAccount.matches(Regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"))) {
                errors.add("Invalid service account name format: $serviceAccount")
            }
        }
        
        // Node selector validation
        template.nodeSelector.forEach { (key, value) ->
            if (!isValidLabelKey(key)) {
                errors.add("Invalid node selector key format: $key")
            }
            if (!isValidLabelValue(value)) {
                errors.add("Invalid node selector value format: $value")
            }
        }
        
        return errors
    }
    
    private fun validateSecurity(template: WorkerTemplate): List<String> {
        val errors = mutableListOf<String>()
        
        // Container security context validation
        template.containerSecurityContext?.let { secCtx ->
            // Check for dangerous privileges
            if (secCtx.allowPrivilegeEscalation == true) {
                errors.add("Privilege escalation is not allowed for security reasons")
            }
            
            if (secCtx.runAsUser != null && secCtx.runAsUser == 0L) {
                errors.add("Running as root (UID 0) is not recommended")
            }
            
            // Validate capabilities
            secCtx.capabilities?.add?.forEach { capability ->
                if (capability in DANGEROUS_CAPABILITIES) {
                    errors.add("Dangerous capability not allowed: $capability")
                }
            }
        }
        
        // Pod security context validation
        template.securityContext?.let { secCtx ->
            if (secCtx.runAsUser != null && secCtx.runAsUser == 0L) {
                errors.add("Running pod as root (UID 0) is not recommended")
            }
        }
        
        return errors
    }
    
    private fun validateVolumes(
        template: WorkerTemplate,
        config: KubernetesOrchestratorConfiguration
    ): List<String> {
        val errors = mutableListOf<String>()
        
        if (template.volumes.size > config.maxVolumesPerWorker) {
            errors.add("Too many volumes: ${template.volumes.size} > ${config.maxVolumesPerWorker}")
        }
        
        template.volumes.forEach { volume ->
            // Volume name validation
            if (!volume.name.matches(Regex("^[a-z0-9]([-a-z0-9]*[a-z0-9])?$"))) {
                errors.add("Invalid volume name format: ${volume.name}")
            }
            
            // Volume type validation
            if (volume.type !in SUPPORTED_VOLUME_TYPES) {
                errors.add("Unsupported volume type: ${volume.type}")
            }
            
            // Host path security check
            if (volume.type == "hostPath") {
                if (volume.source.startsWith("/var/run/docker.sock") || 
                    volume.source.startsWith("/proc") ||
                    volume.source.startsWith("/sys")) {
                    errors.add("Host path not allowed for security reasons: ${volume.source}")
                }
            }
        }
        
        // Validate volume mounts
        template.volumeMounts.forEach { mount ->
            if (!template.volumes.any { it.name == mount.name }) {
                errors.add("Volume mount references non-existent volume: ${mount.name}")
            }
            
            if (mount.mountPath.isBlank()) {
                errors.add("Volume mount path cannot be blank")
            }
            
            if (!mount.mountPath.startsWith("/")) {
                errors.add("Volume mount path must be absolute: ${mount.mountPath}")
            }
        }
        
        return errors
    }
    
    private fun validateNetworking(template: WorkerTemplate): List<String> {
        val errors = mutableListOf<String>()
        
        template.ports.forEach { port ->
            if (port.containerPort <= 0 || port.containerPort > 65535) {
                errors.add("Invalid container port: ${port.containerPort}")
            }
            
            if (port.protocol != null && port.protocol !in setOf("TCP", "UDP", "SCTP")) {
                errors.add("Invalid port protocol: ${port.protocol}")
            }
            
            // Check for well-known system ports
            if (port.containerPort < 1024) {
                errors.add("Container port ${port.containerPort} is in reserved range (< 1024)")
            }
        }
        
        return errors
    }
    
    // Helper methods for validation
    
    private fun isValidImageName(image: String): Boolean {
        // Basic Docker image name validation
        return image.matches(Regex("^[a-z0-9]+(([._-])[a-z0-9]+)*(/[a-z0-9]+(([._-])[a-z0-9]+)*)*(:[a-zA-Z0-9_][a-zA-Z0-9._-]*)?$"))
    }
    
    private fun isValidLabelKey(key: String): Boolean {
        // Kubernetes label key validation
        if (key.length > 253) return false
        
        val parts = key.split("/")
        return when (parts.size) {
            1 -> parts[0].matches(Regex("^[a-z0-9A-Z]([a-z0-9A-Z._-]*[a-z0-9A-Z])?$")) && parts[0].length <= 63
            2 -> {
                val prefix = parts[0]
                val name = parts[1]
                prefix.matches(Regex("^[a-z0-9.-]+$")) && prefix.length <= 253 &&
                name.matches(Regex("^[a-z0-9A-Z]([a-z0-9A-Z._-]*[a-z0-9A-Z])?$")) && name.length <= 63
            }
            else -> false
        }
    }
    
    private fun isValidLabelValue(value: String): Boolean {
        // Kubernetes label value validation
        return value.length <= 63 && 
               (value.isEmpty() || value.matches(Regex("^[a-z0-9A-Z]([a-z0-9A-Z._-]*[a-z0-9A-Z])?$")))
    }
    
    private fun isValidEnvVarName(name: String): Boolean {
        // Environment variable name validation
        return name.matches(Regex("^[a-zA-Z_][a-zA-Z0-9_]*$"))
    }
    
    private fun parseCpuToMillicores(cpu: String): Long {
        return when {
            cpu.endsWith("m") -> cpu.dropLast(1).toLong()
            cpu.endsWith("n") -> cpu.dropLast(1).toLong() / 1_000_000
            else -> cpu.toLong() * 1000
        }
    }
    
    private fun parseMemoryToBytes(memory: String): Long {
        return when {
            memory.endsWith("Ki") -> memory.dropLast(2).toLong() * 1024
            memory.endsWith("Mi") -> memory.dropLast(2).toLong() * 1024 * 1024
            memory.endsWith("Gi") -> memory.dropLast(2).toLong() * 1024 * 1024 * 1024
            memory.endsWith("Ti") -> memory.dropLast(2).toLong() * 1024 * 1024 * 1024 * 1024
            memory.endsWith("k") -> memory.dropLast(1).toLong() * 1000
            memory.endsWith("M") -> memory.dropLast(1).toLong() * 1000 * 1000
            memory.endsWith("G") -> memory.dropLast(1).toLong() * 1000 * 1000 * 1000
            memory.endsWith("T") -> memory.dropLast(1).toLong() * 1000 * 1000 * 1000 * 1000
            else -> memory.toLong()
        }
    }
    
    companion object {
        private val DANGEROUS_CAPABILITIES = setOf(
            "SYS_ADMIN", "NET_ADMIN", "SYS_TIME", "SYS_MODULE", 
            "SYS_RAWIO", "SYS_PTRACE", "DAC_READ_SEARCH", "DAC_OVERRIDE"
        )
        
        private val SUPPORTED_VOLUME_TYPES = setOf(
            "emptyDir", "configMap", "secret", "persistentVolumeClaim", "hostPath"
        )
    }
}