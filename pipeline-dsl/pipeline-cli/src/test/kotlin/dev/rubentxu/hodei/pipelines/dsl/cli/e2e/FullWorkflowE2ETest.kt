package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS

/**
 * Tests end-to-end para flujos de trabajo completos del CLI.
 * Testa la integración entre comandos y flujos realistas de uso.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FullWorkflowE2ETest : BaseE2ETest() {
    
    @Test
    fun `complete pipeline development workflow should work end to end`() {
        // 1. Crear un pipeline nuevo
        val newPipeline = createTempPipeline("workflow-test.pipeline.kts", """
            pipeline("Workflow Test Pipeline") {
                description("Pipeline para test de flujo completo")
                
                environment {
                    "BUILD_ENV" to "test"
                    "VERSION" to "1.0.0"
                }
                
                stages {
                    stage("Validate Environment") {
                        steps {
                            echo("Starting workflow test")
                            sh("echo 'Environment: ${'$'}BUILD_ENV'")
                            sh("echo 'Version: ${'$'}VERSION'")
                        }
                    }
                    
                    stage("Build") {
                        steps {
                            echo("Building application...")
                            sh("mkdir -p build")
                            sh("echo 'Build artifact' > build/app.jar")
                            echo("Build completed successfully")
                        }
                    }
                    
                    stage("Test") {
                        parallel {
                            stage("Unit Tests") {
                                steps {
                                    echo("Running unit tests...")
                                    sh("echo 'All unit tests passed'")
                                }
                            }
                            
                            stage("Integration Tests") {
                                steps {
                                    echo("Running integration tests...")
                                    sh("echo 'All integration tests passed'")
                                }
                            }
                        }
                    }
                    
                    stage("Package") {
                        steps {
                            echo("Packaging application...")
                            sh("tar -czf build/app-${'$'}VERSION.tar.gz build/app.jar")
                            echo("Package created: app-${'$'}VERSION.tar.gz")
                        }
                    }
                }
                
                post {
                    always {
                        echo("Workflow test completed")
                        sh("ls -la build/ || echo 'No build directory'")
                    }
                    
                    success {
                        echo("🎉 Workflow test succeeded!")
                    }
                    
                    failure {
                        echo("❌ Workflow test failed!")
                    }
                }
            }
        """.trimIndent())
        
        // 2. Validar sintaxis
        val validateResult = runCli("validate", newPipeline.absolutePath)
        validateResult.assertSuccess()
        validateResult.assertContains("✅ Validation successful!")
        
        // 3. Compilar pipeline
        val compileResult = runCli("compile", newPipeline.absolutePath, "--verbose")
        compileResult.assertSuccess()
        compileResult.assertContains("✅ Compilation successful!")
        compileResult.assertContains("📋 Pipeline: Workflow Test Pipeline")
        compileResult.assertContains("🏗️ Stages: 4")
        compileResult.assertContains("Environment variables: 2")
        
        // 4. Ejecutar pipeline
        val executeResult = runCli("execute", newPipeline.absolutePath, "--verbose")
        executeResult.assertSuccess()
        executeResult.assertContains("✅ Pipeline execution completed successfully!")
        executeResult.assertContains("Starting workflow test")
        executeResult.assertContains("Environment: test")
        executeResult.assertContains("Version: 1.0.0")
        executeResult.assertContains("Building application...")
        executeResult.assertContains("Running unit tests...")
        executeResult.assertContains("Running integration tests...")
        executeResult.assertContains("Packaging application...")
        executeResult.assertContains("Package created: app-1.0.0.tar.gz")
        executeResult.assertContains("🎉 Workflow test succeeded!")
        executeResult.assertContains("Workflow test completed")
    }
    
    @Test
    fun `error handling workflow should provide helpful feedback`() {
        // Crear un pipeline que falla
        val failingPipeline = createTempPipeline("failing-workflow.pipeline.kts", """
            pipeline("Failing Workflow Pipeline") {
                description("Pipeline que falla intencionalmente para test de error handling")
                
                stages {
                    stage("Pre-failure Setup") {
                        steps {
                            echo("Setting up for failure test")
                            sh("mkdir -p test-artifacts")
                            sh("echo 'Setup completed' > test-artifacts/setup.log")
                        }
                    }
                    
                    stage("Intentional Failure") {
                        steps {
                            echo("About to fail intentionally...")
                            sh("exit 42")  // Código de salida específico
                            echo("This should not execute")
                        }
                    }
                    
                    stage("Should Not Run") {
                        steps {
                            echo("This stage should not run")
                        }
                    }
                }
                
                post {
                    always {
                        echo("Cleanup: Always runs")
                        sh("ls -la test-artifacts/ || echo 'No test artifacts'")
                    }
                    
                    failure {
                        echo("Failure handling executed")
                        sh("echo 'Pipeline failed at stage Intentional Failure' > test-artifacts/failure.log")
                    }
                }
            }
        """.trimIndent())
        
        // 1. Validar (debería pasar)
        val validateResult = runCli("validate", failingPipeline.absolutePath)
        validateResult.assertSuccess()
        
        // 2. Compilar (debería pasar)
        val compileResult = runCli("compile", failingPipeline.absolutePath)
        compileResult.assertSuccess()
        compileResult.assertContains("Failing Workflow Pipeline")
        
        // 3. Ejecutar (debería fallar con información útil)
        val executeResult = runCli("execute", failingPipeline.absolutePath, "--verbose")
        executeResult.assertFailure()
        executeResult.assertContains("❌ Pipeline execution failed!")
        executeResult.assertContains("Setting up for failure test")
        executeResult.assertContains("About to fail intentionally...")
        executeResult.assertContains("Cleanup: Always runs")
        executeResult.assertContains("Failure handling executed")
        executeResult.assertNotContains("This should not execute")
        executeResult.assertNotContains("This stage should not run")
    }
    
    @Test
    fun `performance workflow should handle large pipelines efficiently`() {
        // Crear un pipeline con muchas operaciones para test de rendimiento
        val largePipelineContent = buildString {
            appendLine("pipeline(\"Performance Test Pipeline\") {")
            appendLine("    description(\"Pipeline grande para test de rendimiento\")")
            appendLine("    environment {")
            appendLine("        \"PERF_TEST\" to \"true\"")
            appendLine("        \"STAGE_COUNT\" to \"25\"")
            appendLine("    }")
            appendLine("    stages {")
            
            repeat(25) { i ->
                appendLine("        stage(\"Performance Stage $i\") {")
                appendLine("            steps {")
                appendLine("                echo(\"Executing performance stage $i\")")
                appendLine("                sh(\"echo 'Stage $i: Creating test data'\")")
                appendLine("                sh(\"mkdir -p perf-test/stage-$i\")")
                appendLine("                sh(\"echo 'data-$i' > perf-test/stage-$i/data.txt\")")
                appendLine("                sh(\"echo 'Stage $i completed at: \\$(date)'\")")
                appendLine("            }")
                appendLine("        }")
            }
            
            appendLine("        stage(\"Performance Summary\") {")
            appendLine("            steps {")
            appendLine("                echo(\"Performance test summary\")")
            appendLine("                sh(\"find perf-test -name '*.txt' | wc -l\")")
            appendLine("                sh(\"ls -la perf-test/\")")
            appendLine("                sh(\"rm -rf perf-test\")")
            appendLine("            }")
            appendLine("        }")
            appendLine("    }")
            appendLine("}")
        }
        
        val largePipeline = createTempPipeline("performance-test.pipeline.kts", largePipelineContent)
        
        // Medir tiempos de cada fase
        val validateStartTime = System.currentTimeMillis()
        val validateResult = runCli("validate", largePipeline.absolutePath)
        val validateDuration = System.currentTimeMillis() - validateStartTime
        
        val compileStartTime = System.currentTimeMillis()
        val compileResult = runCli("compile", largePipeline.absolutePath)
        val compileDuration = System.currentTimeMillis() - compileStartTime
        
        val executeStartTime = System.currentTimeMillis()
        val executeResult = runCli("execute", largePipeline.absolutePath)
        val executeDuration = System.currentTimeMillis() - executeStartTime
        
        // Verificar que todas las fases completaron exitosamente
        validateResult.assertSuccess()
        compileResult.assertSuccess()
        executeResult.assertSuccess()
        
        // Verificar contenido esperado
        compileResult.assertContains("🏗️ Stages: 26")  // 25 + summary
        executeResult.assertContains("✅ Pipeline execution completed successfully!")
        executeResult.assertContains("Performance test summary")
        
        // Verificar tiempos razonables
        assert(validateDuration < 5000) { "Validation too slow: ${validateDuration}ms" }
        assert(compileDuration < 10000) { "Compilation too slow: ${compileDuration}ms" }
        assert(executeDuration < 60000) { "Execution too slow: ${executeDuration}ms" }
        
        println("Performance metrics:")
        println("  Validation: ${validateDuration}ms")
        println("  Compilation: ${compileDuration}ms")
        println("  Execution: ${executeDuration}ms")
    }
    
    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Complex parallel workflow may have timing issues on Windows")
    fun `parallel workflow should demonstrate concurrent execution`() {
        val parallelPipeline = createTempPipeline("parallel-workflow.pipeline.kts", """
            pipeline("Parallel Workflow Pipeline") {
                description("Pipeline con múltiples stages paralelos")
                
                environment {
                    "PARALLEL_TEST" to "advanced"
                    "TIMING_TEST" to "true"
                }
                
                stages {
                    stage("Sequential Setup") {
                        steps {
                            echo("Setting up parallel workflow test")
                            sh("mkdir -p parallel-results")
                            sh("echo 'Setup started at: \\$(date)' > parallel-results/timing.log")
                        }
                    }
                    
                    stage("Parallel Processing Group 1") {
                        parallel {
                            stage("CPU Intensive Task") {
                                steps {
                                    echo("Starting CPU intensive task")
                                    sh("echo 'CPU task started at: \\$(date)' >> parallel-results/timing.log")
                                    sh("sleep 2")  // Simular trabajo CPU intensivo
                                    sh("echo 'CPU task result' > parallel-results/cpu-result.txt")
                                    sh("echo 'CPU task completed at: \\$(date)' >> parallel-results/timing.log")
                                }
                            }
                            
                            stage("I/O Intensive Task") {
                                steps {
                                    echo("Starting I/O intensive task")
                                    sh("echo 'I/O task started at: \\$(date)' >> parallel-results/timing.log")
                                    sh("for i in \\$(seq 1 5); do echo \"I/O operation \\${'$'}i\" >> parallel-results/io-result.txt; sleep 0.5; done")
                                    sh("echo 'I/O task completed at: \\$(date)' >> parallel-results/timing.log")
                                }
                            }
                            
                            stage("Network Simulation Task") {
                                steps {
                                    echo("Starting network simulation")
                                    sh("echo 'Network task started at: \\$(date)' >> parallel-results/timing.log")
                                    sh("sleep 1")  // Simular latencia de red
                                    sh("echo 'Network operation result' > parallel-results/network-result.txt")
                                    sh("echo 'Network task completed at: \\$(date)' >> parallel-results/timing.log")
                                }
                            }
                        }
                    }
                    
                    stage("Parallel Processing Group 2") {
                        parallel {
                            stage("Data Processing A") {
                                steps {
                                    echo("Processing data set A")
                                    sh("echo 'Processing A started at: \\$(date)' >> parallel-results/timing.log")
                                    sh("echo 'Data set A: Item 1' > parallel-results/dataset-a.txt")
                                    sh("sleep 1")
                                    sh("echo 'Data set A: Item 2' >> parallel-results/dataset-a.txt")
                                    sh("echo 'Processing A completed at: \\$(date)' >> parallel-results/timing.log")
                                }
                            }
                            
                            stage("Data Processing B") {
                                steps {
                                    echo("Processing data set B")
                                    sh("echo 'Processing B started at: \\$(date)' >> parallel-results/timing.log")
                                    sh("echo 'Data set B: Item 1' > parallel-results/dataset-b.txt")
                                    sh("sleep 1")
                                    sh("echo 'Data set B: Item 2' >> parallel-results/dataset-b.txt")
                                    sh("echo 'Processing B completed at: \\$(date)' >> parallel-results/timing.log")
                                }
                            }
                        }
                    }
                    
                    stage("Results Aggregation") {
                        steps {
                            echo("Aggregating parallel processing results")
                            sh("echo 'Aggregation started at: \\$(date)' >> parallel-results/timing.log")
                            sh("echo '=== PARALLEL WORKFLOW RESULTS ===' > parallel-results/final-report.txt")
                            sh("echo 'CPU Results:' >> parallel-results/final-report.txt")
                            sh("cat parallel-results/cpu-result.txt >> parallel-results/final-report.txt")
                            sh("echo 'I/O Results:' >> parallel-results/final-report.txt")
                            sh("cat parallel-results/io-result.txt >> parallel-results/final-report.txt")
                            sh("echo 'Network Results:' >> parallel-results/final-report.txt")
                            sh("cat parallel-results/network-result.txt >> parallel-results/final-report.txt")
                            sh("echo 'Dataset A:' >> parallel-results/final-report.txt")
                            sh("cat parallel-results/dataset-a.txt >> parallel-results/final-report.txt")
                            sh("echo 'Dataset B:' >> parallel-results/final-report.txt")
                            sh("cat parallel-results/dataset-b.txt >> parallel-results/final-report.txt")
                            sh("echo 'Aggregation completed at: \\$(date)' >> parallel-results/timing.log")
                            
                            echo("Displaying final results:")
                            sh("cat parallel-results/final-report.txt")
                            sh("echo 'Timing log:'")
                            sh("cat parallel-results/timing.log")
                        }
                    }
                }
                
                post {
                    always {
                        echo("Cleaning up parallel workflow test")
                        sh("rm -rf parallel-results")
                    }
                    
                    success {
                        echo("🚀 Parallel workflow completed successfully!")
                    }
                }
            }
        """.trimIndent())
        
        val startTime = System.currentTimeMillis()
        val result = runCli("execute", parallelPipeline.absolutePath, "--verbose")
        val endTime = System.currentTimeMillis()
        val totalDuration = endTime - startTime
        
        result.assertSuccess()
        result.assertContains("🚀 Parallel workflow completed successfully!")
        result.assertContains("Setting up parallel workflow test")
        result.assertContains("Starting CPU intensive task")
        result.assertContains("Starting I/O intensive task")
        result.assertContains("Starting network simulation")
        result.assertContains("Processing data set A")
        result.assertContains("Processing data set B")
        result.assertContains("Aggregating parallel processing results")
        result.assertContains("=== PARALLEL WORKFLOW RESULTS ===")
        
        // El workflow paralelo debería completarse más rápido que la ejecución secuencial
        // Tiempos esperados: Group 1 ~2s paralelo (vs 3.5s secuencial), Group 2 ~1s paralelo (vs 2s secuencial)
        assert(totalDuration < 15000) {
            "Parallel workflow should complete efficiently. Duration: ${totalDuration}ms"
        }
        
        println("Parallel workflow completed in ${totalDuration}ms")
    }
    
    @Test
    fun `info command integration should provide useful context for other commands`() {
        // 1. Primero obtener información del DSL
        val infoResult = runCli("info")
        infoResult.assertSuccess()
        infoResult.assertContains("📖 Pipeline DSL Information")
        infoResult.assertContains("🔧 Supported Step Types:")
        
        // Extraer tipos de step soportados del output
        val supportedSteps = mutableListOf<String>()
        val infoLines = infoResult.stdout.lines()
        var inStepSection = false
        
        for (line in infoLines) {
            when {
                line.contains("Supported Step Types:") -> inStepSection = true
                line.contains("Available Imports:") -> inStepSection = false
                inStepSection && line.contains("•") -> {
                    val stepType = line.substringAfter("•").trim()
                    if (stepType.isNotEmpty()) {
                        supportedSteps.add(stepType)
                    }
                }
            }
        }
        
        // 2. Crear un pipeline que usa algunos de los step types listados
        val integrationPipeline = createTempPipeline("info-integration.pipeline.kts", """
            pipeline("Info Integration Pipeline") {
                description("Pipeline que usa step types mostrados en info command")
                
                stages {
                    stage("Test Supported Steps") {
                        steps {
                            echo("Testing step types from info command")
                            sh("echo 'Shell step works'")
                            // Agregar más tipos según lo que esté disponible
                        }
                    }
                }
            }
        """.trimIndent())
        
        // 3. Verificar que el pipeline funciona
        val executeResult = runCli("execute", integrationPipeline.absolutePath)
        executeResult.assertSuccess()
        executeResult.assertContains("Testing step types from info command")
        executeResult.assertContains("Shell step works")
        
        // 4. Verificar que al menos algunos step types básicos están disponibles
        assert(supportedSteps.contains("sh")) { "Should support 'sh' step type" }
        assert(supportedSteps.contains("echo")) { "Should support 'echo' step type" }
        
        println("Integration test verified ${supportedSteps.size} supported step types")
    }
}