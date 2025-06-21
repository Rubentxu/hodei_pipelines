# Guía del DSL de Pipeline

## Descripción General

El DSL de Pipeline de Hodei-Pipelines proporciona una sintaxis declarativa similar a la de Jenkins para definir pipelines de construcción en Kotlin. Soporta etapas, ejecución en paralelo, scripting dinámico y una amplia personalización a través de extensiones.

## Estructura Básica de un Pipeline

```kotlin
pipeline {
    stage("Preparación") {
        script {
            println("Configurando el entorno...")
            sh("git clean -fdx")
        }
    }
    
    stage("Construcción") {
        parallel {
            task("Compilar") {
                sh("./gradlew compileKotlin")
            }
            task("Generar Recursos") {
                sh("./gradlew processResources")
            }
        }
    }
    
    stage("Pruebas") {
        script {
            sh("./gradlew test")
            publishTestResults("**/build/test-results/test/TEST-*.xml")
        }
    }
}
```

## Estrategias de Ejecución

