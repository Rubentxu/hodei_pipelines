package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Tests end-to-end para el comando 'info' del CLI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfoCommandE2ETest : BaseE2ETest() {
    
    @Test
    fun `info command should display Pipeline DSL information`() {
        val result = runCli("info")
        
        result.assertSuccess()
        result.assertContains("📖 Pipeline DSL Information")
        result.assertContains("=".repeat(50))
        result.assertContains("Version:")
        result.assertContains("Description:")
    }
    
    @Test
    fun `info command should display supported step types`() {
        val result = runCli("info")
        
        result.assertSuccess()
        result.assertContains("🔧 Supported Step Types:")
        
        // Verificar que se muestran los tipos de step principales
        val expectedStepTypes = listOf(
            "sh",
            "bat", 
            "echo",
            "script",
            "docker",
            "archiveArtifacts",
            "publishTestResults",
            "checkout",
            "notification",
            "dir",
            "withEnv"
        )
        
        expectedStepTypes.forEach { stepType ->
            result.assertContains("• $stepType")
        }
    }
    
    @Test
    fun `info command should display available imports`() {
        val result = runCli("info")
        
        result.assertSuccess()
        result.assertContains("📚 Available Imports:")
        result.assertContains("•")  // Al menos debe haber algunas importaciones listadas
    }
    
    @Test
    fun `info command should display features`() {
        val result = runCli("info")
        
        result.assertSuccess()
        result.assertContains("🚀 Features:")
        
        // Verificar que se muestran las características principales
        val expectedFeatures = listOf(
            "Type-safe pipeline definitions",
            "Real-time output streaming",
            "Event-driven architecture",
            "Integration with worker system",
            "Artifact dependency management",
            "Parallel stage execution",
            "Conditional stage execution",
            "Docker support",
            "Notification system"
        )
        
        expectedFeatures.forEach { feature ->
            result.assertContains(feature)
        }
    }
    
    @Test
    fun `info command should execute quickly`() {
        val startTime = System.currentTimeMillis()
        val result = runCli("info")
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        result.assertSuccess()
        result.assertContains("📖 Pipeline DSL Information")
        
        // El comando info debería ser muy rápido
        assert(duration < 2000) {
            "Info command should execute quickly. Duration: ${duration}ms"
        }
    }
    
    @Test
    fun `info command should not require any files`() {
        // El comando info no debería requerir archivos de entrada
        val result = runCli("info")
        
        result.assertSuccess()
        result.assertContains("📖 Pipeline DSL Information")
        
        // No debería haber mensajes de error sobre archivos faltantes
        result.assertNotContains("does not exist")
        result.assertNotContains("file not found")
        result.assertNotContains("File")
    }
    
    @Test
    fun `info command should display version information`() {
        val result = runCli("info")
        
        result.assertSuccess()
        result.assertContains("Version:")
        
        // La versión debería tener un formato reconocible
        // (no verificamos una versión específica para flexibilidad)
        val versionPattern = Regex("Version:\\s*\\d+\\.\\d+\\.\\d+|Version:\\s*\\w+")
        val hasValidVersion = versionPattern.containsMatchIn(result.stdout)
        
        assert(hasValidVersion) {
            "Should display a valid version format in output: ${result.stdout}"
        }
    }
    
    @Test
    fun `info command should be formatted properly`() {
        val result = runCli("info")
        
        result.assertSuccess()
        
        val lines = result.stdout.lines()
        
        // Verificar que la salida está bien estructurada
        assert(lines.isNotEmpty()) { "Output should not be empty" }
        
        // Verificar que hay secciones claramente definidas
        val hasSections = lines.any { it.contains("Supported Step Types") } &&
                         lines.any { it.contains("Available Imports") } &&
                         lines.any { it.contains("Features") }
        
        assert(hasSections) { "Output should have clear sections" }
        
        // Verificar que usa emojis para mejor presentación
        val hasEmojis = result.stdout.contains("📖") ||
                       result.stdout.contains("🔧") ||
                       result.stdout.contains("📚") ||
                       result.stdout.contains("🚀")
        
        assert(hasEmojis) { "Output should use emojis for better presentation" }
    }
    
    @Test
    fun `info command should display complete DSL capabilities`() {
        val result = runCli("info")
        
        result.assertSuccess()
        
        // Verificar que muestra capacidades clave del DSL
        val keyCapabilities = listOf(
            "pipeline",
            "stage",
            "steps",
            "parallel",
            "environment",
            "parameters",
            "agent",
            "post"
        )
        
        // Al menos algunas de estas capacidades deberían mencionarse
        val mentionedCapabilities = keyCapabilities.filter { capability ->
            result.stdout.lowercase().contains(capability.lowercase())
        }
        
        assert(mentionedCapabilities.isNotEmpty()) {
            "Should mention key DSL capabilities. Found: $mentionedCapabilities"
        }
    }
    
    @Test
    fun `info command should be consistent across multiple runs`() {
        // Ejecutar el comando info varias veces para verificar consistencia
        val results = (1..3).map { runCli("info") }
        
        results.forEach { result ->
            result.assertSuccess()
            result.assertContains("📖 Pipeline DSL Information")
        }
        
        // Verificar que todas las ejecuciones producen el mismo resultado
        val outputs = results.map { it.stdout }
        assert(outputs.all { it == outputs.first() }) {
            "Info command should produce consistent output across multiple runs"
        }
    }
    
    @Test
    fun `info command should display comprehensive help information`() {
        val result = runCli("info")
        
        result.assertSuccess()
        
        // Verificar que la información es suficientemente completa
        val outputLength = result.stdout.length
        assert(outputLength > 500) {
            "Info output should be comprehensive. Length: $outputLength"
        }
        
        // Verificar que hay múltiples secciones de información
        val sectionCount = listOf(
            "Information",
            "Step Types",
            "Imports", 
            "Features"
        ).count { section ->
            result.stdout.contains(section)
        }
        
        assert(sectionCount >= 3) {
            "Should display multiple information sections. Found: $sectionCount"
        }
    }
}