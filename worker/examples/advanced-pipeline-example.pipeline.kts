// Advanced Pipeline DSL Example
// This showcases the full capabilities of the Hodei Pipeline DSL

@file:Repository("https://repo1.maven.org/maven2/")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

// Load external libraries
val dockerLib = library("docker-java:3.3.0")
val kubernetesLib = library("kubernetes-client:6.0.0")

// Pipeline configuration
pipeline {
    // Agent configuration
    agent {
        label("docker-enabled")
    }
    
    // Environment variables
    environment {
        set("DOCKER_REGISTRY", params.get("registry", "my-registry.com"))
        set("APP_VERSION", params.get("version", "latest"))
        set("ENVIRONMENT", params.get("environment", "staging"))
        set("NOTIFICATION_CHANNEL", "#deployments")
    }
    
    // Pipeline stages
    stages {
        
        stage("Setup", StageType.SETUP) {
            steps {
                echo("🚀 Starting pipeline for ${env.get("APP_VERSION")}")
                
                // Emit custom event
                emitEvent("pipeline", "setup_started", mapOf(
                    "version" to env.get("APP_VERSION"),
                    "environment" to env.get("ENVIRONMENT")
                ))
                
                // Update progress
                updateProgress(1, 10, "Environment setup complete")
            }
        }
        
        stage("Checkout", StageType.BUILD) {
            steps {
                // Use Git extension
                git.checkout(
                    url = scm.url,
                    branch = env.get("BRANCH_NAME", "main"),
                    depth = 1
                )
                
                updateProgress(2, 10, "Source code checked out")
            }
        }
        
        stage("Build & Test", StageType.BUILD) {
            parallel {
                stage("Unit Tests") {
                    steps {
                        echo("Running unit tests...")
                        
                        script {
                            // Custom Kotlin code
                            val testResults = sh("./gradlew test --info")
                            
                            // Parse test results (simplified)
                            val passedTests = 15
                            val failedTests = 0
                            
                            emitEvent("testing", "unit_tests_completed", mapOf(
                                "passed" to passedTests,
                                "failed" to failedTests,
                                "duration" to 45000
                            ))
                            
                            if (failedTests > 0) {
                                throw RuntimeException("Unit tests failed")
                            }
                        }
                        
                        // Archive test results
                        archiveArtifacts("**/test-results/**/*.xml")
                        updateProgress(4, 10, "Unit tests completed")
                    }
                }
                
                stage("Integration Tests") {
                    steps {
                        script {
                            // Use Docker library for test database
                            val docker = dockerLib.createClient()
                            val containerId = docker.createContainer("postgres:13", mapOf(
                                "POSTGRES_DB" to "testdb",
                                "POSTGRES_USER" to "test",
                                "POSTGRES_PASSWORD" to "test"
                            ))
                            
                            try {
                                docker.startContainer(containerId)
                                
                                // Wait for database to be ready
                                Thread.sleep(10000)
                                
                                sh("./gradlew integrationTest")
                                
                                emitEvent("testing", "integration_tests_completed", mapOf(
                                    "database" to "postgres:13",
                                    "status" to "success"
                                ))
                                
                            } finally {
                                docker.removeContainer(containerId, true)
                            }
                        }
                        
                        updateProgress(5, 10, "Integration tests completed")
                    }
                }
                
                stage("Security Scan") {
                    steps {
                        echo("Running security scan...")
                        
                        script {
                            // Simulate security scan
                            val vulnerabilities = sh("./gradlew dependencyCheckAnalyze --info")
                            
                            emitEvent("security", "scan_completed", mapOf(
                                "vulnerabilities_found" to 0,
                                "severity" to "none"
                            ))
                        }
                        
                        updateProgress(6, 10, "Security scan completed")
                    }
                }
            }
        }
        
        stage("Build Docker Image", StageType.PACKAGING) {
            when_(env.get("BRANCH_NAME") == "main") {
                steps {
                    script {
                        val imageTag = "${env.get("DOCKER_REGISTRY")}/myapp:${env.get("APP_VERSION")}"
                        
                        // Use Docker extension
                        docker.build(
                            tag = imageTag,
                            dockerfile = "Dockerfile",
                            context = "."
                        )
                        
                        // Security scan for Docker image
                        sh("trivy image $imageTag")
                        
                        docker.push(imageTag)
                        
                        emitEvent("build", "docker_image_created", mapOf(
                            "image" to imageTag,
                            "size" to "245MB"
                        ))
                    }
                    
                    archiveArtifacts("target/*.jar")
                    updateProgress(7, 10, "Docker image built and pushed")
                }
            }
        }
        
        stage("Deploy", StageType.DEPLOY) {
            when_(params.get("ENVIRONMENT")) {
                "staging" -> {
                    steps {
                        echo("Deploying to staging environment...")
                        
                        script {
                            val k8s = kubernetesLib.createClient()
                            val imageTag = "${env.get("DOCKER_REGISTRY")}/myapp:${env.get("APP_VERSION")}"
                            
                            // Deploy to Kubernetes
                            k8s.deployApplication(
                                namespace = "staging",
                                image = imageTag,
                                replicas = 2
                            )
                            
                            // Wait for deployment to be ready
                            k8s.waitForDeployment("staging", "myapp", timeout = 300)
                            
                            emitEvent("deployment", "staging_deployed", mapOf(
                                "namespace" to "staging",
                                "image" to imageTag,
                                "replicas" to 2
                            ))
                        }
                        
                        updateProgress(8, 10, "Deployed to staging")
                    }
                }
                
                "production" -> {
                    steps {
                        // Approval step
                        input("Deploy to production?")
                        
                        echo("Deploying to production environment...")
                        
                        script {
                            val k8s = kubernetesLib.createClient()
                            val imageTag = "${env.get("DOCKER_REGISTRY")}/myapp:${env.get("APP_VERSION")}"
                            
                            // Blue-green deployment
                            k8s.blueGreenDeploy(
                                namespace = "production",
                                image = imageTag,
                                replicas = 5
                            )
                            
                            emitEvent("deployment", "production_deployed", mapOf(
                                "namespace" to "production",
                                "image" to imageTag,
                                "replicas" to 5,
                                "strategy" to "blue-green"
                            ))
                        }
                        
                        updateProgress(9, 10, "Deployed to production")
                    }
                }
            }
        }
        
        stage("Post-Deploy Tests", StageType.VALIDATION) {
            steps {
                script {
                    val environment = env.get("ENVIRONMENT")
                    val baseUrl = when (environment) {
                        "staging" -> "https://staging.myapp.com"
                        "production" -> "https://myapp.com"
                        else -> "http://localhost:8080"
                    }
                    
                    // Health check
                    val healthResponse = sh("curl -f $baseUrl/health")
                    
                    // API tests
                    sh("newman run api-tests.postman_collection.json --env-var baseUrl=$baseUrl")
                    
                    emitEvent("validation", "post_deploy_tests_completed", mapOf(
                        "environment" to environment,
                        "health_status" to "healthy",
                        "api_tests" to "passed"
                    ))
                }
                
                updateProgress(10, 10, "Post-deployment validation completed")
            }
        }
    }
    
    // Post-build actions
    post {
        always {
            script {
                // Archive logs
                archiveArtifacts("logs/**/*.log", allowEmptyArchive = true)
                
                // Cleanup
                sh("docker system prune -f")
                
                emitEvent("pipeline", "cleanup_completed", mapOf(
                    "artifacts_archived" to true,
                    "docker_cleaned" to true
                ))
            }
        }
        
        success {
            script {
                // Success notifications
                notification.slack(
                    channel = env.get("NOTIFICATION_CHANNEL"),
                    message = "✅ Pipeline succeeded for ${env.get("APP_VERSION")} in ${env.get("ENVIRONMENT")}",
                    color = "good"
                )
                
                notification.email(
                    to = "team@company.com",
                    subject = "Deployment Success: ${env.get("APP_VERSION")}",
                    body = """
                        The pipeline has completed successfully!
                        
                        Version: ${env.get("APP_VERSION")}
                        Environment: ${env.get("ENVIRONMENT")}
                        Duration: ${currentBuild.duration()}ms
                        
                        Build Number: ${currentBuild.number}
                    """.trimIndent()
                )
                
                emitEvent("pipeline", "success_notifications_sent", mapOf(
                    "slack" to true,
                    "email" to true
                ))
            }
        }
        
        failure {
            script {
                // Failure notifications
                notification.slack(
                    channel = "#alerts",
                    message = "❌ Pipeline failed for ${env.get("APP_VERSION")} in ${env.get("ENVIRONMENT")}",
                    color = "danger"
                )
                
                // Rollback logic for production
                if (env.get("ENVIRONMENT") == "production") {
                    val k8s = kubernetesLib.createClient()
                    k8s.rollbackDeployment("production", "myapp")
                    
                    emitEvent("deployment", "production_rollback", mapOf(
                        "reason" to "pipeline_failure",
                        "previous_version" to "previous"
                    ))
                }
            }
        }
        
        unstable {
            script {
                notification.slack(
                    channel = env.get("NOTIFICATION_CHANNEL"),
                    message = "⚠️ Pipeline unstable for ${env.get("APP_VERSION")}",
                    color = "warning"
                )
            }
        }
    }
}

// Custom functions available in the pipeline
suspend fun deployToProduction() {
    echo("Executing production deployment with additional checks...")
    
    // Custom deployment logic
    val healthChecks = listOf(
        "https://myapp.com/health",
        "https://myapp.com/ready",
        "https://myapp.com/metrics"
    )
    
    healthChecks.forEach { url ->
        val response = sh("curl -f $url")
        echo("Health check $url: OK")
    }
}

// Extension methods for better DSL
fun AdvancedPipelineContext.deploymentStatus(environment: String): String {
    return when (environment) {
        "staging" -> "ready"
        "production" -> "pending-approval"
        else -> "unknown"
    }
}