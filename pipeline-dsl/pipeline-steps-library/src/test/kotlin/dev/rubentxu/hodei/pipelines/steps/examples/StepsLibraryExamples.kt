package dev.rubentxu.hodei.pipelines.steps.examples

import dev.rubentxu.hodei.pipelines.dsl.builders.*
import dev.rubentxu.hodei.pipelines.steps.dsl.*

/**
 * Ejemplos de uso de la librería de steps compatible con Jenkins.
 * Muestra cómo usar todos los steps disponibles con sintaxis DSL natural.
 */

/**
 * Pipeline completo que demuestra workflow-basic-steps.
 */
fun workflowBasicStepsExample() = pipeline("basic-workflow-demo") {
    description("Demonstrates workflow-basic-steps usage")
    
    agent {
        label("linux")
    }
    
    environment {
        "BUILD_VERSION" to "1.0.0"
        "ENVIRONMENT" to "staging"
    }
    
    stages {
        stage("Initialization") {
            steps {
                // Echo message
                echo("Starting build process...")
                
                // Check if running on Unix
                val unixCheck = isUnix()
                echo("Unix check result stored in: $unixCheck")
                
                // Get current directory
                val currentDir = pwd()
                echo("Current directory: $currentDir")
                
                // File operations
                writeFile("build.properties", """
                    version=${env.BUILD_VERSION}
                    environment=${env.ENVIRONMENT}
                    timestamp=${System.currentTimeMillis()}
                """.trimIndent())
                
                val fileContent = readFile("build.properties")
                echo("Build properties: $fileContent")
                
                // Check if file exists
                val exists = fileExists("build.properties")
                echo("File exists check: $exists")
            }
        }
        
        stage("Build") {
            steps {
                // Execute in different directory
                dir("build") {
                    echo("Working in build directory")
                    
                    // Simulate build process
                    script("""
                        echo "Compiling sources..."
                        echo "Running tests..."
                        echo "Creating artifacts..."
                    """.trimIndent())
                    
                    // Handle potential errors
                    warnError("Build warnings detected") {
                        sh("exit 1") // This would normally fail
                    }
                }
                
                // Set milestone
                milestone(ordinal = 1, label = "Build Complete")
            }
        }
        
        stage("Test") {
            parallel(failFast = true) {
                "Unit Tests" {
                    sh("echo 'Running unit tests...'")
                    sleep(2) // Sleep for 2 seconds
                }
                
                "Integration Tests" {
                    sh("echo 'Running integration tests...'")
                    sleep(3)
                }
                
                "Quality Gates" {
                    retry(count = 3) {
                        sh("echo 'Running quality analysis...'")
                    }
                }
            }
        }
        
        stage("Deploy") {
            when {
                branch("main")
                environment("DEPLOY_ENABLED", "true")
            }
            
            steps {
                timeout(time = 10, unit = TimeUnit.MINUTES) {
                    // Load deployment tool
                    val kubectlPath = tool("kubectl", "kubernetes")
                    echo("Using kubectl at: $kubectlPath")
                    
                    // Deploy with environment variables
                    withEnv(
                        "KUBECONFIG=/home/user/.kube/config",
                        "NAMESPACE=staging"
                    ) {
                        sh("kubectl apply -f deployment.yaml")
                        
                        // Wait for deployment
                        waitUntil("deployment ready", quiet = false) {
                            sh("kubectl get pods -l app=myapp | grep Running")
                        }
                    }
                }
                
                milestone(ordinal = 2, label = "Deploy Complete")
            }
        }
        
        stage("Notify") {
            steps {
                // Trigger downstream job
                build("downstream-job") {
                    string("VERSION", env.BUILD_VERSION)
                    booleanParam("DEPLOY", true)
                }
                
                // Send notifications
                mail("team@company.com", "Build ${env.BUILD_NUMBER} Completed") {
                    body = """
                        Build ${env.BUILD_NUMBER} has completed successfully.
                        
                        Version: ${env.BUILD_VERSION}
                        Environment: ${env.ENVIRONMENT}
                        
                        View build: ${env.BUILD_URL}
                    """.trimIndent()
                    attachLog = true
                }
            }
        }
    }
    
    post {
        always {
            // Cleanup
            deleteDir()
        }
        
        failure {
            // Mark as unstable instead of failed for certain conditions
            script("""
                if (currentBuild.result == 'FAILURE') {
                    unstable('Build failed but marked as unstable')
                }
            """.trimIndent())
        }
        
        success {
            echo("🎉 Pipeline completed successfully!")
        }
    }
}

