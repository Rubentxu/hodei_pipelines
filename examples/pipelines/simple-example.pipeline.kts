// Pipeline DSL Integrado - Ejemplo Simple
// Demuestra la integración completa con el sistema de workers

import dev.rubentxu.hodei.pipelines.dsl.pipeline

pipeline("Simple Build Pipeline") {
    description("Pipeline básico que demuestra la integración con workers")
    
    // Configuración del agente
    agent {
        docker("openjdk:17") {
            args("--rm")
            env("JAVA_TOOL_OPTIONS" to "-Xmx1g")
        }
    }
    
    // Variables de entorno
    environment {
        "BUILD_ENV" to "development"
        "GRADLE_OPTS" to "-Dorg.gradle.daemon=false"
    }
    
    // Stages del pipeline
    stages {
        stage("Checkout") {
            description("Obtener código fuente")
            steps {
                echo("🔄 Iniciando checkout...")
                git(
                    url = "https://github.com/example/project.git",
                    branch = "main",
                    shallow = true
                )
                echo("✅ Checkout completado")
            }
            produces("source-code")
        }
        
        stage("Build") {
            description("Compilar la aplicación")
            requires("source-code")
            steps {
                echo("🏗️ Iniciando build...")
                sh("./gradlew clean build")
                archiveArtifacts("build/libs/*.jar")
                echo("✅ Build completado")
            }
            produces("build-artifacts")
        }
        
        stage("Test") {
            description("Ejecutar tests")
            requires("build-artifacts")
            steps {
                echo("🧪 Ejecutando tests...")
                sh("./gradlew test")
                publishTestResults("build/test-results/test/*.xml")
                echo("✅ Tests completados")
            }
        }
    }
    
    // Acciones post-ejecución
    post {
        always {
            echo("🔄 Limpiando workspace...")
            sh("./gradlew clean")
        }
        
        success {
            echo("🎉 ¡Pipeline ejecutado exitosamente!")
            notification("Build successful!") {
                email("team@example.com", subject = "Build Success")
            }
        }
        
        failure {
            echo("💥 Pipeline falló")
            notification("Build failed!") {
                email("team@example.com", subject = "Build Failed")
            }
        }
    }
}