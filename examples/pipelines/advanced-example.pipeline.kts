// Pipeline DSL Integrado - Ejemplo Avanzado
// Demuestra características avanzadas con integración completa de workers

import dev.rubentxu.hodei.pipelines.dsl.pipeline
import dev.rubentxu.hodei.pipelines.port.StageType

pipeline("Advanced CI/CD Pipeline") {
    description("Pipeline avanzado con stages paralelos, condicionales y notificaciones")
    
    // Configuración avanzada del agente
    agent {
        kubernetes {
            yaml("""
                apiVersion: v1
                kind: Pod
                spec:
                  containers:
                  - name: gradle
                    image: gradle:7.6-jdk17
                    command: ['sleep', '3600']
                    resources:
                      requests:
                        memory: "2Gi"
                        cpu: "1"
                      limits:
                        memory: "4Gi"  
                        cpu: "2"
                  - name: docker
                    image: docker:dind
                    securityContext:
                      privileged: true
            """.trimIndent())
            namespace("ci-cd")
        }
    }
    
    // Variables de entorno complejas
    environment {
        "BUILD_NUMBER" to "${System.currentTimeMillis()}"
        "GRADLE_OPTS" to "-Xmx2g -XX:+UseG1GC"
        "DOCKER_REGISTRY" to "registry.example.com"
        "K8S_NAMESPACE" to "production"
    }
    
    // Parámetros del pipeline
    parameters {
        choice("DEPLOY_ENV", "development", "staging", "production", 
               defaultValue = "development",
               description = "Environment to deploy to")
        boolean("SKIP_TESTS", defaultValue = "false", 
                description = "Skip test execution")
        string("DOCKER_TAG", 
               description = "Docker image tag (auto-generated if empty)")
    }
    
    // Opciones del pipeline
    options {
        timeout(30, TimeUnit.MINUTES)
        buildDiscarder {
            numToKeep(10)
            daysToKeep(30)
            artifactNumToKeep(5)
        }
        retry(2, RetryCondition.FAILURE)
    }
    
    stages {
        stage("🔍 Preparation") {
            type(StageType.SETUP)
            description("Preparar entorno y validar configuración")
            steps {
                echo("🚀 Iniciando pipeline avanzado...")
                echo("📋 Build Number: \${BUILD_NUMBER}")
                echo("🎯 Deploy Environment: \${DEPLOY_ENV}")
                
                // Validar herramientas
                sh("java -version")
                sh("gradle --version")
                sh("docker --version")
                sh("kubectl version --client")
            }
        }
        
        stage("📥 Checkout & Setup") {
            type(StageType.BUILD)
            description("Checkout código y configurar workspace")
            steps {
                git(
                    url = "https://github.com/example/microservice.git",
                    branch = "main",
                    submodules = true,
                    lfs = true
                )
                
                // Configurar cache de Gradle
                dir(".gradle") {
                    sh("echo 'Configurando cache de Gradle'")
                }
                
                echo("✅ Workspace configurado")
            }
            produces("source-code", "gradle-cache")
        }
        
        stage("🏗️ Build & Package") {
            type(StageType.BUILD)
            requires("source-code")
            
            parallel(failFast = true) {
                stage("Java Build") {
                    steps {
                        echo("☕ Compilando aplicación Java...")
                        sh("./gradlew clean compileJava")
                        sh("./gradlew jar")
                        archiveArtifacts("build/libs/*.jar")
                    }
                }
                
                stage("Docker Build") {
                    steps {
                        echo("🐳 Construyendo imagen Docker...")
                        withEnv(mapOf("DOCKER_BUILDKIT" to "1")) {
                            sh("docker build -t microservice:\${BUILD_NUMBER} .")
                            sh("docker tag microservice:\${BUILD_NUMBER} \${DOCKER_REGISTRY}/microservice:latest")
                        }
                    }
                }
                
                stage("Documentation") {
                    steps {
                        echo("📚 Generando documentación...")
                        sh("./gradlew javadoc")
                        archiveArtifacts("build/docs/**/*")
                    }
                }
            }
            
            produces("java-artifacts", "docker-image", "documentation")
        }
        
        stage("🧪 Testing") {
            type(StageType.TEST)
            requires("java-artifacts")
            
            `when` {
                not {
                    expression("params.SKIP_TESTS == 'true'")
                }
            }
            
            parallel(failFast = false) {
                stage("Unit Tests") {
                    steps {
                        echo("🔬 Ejecutando tests unitarios...")
                        sh("./gradlew test")
                        publishTestResults("build/test-results/test/*.xml")
                        archiveArtifacts("build/reports/tests/**/*")
                    }
                }
                
                stage("Integration Tests") {
                    steps {
                        echo("🔗 Ejecutando tests de integración...")
                        docker("postgres:13") {
                            env("POSTGRES_DB" to "testdb")
                            env("POSTGRES_PASSWORD" to "testpass")
                            command("postgres")
                        }
                        sh("./gradlew integrationTest")
                        publishTestResults("build/test-results/integrationTest/*.xml")
                    }
                }
                
                stage("Security Scan") {
                    steps {
                        echo("🛡️ Escaneando vulnerabilidades...")
                        sh("./gradlew dependencyCheckAnalyze")
                        archiveArtifacts("build/reports/dependency-check-report.html")
                    }
                }
            }
            
            produces("test-results", "security-report")
        }
        
        stage("📊 Quality Gates") {
            type(StageType.VALIDATION)
            requires("test-results")
            steps {
                echo("📈 Analizando calidad del código...")
                sh("./gradlew sonarqube")
                
                // Simular verificación de quality gates
                sh("""
                    echo "Verificando quality gates..."
                    COVERAGE=\$(grep -o 'coverage>[0-9]*' build/reports/jacoco/test/html/index.html | cut -d'>' -f2 || echo "85")
                    echo "Cobertura actual: \${COVERAGE}%"
                    if [ \$COVERAGE -lt 80 ]; then
                        echo "❌ Cobertura insuficiente: \${COVERAGE}% < 80%"
                        exit 1
                    fi
                    echo "✅ Quality gates aprobados"
                """.trimIndent())
            }
        }
        
        stage("🚀 Deploy") {
            type(StageType.DEPLOY)
            requires("docker-image", "test-results")
            
            `when` {
                anyOf {
                    branch("main")
                    tag("v*")
                    environment("DEPLOY_ENV", "staging")
                }
            }
            
            options {
                timeout(10, TimeUnit.MINUTES)
            }
            
            input("¿Continuar con el despliegue?") {
                ok("Deploy Now")
                submitter("admin")
                parameters {
                    string("RELEASE_NOTES", description = "Notas de la release")
                }
            }
            
            steps {
                echo("🎯 Desplegando a \${DEPLOY_ENV}...")
                
                // Push de imagen Docker
                sh("docker push \${DOCKER_REGISTRY}/microservice:latest")
                sh("docker push \${DOCKER_REGISTRY}/microservice:\${BUILD_NUMBER}")
                
                // Deploy en Kubernetes
                sh("""
                    kubectl set image deployment/microservice \\
                        microservice=\${DOCKER_REGISTRY}/microservice:\${BUILD_NUMBER} \\
                        --namespace=\${K8S_NAMESPACE}
                """.trimIndent())
                
                sh("kubectl rollout status deployment/microservice --namespace=\${K8S_NAMESPACE}")
                
                echo("✅ Despliegue completado en \${DEPLOY_ENV}")
            }
            
            produces("deployment")
        }
        
        stage("🧪 Smoke Tests") {
            type(StageType.TEST)
            requires("deployment")
            
            `when` {
                expression("env.DEPLOY_ENV != 'development'")
            }
            
            steps {
                echo("💨 Ejecutando smoke tests...")
                
                // Esperar a que el servicio esté listo
                sh("""
                    echo "Esperando que el servicio esté disponible..."
                    for i in {1..30}; do
                        if curl -f http://microservice.\${K8S_NAMESPACE}.svc.cluster.local/health; then
                            echo "✅ Servicio disponible"
                            break
                        fi
                        echo "Intento \$i: Servicio no disponible, esperando..."
                        sleep 10
                    done
                """.trimIndent())
                
                // Tests básicos de API
                sh("curl -f http://microservice.\${K8S_NAMESPACE}.svc.cluster.local/api/status")
                sh("./gradlew smokeTest")
                
                echo("✅ Smoke tests completados")
            }
        }
    }
    
    // Acciones post-ejecución avanzadas
    post {
        always {
            echo("🧹 Ejecutando limpieza...")
            sh("docker system prune -f")
            archiveArtifacts("build/logs/**/*")
        }
        
        success {
            echo("🎉 ¡Pipeline completado exitosamente!")
            notification("🚀 Deploy successful in \${DEPLOY_ENV}!") {
                slack("#deployments", color = "good")
                email("devops@example.com", "team@example.com")
                teams("https://outlook.office.com/webhook/...", color = "28a745")
            }
        }
        
        failure {
            echo("💥 Pipeline falló")
            notification("❌ Pipeline failed in stage: \${FAILED_STAGE}") {
                slack("#alerts", color = "danger")
                email("devops@example.com", subject = "URGENT: Pipeline Failure")
            }
        }
        
        unstable {
            echo("⚠️ Pipeline inestable")
            notification("⚠️ Pipeline completed with warnings") {
                slack("#deployments", color = "warning")
            }
        }
        
        changed {
            echo("🔄 Estado del pipeline cambió")
            notification("Pipeline status changed") {
                email("team@example.com")
            }
        }
    }
    
    // Triggers
    triggers {
        // Ejecutar cada noche a las 2 AM
        cron("0 2 * * *")
        
        // Polling SCM cada 5 minutos
        pollSCM("H/5 * * * *")
        
        // Trigger por upstream projects
        upstream("library-build", "shared-components", threshold = "SUCCESS")
    }
}