/**
 * Pipeline que demuestra pipeline-utility-steps.
 */
fun pipelineUtilityStepsExample() = pipeline("utility-steps-demo") {
    description("Demonstrates pipeline-utility-steps usage")
    
    stages {
        stage("File Operations") {
            steps {
                // JSON operations
                writeJSON("config.json", mapOf(
                    "version" to "1.0.0",
                    "features" to listOf("auth", "logging", "metrics"),
                    "database" to mapOf(
                        "host" to "localhost",
                        "port" to 5432
                    )
                ), pretty = true)
                
                val configData = readJSON("config.json")
                echo("Config loaded: $configData")
                
                // YAML operations
                writeYaml("deployment.yaml", mapOf(
                    "apiVersion" to "apps/v1",
                    "kind" to "Deployment",
                    "metadata" to mapOf("name" to "myapp"),
                    "spec" to mapOf(
                        "replicas" to 3,
                        "selector" to mapOf(
                            "matchLabels" to mapOf("app" to "myapp")
                        )
                    )
                ))
                
                val deploymentSpec = readYaml("deployment.yaml")
                echo("Deployment spec: $deploymentSpec")
                
                // Properties operations
                writeProperties("app.properties", mapOf(
                    "app.name" to "MyApplication",
                    "app.version" to "1.0.0",
                    "server.port" to "8080",
                    "database.url" to "jdbc:postgresql://localhost/mydb"
                ), comment = "Application configuration")
                
                val appProps = readProperties("app.properties", interpolate = true)
                echo("App properties: $appProps")
                
                // CSV operations
                writeCSV("users.csv", listOf(
                    mapOf("id" to 1, "name" to "John Doe", "email" to "john@example.com"),
                    mapOf("id" to 2, "name" to "Jane Smith", "email" to "jane@example.com"),
                    mapOf("id" to 3, "name" to "Bob Johnson", "email" to "bob@example.com")
                ))
                
                val userData = readCSV("users.csv")
                echo("User data: $userData")
            }
        }
        
        stage("Archive Operations") {
            steps {
                // Find files to archive
                val filesToArchive = findFiles("**/*.{json,yaml,properties,csv}")
                echo("Found files: $filesToArchive")
                
                // Create ZIP archive
                zip("artifacts.zip", "**/*.{json,yaml,properties,csv}")
                
                // Create TAR archive with compression
                tar("backup.tar.gz", "**/*", compress = true)
                
                // Test archive integrity
                unzip("artifacts.zip", test = true, quiet = false)
                
                // Extract specific files
                val extractedFiles = unzip("artifacts.zip", glob = "*.json", read = true)
                echo("Extracted JSON files: $extractedFiles")
            }
        }
        
        stage("Hash and Encoding") {
            steps {
                // Calculate file hashes
                val configSha256 = sha256("config.json")
                val deploymentMd5 = md5("deployment.yaml")
                
                echo("Config SHA256: $configSha256")
                echo("Deployment MD5: $deploymentMd5")
                
                // Base64 operations
                val encodedConfig = base64Encode("config.json")
                echo("Encoded config: $encodedConfig")
                
                base64Decode(encodedConfig, "decoded-config.json")
                echo("Config decoded and saved to decoded-config.json")
                
                // Hash text directly
                val textHash = sha1("Hello, World!", fromText = true)
                echo("Text SHA1: $textHash")
            }
        }
        
        stage("HTTP Operations") {
            steps {
                // Simple HTTP GET
                val apiResponse = httpRequest("https://api.github.com/repos/jenkinsci/jenkins")
                echo("GitHub API response: $apiResponse")
                
                // HTTP POST with JSON body
                val postResponse = httpRequest("https://httpbin.org/post") {
                    post()
                    json()
                    body("""{"message": "Hello from pipeline!"}""", "application/json")
                    timeout = 60
                }
                echo("POST response: $postResponse")
                
                // HTTP with authentication
                val authenticatedResponse = httpRequest("https://api.private.com/data") {
                    get()
                    auth("bearer-token-here")
                    json()
                }
                echo("Authenticated response: $authenticatedResponse")
            }
        }
        
        stage("Utility Operations") {
            steps {
                // Version comparison
                val versionComparison = compareVersions("1.2.3", "1.2.4")
                echo("Version comparison result: $versionComparison")
                
                // Node operations
                val availableNodes = nodesByLabel("linux")
                echo("Available Linux nodes: $availableNodes")
                
                // Touch files
                touch("lastrun.timestamp")
                touch("custom.timestamp", System.currentTimeMillis())
                
                // Library resources (if using Jenkins shared libraries)
                val libraryScript = libraryResource("scripts/deploy.sh")
                writeFile("deploy.sh", libraryScript)
                sh("chmod +x deploy.sh")
            }
        }
        
        stage("Trusted Operations") {
            steps {
                // Write sensitive data
                writeTrusted("secrets.txt", """
                    API_KEY=super-secret-key
                    DATABASE_PASSWORD=very-secure-password
                """.trimIndent())
                
                // Read trusted content
                val secrets = readTrusted("secrets.txt")
                echo("Secrets loaded (content hidden for security)")
                
                // Parse secrets as properties
                val secretProps = readProperties(secrets, fromText = true)
                echo("Secret properties parsed: ${secretProps.keys}")
            }
        }
    }
    
    post {
        always {
            // Archive all generated files
            archiveArtifacts("**/*.{json,yaml,properties,csv,zip,tar.gz}")
            
            // Cleanup sensitive files
            sh("rm -f secrets.txt decoded-config.json")
        }
    }
}

