// Simple Pipeline DSL Example
// Minimal pipeline demonstrating basic features

import com.hodei.pipeline.dsl.domain.dsl.pipeline

pipeline("Simple Build") {
    description("Basic build pipeline")
    
    stages {
        stage("Build") {
            steps {
                sh("echo 'Building project'")
                sh("gradle clean build")
            }
        }
        
        stage("Test") {
            steps {
                sh("echo 'Running tests'")
                sh("gradle test")
            }
        }
        
        stage("Package") {
            steps {
                sh("echo 'Creating package'")
                sh("gradle jar")
                archiveArtifacts("build/libs/*.jar")
            }
        }
    }
    
    post {
        always {
            sh("echo 'Cleanup complete'")
        }
    }
}