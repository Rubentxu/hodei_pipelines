package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Tests end-to-end para el comando 'execute' del CLI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExecuteCommandE2ETest : BaseE2ETest() {
    
    @Test
    fun `execute simple success pipeline should succeed`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val result = runCli(
            "execute", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath,
            "--verbose"
        )
        
        result.assertSuccess()
        result.assertContains("🚀 Executing pipeline")
        result.assertContains("Simple Success Pipeline")
        result.assertContains("✅ Pipeline compiled successfully")
        result.assertContains("✅ Pipeline execution completed successfully!")
        result.assertContains("Hello from CLI test!")
        result.assertContains("Environment: e2e")
        result.assertContains("Pipeline type: success")
        result.assertContains("Shell command executed")
        result.assertContains("Pipeline succeeded!")
        result.assertContains("Pipeline completed - always runs")
    }
    
    @Test
    fun `execute simple failure pipeline should fail with proper error handling`() {
        assertPipelineExists("simple-failure.pipeline.kts")
        
        val result = runCli(
            "execute", 
            getPipelineFile("simple-failure.pipeline.kts").absolutePath,
            "--verbose"
        )
        
        result.assertFailure()
        result.assertContains("🚀 Executing pipeline")
        result.assertContains("Simple Failure Pipeline")
        result.assertContains("✅ Pipeline compiled successfully")
        result.assertContains("❌ Pipeline execution failed!")
        result.assertContains("This stage should succeed")
        result.assertContains("About to fail...")
        result.assertContains("Post-always: This always runs even on failure")
        result.assertContains("Post-failure: Pipeline failed as expected")
    }
    
    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Parallel execution test may have timing issues on Windows")
    fun `execute parallel stages pipeline should run stages concurrently`() {
        assertPipelineExists("parallel-stages.pipeline.kts")
        
        val startTime = System.currentTimeMillis()
        val result = runCli(
            "execute", 
            getPipelineFile("parallel-stages.pipeline.kts").absolutePath,
            "--verbose"
        )
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        result.assertSuccess()
        result.assertContains("🚀 Executing pipeline")
        result.assertContains("Parallel Stages Pipeline")
        result.assertContains("Setting up parallel test")
        result.assertContains("Starting parallel task A")
        result.assertContains("Starting parallel task B")
        result.assertContains("Starting parallel task C")
        result.assertContains("Task A completed")
        result.assertContains("Task B completed")
        result.assertContains("Task C completed")
        result.assertContains("Verifying parallel execution results")
        
        // Verificar que la ejecución paralela fue más rápida que la secuencial
        // (Tareas A, B, C duran 1, 2, 1 segundos respectivamente = 4s secuencial vs ~2s paralelo)
        assert(duration < 4000) { 
            "Parallel execution should be faster than sequential. Duration: ${duration}ms" 
        }
    }
    
    @Test
    fun `execute security test pipeline should respect sandbox constraints`() {
        assertPipelineExists("security-test.pipeline.kts")
        
        val result = runCli(
            "execute", 
            getPipelineFile("security-test.pipeline.kts").absolutePath,
            "--verbose"
        )
        
        result.assertSuccess()
        result.assertContains("Security Test Pipeline")
        result.assertContains("Testing safe operations")
        result.assertContains("Testing file operations")
        result.assertContains("This is safe")
        result.assertContains("test content")
        result.assertContains("Security test completed")
    }
    
    @Test
    fun `execute with custom job-id and worker-id should use provided values`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val customJobId = "test-job-12345"
        val customWorkerId = "test-worker-67890"
        
        val result = runCli(
            "execute", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath,
            "--job-id", customJobId,
            "--worker-id", customWorkerId,
            "--verbose"
        )
        
        result.assertSuccess()
        result.assertContains("📋 Job ID: $customJobId")
        result.assertContains("🔧 Worker ID: $customWorkerId")
    }
    
    @Test
    fun `execute non-existent pipeline should fail with helpful error`() {
        val nonExistentFile = tempDir.resolve("non-existent.pipeline.kts").toString()
        
        val result = runCli(
            "execute", 
            nonExistentFile
        )
        
        result.assertFailure()
        result.assertContains("does not exist")
    }
    
    @Test
    fun `execute pipeline with syntax errors should fail during compilation`() {
        // Crear un pipeline con errores de sintaxis
        val badPipeline = createTempPipeline("bad-syntax.pipeline.kts", """
            pipeline("Bad Pipeline") {
                stages {
                    stage("Bad Stage") {
                        steps {
                            echo("Missing quote)
                        }
                    }
                }
            // Missing closing brace
        """.trimIndent())
        
        val result = runCli(
            "execute", 
            badPipeline.absolutePath,
            "--verbose"
        )
        
        result.assertFailure()
        result.assertContains("failed")
    }
    
    @Test
    fun `execute pipeline without verbose should produce minimal output`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val result = runCli(
            "execute", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("🚀 Executing pipeline")
        result.assertContains("✅ Pipeline execution completed successfully!")
        // No debería contener eventos detallados sin --verbose
        result.assertNotContains("📡 Event:")
    }
    
    @Test
    fun `execute pipeline with timeout should complete within reasonable time`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val startTime = System.currentTimeMillis()
        val result = runCli(
            "execute", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath,
            timeoutSeconds = 60 // 1 minuto timeout
        )
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        result.assertSuccess()
        // El pipeline simple debería completarse en menos de 10 segundos
        assert(duration < 10000) { 
            "Simple pipeline should complete quickly. Duration: ${duration}ms" 
        }
    }
    
    @Test
    fun `execute multiple times should be consistent`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        // Ejecutar el mismo pipeline varias veces para verificar consistencia
        repeat(3) { iteration ->
            val result = runCli(
                "execute", 
                getPipelineFile("simple-success.pipeline.kts").absolutePath,
                "--job-id", "consistency-test-$iteration"
            )
            
            result.assertSuccess()
            result.assertContains("✅ Pipeline execution completed successfully!")
            result.assertContains("📋 Job ID: consistency-test-$iteration")
        }
    }
}