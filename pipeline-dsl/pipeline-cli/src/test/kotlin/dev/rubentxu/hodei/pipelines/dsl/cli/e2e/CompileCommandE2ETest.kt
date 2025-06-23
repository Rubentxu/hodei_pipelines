package dev.rubentxu.hodei.pipelines.dsl.cli.e2e

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

/**
 * Tests end-to-end para el comando 'compile' del CLI.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CompileCommandE2ETest : BaseE2ETest() {
    
    @Test
    fun `compile simple success pipeline should succeed`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val result = runCli(
            "compile", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("🔧 Compiling pipeline")
        result.assertContains("simple-success.pipeline.kts")
        result.assertContains("✅ Compilation successful!")
        result.assertContains("📋 Pipeline: Simple Success Pipeline")
        result.assertContains("📄 Description: Pipeline básico para tests que siempre tiene éxito")
        result.assertContains("🏗️ Stages:")
        result.assertContains("📦 Total steps:")
        result.assertContains("⏱️ Compilation time:")
    }
    
    @Test
    fun `compile parallel stages pipeline should succeed`() {
        assertPipelineExists("parallel-stages.pipeline.kts")
        
        val result = runCli(
            "compile", 
            getPipelineFile("parallel-stages.pipeline.kts").absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("✅ Compilation successful!")
        result.assertContains("📋 Pipeline: Parallel Stages Pipeline")
        result.assertContains("📄 Description: Pipeline con ejecución paralela para test de concurrencia")
        result.assertContains("🏗️ Stages:")
        result.assertContains("📦 Total steps:")
    }
    
    @Test
    fun `compile with verbose should show detailed analysis`() {
        assertPipelineExists("simple-success.pipeline.kts")
        
        val result = runCli(
            "compile", 
            getPipelineFile("simple-success.pipeline.kts").absolutePath,
            "--verbose"
        )
        
        result.assertSuccess()
        result.assertContains("✅ Compilation successful!")
        result.assertContains("🔍 Detailed Analysis:")
        result.assertContains("Environment variables:")
        result.assertContains("Triggers:")
        result.assertContains("Parameters:")
        result.assertContains("Produced artifacts:")
        result.assertContains("Required artifacts:")
    }
    
    @Test
    fun `compile invalid syntax pipeline should fail with error details`() {
        val invalidPipeline = createTempPipeline("invalid.pipeline.kts", """
            pipeline("Invalid Pipeline") {
                stages {
                    stage("Bad Stage") {
                        steps {
                            echo("Unclosed string
                        }
                    }
                }
            // Missing closing brace
        """.trimIndent())
        
        val result = runCli(
            "compile", 
            invalidPipeline.absolutePath,
            "--verbose"
        )
        
        result.assertFailure()
        result.assertContains("❌ Compilation failed")
    }
    
    @Test
    fun `compile non-existent file should fail with helpful error`() {
        val nonExistentFile = tempDir.resolve("does-not-exist.pipeline.kts").toString()
        
        val result = runCli(
            "compile", 
            nonExistentFile
        )
        
        result.assertFailure()
        result.assertContains("does not exist")
    }
    
    @Test
    fun `compile empty pipeline should handle gracefully`() {
        val emptyPipeline = createTempPipeline("empty.pipeline.kts", """
            pipeline("Empty Pipeline") {
                description("Pipeline without stages")
            }
        """.trimIndent())
        
        val result = runCli(
            "compile", 
            emptyPipeline.absolutePath
        )
        
        result.assertSuccess()
        result.assertContains("✅ Compilation successful!")
        result.assertContains("📋 Pipeline: Empty Pipeline")
        result.assertContains("🏗️ Stages: 0")
        result.assertContains("📦 Total steps: 0")
    }
    
    @Test
    fun `compile large pipeline should complete within reasonable time`() {
        // Crear un pipeline grande con muchos stages
        val largePipelineContent = buildString {
            appendLine("pipeline(\"Large Pipeline\") {")
            appendLine("    description(\"Pipeline with many stages for performance testing\")")
            appendLine("    stages {")
            
            repeat(50) { i ->
                appendLine("        stage(\"Stage $i\") {")
                appendLine("            steps {")
                appendLine("                echo(\"Executing stage $i\")")
                appendLine("                sh(\"echo 'Stage $i completed'\")")
                appendLine("            }")
                appendLine("        }")
            }
            
            appendLine("    }")
            appendLine("}")
        }
        
        val largePipeline = createTempPipeline("large.pipeline.kts", largePipelineContent)
        
        val startTime = System.currentTimeMillis()
        val result = runCli(
            "compile", 
            largePipeline.absolutePath
        )
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        result.assertSuccess()
        result.assertContains("✅ Compilation successful!")
        result.assertContains("📋 Pipeline: Large Pipeline")
        result.assertContains("🏗️ Stages: 50")
        result.assertContains("📦 Total steps: 100")
        
        // La compilación debería ser rápida incluso para pipelines grandes
        assert(duration < 5000) { 
            "Large pipeline compilation should be fast. Duration: ${duration}ms" 
        }
    }
    
    @Test
    fun `compile pipeline with complex syntax should succeed`() {
        val complexPipeline = createTempPipeline("complex.pipeline.kts", """
            pipeline("Complex Pipeline") {
                description("Pipeline with complex syntax and features")
                
                agent {
                    docker("openjdk:17")
                }
                
                environment {
                    "JAVA_HOME" to "/usr/lib/jvm/java-17-openjdk"
                    "GRADLE_OPTS" to "-Xmx2g"
                    "BUILD_NUMBER" to "42"
                }
                
                parameters {
                    string("branch", "main", "Git branch to build")
                    boolean("deploy", false, "Whether to deploy after build")
                    choice("environment", "dev", listOf("dev", "staging", "prod"), "Target environment")
                }
                
                stages {
                    stage("Checkout") {
                        steps {
                            checkout(scm: git {
                                url("https://github.com/example/repo.git")
                                branch(params.branch)
                                credentials("git-credentials")
                            })
                        }
                    }
                    
                    stage("Build") {
                        when {
                            branch("main")
                        }
                        steps {
                            sh("./gradlew clean build")
                            archiveArtifacts("build/libs/*.jar")
                        }
                        post {
                            success {
                                echo("Build successful!")
                            }
                            failure {
                                echo("Build failed!")
                            }
                        }
                    }
                    
                    stage("Test") {
                        parallel {
                            stage("Unit Tests") {
                                steps {
                                    sh("./gradlew test")
                                    publishTestResults("build/test-results/test/*.xml")
                                }
                            }
                            
                            stage("Integration Tests") {
                                steps {
                                    sh("./gradlew integrationTest")
                                    publishTestResults("build/test-results/integrationTest/*.xml")
                                }
                            }
                        }
                    }
                    
                    stage("Deploy") {
                        when {
                            expression("params.deploy == true")
                        }
                        steps {
                            script {
                                if (params.environment == "prod") {
                                    echo("Deploying to production...")
                                    sh("./deploy.sh production")
                                } else {
                                    echo("Deploying to ${'$'}{params.environment}...")
                                    sh("./deploy.sh ${'$'}{params.environment}")
                                }
                            }
                        }
                    }
                }
                
                post {
                    always {
                        echo("Pipeline completed")
                        archiveArtifacts("logs/*.log")
                    }
                    
                    success {
                        notification {
                            slack {
                                channel("#builds")
                                message("✅ Pipeline succeeded for branch ${'$'}{params.branch}")
                            }
                        }
                    }
                    
                    failure {
                        notification {
                            slack {
                                channel("#builds")
                                message("❌ Pipeline failed for branch ${'$'}{params.branch}")
                            }
                        }
                    }
                }
            }
        """.trimIndent())
        
        val result = runCli(
            "compile", 
            complexPipeline.absolutePath,
            "--verbose"
        )
        
        result.assertSuccess()
        result.assertContains("✅ Compilation successful!")
        result.assertContains("📋 Pipeline: Complex Pipeline")
        result.assertContains("🔍 Detailed Analysis:")
        result.assertContains("Environment variables: 3")
        result.assertContains("Parameters: 3")
    }
    
    @Test
    fun `compile multiple pipelines consecutively should be consistent`() {
        val pipeline1 = "simple-success.pipeline.kts"
        val pipeline2 = "parallel-stages.pipeline.kts"
        val pipeline3 = "security-test.pipeline.kts"
        
        listOf(pipeline1, pipeline2, pipeline3).forEach { pipelineName ->
            assertPipelineExists(pipelineName)
            
            val result = runCli(
                "compile", 
                getPipelineFile(pipelineName).absolutePath
            )
            
            result.assertSuccess()
            result.assertContains("✅ Compilation successful!")
        }
    }
}