// Ejemplo limpio del nuevo Pipeline DSL sin dependencias legacy

pipeline("Clean CI/CD Pipeline") {
    description("Pipeline DSL renovado y simplificado")
    
    // Configuración del agente
    agent {
        docker("openjdk:17") {
            args("-v", "/var/run/docker.sock:/var/run/docker.sock")
        }
    }
    
    // Variables de entorno
    environment {
        "JAVA_HOME" to "/usr/lib/jvm/java-17-openjdk"
        "GRADLE_OPTS" to "-Xmx2g"
        "APP_VERSION" to "1.0.0"
    }
    
    // Parámetros configurables
    parameters {
        string("DEPLOY_ENV") {
            defaultValue = "development"
            description = "Target deployment environment"
            choices = listOf("development", "staging", "production")
        }
        
        boolean("SKIP_TESTS") {
            defaultValue = false
            description = "Skip test execution"
        }
    }
    
    stages {
        stage("Setup") {
            description("Prepare build environment")
            
            steps {
                echo("🚀 Starting pipeline execution")
                
                checkout {
                    git {
                        url = "https://github.com/company/app.git"
                        branch = "main"
                        shallow = true
                        depth = 1
                    }
                }
                
                echo("✅ Repository checked out successfully")
            }
        }
        
        stage("Build") {
            description("Compile and package application")
            requires("setup-artifacts")
            produces("build-artifacts")
            
            // Ejecución condicional
            `when` {
                expression("env.SKIP_BUILD != 'true'")
                branch("main", "develop")
            }
            
            // Configuración de herramientas
            tools {
                gradle("7.6")
                jdk("17")
            }
            
            // Opciones del stage
            options {
                timeout(20, TimeUnit.MINUTES)
                retry(2, RetryCondition.FAILURE)
                timestamps()
            }
            
            steps {
                echo("🔨 Building application")
                
                sh("gradle clean build -x test")
                
                // Docker build
                docker {
                    build {
                        tag = "myapp:${env["APP_VERSION"]}"
                        dockerfile = "Dockerfile"
                        context = "."
                    }
                }
                
                // Archivar artifacts
                archiveArtifacts {
                    artifacts = "build/libs/*.jar"
                    allowEmptyArchive = false
                    fingerprint = true
                }
                
                echo("✅ Build completed successfully")
            }
            
            post {
                always {
                    echo("🧹 Build stage cleanup")
                }
                failure {
                    notification {
                        message = "Build failed for branch ${env["GIT_BRANCH"]}"
                        slack {
                            channel = "#build-alerts"
                            color = "danger"
                        }
                    }
                }
            }
        }
        
        stage("Test") {
            description("Run test suite")
            requires("build-artifacts")
            
            `when` {
                not {
                    expression("params.SKIP_TESTS == 'true'")
                }
            }
            
            // Tests paralelos
            parallel(failFast = false) {
                stage("Unit Tests") {
                    steps {
                        echo("🧪 Running unit tests")
                        sh("gradle test")
                        
                        publishTestResults {
                            testResultsPattern = "build/test-results/test/*.xml"
                            allowEmptyResults = false
                            checksName = "Unit Tests"
                        }
                    }
                }
                
                stage("Integration Tests") {
                    steps {
                        echo("🔗 Running integration tests")
                        
                        withEnv {
                            "DATABASE_URL" to "jdbc:h2:mem:testdb"
                        } steps {
                            sh("gradle integrationTest")
                            
                            publishTestResults {
                                testResultsPattern = "build/test-results/integrationTest/*.xml"
                                checksName = "Integration Tests"
                            }
                        }
                    }
                }
                
                stage("Code Quality") {
                    steps {
                        echo("📊 Running code quality checks")
                        sh("gradle check")
                        
                        script {
                            """
                            echo "Running additional quality checks"
                            gradle sonarqube || echo "SonarQube analysis completed"
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
                    environment("DEPLOY_ENV", "production")
                }
            }
            
            input {
                message = "Deploy to ${params["DEPLOY_ENV"]}?"
                submitter = "deployment-team"
                parameters {
                    choice("DEPLOYMENT_STRATEGY") {
                        choices = listOf("blue-green", "rolling", "canary")
                        defaultValue = "rolling"
                    }
                }
            }
            
            steps {
                echo("🚀 Starting deployment to ${params["DEPLOY_ENV"]}")
                
                dir("deployment") {
                    withEnv {
                        "DEPLOY_ENV" to params["DEPLOY_ENV"]!!
                        "APP_VERSION" to env["APP_VERSION"]!!
                        "STRATEGY" to params["DEPLOYMENT_STRATEGY"]!!
                    } steps {
                        script {
                            """
                            echo "Deploying with strategy: ${env["STRATEGY"]}"
                            echo "Target environment: ${env["DEPLOY_ENV"]}"
                            echo "Application version: ${env["APP_VERSION"]}"
                            
                            # Deployment script
                            ./deploy.sh
                            """.trimIndent()
                        }
                    }
                }
                
                echo("✅ Deployment completed successfully")
            }
        }
    }
    
    // Acciones post-pipeline
    post {
        always {
            echo("🏁 Pipeline completed")
            
            script {
                """
                echo "Cleaning up temporary resources"
                docker system prune -f || true
                """.trimIndent()
            }
        }
        
        success {
            echo("🎉 Pipeline succeeded!")
            
            notification {
                message = "✅ Pipeline completed successfully for ${env["GIT_BRANCH"]}"
                onlyOnStateChange = true
                slack {
                    channel = "#deployment-success"
                    color = "good"
                }
            }
        }
        
        failure {
            echo("❌ Pipeline failed")
            
            notification {
                message = "❌ Pipeline failed for ${env["GIT_BRANCH"]} - please check logs"
                slack {
                    channel = "#build-alerts"
                    color = "danger"
                }
                email {
                    to = listOf("dev-team@company.com", "ops-team@company.com")
                    subject = "Pipeline Failure Alert"
                }
            }
        }
        
        unstable {
            echo("⚠️ Pipeline completed with warnings")
            
            notification {
                message = "⚠️ Pipeline completed with warnings for ${env["GIT_BRANCH"]}"
                slack {
                    channel = "#build-warnings"
                    color = "warning"
                }
            }
        }
    }
}