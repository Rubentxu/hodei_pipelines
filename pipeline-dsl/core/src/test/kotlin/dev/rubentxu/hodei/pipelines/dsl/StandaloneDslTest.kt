package dev.rubentxu.hodei.pipelines.dsl

import dev.rubentxu.hodei.pipelines.dsl.model.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests básicos para el Pipeline DSL standalone.
 */
class StandaloneDslTest {

    @Test
    fun `should create simple pipeline with DSL`() {
        // Given/When
        val pipeline = pipeline("Test Pipeline") {
            description("Test pipeline for basic functionality")
            
            stages {
                stage("Test Stage") {
                    steps {
                        echo("Hello World")
                        sh("echo 'Shell command'")
                        bat("echo 'Batch command'")
                    }
                }
            }
        }

        // Then
        assertEquals("Test Pipeline", pipeline.name)
        assertEquals("Test pipeline for basic functionality", pipeline.description)
        assertEquals(1, pipeline.stages.size)
        
        val stage = pipeline.stages.first()
        assertEquals("Test Stage", stage.name)
        assertEquals(3, stage.steps.size)
        
        assertTrue(stage.steps[0] is Step.Echo)
        assertTrue(stage.steps[1] is Step.Shell)
        assertTrue(stage.steps[2] is Step.Batch)
        
        assertEquals("Hello World", (stage.steps[0] as Step.Echo).message)
        assertEquals("echo 'Shell command'", (stage.steps[1] as Step.Shell).command)
        assertEquals("echo 'Batch command'", (stage.steps[2] as Step.Batch).command)
    }

    @Test
    fun `should create pipeline with script step`() {
        // Given/When
        val pipeline = pipeline("Script Test") {
            stages {
                stage("Script Stage") {
                    steps {
                        script(
                            scriptFile = "deploy.sh",
                            parameters = mapOf("ENV" to "prod"),
                            interpreter = "/bin/bash"
                        )
                    }
                }
            }
        }

        // Then
        val scriptStep = pipeline.stages[0].steps[0] as Step.Script
        assertEquals("deploy.sh", scriptStep.scriptFile)
        assertEquals(mapOf("ENV" to "prod"), scriptStep.parameters)
        assertEquals("/bin/bash", scriptStep.interpreter)
    }

    @Test 
    fun `should create pipeline with custom step`() {
        // Given/When
        val pipeline = pipeline("Custom Test") {
            stages {
                stage("Custom Stage") {
                    steps {
                        custom(
                            action = "myCustomAction",
                            parameters = mapOf("param1" to "value1"),
                            name = "My Custom Step"
                        )
                    }
                }
            }
        }

        // Then
        val customStep = pipeline.stages[0].steps[0] as Step.Custom
        assertEquals("myCustomAction", customStep.action)
        assertEquals(mapOf("param1" to "value1"), customStep.parameters)
        assertEquals("My Custom Step", customStep.name)
    }

    @Test
    fun `should create pipeline with environment variables`() {
        // Given/When
        val pipeline = pipeline("Env Test") {
            environment {
                "BUILD_ENV" to "test"
                "DEBUG" to "true"
            }
            
            stages {
                stage("Env Stage") {
                    steps {
                        echo("Testing environment")
                    }
                }
            }
        }

        // Then
        assertEquals(2, pipeline.environment.size)
        assertEquals("test", pipeline.environment["BUILD_ENV"])
        assertEquals("true", pipeline.environment["DEBUG"])
    }

    @Test
    fun `should create pipeline with parallel stages`() {
        // Given/When
        val pipeline = pipeline("Parallel Test") {
            stages {
                stage("Stage 1") {
                    steps {
                        echo("Stage 1")
                    }
                }
                stage("Stage 2") {
                    steps {
                        echo("Stage 2")
                    }
                }
            }
        }

        // Then
        assertEquals(2, pipeline.stages.size)
        assertEquals("Stage 1", pipeline.stages[0].name)
        assertEquals("Stage 2", pipeline.stages[1].name)
    }
}