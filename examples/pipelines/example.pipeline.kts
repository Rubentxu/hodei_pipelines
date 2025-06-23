// Pipeline DSL Script Demonstration
// This is a Kotlin script with full type safety and IDE support

import com.hodei.pipeline.dsl.domain.dsl.pipeline

pipeline("Advanced CI/CD Pipeline") {
    description("Comprehensive CI/CD pipeline demonstrating the Pipeline DSL capabilities")
    
    agent {
        docker("openjdk:17")
    }
    
    environment {
        "JAVA_HOME" to "/usr/lib/jvm/java-17-openjdk"
        "GRADLE_OPTS" to "-Xmx2g"
        "CI" to "true"
    }
    
    stages {
        stage("🔍 Checkout") {
            steps {
                sh("echo 'Checking out source code'")
                sh("git --version")
                sh("pwd && ls -la")
            }
        }
        
        stage("🏗️ Build") {
            steps {
                sh("echo 'Building application'")
                sh("echo 'Compiling Kotlin sources...'")
                sh("sleep 2")  // Simulate build time
                sh("echo 'Build completed successfully'")
                archiveArtifacts("target/*.jar")
            }
            produces("build-artifacts")
        }
        
        stage("🧪 Testing") {
            requires("build-artifacts")
            parallel {
                stage("Unit Tests") {
                    steps {
                        sh("echo 'Running unit tests'")
                        sh("echo 'Testing core functionality...'")
                        sh("sleep 1")
                        publishTestResults("target/test-results/*.xml")
                    }
                }
                stage("Integration Tests") {
                    steps {
                        sh("echo 'Running integration tests'")
                        sh("echo 'Testing API endpoints...'")
                        sh("sleep 1")
                        publishTestResults("target/integration-test-results/*.xml")
                    }
                }
                stage("Security Tests") {
                    steps {
                        sh("echo 'Running security scans'")
                        sh("echo 'Scanning for vulnerabilities...'")
                        sh("sleep 1")
                    }
                }
            }
            produces("test-reports")
        }
        
        stage("📊 Quality Gates") {
            requires("test-reports")
            steps {
                sh("echo 'Analyzing code quality'")
                sh("echo 'Code coverage: 85%'")
                sh("echo 'Quality gate passed'")
            }
        }
        
        stage("🚀 Deploy to Staging") {
            `when` {
                branch("main", "develop")
            }
            requires("build-artifacts")
            steps {
                sh("echo 'Deploying to staging environment'")
                sh("echo 'Starting deployment...'")
                sh("sleep 2")
                sh("echo 'Staging deployment complete'")
            }
            produces("staging-deployment")
        }
        
        stage("🧑‍💻 Smoke Tests") {
            requires("staging-deployment")
            steps {
                sh("echo 'Running smoke tests'")
                sh("echo 'Testing critical paths...'")
                sh("echo 'All smoke tests passed'")
            }
        }
        
        stage("🌍 Deploy to Production") {
            `when` {
                branch("main")
                tag("v*")
            }
            requires("staging-deployment")
            steps {
                sh("echo 'Deploying to production'")
                sh("echo 'Blue-green deployment in progress...'")
                sh("sleep 3")
                sh("echo 'Production deployment complete'")
            }
        }
    }
    
    post {
        always {
            sh("echo 'Pipeline execution completed'")
            sh("echo 'Cleaning up temporary files'")
        }
        success {
            sh("echo '✅ Pipeline succeeded!'")
            sh("echo 'Sending success notifications'")
        }
        failure {
            sh("echo '❌ Pipeline failed!'")
            sh("echo 'Sending failure notifications'")
        }
        unstable {
            sh("echo '⚠️ Pipeline unstable'")
            sh("echo 'Sending warning notifications'")
        }
    }
}