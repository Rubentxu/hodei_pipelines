package dev.rubentxu.hodei.pipelines.dsl

import dev.rubentxu.hodei.pipelines.dsl.builders.PipelineBuilder
import dev.rubentxu.hodei.pipelines.dsl.model.Pipeline

/**
 * Función principal del Pipeline DSL.
 * 
 * Punto de entrada para la definición de pipelines usando una sintaxis
 * declarativa y tipada que integra con el sistema de workers existente.
 * 
 * Ejemplo de uso:
 * Example usage:
 * ```kotlin
 * pipeline("My CI/CD Pipeline") {
 *     description("Continuous integration pipeline")
 *     
 *     agent {
 *         docker("openjdk:17")
 *     }
 *     
 *     environment {
 *         "JAVA_HOME" to "/usr/lib/jvm/java-17-openjdk"
 *         "GRADLE_OPTS" to "-Xmx2g"
 *     }
 *     
 *     stages {
 *         stage("Build") {
 *             steps {
 *                 sh("gradle clean build")
 *                 archiveArtifacts("build/libs/app.jar")
 *             }
 *             produces("build-artifacts")
 *         }
 *         
 *         stage("Test") {
 *             requires("build-artifacts")
 *             steps {
 *                 sh("gradle test")
 *                 publishTestResults("build/test-results/test.xml")
 *             }
 *         }
 *     }
 * }
 * ```
 * 
 * @param name Nombre del pipeline
 * @param block Lambda con receptor para construir el pipeline
 * @return Pipeline completamente configurado
 * 
 * @since 1.0.0
 */
fun pipeline(name: String, block: PipelineBuilder.() -> Unit): Pipeline {
    val builder = PipelineBuilder(name)
    builder.block()
    return builder.build()
}