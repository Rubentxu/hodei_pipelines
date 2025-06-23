// Ejemplo avanzado que demuestra la integración completa del Pipeline DSL
// con toda la infraestructura worker existente

pipeline("Unified Advanced CI/CD Pipeline") {
    description("Pipeline que integra DSL nuevo con extensiones worker, bibliotecas y seguridad")
    
    // Configuración del agente usando sintaxis DSL
    agent {
        docker("openjdk:17") {
            args("-v", "/var/run/docker.sock:/var/run/docker.sock")
            user("1000:1000")
        }
    }
    
    // Variables de entorno combinando ambos sistemas
    environment {
        "JAVA_HOME" to "/usr/lib/jvm/java-17-openjdk"
        "GRADLE_OPTS" to "-Xmx2g -XX:+HeapDumpOnOutOfMemoryError"
        "DOCKER_REGISTRY" to "registry.example.com"
        "APP_VERSION" to env.get("BUILD_NUMBER", "1.0.0-SNAPSHOT")
    }
    
    // Parámetros del pipeline
    parameters {
        string("DEPLOY_ENV") {
            defaultValue = "development"
            description = "Environment to deploy to"
            choices = listOf("development", "staging", "production")
        }
        boolean("SKIP_TESTS") {
            defaultValue = false
            description = "Skip test execution"
        }
    }
    
    // Triggers usando worker infrastructure
    triggers {
        cron("H 2 * * 1-5") // Weekdays at 2 AM
        scm("H/15 * * * *")  // Poll SCM every 15 minutes
        upstream(projects = listOf("dependency-project"), threshold = "SUCCESS")
    }
    
    stages {
        stage("Setup") {
            description("Prepare build environment and load libraries")
            
            steps {
                // Usar worker context directamente para operaciones avanzadas
                script {
                    """
                    // Acceso directo al contexto worker desde el DSL
                    println("🚀 Starting pipeline for job: ${jobId.value}")
                    println("📍 Running on worker: ${workerId.value}")
                    
                    // Cargar bibliotecas usando worker library management
                    try {
                        val testLibrary = library("org.junit:junit-jupiter:5.8.2")
                        println("📚 Loaded library: ${testLibrary.identifier}")
                    } catch (e: Exception) {
                        println("⚠️ Failed to load optional library: ${e.message}")
                    }
                    
                    // Usar extensiones worker directamente
                    updateProgress(1, 10, "Environment setup completed")
                    """.trimIndent()
                }
                
                // Checkout usando extensión worker mejorada
                checkout {
                    git {
                        url = env.getRequired("GIT_URL")
                        branch = env.get("GIT_BRANCH", "main")
                        credentialsId = "git-credentials"
                        shallow = true
                        depth = 1
                        lfs = true
                        clean = true
                    }
                }
            }
        }
        
        stage("Build") {
            description("Compile and package the application")
            requires("setup-artifacts")
            produces("build-artifacts", "test-reports")
            
            // Herramientas específicas del stage
            tools {
                gradle("7.6")
                jdk("17")
                docker("latest")
            }
            
            // Ejecución condicional usando worker when expressions
            `when` {
                expression("env.SKIP_BUILD != 'true'")
                branch("main", "develop", "release/*")
            }
            
            // Opciones avanzadas del stage
            options {
                timeout(30, TimeUnit.MINUTES)
                retry(2, RetryCondition.FAILURE)
                timestamps()
            }
            
            steps {
                // Usar herramientas configuradas
                withTools {
                    gradle {
                        sh("gradle clean build -x test")
                    }
                }
                
                // Docker operations usando worker extensions
                script {
                    """
                    // Acceso a extensiones worker desde script
                    val dockerExt = getExtension("docker")
                    if (dockerExt != null) {
                        // Usar extensión worker para operaciones Docker
                        sh("docker build -t myapp:${env["APP_VERSION"]} .")
                    }
                    
                    // Generar eventos customizados
                    emitEvent("BUILD", "ArtifactGenerated", mapOf(
                        "artifact" to "myapp.jar",
                        "size" to "15MB",
                        "version" to env["APP_VERSION"]!!
                    ))
                    """.trimIndent()
                }
                
                // Archive usando worker artifact management
                archiveArtifacts {
                    artifacts = "build/libs/*.jar"
                    allowEmptyArchive = false
                    fingerprint = true
                }
            }
            
            // Post actions del stage
            post {
                always {
                    script {
                        "println('🏁 Build stage completed')"
                    }
                }
                failure {
                    notification {
                        message = "Build failed for ${env["GIT_BRANCH"]}"
                        slack {
                            channel = "#ci-alerts"
                            color = "danger"
                        }
                    }
                }
            }
        }
        
        stage("Test") {
            description("Run comprehensive test suite")
            requires("build-artifacts")
            
            `when` {
                not {
                    expression("params.SKIP_TESTS == 'true'")
                }
            }
            
            // Stages paralelos con worker parallel execution
            parallel(failFast = true) {
                stage("Unit Tests") {
                    steps {
                        sh("gradle test")
                        publishTestResults {
                            testResultsPattern = "build/test-results/test/*.xml"
                            allowEmptyResults = false
                            checksName = "Unit Tests"
                        }
                    }
                }
                
                stage("Integration Tests") {
                    agent {
                        docker("postgres:13") {
                            args("--name", "test-db")
                        }
                    }
                    
                    steps {
                        withEnv {
                            "DATABASE_URL" to "jdbc:postgresql://test-db:5432/testdb"
                            "DATABASE_USER" to "test"
                            "DATABASE_PASSWORD" to "test"
                        } steps {
                            sh("gradle integrationTest")
                            publishTestResults {
                                testResultsPattern = "build/test-results/integrationTest/*.xml"
                                checksName = "Integration Tests"
                            }
                        }
                    }
                }
                
                stage("Security Scan") {
                    steps {
                        script {
                            """
                            // Usar worker security manager
                            try {
                                val scanResult = securityManager?.checkScriptAccess("security-scan.sh")
                                if (scanResult?.allowed == true) {
                                    sh("./security-scan.sh")
                                } else {
                                    println("⚠️ Security scan skipped due to policy restrictions")
                                }
                            } catch (e: Exception) {
                                println("⚠️ Security scan failed: ${e.message}")
                            }
                            """.trimIndent()
                        }
                    }
                }
            }
        }
        
        stage("Deploy") {
            description("Deploy to target environment")
            requires("build-artifacts")
            
            `when` {
                anyOf {
                    branch("main")
                    changeRequest(target = "main")
                    environment("DEPLOY_ENV", "production")
                }
            }
            
            input {
                message = "Deploy to ${params["DEPLOY_ENV"]}?"
                submitter = "deploy-team"
                parameters {
                    choice("DEPLOYMENT_STRATEGY") {
                        choices = listOf("blue-green", "rolling", "canary")
                        defaultValue = "rolling"
                    }
                }
            }
            
            steps {
                dir("deployment") {
                    // Usar worker context para operaciones complejas
                    script {
                        """
                        // Acceso completo al contexto worker
                        val deployEnv = params["DEPLOY_ENV"]!!
                        val strategy = params["DEPLOYMENT_STRATEGY"]!!
                        
                        println("🚀 Deploying to $deployEnv using $strategy strategy")
                        
                        // Usar worker para operaciones seguras
                        updateProgress(8, 10, "Starting deployment to $deployEnv")
                        
                        // Emitir eventos de deployment
                        emitEvent("DEPLOYMENT", "DeploymentStarted", mapOf(
                            "environment" to deployEnv,
                            "strategy" to strategy,
                            "version" to env["APP_VERSION"]!!
                        ))
                        """.trimIndent()
                    }
                    
                    // Operations usando workers y extensions
                    withEnv {
                        "DEPLOY_ENV" to params["DEPLOY_ENV"]!!
                        "APP_VERSION" to env["APP_VERSION"]!!
                    } steps {
                        sh("./deploy.sh")
                    }
                }
            }
        }
    }
    
    // Post actions globales del pipeline
    post {
        always {
            script {
                """
                // Cleanup usando worker context
                println("🧹 Cleaning up resources")
                
                // Usar worker para cleanup seguro
                try {
                    sh("docker system prune -f")
                } catch (e: Exception) {
                    println("⚠️ Docker cleanup failed: ${e.message}")
                }
                
                // Métricas finales
                val duration = System.currentTimeMillis() - currentBuild.timestamp.toEpochMilli()
                emitEvent("PIPELINE", "PipelineCompleted", mapOf(
                    "duration" to duration,
                    "result" to currentBuild.result,
                    "stages" to stages.size
                ))
                """.trimIndent()
            }
        }
        
        success {
            notification {
                message = "✅ Pipeline completed successfully for ${env["GIT_BRANCH"]}"
                onlyOnStateChange = true
                slack {
                    channel = "#ci-success"
                    color = "good"
                }
            }
        }
        
        failure {
            script {
                """
                // Usar worker para análisis de fallos
                println("❌ Pipeline failed - collecting diagnostic info")
                
                try {
                    archiveArtifacts("logs/**/*.log", allowEmptyArchive = true)
                } catch (e: Exception) {
                    println("Failed to archive logs: ${e.message}")
                }
                """.trimIndent()
            }
            
            notification {
                message = "❌ Pipeline failed for ${env["GIT_BRANCH"]} - please check logs"
                slack {
                    channel = "#ci-alerts" 
                    color = "danger"
                }
                email {
                    to = listOf("dev-team@example.com")
                    subject = "CI/CD Pipeline Failed"
                }
            }
        }
    }
}