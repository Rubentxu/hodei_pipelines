package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Tests end-to-end para el comando 'validate' del CLI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidateCommandE2ETest : BaseE2ETest() {
    
    @Test
    fun `validate simple success pipeline should succeed`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val result = runCli(
            "validate", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("🔍 Validating pipeline")
        result.assertContains("simple-success.pipeline.kts")
        result.assertContains("✅ Validation successful! No syntax errors found.")
    }
    
    @Test
    fun `validate parallel stages pipeline should succeed`() {
        assertPipelineExists("parallel-stages.pipeline.kts")
        
        val result = runCli(
            "validate", 
            getPipelineFile("parallel-stages.pipeline.kts").absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("🔍 Validating pipeline")
        result.assertContains("parallel-stages.pipeline.kts")
        result.assertContains("✅ Validation successful! No syntax errors found.")
    }
    
    @Test
    fun `validate security test pipeline should succeed`() {
        assertPipelineExists("security-test.pipeline.kts")
        
        val result = runCli(
            "validate", 
            getPipelineFile("security-test.pipeline.kts").absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("🔍 Validating pipeline")
        result.assertContains("security-test.pipeline.kts")
        result.assertContains("✅ Validation successful! No syntax errors found.")
    }
    
    @Test
    fun `validate invalid syntax pipeline should fail with error details`() {
        val invalidPipeline = createTempPipeline("invalid-validate.pipeline.kts", """
            pipeline("Invalid Pipeline") {
                description("Pipeline with syntax errors")
                
                stages {
                    stage("Bad Stage") {
                        steps {
                            echo("Unclosed string
                            sh("missing quote)
                        }
                    // Missing closing brace for stage
                }
            // Missing closing brace for pipeline
        """.trimIndent())
        
        val result = runCli(
            "validate", 
            invalidPipeline.absolutePath
        )
        
        result.assertFailure()
        result.assertContains("🔍 Validating pipeline")
        result.assertContains("❌ Validation failed")
        result.assertContains("error")
    }
    
    @Test
    fun `validate non-existent file should fail with helpful error`() {
        val nonExistentFile = tempDir.resolve("does-not-exist.pipeline.kts").toString()
        
        val result = runCli(
            "validate", 
            nonExistentFile
        )
        
        result.assertFailure()
        result.assertContains("does not exist")
    }
    
    @Test
    fun `validate empty pipeline should succeed`() {
        val emptyPipeline = createTempPipeline("empty-validate.pipeline.kts", """
            pipeline("Empty Pipeline") {
                description("Pipeline without stages for validation test")
            }
        """.trimIndent())
        
        val result = runCli(
            "validate", 
            emptyPipeline.absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("✅ Validation successful! No syntax errors found.")
    }
    
    @Test
    fun `validate pipeline with semantic errors should succeed syntactically`() {
        // Pipeline que es sintácticamente correcto pero puede tener errores semánticos
        val semanticErrorPipeline = createTempPipeline("semantic-error.pipeline.kts", """
            pipeline("Semantic Error Pipeline") {
                description("Pipeline with semantic issues but valid syntax")
                
                stages {
                    stage("Semantic Issues") {
                        steps {
                            // Referencias a variables no definidas (válido sintácticamente)
                            echo("Value: ${'$'}{undefinedVariable}")
                            sh("${'$'}{anotherUndefinedVariable}")
                            
                            // Comandos que probablemente fallarán
                            sh("non-existent-command --invalid-flag")
                            sh("rm -rf /definitely/does/not/exist")
                        }
                    }
                }
            }
        """.trimIndent())
        
        val result = runCli(
            "validate", 
            semanticErrorPipeline.absolutePath
        )
        
        // La validación sintáctica debería pasar, aunque el pipeline tenga problemas semánticos
        result.assertSuccess()
        result.assertContains("✅ Validation successful! No syntax errors found.")
    }
    
    @Test
    fun `validate pipeline with complex syntax should succeed`() {
        val complexPipeline = createTempPipeline("complex-validate.pipeline.kts", """
            pipeline("Complex Validation Pipeline") {
                description("Pipeline with complex syntax for validation testing")
                
                agent {
                    docker("openjdk:17") {
                        args("-v", "/tmp:/tmp")
                        env("JAVA_OPTS", "-Xmx1g")
                    }
                }
                
                environment {
                    "BUILD_ENV" to "test"
                    "VERSION" to "1.0.0"
                }
                
                parameters {
                    string("branch", "main", "Branch to build")
                    boolean("skipTests", false, "Skip test execution")
                    choice("logLevel", "INFO", listOf("DEBUG", "INFO", "WARN", "ERROR"), "Log level")
                }
                
                stages {
                    stage("Conditional Stage") {
                        when {
                            branch("main", "develop")
                            not(changeRequest())
                            expression("params.skipTests != true")
                        }
                        steps {
                            script {
                                println("Complex script block")
                                val result = "computed value"
                                echo("Result: ${'$'}result")
                            }
                        }
                    }
                    
                    stage("Parallel Complex") {
                        parallel {
                            stage("Nested Stage A") {
                                agent {
                                    label("linux")
                                }
                                steps {
                                    dir("subdir") {
                                        sh("echo 'In subdirectory'")
                                        withEnv(["NESTED_VAR": "value"]) {
                                            sh("echo ${'$'}NESTED_VAR")
                                        }
                                    }
                                }
                            }
                            
                            stage("Nested Stage B") {
                                steps {
                                    timeout(30) {
                                        retry(3) {
                                            sh("flaky-command")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                post {
                    always {
                        archiveArtifacts {
                            patterns("**/*.log", "reports/**")
                            allowEmpty(true)
                            fingerprint(true)
                        }
                    }
                    
                    success {
                        notification {
                            slack {
                                channel("#success")
                                message("Pipeline succeeded")
                                color("good")
                            }
                            email {
                                to("team@example.com")
                                subject("Build Success")
                            }
                        }
                    }
                }
            }
        """.trimIndent())
        
        val result = runCli(
            "validate", 
            complexPipeline.absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("✅ Validation successful! No syntax errors found.")
    }
    
    @Test
    fun `validate multiple pipelines should handle each independently`() {
        val validPipeline = createTempPipeline("valid.pipeline.kts", """
            pipeline("Valid Pipeline") {
                stages {
                    stage("Valid Stage") {
                        steps {
                            echo("This is valid")
                        }
                    }
                }
            }
        """.trimIndent())
        
        val invalidPipeline = createTempPipeline("invalid.pipeline.kts", """
            pipeline("Invalid Pipeline") {
                stages {
                    stage("Invalid Stage") {
                        steps {
                            echo("Unclosed string
                        }
                    }
                }
        """.trimIndent())
        
        // Valid pipeline should pass
        val validResult = runCli("validate", validPipeline.absolutePath)
        validResult.assertSuccess()
        validResult.assertContains("✅ Validation successful!")
        
        // Invalid pipeline should fail
        val invalidResult = runCli("validate", invalidPipeline.absolutePath)
        invalidResult.assertFailure()
        invalidResult.assertContains("❌ Validation failed")
    }
    
    @Test
    fun `validate should be faster than compile`() {
        assertPipelineExists("simple-success.pipeline.kts")
        val pipelineFile = getPipelineFile("simple-success.pipeline.kts").absolutePath
        
        // Medir tiempo de validación
        val validateStartTime = System.currentTimeMillis()
        val validateResult = runCli("validate", pipelineFile)
        val validateEndTime = System.currentTimeMillis()
        val validateDuration = validateEndTime - validateStartTime
        
        // Medir tiempo de compilación
        val compileStartTime = System.currentTimeMillis()
        val compileResult = runCli("compile", pipelineFile)
        val compileEndTime = System.currentTimeMillis()
        val compileDuration = compileEndTime - compileStartTime
        
        validateResult.assertSuccess()
        compileResult.assertSuccess()
        
        // La validación debería ser más rápida o igual que la compilación
        assert(validateDuration <= compileDuration) {
            "Validation ($validateDuration ms) should be faster than or equal to compilation ($compileDuration ms)"
        }
    }
    
    @Test
    fun `validate large pipeline should complete quickly`() {
        // Crear un pipeline grande para test de rendimiento
        val largePipelineContent = buildString {
            appendLine("pipeline(\"Large Validation Pipeline\") {")
            appendLine("    description(\"Large pipeline for validation performance testing\")")
            appendLine("    stages {")
            
            repeat(100) { i ->
                appendLine("        stage(\"Stage $i\") {")
                appendLine("            steps {")
                appendLine("                echo(\"Step in stage $i\")")
                appendLine("                sh(\"echo 'Command in stage $i'\")")
                appendLine("                script {")
                appendLine("                    println(\"Script in stage $i\")")
                appendLine("                }")
                appendLine("            }")
                appendLine("        }")
            }
            
            appendLine("    }")
            appendLine("}")
        }
        
        val largePipeline = createTempPipeline("large-validate.pipeline.kts", largePipelineContent)
        
        val startTime = System.currentTimeMillis()
        val result = runCli("validate", largePipeline.absolutePath)
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        result.assertSuccess()
        result.assertContains("✅ Validation successful!")
        
        // La validación de un pipeline grande debería ser rápida
        assert(duration < 3000) {
            "Large pipeline validation should be fast. Duration: ${duration}ms"
        }
    }
}