/**
 * Pipeline de CI/CD completo usando ambas librerías.
 */
fun fullCiCdPipelineExample() = pipeline("full-cicd-pipeline") {
    description("Complete CI/CD pipeline using both workflow-basic and utility steps")
    
    parameters {
        string("BRANCH", "main", "Branch to build")
        choice("ENVIRONMENT", listOf("dev", "staging", "prod"), "Target environment")
        booleanParam("SKIP_TESTS", false, "Skip test execution")
        booleanParam("DEPLOY", true, "Deploy after successful build")
    }
    
    environment {
        "BUILD_VERSION" to "${env.BUILD_NUMBER}"
        "GIT_BRANCH" to "${params.BRANCH}"
        "TARGET_ENV" to "${params.ENVIRONMENT}"
    }
    
    stages {
        stage("Checkout & Setup") {
            steps {
                echo("🚀 Starting CI/CD pipeline for ${params.BRANCH}")
                
                // Checkout code
                checkout(scm = SCMConfig.Git(
                    url = "https://github.com/company/myapp.git",
                    branch = params.BRANCH,
                    shallow = true,
                    depth = 1
                ))
                
                // Load build configuration
                val buildConfig = readYaml("build.yaml")
                echo("Build configuration loaded: $buildConfig")
                
                // Generate build metadata
                writeJSON("build-metadata.json", mapOf(
                    "buildNumber" to env.BUILD_NUMBER,
                    "branch" to params.BRANCH,
                    "environment" to params.ENVIRONMENT,
                    "timestamp" to System.currentTimeMillis(),
                    "gitCommit" to env.GIT_COMMIT
                ), pretty = true)
                
                milestone(1, "Checkout Complete")
            }
        }
        
        stage("Build") {
            steps {
                dir("app") {
                    withEnv("NODE_ENV=production", "BUILD_VERSION=${env.BUILD_VERSION}") {
                        sh("npm ci")
                        sh("npm run build")
                        
                        // Calculate build artifacts hash
                        val buildHash = sha256("dist/app.js")
                        echo("Build artifact hash: $buildHash")
                        
                        // Store build info
                        writeProperties("build.properties", mapOf(
                            "version" to env.BUILD_VERSION,
                            "hash" to buildHash,
                            "timestamp" to System.currentTimeMillis().toString()
                        ))
                    }
                }
                
                milestone(2, "Build Complete")
            }
        }
        
        stage("Test") {
            when {
                not {
                    expression { params.SKIP_TESTS }
                }
            }
            
            parallel(failFast = true) {
                "Unit Tests" {
                    dir("app") {
                        sh("npm run test:unit")
                        
                        // Publish test results
                        publishTestResults("test-results.xml")
                    }
                }
                
                "Integration Tests" {
                    timeout(time = 15, unit = TimeUnit.MINUTES) {
                        sh("docker-compose up -d postgres redis")
                        
                        try {
                            waitUntil("services ready") {
                                sh("docker-compose ps | grep healthy")
                            }
                            
                            dir("app") {
                                sh("npm run test:integration")
                            }
                        } finally {
                            sh("docker-compose down")
                        }
                    }
                }
                
                "Quality Analysis" {
                    dir("app") {
                        sh("npm run lint")
                        sh("npm run security-scan")
                        
                        // Archive quality reports
                        zip("quality-reports.zip", "reports/**/*")
                    }
                }
            }
            
            milestone(3, "Testing Complete")
        }
        
        stage("Package") {
            steps {
                // Create deployment package
                tar("app-${env.BUILD_VERSION}.tar.gz", "app/dist/**/*", compress = true)
                
                // Create Docker image
                dir("app") {
                    sh("docker build -t myapp:${env.BUILD_VERSION} .")
                    sh("docker tag myapp:${env.BUILD_VERSION} myapp:latest")
                }
                
                // Generate deployment manifest
                writeYaml("k8s-deployment.yaml", mapOf(
                    "apiVersion" to "apps/v1",
                    "kind" to "Deployment",
                    "metadata" to mapOf(
                        "name" to "myapp",
                        "namespace" to params.ENVIRONMENT
                    ),
                    "spec" to mapOf(
                        "replicas" to (if (params.ENVIRONMENT == "prod") 5 else 2),
                        "template" to mapOf(
                            "spec" to mapOf(
                                "containers" to listOf(
                                    mapOf(
                                        "name" to "myapp",
                                        "image" to "myapp:${env.BUILD_VERSION}",
                                        "ports" to listOf(mapOf("containerPort" to 3000))
                                    )
                                )
                            )
                        )
                    )
                ))
                
                milestone(4, "Packaging Complete")
            }
        }
        
        stage("Deploy") {
            when {
                allOf {
                    expression { params.DEPLOY }
                    anyOf {
                        branch("main")
                        branch("develop")
                    }
                }
            }
            
            steps {
                timeout(time = 20, unit = TimeUnit.MINUTES) {
                    // Deploy to Kubernetes
                    withEnv("KUBECONFIG=/home/jenkins/.kube/config") {
                        sh("kubectl apply -f k8s-deployment.yaml")
                        
                        // Wait for rollout
                        waitUntil("deployment ready") {
                            sh("kubectl rollout status deployment/myapp -n ${params.ENVIRONMENT}")
                        }
                        
                        // Smoke tests
                        retry(count = 5) {
                            val healthCheck = httpRequest("http://myapp.${params.ENVIRONMENT}.company.com/health") {
                                get()
                                json()
                                timeout = 30
                            }
                            echo("Health check: $healthCheck")
                        }
                    }
                }
                
                milestone(5, "Deployment Complete")
            }
        }
        
        stage("Post-Deploy") {
            steps {
                // Trigger downstream jobs
                build("integration-tests") {
                    string("APP_VERSION", env.BUILD_VERSION)
                    string("ENVIRONMENT", params.ENVIRONMENT)
                }
                
                // Update monitoring
                httpRequest("https://monitoring.company.com/api/deployments") {
                    post()
                    json()
                    body("""
                        {
                            "application": "myapp",
                            "version": "${env.BUILD_VERSION}",
                            "environment": "${params.ENVIRONMENT}",
                            "timestamp": ${System.currentTimeMillis()}
                        }
                    """.trimIndent())
                    auth("api-key-here")
                }
                
                // Send notifications
                mail("team@company.com", "Deployment ${env.BUILD_VERSION} to ${params.ENVIRONMENT}") {
                    body = """
                        🚀 Deployment Successful!
                        
                        Application: MyApp
                        Version: ${env.BUILD_VERSION}
                        Environment: ${params.ENVIRONMENT}
                        Branch: ${params.BRANCH}
                        
                        Build URL: ${env.BUILD_URL}
                        App URL: http://myapp.${params.ENVIRONMENT}.company.com
                    """.trimIndent()
                }
            }
        }
    }
    
    post {
        always {
            // Archive artifacts
            archiveArtifacts("**/*.{json,yaml,tar.gz,zip,properties}")
            
            // Cleanup
            sh("docker system prune -f")
            deleteDir()
        }
        
        failure {
            mail("team@company.com", "🔥 Pipeline Failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}") {
                body = """
                    Pipeline failed at stage: ${env.STAGE_NAME}
                    
                    Build: ${env.BUILD_URL}
                    Console: ${env.BUILD_URL}console
                    
                    Please investigate and fix the issue.
                """.trimIndent()
                attachLog = true
            }
        }
        
        success {
            echo("🎉 Pipeline completed successfully!")
            
            if (params.ENVIRONMENT == "prod") {
                // Create Git tag for production releases
                sh("git tag -a v${env.BUILD_VERSION} -m 'Release ${env.BUILD_VERSION}'")
                sh("git push origin v${env.BUILD_VERSION}")
            }
        }
    }
}